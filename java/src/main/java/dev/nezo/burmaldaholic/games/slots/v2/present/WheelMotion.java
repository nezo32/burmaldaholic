package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * Dragon Wheel ring motion (slots.md §4.10, SLOTS.md §10.4; PURE). The pointer sits at the top; wedge i spans
 * {@code [i·a, (i+1)·a)} degrees clockwise from the pointer at rest ({@code a = 360 / n}). The ring turns clockwise
 * by {@code angle(t) = θ0 + (3 turns + Δ)·outCubic(t/T)}; {@code Δ} lands the pointer on the DRAWN wedge at a seeded
 * offset inside its central 60 % (F4: never near a boundary, so the eye never reads a "near miss").
 */
public final class WheelMotion {
	public static final int TURNS = 3;
	public static final double PEG_DEFLECT_DEG = 12;
	public static final int PEG_MS = 150;
	public static final int MIN_TICK_GAP_MS = 50;
	public static final int ZOOM_MS = 800;

	private final int wedges;
	private final double start;
	private final double total;
	private final double durMs;

	/**
	 * @param wedges  wedge count of the ring
	 * @param segment drawn wedge index
	 * @param start   ring angle at the spin start (degrees; the previous rest angle, or 0)
	 * @param durMs   spin duration (the WHEEL_SPIN beat)
	 * @param seed    cosmetic seed ({@code SeedMix.mix(timelineSeed, ring)})
	 */
	public WheelMotion(int wedges, int segment, double start, double durMs, int seed) {
		this.wedges = Math.max(1, wedges);
		this.start = start;
		this.durMs = Math.max(1, durMs);
		double a = 360.0 / this.wedges;
		double f = new SeedMix.FxRng(seed).nextDouble(); // [0, 1)
		double phi = (Math.floorMod(segment, this.wedges) + 0.2 + 0.6 * f) * a; // pointer's wheel-local angle
		double end = ((-phi) % 360 + 360) % 360; // ring angle that brings phi under the pointer
		double delta = ((end - start) % 360 + 360) % 360;
		this.total = TURNS * 360 + delta;
	}

	/** Ring angle (degrees, clockwise) at {@code ms} since the spin start. */
	public double angle(double ms) {
		if (ms <= 0) return start;
		if (ms >= durMs) return start + total;
		return start + total * Ease.OUT_CUBIC.apply(ms / durMs);
	}

	public double finalAngle() {
		return start + total;
	}

	/** Wedge under the pointer at a ring angle. */
	public int wedgeAt(double angle) {
		double phi = ((-angle) % 360 + 360) % 360;
		return (int) Math.floor(phi / (360.0 / wedges)) % wedges;
	}

	/** Number of wedge boundaries that passed the pointer since the spin start (for peg ticks). */
	public int boundariesPassed(double ms) {
		double a = 360.0 / wedges;
		return (int) Math.floor((angle(ms) - start) / a);
	}

	/** Tick pitch 0.9 → 1.4 as the wheel slows. */
	public float tickPitch(double ms) {
		return (float) (0.9 + 0.5 * Math.min(1, Math.max(0, ms / durMs)));
	}

	/** Pointer deflection (degrees, against the rotation) {@code ms} after a peg passed: 12° springing back (outElastic). */
	public static double pointerDeflect(double msSincePeg) {
		if (msSincePeg < 0 || msSincePeg >= PEG_MS) return 0;
		return -PEG_DEFLECT_DEG * (1 - Ease.OUT_ELASTIC.apply(msSincePeg / PEG_MS));
	}

	/** UP zoom progress (inOutSine over 800 ms). */
	public static double zoom(double ms) {
		if (ms <= 0) return 0;
		if (ms >= ZOOM_MS) return 1;
		return Ease.IN_OUT_SINE.apply(ms / ZOOM_MS);
	}

	/** Intro rise: y offset (px) +140 → 0 with outBack over 700 ms. */
	public static double rise(double ms) {
		if (ms <= 0) return 140;
		if (ms >= 700) return 0;
		return 140 * (1 - Ease.OUT_BACK.apply(ms / 700.0));
	}
}
