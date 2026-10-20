package dev.nezo.burmaldaholic.core.anim.sync;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * The pure math of the client's one shared animation clock (SY-SPEC §1): an estimate of the SERVER's level time in ms
 * ({@code gameTime · 50}) that is monotonic within an epoch and corrects drift by changing its speed (±{@link #SLEW}),
 * hard-snapping forward only when it is more than {@link #SNAP_MS} behind.
 *
 * <p>Each frame ({@link #advance}), with the wall delta {@code dw}: every observation of the last {@link #WINDOW_MS} is
 * projected to now ({@code obs·50 + (now − w_obs)·50/msPerTick}); the TARGET is the largest projection (late or bunched
 * packets project low and can never pull the clock back). With {@code e = target − display}:
 * <ul>
 *   <li>{@code e > SNAP_MS}: {@code display = target} (forward only);</li>
 *   <li>{@code e < −AHEAD_SLOW_MS}: rate 0.5;</li>
 *   <li>else rate {@code 1 + clamp(e / GAIN_MS, ±SLEW)};</li>
 *   <li>paused: rate 0 (and the projections do not age while paused).</li>
 * </ul>
 * {@code display += dw · rate · 50/msPerTick}. Only {@link #reset} moves the clock back, and it bumps the {@link #epoch}.
 *
 * <p>Not thread-safe; the client wrapper {@code SmoothClock} owns one instance on the render thread.
 */
public final class SmoothClockCore {
	public static final double SLEW = 0.10;
	public static final double SNAP_MS = 500;
	public static final double AHEAD_SLOW_MS = 500;
	public static final double WINDOW_MS = 2000;
	public static final double GAIN_MS = 1000;
	/** Rate while more than {@link #AHEAD_SLOW_MS} ahead of the target. */
	public static final double AHEAD_RATE = 0.5;

	private static final double NANOS_PER_MS = 1_000_000.0;

	/** Observations {serverTick, activeWallNanos}: wall time with paused spans removed. */
	private final ArrayDeque<long[]> window = new ArrayDeque<>();
	private boolean started;
	private int epoch;
	private double display;
	private long lastWall;
	private boolean wallKnown;
	/** Total paused wall nanos (subtracted from wall stamps so the projections freeze while paused). */
	private long pausedNanos;
	private double lastRate = 1;
	private boolean lastSnap;
	private double lastTarget = Double.NaN;

	/** One observation of the server's game time (a state packet's {@code game_time}, or the level time at tick end). */
	public void observe(long serverTick, long wallNanos) {
		if (!started) {
			start(serverTick, wallNanos);
			return;
		}
		long active = wallNanos - pausedNanos;
		long[] last = window.peekLast();
		// the same tick observed again later projects lower: it adds nothing
		if (last != null && last[0] == serverTick && last[1] <= active) return;
		window.addLast(new long[] {serverTick, active});
	}

	/** Advances the display clock to {@code wallNanos} (once per frame; more often is a finer integration step). */
	public void advance(long wallNanos, double msPerTick, boolean paused) {
		lastSnap = false;
		if (!wallKnown) {
			lastWall = wallNanos;
			wallKnown = true;
		}
		double dw = Math.max(0, (wallNanos - lastWall) / NANOS_PER_MS);
		lastWall = Math.max(lastWall, wallNanos);
		if (!started) return;
		if (paused) {
			pausedNanos += (long) (dw * NANOS_PER_MS);
			lastRate = 0;
			return;
		}
		double speed = 50.0 / (msPerTick > 0 ? msPerTick : 50.0);
		long active = wallNanos - pausedNanos;
		double target = target(active, speed);
		lastTarget = target;
		// the error against where the display would be now at the nominal rate (not against last frame's value)
		double e = target - (display + dw * speed);
		if (e > SNAP_MS) {
			display = target;
			lastSnap = true;
			lastRate = 1;
			return;
		}
		double rate;
		if (e < -AHEAD_SLOW_MS) {
			rate = AHEAD_RATE;
		} else {
			rate = 1 + Math.max(-SLEW, Math.min(SLEW, e / GAIN_MS));
		}
		lastRate = rate;
		display += dw * rate * speed;
	}

	private double target(long activeNanos, double speed) {
		// drop observations older than the window, but always keep the newest
		long horizon = activeNanos - (long) (WINDOW_MS * NANOS_PER_MS);
		while (window.size() > 1 && window.peekFirst()[1] < horizon) window.pollFirst();
		double best = Double.NEGATIVE_INFINITY;
		for (Iterator<long[]> it = window.iterator(); it.hasNext(); ) {
			long[] o = it.next();
			double p = o[0] * 50.0 + (activeNanos - o[1]) / NANOS_PER_MS * speed;
			if (p > best) best = p;
		}
		return best;
	}

	/** Restarts the clock at {@code serverTick} (rejoin, dimension change, level gone): the only way back; bumps the epoch. */
	public void reset(long serverTick, long wallNanos) {
		epoch++;
		start(serverTick, wallNanos);
	}

	/** Forgets everything (no level): {@link #started()} is false until the next observation; bumps the epoch. */
	public void clear() {
		epoch++;
		started = false;
		window.clear();
		display = 0;
		lastTarget = Double.NaN;
	}

	private void start(long serverTick, long wallNanos) {
		window.clear();
		pausedNanos = 0;
		window.addLast(new long[] {serverTick, wallNanos});
		display = serverTick * 50.0;
		lastWall = wallNanos;
		wallKnown = true;
		started = true;
		lastRate = 1;
	}

	/** The display clock: estimated server level time in ms. Meaningless until {@link #started()}. */
	public double nowMs() {
		return display;
	}

	public int epoch() {
		return epoch;
	}

	/** True once an observation arrived (since the last {@link #clear}). */
	public boolean started() {
		return started;
	}

	/** The rate multiplier of the last {@link #advance} (1 = nominal; 0 paused; {@link #AHEAD_RATE} running ahead). */
	public double lastRate() {
		return lastRate;
	}

	/** True when the last {@link #advance} hard-snapped forward. */
	public boolean lastSnap() {
		return lastSnap;
	}

	/** The target of the last {@link #advance} (NaN before one). */
	public double lastTarget() {
		return lastTarget;
	}
}
