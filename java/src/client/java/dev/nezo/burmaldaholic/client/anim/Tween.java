package dev.nezo.burmaldaholic.client.anim;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * A mutable float tween (global.md §2.5), reusable to avoid per-frame allocation. Time is whatever clock
 * the owner samples ({@link AnimClock#localMs()} for UI, shared ms for world-synced motion).
 */
public final class Tween {
	private float from;
	private float to;
	private double startMs;
	private int durMs;
	private Ease ease = Ease.LINEAR;

	/** Restarts the tween from its current value (no jump) towards {@code target}. */
	public Tween retarget(float target, double nowMs, int durMs, Ease ease) {
		this.from = value(nowMs);
		this.to = target;
		this.startMs = nowMs;
		this.durMs = Math.max(0, durMs);
		this.ease = ease;
		return this;
	}

	public Tween set(float value) {
		this.from = value;
		this.to = value;
		this.durMs = 0;
		return this;
	}

	public float value(double nowMs) {
		if (durMs == 0 || nowMs >= startMs + durMs) return to;
		if (nowMs <= startMs) return from;
		return (float) (from + (to - from) * ease.apply((nowMs - startMs) / durMs));
	}

	public boolean done(double nowMs) {
		return durMs == 0 || nowMs >= startMs + durMs;
	}

	public float target() {
		return to;
	}

	/** Jumps to the end (skip / reduce motion / overrun rule). */
	public void finish() {
		from = to;
		durMs = 0;
	}
}
