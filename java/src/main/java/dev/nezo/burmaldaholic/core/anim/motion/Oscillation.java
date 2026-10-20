package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Damped oscillations used by shakes and springs (PL §3.4 peg wobble {@code A·e^(−t/40)·cos(2πt/90)}, PL §3.5 bin
 * shake {@code A·e^(−t/τ)·sin(2π·22·t)}, DI §5.2 stamp shake, RO §2.3 settle rock). Pure; time in ms.
 *
 * <p>Units are those of {@link Track.Seg#osc}: decay constant {@code tauMs} and PERIOD {@code periodMs}, both in ms
 * (a 22 Hz shake is {@code periodMs = 1000/22}; {@link #periodOfHz} converts). Transcendentals use
 * {@link StrictMath} / {@link DetMath} (bit-identical on every CPU, allocation-free).
 */
public final class Oscillation {
	private static final double TWO_PI = 2 * Math.PI;

	private Oscillation() {}

	/** Period in ms of a frequency in Hz ({@code 1000/hz}). */
	public static double periodOfHz(double hz) {
		if (!(hz > 0)) throw new IllegalArgumentException("hz " + hz);
		return 1000.0 / hz;
	}

	/** {@code a·e^(−t/τ)·sin(2π·t/P)}; 0 for t < 0; {@code tauMs = 0} = no decay. */
	public static double dampedSin(double a, double tauMs, double periodMs, double tMs) {
		if (tMs < 0) return 0;
		return a * envelope(tauMs, tMs) * DetMath.sin(TWO_PI * tMs / periodMs);
	}

	/** {@code a·e^(−t/τ)·cos(2π·t/P)}; 0 for t < 0; {@code tauMs = 0} = no decay. */
	public static double dampedCos(double a, double tauMs, double periodMs, double tMs) {
		if (tMs < 0) return 0;
		return a * envelope(tauMs, tMs) * DetMath.cos(TWO_PI * tMs / periodMs);
	}

	/** {@code e^(−t/τ)} (1 when τ = 0; grows for a negative τ). */
	public static double envelope(double tauMs, double tMs) {
		return tauMs != 0 ? StrictMath.exp(-Math.max(0, tMs) / tauMs) : 1;
	}

	/**
	 * Shake offset ({@link #dampedSin}) cut to exactly 0 once the envelope is below 2 % (so a finished shake is never a
	 * lingering sub-pixel jitter). A shake must decay: {@code tauMs ≤ 0} returns 0 (no shake), never an endless one.
	 */
	public static double shake(double a, double tauMs, double periodMs, double tMs) {
		if (!(tauMs > 0) || tMs < 0 || tMs > tauMs * 3.912) return 0;
		return dampedSin(a, tauMs, periodMs, tMs);
	}
}
