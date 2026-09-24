package dev.nezo.burmaldaholic.core.ui;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The balance ticker (global.md §4.1.1 "Balance ticker", used by the HUD chip counter, the Casino Menu balance plaque and
 * the wallet hero): the shown number tweens from what is displayed now to the new target. Duration
 * {@code clamp(250 + 150·log10|Δ|, 250, 1100)} ms; gains {@code outCubic}, losses {@code inOutQuad} capped at 600 ms. A new
 * target mid-tween restarts from the currently shown value (never jumps back). The last frame is exactly the target
 * and the count is monotonic (outcome fidelity: the ticker always ends on the server's balance). Pure; times in ms.
 */
public final class BalanceTicker {
	private long from;
	private long to;
	private long startMs;
	private int durMs;
	private boolean started;

	/** Tween length for a change of {@code delta} chips. */
	public static int durationMs(long delta) {
		long a = Math.abs(delta);
		if (a == 0) return 0;
		int d = (int) Math.round(250 + 150 * Math.log10(a));
		d = Math.max(250, Math.min(1100, d));
		return delta < 0 ? Math.min(d, 600) : d;
	}

	/** Shows {@code value} at once (first sync, reduce motion, resync after a hold). */
	public void snap(long value) {
		from = value;
		to = value;
		durMs = 0;
		started = true;
	}

	/** Tweens to {@code target}; the first call snaps. */
	public void retarget(long target, long nowMs, boolean reduceMotion) {
		if (!started) {
			snap(target);
			return;
		}
		if (target == to) return;
		long shown = value(nowMs);
		if (reduceMotion) {
			snap(target);
			return;
		}
		from = shown;
		to = target;
		startMs = nowMs;
		durMs = durationMs(target - shown);
	}

	/** The number to show at {@code nowMs}. */
	public long value(long nowMs) {
		if (durMs <= 0 || nowMs - startMs >= durMs) return to;
		if (nowMs <= startMs) return from;
		double t = (nowMs - startMs) / (double) durMs;
		double e = (to >= from ? Ease.OUT_CUBIC : Ease.IN_OUT_QUAD).apply(t);
		long v = from + Math.round((to - from) * e);
		return to >= from ? Math.min(to, Math.max(from, v)) : Math.max(to, Math.min(from, v));
	}

	public long target() {
		return to;
	}

	public boolean active(long nowMs) {
		return durMs > 0 && nowMs - startMs < durMs;
	}

	/** +1 while counting up, −1 while counting down, 0 at rest. */
	public int direction(long nowMs) {
		return !active(nowMs) ? 0 : to > from ? 1 : -1;
	}

	/**
	 * How strongly the number is tinted (1 = full {@code bonus} for gains / {@code chip.red} for losses, 0 = the normal
	 * colour): full for the first 70 % of the tween, then a linear lerp back over the last 30 %.
	 */
	public float tint(long nowMs) {
		if (!active(nowMs)) return 0f;
		double t = (nowMs - startMs) / (double) durMs;
		return t < 0.7 ? 1f : (float) Math.max(0, 1 - (t - 0.7) / 0.3);
	}
}
