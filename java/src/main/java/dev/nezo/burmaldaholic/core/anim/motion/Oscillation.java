package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Damped oscillations used by shakes and springs (PL §3.4 peg wobble {@code A·e^(−t/40)·cos(2πt/90)}, PL §3.5 bin
 * shake {@code A·e^(−t/τ)·sin(2π·22·t)}, DI §5.2 stamp shake, RO §2.3 settle rock). Pure; time in ms.
 */
public final class Oscillation {
	private static final double TWO_PI = 2 * Math.PI;

	private Oscillation() {}

	/** {@code a·e^(−t/τ)·sin(2π·hz·t)}; 0 for t < 0; {@code tauMs ≤ 0} = no decay. */
	public static double dampedSin(double a, double tauMs, double hz, double tMs) {
		if (tMs < 0) return 0;
		return a * envelope(tauMs, tMs) * Math.sin(TWO_PI * hz * tMs / 1000.0);
	}

	/** {@code a·e^(−t/τ)·cos(2π·t/P)}; 0 for t < 0. */
	public static double dampedCos(double a, double tauMs, double periodMs, double tMs) {
		if (tMs < 0) return 0;
		return a * envelope(tauMs, tMs) * Math.cos(TWO_PI * tMs / periodMs);
	}

	/** {@code e^(−t/τ)} (1 when τ ≤ 0). */
	public static double envelope(double tauMs, double tMs) {
		return tauMs > 0 ? Math.exp(-Math.max(0, tMs) / tauMs) : 1;
	}

	/**
	 * Shake offset on x of a GUI shake ({@link #dampedSin}) cut off once the envelope is below 2 % (so a finished shake
	 * is exactly 0, never a lingering sub-pixel jitter).
	 */
	public static double shake(double a, double tauMs, double hz, double tMs) {
		if (tMs < 0 || (tauMs > 0 && tMs > tauMs * 3.912)) return 0;
		return dampedSin(a, tauMs, hz, tMs);
	}
}
