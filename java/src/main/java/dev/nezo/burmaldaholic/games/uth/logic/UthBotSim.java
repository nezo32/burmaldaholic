package dev.nezo.burmaldaholic.games.uth.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * What each bot level costs per round (BOTS.md §4.5 {@code …bots.uth_edge}, §12.3). Pure; used by the tests
 * (twin of Bedrock {@code uth/logic/sim.ts} + {@code bot-sim.ts}).
 *
 * <p>Monte-Carlo over boards with exact inner sums: for a random 5-card board every one of the 1 081 hole
 * pairs from the other 47 cards is played against every one of the dealer's 990 hole pairs from the remaining
 * 45 cards — exactly the joint distribution of a round given the board, at ~1 100 evaluations per board. A
 * level's mixed choices are taken as EXPECTATIONS (EASY's 15 % scared ×3, 30 % river calls, 50 % Trips habit
 * are weighted exactly), so only the board is sampled.
 */
public final class UthBotSim {
	/** Dealer qualifies iff value ≥ this (category pair). */
	private static final int QUALIFY = UthCards.PAIR << 20;

	private UthBotSim() {}

	/** Table rules the costs depend on. */
	public record Rules(boolean allow3x, boolean tripsEnabled, Paytables pays) {}

	/** One seat's exact situation on a board. */
	public interface HandOnBoard {
		int[] hole();

		/** Value of hole + board. */
		int value();

		/** Mean result in Antes (Ante + Blind + a Play bet of {@code m} Antes) over the dealer's 990 hole pairs. */
		double ev(int m);
	}

	public interface HandConsumer {
		void accept(HandOnBoard h);
	}

	/** Every one of the 1 081 hole pairs not on {@code board}, each with its exact result against the dealer's 990 pairs. */
	public static void forEachHand(int[] board, Paytables pays, HandConsumer cb) {
		boolean[] on = new boolean[UthCards.DECK_SIZE];
		for (int c : board) {
			on[c] = true;
		}
		int[] rest = new int[UthCards.DECK_SIZE - board.length];
		int n = 0;
		for (int c = 0; c < UthCards.DECK_SIZE; c++) {
			if (!on[c]) {
				rest[n++] = c;
			}
		}
		int[][] dv = new int[n][n];
		int[] all = new int[n * (n - 1) / 2];
		int[] seven = new int[7];
		System.arraycopy(board, 0, seven, 2, 5);
		int p = 0;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				seven[0] = rest[i];
				seven[1] = rest[j];
				int v = UthCards.evaluate(seven, 7);
				dv[i][j] = v;
				dv[j][i] = v;
				all[p++] = v;
			}
		}
		Arrays.sort(all);
		int total = all.length;
		int lowerQ = lowerBound(all, QUALIFY);
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				int pv = dv[i][j];
				int lo = lowerBound(all, pv);
				int up = lowerBound(all, pv + 1);
				// counts over the 1 081 pairs (value < pv → the seat wins), then remove the 91 pairs using i or j
				long[] c = new long[5]; // winQ, winNQ, loseQ, loseNQ, tie
				c[1] = Math.min(lo, lowerQ);
				c[0] = Math.max(0, lo - lowerQ);
				c[4] = up - lo;
				c[3] = Math.max(0, lowerQ - up);
				c[2] = total - Math.max(up, lowerQ);
				for (int k = 0; k < n; k++) {
					if (k == i || k == j) {
						continue;
					}
					remove(c, dv[i][k], pv);
					remove(c, dv[j][k], pv);
				}
				c[4]--; // the pair (i, j) itself
				long dealers = c[0] + c[1] + c[2] + c[3] + c[4];
				double bp = pays.blindPay(PayHand.of(pv));
				int[] hole = {rest[i], rest[j]};
				long winQ = c[0], winNQ = c[1], loseQ = c[2], loseNQ = c[3];
				cb.accept(new HandOnBoard() {
					@Override
					public int[] hole() {
						return hole;
					}

					@Override
					public int value() {
						return pv;
					}

					@Override
					public double ev(int m) {
						return (winQ * (m + 1 + bp) + winNQ * (m + bp) - loseQ * (m + 2.0) - loseNQ * (m + 1.0)) / dealers;
					}
				});
			}
		}
	}

	private static void remove(long[] c, int x, int pv) {
		if (x < pv) {
			c[x >= QUALIFY ? 0 : 1]--;
		} else if (x > pv) {
			c[x >= QUALIFY ? 2 : 3]--;
		} else {
			c[4]--;
		}
	}

	private static int lowerBound(int[] a, int x) {
		int lo = 0;
		int hi = a.length;
		while (lo < hi) {
			int m = (lo + hi) >>> 1;
			if (a[m] < x) {
				lo = m + 1;
			} else {
				hi = m;
			}
		}
		return lo;
	}

	/** Expected result / wager (in Antes) of one hand on a known board for a level. */
	public static double[] expectedHand(BotDifficulty level, HandOnBoard h, int[] board, Rules r) {
		int[] hole = h.hole();
		int[] flop = Arrays.copyOf(board, 3);
		double result;
		double wagered;
		if (level == BotDifficulty.EASY) {
			if (UthBotPolicy.easyPreflopBet4(hole)) {
				result = h.ev(4);
				wagered = 6;
			} else {
				double rest;
				double restW;
				if (UthBotPolicy.easyFlopBet2(hole, flop)) {
					rest = h.ev(2);
					restW = 4;
				} else if (UthBotPolicy.easyRiverBet1(hole, board)) {
					rest = h.ev(1);
					restW = 3;
				} else {
					double call = UthBotPolicy.EASY_RIVER_CALL;
					rest = call * h.ev(1) + (1 - call) * -2;
					restW = call * 3 + (1 - call) * 2;
				}
				double p3 = r.allow3x() ? UthBotPolicy.EASY_SCARED_3X : 0;
				result = p3 * h.ev(3) + (1 - p3) * rest;
				wagered = p3 * 5 + (1 - p3) * restW;
			}
			if (r.tripsEnabled()) {
				int m = r.pays().tripsPay(PayHand.of(h.value()));
				result += UthBotPolicy.EASY_TRIPS * (m > 0 ? m : -1);
				wagered += UthBotPolicy.EASY_TRIPS;
			}
		} else if (ReferenceStrategy.preflopBet(hole)) {
			result = h.ev(4);
			wagered = 6;
		} else if (ReferenceStrategy.flopBet(hole, flop)) {
			result = h.ev(2);
			wagered = 4;
		} else {
			double r1 = h.ev(1);
			result = r1 > -2 ? r1 : -2;
			wagered = r1 > -2 ? 3 : 2;
		}
		return new double[] {result, wagered};
	}

	/** Sums of a measurement; {@code boardMeans} per board for the standard error. */
	public record Stats(long hands, double sumResult, double sumWagered, double[] boardMeans) {
		/** Cost per round as a share of the Ante. */
		public double edge() {
			return -sumResult / hands;
		}

		/** Standard error of {@link #edge} from the per-board means. */
		public double se() {
			int m = boardMeans.length;
			double mean = Arrays.stream(boardMeans).average().orElse(0);
			double v = Arrays.stream(boardMeans).map(x -> (x - mean) * (x - mean)).sum() / Math.max(1, m - 1);
			return Math.sqrt(v / Math.max(1, m));
		}
	}

	/**
	 * Runs {@code boards} random boards for a level. {@code nextInt} draws the boards (a fair shuffle);
	 * {@code level} null = strategy R measured directly (the base game's house edge).
	 */
	public static Stats measure(IntUnaryOperator nextInt, int boards, BotDifficulty level, Rules rules) {
		long hands = 0;
		double sum = 0;
		double wag = 0;
		double[] means = new double[boards];
		for (int b = 0; b < boards; b++) {
			int[] board = Arrays.copyOf(UthCards.shuffledDeck(nextInt), 5);
			double[] acc = new double[3];
			forEachHand(board, rules.pays(), h -> {
				double[] e = expectedHand(level, h, board, rules);
				acc[0] += e[0];
				acc[1] += e[1];
				acc[2]++;
			});
			hands += (long) acc[2];
			sum += acc[0];
			wag += acc[1];
			means[b] = acc[0] / acc[2];
		}
		return new Stats(hands, sum, wag, means);
	}
}
