package dev.nezo.burmaldaholic.games.roulette.present;

/**
 * The time a roulette viewer samples its spin at (owner playtest S1): ms since the launch, smoothed so a state packet
 * that moves the raw shared clock never makes the ball jump. Pure (the caller passes wall time), so it is unit-tested.
 *
 * <p>Rules: monotonic within one launch ({@code epoch}); follows the raw clock by running 10 % fast or slow; a raw clock
 * more than {@link #SNAP_MS} ahead snaps forward (a long hitch: there is nothing sensible to show in between); one far
 * behind runs at half speed until it is back in range (never backwards). A new epoch (the next launch) re-anchors at once.
 *
 * <p>This is RO's stand-in for SY's shared {@code SmoothClock}: when that lands, {@code RouletteScreen.rawClock} feeds it
 * instead of {@code TableClock} and this filter reduces to its monotonic guard (or is dropped).
 */
public final class RouletteTime {
	/** A raw clock further ahead than this snaps (ms). */
	public static final double SNAP_MS = 500;
	/** Rate correction while following the raw clock (±10 %). */
	public static final double SLEW = 0.10;
	/** Error (ms) at which the correction reaches its full {@link #SLEW}. */
	static final double FULL_AT_MS = 100;

	private long epoch = Long.MIN_VALUE;
	private double shown = Double.NaN;
	private double lastWall = Double.NaN;

	/**
	 * Advances to wall time {@code wallMs} given the raw clock reading {@code rawMs} (ms since the epoch's launch) and
	 * returns the smoothed time. Call once per frame; calls at the same wall time return the same value.
	 */
	public double advance(long epoch, double rawMs, double wallMs) {
		if (epoch != this.epoch || Double.isNaN(shown) || Double.isNaN(lastWall)) {
			this.epoch = epoch;
			shown = rawMs;
			lastWall = wallMs;
			return shown;
		}
		double dt = Math.max(0, Math.min(250, wallMs - lastWall));
		lastWall = wallMs;
		double predicted = shown + dt;
		double err = rawMs - predicted;
		if (err > SNAP_MS) {
			shown = rawMs;
			return shown;
		}
		double rate = err < -SNAP_MS ? 0.5 : 1 + SLEW * Math.max(-1, Math.min(1, err / FULL_AT_MS));
		double next = shown + dt * rate;
		// never overshoot the raw clock while catching up (no oscillation)
		if (err > 0) next = Math.min(next, rawMs);
		else if (err < 0) next = Math.max(next, Math.min(rawMs, shown + dt));
		shown = Math.max(shown, next);
		return shown;
	}

	/** The last value returned (NaN before the first {@link #advance}). */
	public double shown() {
		return shown;
	}

	public void reset() {
		epoch = Long.MIN_VALUE;
		shown = Double.NaN;
		lastWall = Double.NaN;
	}
}
