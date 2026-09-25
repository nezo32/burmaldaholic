package dev.nezo.burmaldaholic.core.anim;

/**
 * Roll-up (count-up) maths shared by every game and both editions (global.md §2.6, SLOTS.md §10.1).
 * Monotonic ({@link Ease#OUT_CUBIC}), never overshoots, and the last frame is the exact server amount
 * (fidelity rule F5 / global §6.2).
 */
public final class RollUp {
	private RollUp() {}

	/**
	 * Duration in ms: {@code clamp(floor(600 + 900 × log10(1 + ret / stake)), minMs, maxMs)}.
	 * Global kit: {@code minMs} 400 (WIN) and {@code maxMs} = the tier's maximum (§2.6 table); slots:
	 * 600 / 8 000 (SLOTS.md §10.1). A tiny epsilon keeps floor() identical across JVM and V8.
	 */
	public static int durationMs(long ret, long stake, int minMs, int maxMs) {
		double ratio = stake <= 0 ? 0 : (double) ret / stake;
		int d = (int) Math.floor(600 + 900 * Math.log10(1 + Math.max(0, ratio)) + 1e-9);
		return Math.max(minMs, Math.min(maxMs, d));
	}

	/** Displayed value at progress {@code t} ∈ [0, 1]: {@code floor(total × outCubic(t))}, exact at t ≥ 1. */
	public static long valueAt(long total, double t) {
		if (t >= 1) return total;
		if (t <= 0) return 0;
		return (long) Math.floor(total * Ease.OUT_CUBIC.apply(t));
	}

	/** Tick-sound pitch for the n-th tick: {@code 1.01^n}, capped at 1.4 (slots.md §4.12). */
	public static float tickPitch(int n) {
		return (float) Math.min(1.4, Math.pow(1.01, n));
	}
}
