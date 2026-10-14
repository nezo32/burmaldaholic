package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Exponential slow-down that covers an EXACT distance in a fixed time with fixed start and end speeds (RO §2.3–2.4:
 * the roulette orbit stage B; also a wheel of fortune, a slot reel stop).
 *
 * <p>Profile over {@code s ∈ [0, T]} (x = s/T): {@code ω(s) = ωD + (ωS − ωD)·g_k(x)},
 * {@code g_k(x) = (e^(−kx) − e^(−k)) / (1 − e^(−k))}. It starts at ωS, ends at ωD, decays monotonically, and its
 * integral is {@code Θ(k) = T·[ωD + (ωS − ωD)·G(k)]} with {@code G(k) = ((1 − e^(−k))/k − e^(−k)) / (1 − e^(−k))},
 * strictly decreasing in k (for ωS > ωD). {@link #solve} bisects k so that Θ(k) equals the target.
 *
 * <p>Units: speeds in degrees (or any unit) per SECOND, {@code T} in SECONDS, angles in the same unit as speed·s.
 */
public final class DecaySolver {
	private static final int MAX_ITER = 200;

	private DecaySolver() {}

	/**
	 * A solved profile.
	 *
	 * @param k        the decay shape
	 * @param ok       true when the target was reachable inside [kMin, kMax] (then |residual| ≤ ~1e-9)
	 * @param theta    the distance covered, Θ(k)
	 * @param residual {@code target − theta} (non-zero only when {@code !ok}: the caller shifts the drop time)
	 * @param omegaS   start speed
	 * @param omegaD   end speed
	 * @param T        duration (s)
	 * @param kNat     the natural shape the caller passed (for diagnostics / continuity checks)
	 */
	public record Solution(double k, boolean ok, double theta, double residual, double omegaS, double omegaD, double T, double kNat) {
		/** Speed at {@code s} seconds (clamped to [0, T]). */
		public double omega(double s) {
			return DecaySolver.omega(k, omegaS, omegaD, T, s);
		}

		/** Distance covered after {@code s} seconds (clamped to [0, T]; Θ at T). */
		public double angle(double s) {
			return DecaySolver.angle(k, omegaS, omegaD, T, s);
		}

		/** Deceleration (−dω/ds, ≥ 0 when ωS ≥ ωD) at {@code s} seconds. */
		public double decel(double s) {
			double x = Math.max(0, Math.min(1, s / T));
			return (omegaS - omegaD) * k * Math.exp(-k * x) / (T * oneMinusExp(k));
		}
	}

	/**
	 * Solves the shape k so that the profile covers {@code target} in {@code T} seconds.
	 *
	 * @param omegaS start speed (> omegaD for a slow-down)
	 * @param omegaD end (critical) speed
	 * @param T      duration in seconds (> 0)
	 * @param target distance to cover
	 * @param kNat   natural shape (kept in the solution for diagnostics; the solve itself bisects [kMin, kMax])
	 * @param kMin   lower bound of k (> 0), e.g. 0.2
	 * @param kMax   upper bound of k, e.g. 6
	 */
	public static Solution solve(double omegaS, double omegaD, double T, double target, double kNat, double kMin, double kMax) {
		if (!(T > 0) || !(kMin > 0) || !(kMax >= kMin) || !Double.isFinite(target)) {
			throw new IllegalArgumentException("T " + T + " k [" + kMin + ", " + kMax + "] target " + target);
		}
		if (omegaS == omegaD) {
			double th = omegaD * T;
			boolean ok = Math.abs(target - th) <= 1e-9 * Math.max(1, Math.abs(th));
			return new Solution(kNat, ok, th, target - th, omegaS, omegaD, T, kNat);
		}
		boolean decreasing = omegaS > omegaD; // Θ decreases with k
		double thLo = theta(kMin, omegaS, omegaD, T);
		double thHi = theta(kMax, omegaS, omegaD, T);
		double reachMax = Math.max(thLo, thHi);
		double reachMin = Math.min(thLo, thHi);
		if (target > reachMax || target < reachMin) {
			boolean high = target > reachMax;
			double k = high == decreasing ? kMin : kMax;
			double th = theta(k, omegaS, omegaD, T);
			return new Solution(k, false, th, target - th, omegaS, omegaD, T, kNat);
		}
		double lo = kMin;
		double hi = kMax;
		for (int i = 0; i < MAX_ITER; i++) {
			double mid = 0.5 * (lo + hi);
			if (mid <= lo || mid >= hi) break;
			double f = theta(mid, omegaS, omegaD, T) - target;
			if (f == 0) {
				lo = hi = mid;
				break;
			}
			if ((f > 0) == decreasing) lo = mid;
			else hi = mid;
		}
		double fLo = theta(lo, omegaS, omegaD, T) - target;
		double fHi = theta(hi, omegaS, omegaD, T) - target;
		double k = Math.abs(fLo) <= Math.abs(fHi) ? lo : hi;
		double th = theta(k, omegaS, omegaD, T);
		return new Solution(k, true, th, target - th, omegaS, omegaD, T, kNat);
	}

	/** Distance Θ(k) covered in T seconds. */
	public static double theta(double k, double omegaS, double omegaD, double T) {
		return T * (omegaD + (omegaS - omegaD) * bigG(k));
	}

	/** Reachable distances {@code [min, max]} over k ∈ [kMin, kMax] (written to {@code out[0..1]}). */
	public static void reach(double omegaS, double omegaD, double T, double kMin, double kMax, double[] out) {
		double a = theta(kMin, omegaS, omegaD, T);
		double b = theta(kMax, omegaS, omegaD, T);
		out[0] = Math.min(a, b);
		out[1] = Math.max(a, b);
	}

	/** {@code G(k) = ((1 − e^(−k))/k − e^(−k)) / (1 − e^(−k))}; 1/2 at k → 0, → 0 as k → ∞. */
	public static double bigG(double k) {
		if (k < 1e-3) return 0.5 - k / 12 + k * k * k / 720;
		double om = oneMinusExp(k);
		return (om / k - Math.exp(-k)) / om;
	}

	/** Speed of the profile at {@code s} seconds. */
	public static double omega(double k, double omegaS, double omegaD, double T, double s) {
		double x = Math.max(0, Math.min(1, s / T));
		return omegaD + (omegaS - omegaD) * (Math.exp(-k * x) - Math.exp(-k)) / oneMinusExp(k);
	}

	/** Distance covered after {@code s} seconds: {@code ωD·s + (ωS−ωD)·T·[(1−e^(−kx))/k − x·e^(−k)]/(1−e^(−k))}. */
	public static double angle(double k, double omegaS, double omegaD, double T, double s) {
		double x = Math.max(0, Math.min(1, s / T));
		double integ = T * (-Math.expm1(-k * x) / k - x * Math.exp(-k)) / oneMinusExp(k);
		return omegaD * x * T + (omegaS - omegaD) * integ;
	}

	/**
	 * The shape whose initial deceleration equals {@code decel} (RO §2.3: k_nat matches the stage-A deceleration at
	 * t_S, so the deceleration is continuous): solves {@code (ωS−ωD)·k / (T·(1−e^(−k))) = decel}, clamped to
	 * [kMin, kMax].
	 */
	public static double kForDecel(double omegaS, double omegaD, double T, double decel, double kMin, double kMax) {
		double lo = kMin;
		double hi = kMax;
		double d = omegaS - omegaD;
		if (d <= 0) return kMin;
		// f(k) = d·k/(T(1−e^{−k})) is increasing in k
		if (d * lo / (T * oneMinusExp(lo)) >= decel) return kMin;
		if (d * hi / (T * oneMinusExp(hi)) <= decel) return kMax;
		for (int i = 0; i < MAX_ITER; i++) {
			double mid = 0.5 * (lo + hi);
			if (mid <= lo || mid >= hi) break;
			if (d * mid / (T * oneMinusExp(mid)) < decel) lo = mid;
			else hi = mid;
		}
		return 0.5 * (lo + hi);
	}

	private static double oneMinusExp(double k) {
		return -Math.expm1(-k);
	}
}
