package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Closed-form ballistics between contacts (PL §2.2, CS §2.3, DI §3). Units are the caller's (px and seconds, tray
 * units and seconds…), consistently; times in SECONDS. For {@link #fallTime} the axis points DOWN (screen y): a
 * negative {@code vy0} is upward.
 */
public final class Ballistic {
	private Ballistic() {}

	/**
	 * Time to fall {@code dy} (down positive) starting with vertical speed {@code vy0} under gravity {@code g > 0}:
	 * {@code (−vy0 + √(vy0² + 2·g·dy)) / g} (PL §2.2 "segment r → r+1"). NaN when the target height is never reached
	 * (an upward throw that cannot climb {@code −dy}).
	 */
	public static double fallTime(double vy0, double dy, double g) {
		if (!(g > 0)) throw new IllegalArgumentException("g " + g);
		double disc = vy0 * vy0 + 2 * g * dy;
		if (disc < 0) return Double.NaN;
		return (-vy0 + Math.sqrt(disc)) / g;
	}

	/** Constant horizontal speed covering {@code dx} in {@code dt} seconds ({@code dx/dt}; x is linear in time). */
	public static double vxFor(double dx, double dt) {
		if (!(dt > 0)) throw new IllegalArgumentException("dt " + dt);
		return dx / dt;
	}

	/** Initial speed so that {@code p0 + v0·T + ½·a·T² = p0 + dp} (any axis, any sign of {@code a}). */
	public static double v0For(double dp, double t, double a) {
		if (!(t > 0)) throw new IllegalArgumentException("t " + t);
		return dp / t - 0.5 * a * t;
	}

	/** Position {@code p0 + v0·t + ½·a·t²}. */
	public static double pos(double p0, double v0, double a, double t) {
		return p0 + v0 * t + 0.5 * a * t * t;
	}

	/** Upward launch speed reaching apex height {@code h} under gravity {@code g}: {@code √(2·g·h)}. */
	public static double vzForApex(double h, double g) {
		return Math.sqrt(2 * g * Math.max(0, h));
	}

	/** Time to the apex for an upward speed {@code vz0}: {@code vz0/g}. */
	public static double apexTime(double vz0, double g) {
		return vz0 / g;
	}

	/** Apex height of a hop of airtime {@code t} landing at its take-off height: {@code g·t²/8}. */
	public static double apexOfHop(double t, double g) {
		return g * t * t / 8;
	}

	/** Airtime of a hop with apex {@code h} that lands at its take-off height: {@code √(8h/g)}. */
	public static double hopTime(double h, double g) {
		return Math.sqrt(8 * Math.max(0, h) / g);
	}

	/**
	 * Flight time from height {@code z0} up to an apex {@code h ≥ z0} and down to 0 (CS §2.3 step 3):
	 * {@code √(2(h−z0)/g) + √(2h/g)}.
	 */
	public static double flightTime(double z0, double h, double g) {
		return Math.sqrt(2 * Math.max(0, h - z0) / g) + Math.sqrt(2 * Math.max(0, h) / g);
	}
}
