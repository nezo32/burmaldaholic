package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exact Punto Banco odds (GAME_DESIGN §20.2, §20.8), computed by enumerating ordered card sequences
 * by <b>value class</b> (value 0 with {@code 16 × decks} cards, A…9 with {@code 4 × decks} each): the
 * weight of a k-card coup is the product of the remaining class counts, extended by
 * {@code (N − k)…(N − 5)} so every leaf shares the denominator {@code N!/(N − 6)!}. About 10⁶ leaves,
 * a few milliseconds; cached per deck count. PURE.
 *
 * <p>Used for the per-bet house edges reported with each settled coup (VIP cashback = theoretical
 * loss) and by the tests that pin the §20.2 counts.
 */
public final class BaccaratOdds {
	/** Weighted counts: Banker wins, Player wins, Tie; {@code denominator} = N(N−1)…(N−5). */
	public record Counts(int decks, long banker, long player, long tie, long denominator) {
		public double pBanker() {
			return (double) banker / denominator;
		}

		public double pPlayer() {
			return (double) player / denominator;
		}

		public double pTie() {
			return (double) tie / denominator;
		}

		/** Probability that a hand's first two cards share a rank: {@code (4d − 1)/(52d − 1)}. */
		public double pPair() {
			return (4.0 * decks - 1) / (52.0 * decks - 1);
		}

		/** House edge per chip of each bet under {@code pay} (Banker assumes step-multiple stakes). */
		public double edge(BetKind kind, Paytable pay) {
			double c = pay.commissionBp() / 10_000.0;
			return switch (kind) {
				case BANKER -> -(pBanker() * (1 - c) - pPlayer());
				case PLAYER -> -(pPlayer() - pBanker());
				case TIE -> 1 - (pay.tiePays() + 1.0) * pTie();
				case PLAYER_PAIR, BANKER_PAIR -> 1 - (pay.pairPays() + 1.0) * pPair();
			};
		}
	}

	private static final Map<Integer, Counts> CACHE = new ConcurrentHashMap<>();

	private BaccaratOdds() {}

	public static Counts of(int decks) {
		int d = Math.max(1, Math.min(8, decks));
		return CACHE.computeIfAbsent(d, BaccaratOdds::enumerate);
	}

	static Counts enumerate(int decks) {
		long n = 52L * decks;
		long[] cnt = new long[10];
		cnt[0] = 16L * decks;
		for (int v = 1; v <= 9; v++) {
			cnt[v] = 4L * decks;
		}
		long[] ext = new long[7];
		ext[6] = 1;
		for (int k = 5; k >= 0; k--) {
			ext[k] = ext[k + 1] * (n - k);
		}
		long[] acc = new long[3]; // banker, player, tie
		for (int p1 = 0; p1 < 10; p1++) {
			long w1 = cnt[p1]--;
			for (int b1 = 0; b1 < 10; b1++) {
				long w2 = w1 * cnt[b1];
				if (w2 == 0) {
					continue;
				}
				cnt[b1]--;
				for (int p2 = 0; p2 < 10; p2++) {
					long w3 = w2 * cnt[p2];
					if (w3 == 0) {
						continue;
					}
					cnt[p2]--;
					for (int b2 = 0; b2 < 10; b2++) {
						long w4 = w3 * cnt[b2];
						if (w4 == 0) {
							continue;
						}
						cnt[b2]--;
						int p = (p1 + p2) % 10;
						int b = (b1 + b2) % 10;
						if (BaccaratRules.isNatural(p) || BaccaratRules.isNatural(b)) {
							add(acc, p, b, w4 * ext[4]);
						} else if (BaccaratRules.playerDraws(p)) {
							for (int p3 = 0; p3 < 10; p3++) {
								long w5 = w4 * cnt[p3];
								if (w5 == 0) {
									continue;
								}
								cnt[p3]--;
								int pf = (p + p3) % 10;
								if (BaccaratRules.bankerDraws(b, p3)) {
									for (int b3 = 0; b3 < 10; b3++) {
										long w6 = w5 * cnt[b3];
										if (w6 != 0) {
											add(acc, pf, (b + b3) % 10, w6);
										}
									}
								} else {
									add(acc, pf, b, w5 * ext[5]);
								}
								cnt[p3]++;
							}
						} else if (BaccaratRules.bankerDraws(b, -1)) {
							for (int b3 = 0; b3 < 10; b3++) {
								long w5 = w4 * cnt[b3];
								if (w5 != 0) {
									add(acc, p, (b + b3) % 10, w5 * ext[5]);
								}
							}
						} else {
							add(acc, p, b, w4 * ext[4]);
						}
						cnt[b2]++;
					}
					cnt[p2]++;
				}
				cnt[b1]++;
			}
			cnt[p1]++;
		}
		return new Counts(decks, acc[0], acc[1], acc[2], ext[0]);
	}

	private static void add(long[] acc, int player, int banker, long w) {
		acc[player > banker ? 1 : banker > player ? 0 : 2] += w;
	}
}
