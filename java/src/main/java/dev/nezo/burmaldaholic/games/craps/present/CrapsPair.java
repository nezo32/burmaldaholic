package dev.nezo.burmaldaholic.games.craps.present;

/**
 * Keeps two dice apart while they move together (T-CM M1/M2): both dice go from their start points to their end points
 * with ONE shared ease {@code e} ∈ [0, 1], so their offset {@code r = b − a} moves on a straight line from {@code r0} to
 * {@code r1}. When that line passes near zero (the pair swaps order: the stick lines the dice up in rest order and
 * leaves them in home order, or the dice home moves to the other end of the Pass line) one die is pushed sideways by
 * {@code p · sin(π e)} across the line (toward the back wall when the line is not vertical), the smallest push that keeps
 * the centres at least {@code min(gap, |r0|, |r1|)} apart for every {@code e}. Pure, allocation-free.
 */
public final class CrapsPair {
	private CrapsPair() {}

	private static final int SAMPLES = 96;
	private static final double[] SIN = new double[SAMPLES + 1];

	static {
		for (int i = 0; i <= SAMPLES; i++) SIN[i] = StrictMath.sin(Math.PI * i / SAMPLES);
	}

	/** A planned push: die {@link #die} moves {@code p · sin(π e)} along ({@link #nx}, {@link #ny}). */
	public static final class Push {
		public int die;
		public double nx, ny, p;

		public Push set(Push o) {
			die = o.die;
			nx = o.nx;
			ny = o.ny;
			p = o.p;
			return this;
		}
	}

	/**
	 * Plans the push for dice a and b moving from (a0, b0) to (a1, b1) (any units; {@code gap} in the same units).
	 */
	public static Push plan(double ax0, double ay0, double bx0, double by0, double ax1, double ay1, double bx1, double by1, double gap, Push out) {
		double r0x = bx0 - ax0, r0y = by0 - ay0;
		double r1x = bx1 - ax1, r1y = by1 - ay1;
		double dx = r1x - r0x, dy = r1y - r0y;
		double len = Math.hypot(dx, dy);
		out.die = 1;
		out.nx = 0;
		out.ny = -1;
		out.p = 0;
		if (len < 1e-9) return out;
		double nx = -dy / len, ny = dx / len;
		if (ny > 1e-12 || (Math.abs(ny) <= 1e-12 && nx < 0)) {
			nx = -nx;
			ny = -ny;
		}
		double c = r0x * nx + r0y * ny;
		out.nx = nx;
		out.ny = ny;
		out.die = c >= 0 ? 1 : 0;
		double sg = c >= 0 ? 1 : -1;
		double need = Math.min(gap, Math.min(Math.hypot(r0x, r0y), Math.hypot(r1x, r1y)));
		if (closest(r0x, r0y, dx, dy, nx * sg, ny * sg, 0) >= need) return out;
		double lo = 0, hi = 4 * Math.max(gap, 1e-6);
		for (int i = 0; i < 30; i++) {
			double mid = (lo + hi) / 2;
			if (closest(r0x, r0y, dx, dy, nx * sg, ny * sg, mid) >= need) hi = mid;
			else lo = mid;
		}
		// a small margin for the distance between the samples
		out.p = hi * 1.04 + gap * 0.01;
		return out;
	}

	private static double closest(double r0x, double r0y, double dx, double dy, double nx, double ny, double p) {
		double min = Double.POSITIVE_INFINITY;
		for (int i = 0; i <= SAMPLES; i++) {
			double e = (double) i / SAMPLES;
			double s = p * SIN[i];
			min = Math.min(min, Math.hypot(r0x + dx * e + nx * s, r0y + dy * e + ny * s));
		}
		return min;
	}

	/** Adds the push at ease {@code e} to the die centres {@code x[2], y[2]}. */
	public static void apply(Push push, double e, double[] x, double[] y) {
		if (push.p <= 0) return;
		double s = push.p * StrictMath.sin(Math.PI * Math.max(0, Math.min(1, e)));
		x[push.die] += push.nx * s;
		y[push.die] += push.ny * s;
	}
}
