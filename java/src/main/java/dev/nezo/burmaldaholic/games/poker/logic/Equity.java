package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Chen hand score and the range-aware Monte-Carlo equity of the poker bots (BOTS.md §4.3). Pure; same
 * rules as Bedrock {@code games/poker/logic/equity.ts}.
 *
 * <p>Each opponent's hole cards are drawn from a slice of the Chen-ranked combos ({@link Ranges}); the
 * board is completed at random. Only the cards the bot can see are excluded (own hole cards + board: 50
 * cards preflop, 47 flop, 46 turn, 45 river). Optionally the same samples also measure where the bot's
 * hand stands inside ITS OWN public range (HARD "minimum defence"). The work is resumable ({@link Work}):
 * {@code BotJobs} steps it across ticks and the policy gets the partial result at the think deadline.
 * Draws come from the BOT rng only.
 */
public final class Equity {
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

	public static int chen(int[] hole) {
		return chen(hole[0], hole[1]);
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

	/**
	 * @param equity  share of the pot won vs the sampled opponents, 0..1
	 * @param pct     share of the bot's own range this hand beats on the same run-outs (0.5 when not measured)
	 * @param samples samples done
	 */
	public record Result(double equity, double pct, int samples) {}

	/**
	 * What to simulate.
	 *
	 * @param ranges one range per live opponent
	 * @param own    the bot's own public range (null = do not measure the defence percentile)
	 * @param trace  tests: every card drawn in a sample (opponents, own-range hand, run-out); may be null
	 */
	public record Input(int[] hole, int[] board, List<Ranges.Spec> ranges, Ranges.Spec own, int samples, IntConsumer trace) {
		public Input(int[] hole, int[] board, List<Ranges.Spec> ranges, Ranges.Spec own, int samples) {
			this(hole, board, ranges, own, samples, null);
		}

		/** {@code opponents} × any two cards. */
		public static Input vsRandom(int[] hole, int[] board, int opponents, int samples) {
			return new Input(hole, board, java.util.Collections.nCopies(Math.max(0, opponents), Ranges.Spec.FULL), null, samples);
		}
	}

	/** Resumable Monte-Carlo equity (1 unit = 1 sample). */
	public static final class Work implements BotWork {
		private final Input in;
		private final BotRng rng;
		private final boolean[] dead = new boolean[52];
		private final int[] mark = new int[52];
		private final int[] combos = Ranges.rankedCombos();
		private final int total;
		private final int[] hero = new int[7];
		private final int[] villain = new int[7];
		private final int[][] opp;
		private final int[] alt = new int[2];
		private int stamp;
		private int done;
		private double won;
		private double beat;

		public Work(Input in, BotRng rng) {
			this.in = in;
			this.rng = rng;
			this.total = Math.max(1, in.samples());
			for (int c : in.hole()) {
				dead[c] = true;
			}
			for (int c : in.board()) {
				dead[c] = true;
			}
			this.opp = new int[in.ranges().size()][2];
		}

		@Override
		public boolean done() {
			return done >= total || in.ranges().isEmpty();
		}

		@Override
		public int progress() {
			return done;
		}

		@Override
		public Result result() {
			if (in.ranges().isEmpty()) {
				return new Result(1, 1, total);
			}
			if (done == 0) {
				return new Result(0, 0.5, 0);
			}
			return new Result(won / done, in.own() != null ? beat / done : 0.5, done);
		}

		@Override
		public int step(int budget) {
			int n = 0;
			while (n < budget && !done()) {
				sample();
				done++;
				n++;
			}
			return n;
		}

		/** Runs to the end synchronously (tests, drawn-outcome play-out). */
		public Result run() {
			while (!done()) {
				step(1000);
			}
			return result();
		}

		private boolean free(int c) {
			return !dead[c] && mark[c] != stamp;
		}

		private int take(int c) {
			mark[c] = stamp;
			if (in.trace() != null) {
				in.trace().accept(c);
			}
			return c;
		}

		private int randomCard() {
			while (true) {
				int c = rng.nextInt(52);
				if (free(c)) {
					return take(c);
				}
			}
		}

		/** Two cards from a range slice (rejection; falls back to two random cards). */
		private void fromRange(Ranges.Spec r, int[] out) {
			int n = combos.length;
			int lo = Math.max(0, Math.min(n - 1, (int) Math.floor(r.lo() * n)));
			int hi = Math.max(lo + 1, Math.min(n, (int) Math.ceil(r.hi() * n)));
			for (int tries = 0; tries < 40; tries++) {
				int code = combos[lo + rng.nextInt(hi - lo)];
				int a = code / 52;
				int b = code % 52;
				if (free(a) && free(b)) {
					out[0] = take(a);
					out[1] = take(b);
					return;
				}
			}
			out[0] = randomCard();
			out[1] = randomCard();
		}

		private void sample() {
			stamp++;
			for (int o = 0; o < opp.length; o++) {
				fromRange(in.ranges().get(o), opp[o]);
			}
			if (in.own() != null) {
				fromRange(in.own(), alt);
			}
			int b = in.board().length;
			System.arraycopy(in.board(), 0, hero, 2, b);
			for (int k = b; k < 5; k++) {
				hero[2 + k] = randomCard();
			}
			hero[0] = in.hole()[0];
			hero[1] = in.hole()[1];
			System.arraycopy(hero, 2, villain, 2, 5);
			int heroVal = HandEvaluator.evaluate(hero, 7);
			int ties = 0;
			boolean lost = false;
			for (int[] h : opp) {
				villain[0] = h[0];
				villain[1] = h[1];
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
				won += 1.0 / (ties + 1);
			}
			if (in.own() != null) {
				villain[0] = alt[0];
				villain[1] = alt[1];
				int a = HandEvaluator.evaluate(villain, 7);
				beat += heroVal > a ? 1 : heroVal == a ? 0.5 : 0;
			}
		}
	}

	/** Equity alone, synchronously (tests). */
	public static double equity(Input in, BotRng rng) {
		return new Work(in, rng).run().equity();
	}
}
