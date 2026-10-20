package dev.nezo.burmaldaholic.games.roulette.logic;

/**
 * The roulette rotor (wheel head) in closed form (ROULETTE-ANIM-SPEC §2.2). A real rotor never stops: the dealer pushes
 * it at every spin and it then coasts toward a slow idle turn. Pure; the server evaluates the previous model at the next
 * launch to publish {@code rotor0}, every client samples the same model, so the head never snaps (fixes §0.6).
 *
 * <p>Angles in degrees (clockwise positive, unwrapped), speeds signed in degrees per second, {@code t} in ms since the
 * model's launch. With a push, {@code ω} eases ({@code outCubic}, {@link #PUSH_MS}) from the launch speed {@code ω0} to
 * {@code dir·ω_push} (so it reverses smoothly when the dealer alternates the direction), then decays exponentially
 * ({@link #TAU_MS}) toward {@code dir·ω_idle}. Without a push (a new table, a server restart) it decays from {@code ω0}.
 *
 * @param angle0 rotor angle at t = 0
 * @param omega0 signed rotor speed at t = 0 (deg/s)
 * @param dir    the direction of this spin's push (±1)
 * @param push   whether the dealer pushes at t = 0
 */
public record RotorModel(double angle0, double omega0, int dir, boolean push) {
	/** Idle speed the rotor coasts toward (0.1 rev/s). */
	public static final double OMEGA_IDLE = 36;
	/** Speed right after the dealer's push (0.42 rev/s). */
	public static final double OMEGA_PUSH = 150;
	/** Coast time constant. */
	public static final double TAU_MS = 14_000;
	/** Duration of the push. */
	public static final double PUSH_MS = 400;

	public RotorModel {
		if (dir != 1 && dir != -1) throw new IllegalArgumentException("dir " + dir);
		if (!Double.isFinite(angle0) || !Double.isFinite(omega0)) throw new IllegalArgumentException("rotor " + angle0 + " " + omega0);
	}

	/** A table that has not spun yet: the seed's angle, turning at idle speed clockwise. */
	public static RotorModel idle(int seed) {
		return new RotorModel(Math.floorMod(seed, 36000) / 100.0, OMEGA_IDLE, 1, false);
	}

	/** The rotor launched at {@code (angle, omega)} with a push in {@code dir}. */
	public static RotorModel launch(double angle, double omega, int dir) {
		return new RotorModel(angle, omega, dir, true);
	}

	/** Rotor angle at {@code tMs} (≥ 0; earlier times are clamped). */
	public double angle(double tMs) {
		double t = Math.max(0, tMs);
		double idle = dir * OMEGA_IDLE;
		if (!push) {
			return angle0 + idle * t / 1000 + (omega0 - idle) * TAU_MS / 1000 * -StrictMath.expm1(-t / TAU_MS);
		}
		double target = dir * OMEGA_PUSH;
		if (t <= PUSH_MS) {
			return angle0 + pushIntegral(t, target);
		}
		double a = angle0 + pushIntegral(PUSH_MS, target);
		double s = t - PUSH_MS;
		return a + idle * s / 1000 + (target - idle) * TAU_MS / 1000 * -StrictMath.expm1(-s / TAU_MS);
	}

	/** Signed rotor speed at {@code tMs} (deg/s). */
	public double omega(double tMs) {
		double t = Math.max(0, tMs);
		double idle = dir * OMEGA_IDLE;
		if (!push) {
			return idle + (omega0 - idle) * StrictMath.exp(-t / TAU_MS);
		}
		double target = dir * OMEGA_PUSH;
		if (t <= PUSH_MS) {
			double x = t / PUSH_MS;
			double om = 1 - x;
			return omega0 + (target - omega0) * (1 - om * om * om);
		}
		return idle + (target - idle) * StrictMath.exp(-(t - PUSH_MS) / TAU_MS);
	}

	/** ∫₀ᵗ ω over the push (deg): ω0·t + (target − ω0)·∫ outCubic. */
	private double pushIntegral(double t, double target) {
		double x = t / PUSH_MS;
		double om = 1 - x;
		// ∫₀ˣ (1 − (1 − u)³) du = x + ((1 − x)⁴ − 1) / 4
		double shape = x + (om * om * om * om - 1) / 4;
		return omega0 * t / 1000 + (target - omega0) * shape * PUSH_MS / 1000;
	}

	/** The wire form of an angle: centi-degrees in [0, 36000). */
	public static int centiAngle(double deg) {
		long c = Math.round(deg * 100);
		return (int) Math.floorMod(c, 36000L);
	}

	/** The wire form of a speed: centi-degrees per second. */
	public static int centiOmega(double degPerS) {
		return (int) Math.round(degPerS * 100);
	}
}
