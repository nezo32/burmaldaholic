package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * CONFIRM → DRAW TAPE → (streak re-draw) → reserve/award jackpots → PERSIST (SLOTS.md §1.2). Pure: the
 * module passes the jackpot view and receives the tape; it then debits pools, persists and presents.
 *
 * <p><b>Draw order</b> (normative for the cross-edition vectors {@code slots_engine.json}; Bedrock's
 * {@code drawSpin} consumes the RNG in exactly this order):
 * <ol>
 *   <li>base spin: {@code nextInt(L_r)} for reels 1…5 (skipped for a bought feature);</li>
 *   <li>bonus game on the final base window (Hoard first when both trigger, SLOTS.md §3.2; same order on every
 *       machine): Treasure Hunt = {@code board} × {@code weighted(pickWeights)}; Piglin's Hoard = one
 *       {@code weighted(holdWeights)} per initial coin (ascending cell), then per respin for every empty cell in
 *       ascending order {@code nextInt(1 000 000) < coinPpm}, then one {@code weighted} per new coin; Dragon Wheel
 *       = {@code nextInt(ring length)} per ring reached;</li>
 *   <li>free spins: 5 stops per spin, until the spins run out or the max-win cap is reached.</li>
 * </ol>
 * Jackpots are awarded in tape order (hunt reveal order, hoard coins as above then the Grand for a full grid,
 * the wheel). Progressive awards: {@code floor(pool × min(bet, ref) / ref)}; a second hit of the same tier comes
 * from the pool after the first ({@link Jackpots#poolAfter}). Owned machines: fixed multiples inside the cap.
 */
public final class SlotDraw {
	private SlotDraw() {}

	/**
	 * @param def    machine definition
	 * @param bet    total bet (on the ladder, multiple of 5); for a buy, the underlying bet
	 * @param buy    buy feature instead of a base spin
	 * @param owned  owned-casino machine (fixed jackpots, no pools, SLOTS.md §5.2)
	 * @param pools  current pool values ({@code seed + increment}) per tier 1..4 (index 0 unused); ignored when owned
	 */
	public record Request(MachineDef def, long bet, boolean buy, boolean owned, long[] pools) {}

	public static SpinTape draw(Request q, SlotRng rng) {
		MachineDef def = q.def();
		MachineDef.Features f = def.features();
		Acc acc = new Acc(q, (long) def.capMultiple() * 5);
		int[] stops = new int[0];
		SpinTape.Hunt hunt = null;
		SpinTape.Hoard hoard = null;
		SpinTape.Wheel wheel = null;
		int fsAward;
		if (!q.buy()) {
			stops = new int[5];
			for (int r = 0; r < 5; r++) stops[r] = rng.nextInt(def.stripLength(r));
			SpinEval base = SpinEval.of(def, stops, false, 0);
			acc.add(base.payFifths());
			if (!acc.capHit) {
				switch (def.machine()) {
					case OVERWORLD -> {
						if (base.bonus() && f.pickBoard() > 0) hunt = hunt(f, rng, acc);
					}
					case NETHER -> {
						if (f.holdTrigger() > 0 && base.coins() >= f.holdTrigger()) hoard = hoard(f, base.coinMask(), rng, acc);
					}
					case END -> {
						if (base.bonus() && f.wheelRings().length > 0) wheel = wheel(f, rng, acc);
					}
				}
			}
			fsAward = base.scatters() >= 3 ? def.freeSpins()[Math.min(base.scatters(), 5) - 3] : 0;
		} else {
			if (def.buyPriceFifths() <= 0) throw new IllegalArgumentException(def.machine().id + ": no buy feature");
			fsAward = def.freeSpins()[0];
		}
		SpinTape.FreeSpins fs = fsAward > 0 && !acc.capHit ? freeSpins(def, fsAward, acc, rng) : null;
		return new SpinTape(def.machine(), q.bet(), q.buy(), stops, fs, hunt, hoard, wheel, acc.jackpots, acc.total, acc.capHit);
	}

	/** Running total (fifths, capped) and jackpot awards of one draw. */
	private static final class Acc {
		final Request q;
		final long capFifths;
		final long[] pools;
		long total;
		boolean capHit;
		final List<SpinTape.JackpotAward> jackpots = new ArrayList<>();

		Acc(Request q, long capFifths) {
			this.q = q;
			this.capFifths = capFifths;
			this.pools = q.pools() == null ? new long[0] : q.pools().clone();
		}

		/** Adds fifths; true once the cap is reached (the feature ends at once, SLOTS.md §1.3). */
		boolean add(long f) {
			if (capHit) return true;
			total += f;
			if (total >= capFifths) {
				total = capFifths;
				capHit = true;
			}
			return capHit;
		}

		/** A prize code: positive = × bet, negative = jackpot tier, 0 = nothing. True when the cap is reached. */
		boolean prize(int value) {
			return value > 0 ? add(value * 5L) : value < 0 ? jackpot(-value) : capHit;
		}

		boolean jackpot(int tier) {
			MachineDef.Features f = q.def().features();
			if (q.owned()) {
				long fifths = f.ownedMult()[tier - 1] * 5L;
				jackpots.add(new SpinTape.JackpotAward(tier, fifths * q.bet() / 5, true));
				return add(fifths);
			}
			long seed = f.seedChips(tier);
			long pool = tier < pools.length ? pools[tier] : seed;
			long chips = Jackpots.awardFromPool(pool, seed, q.bet(), f.jackpotRef());
			if (tier < pools.length) pools[tier] = Jackpots.poolAfter(pool, seed, q.bet(), f.jackpotRef());
			jackpots.add(new SpinTape.JackpotAward(tier, chips, false));
			return capHit;
		}
	}

	private static SpinTape.Hunt hunt(MachineDef.Features f, SlotRng rng, Acc acc) {
		int[] entries = new int[f.pickBoard()];
		for (int i = 0; i < entries.length; i++) entries[i] = f.pickValues()[rng.weighted(f.pickWeights())];
		for (int e : entries) {
			if (e == 0 || acc.prize(e)) break;
		}
		return new SpinTape.Hunt(entries, 0); // opened = pick PROGRESS (0 at draw), persisted per pick
	}

	private static SpinTape.Hoard hoard(MachineDef.Features f, int coinMask, SlotRng rng, Acc acc) {
		int n = Integer.bitCount(coinMask);
		int[] cells = new int[n];
		int[] values = new int[n];
		int filled = coinMask;
		for (int i = 0, c = 0; c < 15; c++) {
			if ((coinMask >> c & 1) != 0) {
				cells[i] = c;
				values[i] = f.holdValues()[rng.weighted(f.holdWeights())];
				i++;
			}
		}
		List<int[]> rc = new ArrayList<>();
		List<int[]> rv = new ArrayList<>();
		int left = f.holdRespins();
		while (left > 0 && n < 15) {
			int[] tc = new int[15];
			int[] tv = new int[15];
			int k = 0;
			for (int c = 0; c < 15; c++) {
				if ((filled >> c & 1) != 0) continue;
				if (rng.nextInt(1_000_000) < f.holdCoinPpm()) {
					filled |= 1 << c;
					tc[k] = c;
					tv[k] = f.holdValues()[rng.weighted(f.holdWeights())];
					k++;
				}
			}
			rc.add(java.util.Arrays.copyOf(tc, k));
			rv.add(java.util.Arrays.copyOf(tv, k));
			n += k;
			left = k > 0 ? f.holdRespins() : left - 1;
		}
		// collect in reveal order: locked coins, then each respin's coins; all 15 → Grand
		boolean capped = false;
		for (int v : values) if (!capped) capped = acc.prize(v);
		for (int[] vs : rv) for (int v : vs) if (!capped) capped = acc.prize(v);
		if (!acc.capHit && n >= 15) acc.jackpot(Jackpots.GRAND);
		return new SpinTape.Hoard(cells, values, rc, rv);
	}

	private static SpinTape.Wheel wheel(MachineDef.Features f, SlotRng rng, Acc acc) {
		int[][] rings = f.wheelRings();
		List<Integer> segs = new ArrayList<>();
		for (int ring = 0; ring < rings.length; ring++) {
			int seg = rng.nextInt(rings[ring].length);
			segs.add(seg);
			int v = rings[ring][seg];
			if (v == 0 && ring < rings.length - 1) continue;
			acc.prize(v);
			break;
		}
		return new SpinTape.Wheel(segs.stream().mapToInt(Integer::intValue).toArray());
	}

	private static SpinTape.FreeSpins freeSpins(MachineDef def, int awarded, Acc acc, SlotRng rng) {
		List<SpinTape.FreeSpin> spins = new ArrayList<>();
		int remaining = Math.min(awarded, def.fsCap());
		int given = remaining;
		int sticky = 0;
		long pay = 0;
		while (remaining > 0 && !acc.capHit) {
			remaining--;
			int[] st = new int[5];
			for (int r = 0; r < 5; r++) st[r] = rng.nextInt(def.stripLength(r));
			SpinEval e = SpinEval.of(def, st, true, sticky);
			long p = e.payFifths() * def.fsMultiplier();
			sticky = e.stickyAfter();
			boolean retrigger = e.scatters() >= 3;
			if (retrigger) {
				int d = Math.min(def.retrigger(), def.fsCap() - given);
				remaining += d;
				given += d;
			}
			spins.add(new SpinTape.FreeSpin(st, sticky, retrigger, p));
			pay += p;
			acc.add(p);
		}
		return new SpinTape.FreeSpins(Math.min(awarded, def.fsCap()), spins, pay);
	}

	/**
	 * Chests the Treasure Hunt of a tape opens: up to and including the Creeper, all of them, or fewer when the
	 * max-win cap ends the hunt (SLOTS.md §1.3). A pure function of the tape (the picks only reveal it); twin of
	 * Bedrock {@code huntOpens}.
	 */
	public static int huntOpens(MachineDef def, SpinTape tape) {
		SpinTape.Hunt h = tape.hunt();
		if (h == null) return 0;
		long cap = def.capMultiple() * 5L;
		long total = tape.bought() ? 0 : SpinEval.of(def, tape.stops(), false, 0).payFifths();
		int gem = 0;
		int n = 0;
		for (int e : h.entries()) {
			n++;
			if (e == 0) break;
			if (e > 0) total += e * 5L;
			else if (gem < tape.jackpots().size() && tape.jackpots().get(gem++).owned()) total += def.features().ownedMult()[-e - 1] * 5L;
			if (total >= cap) break;
		}
		return n;
	}

	/** Owned-casino reservation per spin (SLOTS.md §8.6): {@code cap × bet}, also for a bought feature. */
	public static long reservation(MachineDef def, long bet) {
		return (long) def.capMultiple() * bet;
	}

	/** The spin returns less than its stake (streak re-draw candidate, SLOTS.md §8.2; never used for buys). */
	public static boolean losing(SpinTape t) {
		return t.payoutChips() < t.bet();
	}

	/**
	 * Replays a tape's pool effects onto {@code pools} (index 1..4), exactly as the draw computed them; the module
	 * calls it to debit the persistent pools at draw time. Owned tapes change nothing.
	 */
	public static void applyAwards(MachineDef def, SpinTape tape, long[] pools) {
		MachineDef.Features f = def.features();
		for (SpinTape.JackpotAward j : tape.jackpots()) {
			if (j.owned()) continue;
			long seed = f.seedChips(j.tier());
			pools[j.tier()] = Jackpots.poolAfter(pools[j.tier()], seed, tape.bet(), f.jackpotRef());
		}
	}
}
