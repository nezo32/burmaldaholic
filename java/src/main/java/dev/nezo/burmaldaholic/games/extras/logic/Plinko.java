package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;

/**
 * Plinko (GAME_DESIGN.md §11.4). 12 rows of pegs: the ball makes 12 independent fair left/right choices,
 * so the bin (number of rights) is Binomial(12, ½). Payout = {@code floor(bet × multiplier[bin])}. Pure Java.
 */
public final class Plinko {
	public static final int ROWS = 12;
	public static final int BINS = ROWS + 1;
	/** Binomial(12, ½) weights ×4096. */
	public static final int[] BIN_WEIGHTS = {1, 12, 66, 220, 495, 792, 924, 792, 495, 220, 66, 12, 1};
	public static final int PATHS = 1 << ROWS;

	private Plinko() {}

	public enum Risk {
		LOW("low", new double[] {10, 3, 1.6, 1.4, 1.0, 1.0, 0.5, 1.0, 1.0, 1.4, 1.6, 3, 10}),
		MEDIUM("medium", new double[] {33, 11, 4, 2, 1.0, 0.6, 0.3, 0.6, 1.0, 2, 4, 11, 33}),
		HIGH("high", new double[] {170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170});

		private final String id;
		private final double[] defaults;

		Risk(String id, double[] defaults) {
			this.id = id;
			this.defaults = defaults;
		}

		public String id() {
			return id;
		}

		public double[] defaults() {
			return defaults.clone();
		}

		public String key() {
			return "gui.burmaldaholic.extras.plinko.risk." + id;
		}

		public static Risk parse(String s) {
			for (Risk r : values()) {
				if (r.id.equals(s)) {
					return r;
				}
			}
			return null;
		}
	}

	/** Config list → 13 multipliers; anything malformed falls back to the risk's default. */
	public static double[] table(double[] configured, Risk risk) {
		if (configured == null || configured.length != BINS) {
			return risk.defaults();
		}
		for (double v : configured) {
			if (!Double.isFinite(v) || v < 0) {
				return risk.defaults();
			}
		}
		return configured.clone();
	}

	/**
	 * @param path one entry per row, true = right (the exact animated path)
	 */
	public record Drop(boolean[] path, int bin, double multiplier) {}

	/** Drop one ball (fair: every peg is {@code nextInt(2)}). */
	public static Drop drop(CasinoRng rng, double[] table) {
		boolean[] path = new boolean[ROWS];
		for (int i = 0; i < ROWS; i++) {
			path[i] = rng.nextInt(2) == 1;
		}
		return fromPath(path, table);
	}

	public static Drop fromPath(boolean[] path, double[] table) {
		int bin = 0;
		for (boolean right : path) {
			if (right) {
				bin++;
			}
		}
		return new Drop(path.clone(), bin, table[bin]);
	}

	/** The path encoded as bits (bit i = row i went right) and back — used on the wire. */
	public static int encode(boolean[] path) {
		int bits = 0;
		for (int i = 0; i < path.length; i++) {
			if (path[i]) {
				bits |= 1 << i;
			}
		}
		return bits;
	}

	public static boolean[] decode(int bits) {
		boolean[] path = new boolean[ROWS];
		for (int i = 0; i < ROWS; i++) {
			path[i] = (bits >> i & 1) == 1;
		}
		return path;
	}

	public static long totalReturn(long bet, Drop drop) {
		return Payouts.floorPay(bet, drop.multiplier());
	}

	/** Exact RTP before flooring. */
	public static double rtp(double[] table) {
		double s = 0;
		for (int i = 0; i < BINS; i++) {
			s += table[i] * BIN_WEIGHTS[i];
		}
		return s / PATHS;
	}

	public static double maxMultiplier(double[] table) {
		double m = 0;
		for (double v : table) {
			m = Math.max(m, v);
		}
		return m;
	}
}
