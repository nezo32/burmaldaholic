package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.Arrays;

/** Chen hand score and Monte-Carlo equity for the bots (GAME_DESIGN.md §7.4). */
public final class Equity {
	private static int top40 = Integer.MIN_VALUE;

	private Equity() {}

	/** Bill Chen's formula, rounded up to an integer. */
	public static int chen(int a, int b) {
		int ra = Cards.rank(a);
		int rb = Cards.rank(b);
		int hi = Math.max(ra, rb);
		int lo = Math.min(ra, rb);
		double score = switch (hi) {
			case 14 -> 10;
			case 13 -> 8;
			case 12 -> 7;
			case 11 -> 6;
			default -> hi / 2.0;
		};
		if (hi == lo) {
			return (int) Math.ceil(Math.max(5, score * 2));
		}
		if (Cards.suit(a) == Cards.suit(b)) {
			score += 2;
		}
		int gap = hi - lo - 1;
		score -= switch (gap) {
			case 0 -> 0;
			case 1 -> 1;
			case 2 -> 2;
			case 3 -> 4;
			default -> 5;
		};
		if (gap <= 1 && hi < 12) {
			score += 1;
		}
		return (int) Math.ceil(score);
	}

	/** Minimum Chen score of the top {@code fraction} of all 1326 starting hands. */
	public static int chenThreshold(double fraction) {
		int[] scores = new int[1326];
		int k = 0;
		for (int i = 0; i < 52; i++) {
			for (int j = i + 1; j < 52; j++) {
				scores[k++] = chen(i, j);
			}
		}
		Arrays.sort(scores);
		int idx = Math.max(0, Math.min(scores.length - 1, (int) Math.ceil(scores.length * fraction) - 1));
		return scores[scores.length - 1 - idx];
	}

	/** Chen cut-off of the "top 40 %" range sharks assume for preflop raisers. */
	public static synchronized int top40Chen() {
		if (top40 == Integer.MIN_VALUE) {
			top40 = chenThreshold(0.4);
		}
		return top40;
	}

	/**
	 * Monte-Carlo equity: share of the pot won vs {@code opponents} random hands (board completed at
	 * random), in [0, 1].
	 *
	 * @param ranges per-opponent minimum Chen score, or null / {@link Integer#MIN_VALUE} entries for any two cards
	 */
	public static double equity(int[] hole, int[] board, int opponents, int samples, int[] ranges, PokerRng rng) {
		int opp = Math.max(0, opponents);
		if (opp == 0) {
			return 1;
		}
		boolean[] known = new boolean[52];
		for (int c : hole) {
			known[c] = true;
		}
		for (int c : board) {
			known[c] = true;
		}
		int[] pool = new int[52];
		int poolSize = 0;
		for (int c = 0; c < 52; c++) {
			if (!known[c]) {
				pool[poolSize++] = c;
			}
		}
		int need = 5 - board.length;
		int iterations = Math.max(1, samples);
		int[] hero = new int[7];
		hero[0] = hole[0];
		hero[1] = hole[1];
		System.arraycopy(board, 0, hero, 2, board.length);
		int[] villain = new int[7];
		System.arraycopy(board, 0, villain, 2, board.length);
		int[][] oppHands = new int[opp][2];
		double total = 0;
		for (int it = 0; it < iterations; it++) {
			int k = 0;
			for (int o = 0; o < opp; o++) {
				int min = ranges == null || o >= ranges.length ? Integer.MIN_VALUE : ranges[o];
				if (min == Integer.MIN_VALUE) {
					oppHands[o][0] = take(pool, poolSize, k++, rng);
					oppHands[o][1] = take(pool, poolSize, k++, rng);
					continue;
				}
				// rejection-sample a hand inside the range (give up after a few tries)
				for (int tries = 0; tries < 25; tries++) {
					int i1 = k + rng.nextInt(poolSize - k);
					int i2 = k + rng.nextInt(poolSize - k - 1);
					if (i2 >= i1) {
						i2++;
					}
					if (chen(pool[i1], pool[i2]) >= min || tries == 24) {
						int c1 = pool[i1];
						int c2 = pool[i2];
						swap(pool, k, i1);
						// c2 may have moved to i1 if it was at k
						swap(pool, k + 1, indexOf(pool, c2, k + 1, poolSize));
						oppHands[o][0] = c1;
						oppHands[o][1] = c2;
						k += 2;
						break;
					}
				}
			}
			for (int b = 0; b < need; b++) {
				int c = take(pool, poolSize, k++, rng);
				hero[2 + board.length + b] = c;
				villain[2 + board.length + b] = c;
			}
			int heroVal = HandEvaluator.evaluate(hero, 7);
			int ties = 0;
			boolean lost = false;
			for (int o = 0; o < opp; o++) {
				villain[0] = oppHands[o][0];
				villain[1] = oppHands[o][1];
				int v = HandEvaluator.evaluate(villain, 7);
				if (v > heroVal) {
					lost = true;
					break;
				}
				if (v == heroVal) {
					ties++;
				}
			}
			if (!lost) {
				total += 1.0 / (ties + 1);
			}
		}
		return total / iterations;
	}

	private static int take(int[] pool, int size, int k, PokerRng rng) {
		int j = k + rng.nextInt(size - k);
		swap(pool, k, j);
		return pool[k];
	}

	private static void swap(int[] a, int i, int j) {
		int t = a[i];
		a[i] = a[j];
		a[j] = t;
	}

	private static int indexOf(int[] a, int v, int from, int to) {
		for (int i = from; i < to; i++) {
			if (a[i] == v) {
				return i;
			}
		}
		throw new IllegalStateException("card not in pool");
	}
}
