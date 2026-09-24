package dev.nezo.burmaldaholic.core.anim;

/**
 * Presentation speed and motion preferences as seen by a timeline builder (global.md §2.8). Integer
 * maths only so both editions scale identically: {@code scale(ms) = floor(ms * 100 / speedPct)}.
 *
 * @param speedPct      50 (slow ×2 durations), 100 (normal), 150 (turbo preference, ×0.67), 200 (machine
 *                      turbo ×0.5, SLOTS.md §6.4)
 * @param reduceMotion  {@code anim.reduceMotion}: builders emit the reduced variant (cross-fades, ≤ 300 ms
 *                      roll-ups, no shake/flash beats)
 * @param flashes       {@code anim.flashes}: when false, builders omit flash beats
 */
public record TimingProfile(int speedPct, boolean reduceMotion, boolean flashes) {
	/** Server-paced shared time: never changed by a viewer preference. */
	public static final TimingProfile SHARED = new TimingProfile(100, false, true);

	public TimingProfile {
		if (speedPct < 25 || speedPct > 400) throw new IllegalArgumentException("speedPct " + speedPct);
	}

	public int scale(int ms) {
		return Math.floorDiv(ms * 100, speedPct);
	}

	public TimingProfile withSpeed(int pct) {
		return new TimingProfile(pct, reduceMotion, flashes);
	}
}
