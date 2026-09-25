package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.stream.IntStream;

/**
 * Exact enumeration of every stop combination of a machine (SLOTS.md §7.5, the reference {@code engine.c} of
 * {@code docs/design/slots-math}): Σ spin pay, hit counts, scatter / coin histograms, bonus triggers, tumble
 * histogram, 5-of-a-kind of the top symbol and, for End free spins, the sticky-mask transitions. Integer-exact.
 *
 * <p>Non-tumbling machines use per-reel precomputed counts with incremental way products (≈ 10⁸ leaves in a few
 * seconds); Nether evaluates the tumble chain of every initially winning combination with an allocation-free
 * evaluator. Used by the slow unit tests and by {@code slots.validateRtp} (when the config differs from the
 * defaults). Runs in parallel over reel 1.
 */
public final class SlotEnumerator {
	private SlotEnumerator() {}

	/** Which spin to enumerate. */
	public enum Mode {
		/** Base game (base ladder on Nether). */
		BASE,
		/** One free spin: Nether free-spin ladder; End with the sticky mask and expanding Eggs; Overworld = base. */
		FREE
	}

	/**
	 * Totals over all {@code n} combinations (engine.c's JSON fields).
	 *
	 * @param post     End free spins: counts by [mask after][retrigger 0/1]
	 * @param postPay  Σ pay by the same keys
	 * @param maxPay   largest single-spin pay (fifths)
	 */
	public record Stats(long n, long sumPay, long hitPay, long hitAny, long[] scat, long bonus, long[] coins, long[] tumbles, long maxPay,
			long fiveTop, long[][] post, long[][] postPay) {
		static Stats empty() {
			return new Stats(0, 0, 0, 0, new long[16], 0, new long[16], new long[64], 0, 0, new long[8][2], new long[8][2]);
		}

		Stats plus(Stats o) {
			long[] sc = scat.clone();
			long[] co = coins.clone();
			long[] tu = tumbles.clone();
			for (int i = 0; i < 16; i++) {
				sc[i] += o.scat[i];
				co[i] += o.coins[i];
			}
			for (int i = 0; i < 64; i++) tu[i] += o.tumbles[i];
			long[][] p = new long[8][2];
			long[][] pp = new long[8][2];
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 2; j++) {
					p[i][j] = post[i][j] + o.post[i][j];
					pp[i][j] = postPay[i][j] + o.postPay[i][j];
				}
			}
			return new Stats(n + o.n, sumPay + o.sumPay, hitPay + o.hitPay, hitAny + o.hitAny, sc, bonus + o.bonus, co, tu,
				Math.max(maxPay, o.maxPay), fiveTop + o.fiveTop, p, pp);
		}

		/** P(k scatters) with k = 5 meaning "5 or more". */
		public double pScatter(int k) {
			if (k < 5) return (double) scat[k] / n;
			long s = 0;
			for (int i = 5; i < 16; i++) s += scat[i];
			return (double) s / n;
		}
	}

	/** Enumerates {@code mode} with the End sticky mask {@code stickyMask} (bits 0–2 = reels 2–4). */
	public static Stats enumerate(MachineDef def, Mode mode, int stickyMask) {
		boolean tumbling = def.tumbles();
		int[] ladder = mode == Mode.FREE ? def.ladderFree() : def.ladder();
		boolean expand = mode == Mode.FREE && def.stickyWilds();
		Pre pre = new Pre(def, expand, stickyMask, !tumbling);
		return IntStream.range(0, pre.rep[0].length).parallel()
			.mapToObj(t0 -> new Worker(def, pre, tumbling, ladder, expand).run(t0))
			.reduce(Stats.empty(), Stats::plus);
	}

	/** Per-reel precomputation (windows after the End expansion transform). */
	private static final class Pre {
		final int np;
		final int[] paySym;
		final int[][] pay; // [pi][k-3]
		final int[][][] cnt; // [r][t][pi]
		final int[][] scat; // [r][t]
		final int[][] coins; // [r][t]
		final int[] bonusBits; // [r] -> per t bit? stored as boolean arrays below
		final boolean[][] bonusHit; // [r][t]
		final int[][] wildBit; // [r][t] End post-mask bit
		final int[] scatPay;
		final int bonusMask;
		final int holdTrigger;
		final int topPi;
		/** Class → representative stop, and class weight (identical reel windows are merged when not tumbling). */
		final int[][] rep;
		final long[][] wt;

		Pre(MachineDef def, boolean expand, int stickyMask, boolean compress) {
			SymbolRole[] roles = def.roles();
			int n = 0;
			for (SymbolRole r : roles) if (r == SymbolRole.PAY) n++;
			np = n;
			paySym = new int[n];
			pay = new int[n][];
			int i = 0;
			for (int s = 0; s < roles.length; s++) {
				if (roles[s] == SymbolRole.PAY) {
					paySym[i] = s;
					pay[i] = def.paysFifths()[s];
					i++;
				}
			}
			int top = def.topSymbol();
			int tp = 0;
			for (int k = 0; k < n; k++) if (paySym[k] == top) tp = k;
			topPi = tp;
			int wild = def.wild();
			int sc = def.scatter();
			int co = def.coin();
			int bo = def.bonus();
			cnt = new int[5][][];
			scat = new int[5][];
			coins = new int[5][];
			bonusHit = new boolean[5][];
			wildBit = new int[5][];
			bonusBits = new int[5];
			for (int r = 0; r < 5; r++) {
				int len = def.stripLength(r);
				cnt[r] = new int[len][n];
				scat[r] = new int[len];
				coins[r] = new int[len];
				bonusHit[r] = new boolean[len];
				wildBit[r] = new int[len];
				for (int t = 0; t < len; t++) {
					int[] w = {def.symbolAt(r, t, 0), def.symbolAt(r, t, 1), def.symbolAt(r, t, 2)};
					if (expand && r >= 1 && r <= 3) {
						boolean has = (stickyMask >> (r - 1) & 1) != 0;
						for (int x : w) has |= x == wild;
						if (has) {
							w = new int[] {wild, wild, wild};
							wildBit[r][t] = 1 << (r - 1);
						}
					}
					for (int x : w) {
						if (x == sc) scat[r][t]++;
						if (x == co) coins[r][t]++;
						if (x == bo && (def.bonusReelsMask() >> r & 1) != 0) bonusHit[r][t] = true;
						for (int k = 0; k < n; k++) if (x == paySym[k] || (x == wild && r > 0)) cnt[r][t][k]++;
					}
				}
			}
			rep = new int[5][];
			wt = new long[5][];
			for (int r = 0; r < 5; r++) {
				int len = def.stripLength(r);
				java.util.LinkedHashMap<String, Integer> key = new java.util.LinkedHashMap<>();
				java.util.List<Integer> reps = new java.util.ArrayList<>();
				java.util.List<Long> ws = new java.util.ArrayList<>();
				for (int t = 0; t < len; t++) {
					String k = compress ? java.util.Arrays.toString(cnt[r][t]) + "/" + scat[r][t] + "/" + coins[r][t] + "/" + bonusHit[r][t] + "/" + wildBit[r][t]
						: Integer.toString(t);
					Integer c = key.get(k);
					if (c == null) {
						key.put(k, reps.size());
						reps.add(t);
						ws.add(1L);
					} else {
						ws.set(c, ws.get(c) + 1);
					}
				}
				rep[r] = reps.stream().mapToInt(Integer::intValue).toArray();
				wt[r] = ws.stream().mapToLong(Long::longValue).toArray();
				int[][] c2 = new int[rep[r].length][];
				int[] s2 = new int[rep[r].length];
				int[] co2 = new int[rep[r].length];
				boolean[] b2 = new boolean[rep[r].length];
				int[] w2 = new int[rep[r].length];
				for (int i2 = 0; i2 < rep[r].length; i2++) {
					int t = rep[r][i2];
					c2[i2] = cnt[r][t];
					s2[i2] = scat[r][t];
					co2[i2] = coins[r][t];
					b2[i2] = bonusHit[r][t];
					w2[i2] = wildBit[r][t];
				}
				cnt[r] = c2;
				scat[r] = s2;
				coins[r] = co2;
				bonusHit[r] = b2;
				wildBit[r] = w2;
			}
			scatPay = def.scatterFifths();
			bonusMask = def.bonusReelsMask();
			holdTrigger = def.features().holdTrigger();
		}
	}

	private static final class Worker {
		final MachineDef def;
		final Pre p;
		final boolean tumbling;
		final int[] ladder;
		final boolean expand;
		long n, sumPay, hitPay, hitAny, bonus, maxPay, five;
		final long[] scat = new long[16];
		final long[] coins = new long[16];
		final long[] tumbles = new long[64];
		final long[][] post = new long[8][2];
		final long[][] postPay = new long[8][2];
		// tumble evaluator scratch
		final int[] cells = new int[15];
		final int[] top = new int[5];
		int lastMask;
		boolean lastFive;

		Worker(MachineDef def, Pre p, boolean tumbling, int[] ladder, boolean expand) {
			this.def = def;
			this.p = p;
			this.tumbling = tumbling;
			this.ladder = ladder;
			this.expand = expand;
		}

		Stats run(int t0) {
			int np = p.np;
			long[][] prod = new long[6][np];
			int[] alive = new int[6];
			long[] partial = new long[6];
			int[] L = new int[5];
			for (int r = 0; r < 5; r++) L[r] = p.rep[r].length;
			long w0 = p.wt[0][t0];
			int full = (1 << np) - 1;
			// reel 0
			{
				int a = 0;
				int[] c = p.cnt[0][t0];
				for (int k = 0; k < np; k++) {
					if (c[k] > 0) {
						a |= 1 << k;
						prod[1][k] = c[k];
					}
				}
				alive[1] = a & full;
				partial[1] = 0;
			}
			int[] st = new int[5];
			st[0] = p.rep[0][t0];
			for (int t1 = 0; t1 < L[1]; t1++) {
				st[1] = p.rep[1][t1];
				long w01 = w0 * p.wt[1][t1];
				step(1, t1, prod, alive, partial);
				for (int t2 = 0; t2 < L[2]; t2++) {
					st[2] = p.rep[2][t2];
					long w012 = w01 * p.wt[2][t2];
					step(2, t2, prod, alive, partial);
					for (int t3 = 0; t3 < L[3]; t3++) {
						st[3] = p.rep[3][t3];
						long w0123 = w012 * p.wt[3][t3];
						step(3, t3, prod, alive, partial);
						int s0123 = p.scat[0][t0] + p.scat[1][t1] + p.scat[2][t2] + p.scat[3][t3];
						int c0123 = p.coins[0][t0] + p.coins[1][t1] + p.coins[2][t2] + p.coins[3][t3];
						int w123 = p.wildBit[1][t1] | p.wildBit[2][t2] | p.wildBit[3][t3];
						int b0123 = (p.bonusHit[0][t0] ? 1 : 0) | (p.bonusHit[1][t1] ? 2 : 0) | (p.bonusHit[2][t2] ? 4 : 0) | (p.bonusHit[3][t3] ? 8 : 0);
						int a4 = alive[4];
						long base4 = partial[4];
						long[] pr4 = prod[4];
						for (int t4 = 0; t4 < L[4]; t4++) {
							st[4] = p.rep[4][t4];
							long w = w0123 * p.wt[4][t4];
							long pay = base4;
							boolean fiveTop = false;
							if (a4 != 0) {
								int[] c = p.cnt[4][t4];
								for (int k = 0; k < np; k++) {
									if ((a4 >> k & 1) == 0) continue;
									int[] pk = p.pay[k];
									if (c[k] > 0) {
										pay += pr4[k] * c[k] * pk[2];
										if (k == p.topPi) fiveTop = true;
									} else {
										pay += pr4[k] * pk[1];
									}
								}
							}
							int sc = s0123 + p.scat[4][t4];
							int co = c0123 + p.coins[4][t4];
							int bmask = b0123 | (p.bonusHit[4][t4] ? 16 : 0);
							int tumb = 0;
							if (tumbling && pay > 0) {
								pay = chain(st);
								tumb = lastTumbles;
								sc = lastScat;
								co = lastCoins;
								fiveTop = lastFive;
								bmask = 0; // no bonus symbol on the tumbling machine
							}
							if (sc >= 3) pay += p.scatPay[Math.min(sc, 5) - 3];
							boolean bon = p.bonusMask != 0 && (bmask & p.bonusMask) == p.bonusMask;
							n += w;
							sumPay += pay * w;
							if (pay > 0) hitPay += w;
							if (pay > 0 || sc >= 3 || bon || (p.holdTrigger > 0 && co >= p.holdTrigger)) hitAny += w;
							scat[Math.min(sc, 15)] += w;
							coins[Math.min(co, 15)] += w;
							if (bon) bonus += w;
							tumbles[Math.min(tumb, 63)] += w;
							if (pay > maxPay) maxPay = pay;
							if (fiveTop) five += w;
							if (expand) {
								int rt = sc >= 3 ? 1 : 0;
								post[w123][rt] += w;
								postPay[w123][rt] += pay * w;
							}
						}
					}
				}
			}
			return new Stats(n, sumPay, hitPay, hitAny, scat, bonus, coins, tumbles, maxPay, five, post, postPay);
		}

		private void step(int d, int t, long[][] prod, int[] alive, long[] partial) {
			int a = alive[d];
			int na = 0;
			long part = partial[d];
			int[] c = p.cnt[d][t];
			long[] src = prod[d];
			long[] dst = prod[d + 1];
			for (int k = 0; k < p.np; k++) {
				if ((a >> k & 1) == 0) continue;
				if (c[k] > 0) {
					dst[k] = src[k] * c[k];
					na |= 1 << k;
				} else if (d >= 3) {
					part += src[k] * p.pay[k][d - 3];
				}
			}
			alive[d + 1] = na;
			partial[d + 1] = part;
		}

		int lastTumbles;
		int lastScat;
		int lastCoins;

		/** Allocation-free tumble chain (Nether) from the stops; sets the last* fields. Returns Σ way pay (fifths). */
		private long chain(int[] st) {
			for (int r = 0; r < 5; r++) {
				top[r] = st[r];
				for (int y = 0; y < 3; y++) cells[r * 3 + y] = def.symbolAt(r, st[r], y);
			}
			long total = 0;
			boolean five = false;
			int step = 0;
			for (;;) {
				long pay = eval();
				five |= lastFive;
				if (pay == 0) break;
				total += pay * Tumble.multiplier(ladder, step);
				step++;
				if (step >= Tumble.MAX_STEPS) break;
				Tumble.refill(def, cells, top, lastMask);
			}
			int sc = def.scatter();
			int co = def.coin();
			int s = 0;
			int c = 0;
			for (int i = 0; i < 15; i++) {
				if (cells[i] == sc) s++;
				if (cells[i] == co) c++;
			}
			lastTumbles = Math.min(step, Tumble.MAX_STEPS - 1);
			lastScat = s;
			lastCoins = c;
			lastFive = five;
			return total;
		}

		private long eval() {
			int wild = def.wild();
			long pay = 0;
			int mask = 0;
			boolean five = false;
			for (int k = 0; k < p.np; k++) {
				int s = p.paySym[k];
				int ways = 1;
				int kk = 0;
				for (int r = 0; r < 5; r++) {
					int cnt = 0;
					for (int y = 0; y < 3; y++) {
						int x = cells[r * 3 + y];
						if (x == s || (x == wild && r > 0)) cnt++;
					}
					if (cnt == 0) break;
					ways *= cnt;
					kk++;
				}
				if (kk >= 3 && p.pay[k][kk - 3] > 0) {
					pay += (long) ways * p.pay[k][kk - 3];
					if (kk == 5 && k == p.topPi) five = true;
					for (int r = 0; r < kk; r++) {
						for (int y = 0; y < 3; y++) {
							int x = cells[r * 3 + y];
							if (x == s || (x == wild && r > 0)) mask |= 1 << (r * 3 + y);
						}
					}
				}
			}
			lastMask = mask;
			lastFive = five;
			return pay;
		}
	}
}
