package dev.nezo.burmaldaholic.core.anim.sync;

import java.util.ArrayDeque;

/**
 * The pure math of the client's one shared animation clock (SY-SPEC §1, T-SY fixes): an estimate of the SERVER's level
 * time in ms ({@code gameTime · 50}) that is monotonic within an epoch, corrects drift by changing its speed and
 * hard-snaps forward only when more than {@link #SNAP_MS} behind.
 *
 * <p><b>Server speed.</b> The clock runs at {@code speed} server-ms per wall-ms. It starts at the nominal
 * {@code 50 / msPerTick}; a change of {@code msPerTick} (/tick rate) re-anchors the window on the newest observation and
 * takes the new nominal at once. While the observations of the last {@link #WINDOW_MS} fit a straight line (residual RMS
 * ≤ {@link #FIT_RMS_MS}, span ≥ {@link #FIT_SPAN_MS}), their least-squares slope is the MEASURED speed (a lagging server
 * at 12 TPS runs at 0.6, a stalled one at 0), capped at nominal (a server ahead is caught up by the rate and the snap);
 * within {@link #SPEED_DEADBAND} of nominal the nominal wins (no jitter noise). A bad fit (a time jump, a bunch after a
 * stall) falls back to nominal. The speed is low-pass filtered ({@link #SPEED_TAU_MS}), never switched.
 *
 * <p><b>Target.</b> Every window observation is projected to now at that speed; the target is the largest projection
 * (late or bunched packets project low and can never pull the clock back).
 *
 * <p><b>Rate</b> (a multiplier of the speed) from {@code e = target − (display + dw·speed)}:
 * <ul>
 *   <li>{@code e > SNAP_MS}: {@code display = target} (forward only);</li>
 *   <li>{@code e ≥ −AHEAD_SLOW_MS}: {@code 1 + clamp(e / GAIN_MS, ±SLEW)};</li>
 *   <li>further ahead: a continuous ramp from {@code 1 − SLEW} down to {@link #AHEAD_RATE} at {@code −2·AHEAD_SLOW_MS};</li>
 *   <li>paused: 0, and the projections do not age while paused.</li>
 * </ul>
 * A single step longer than {@link #MAX_STEP_MS} (nobody advanced the clock for a while) never carries the display past
 * the target. Only {@link #reset} / {@link #clear} move the clock back, and they bump the {@link #epoch}.
 *
 * <p>Not thread-safe; the client wrapper {@code SmoothClock} owns one instance on the client thread.
 */
public final class SmoothClockCore {
	public static final double SLEW = 0.10;
	public static final double SNAP_MS = 500;
	public static final double AHEAD_SLOW_MS = 500;
	public static final double WINDOW_MS = 2000;
	public static final double GAIN_MS = 1000;
	/** The slowest rate, reached {@code 2·AHEAD_SLOW_MS} ahead of the target. */
	public static final double AHEAD_RATE = 0.5;
	/** A longer single advance never passes the target. */
	public static final double MAX_STEP_MS = 250;
	/** Speed measurement: least-squares fit over the window. */
	public static final double FIT_SPAN_MS = 1000;
	public static final double FIT_RMS_MS = 120;
	public static final double SPEED_DEADBAND = 0.05;
	public static final double SPEED_TAU_MS = 500;
	private static final int MAX_OBS = 512;

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
	private double speed = 1;
	private double lastMsPerTick = Double.NaN;
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
		if (last != null && last[0] == serverTick && last[1] == active) return;
		window.addLast(new long[] {serverTick, active});
		if (window.size() > MAX_OBS) window.pollFirst();
	}

	/** Advances the display clock to {@code wallNanos} (once per frame and once per client tick; more is harmless). */
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
		double mpt = msPerTick > 0 ? msPerTick : 50.0;
		double nominal = 50.0 / mpt;
		if (Double.isNaN(lastMsPerTick) || Math.abs(mpt - lastMsPerTick) > 1e-6) {
			if (!Double.isNaN(lastMsPerTick)) {
				// /tick rate: older observations were made at the old speed; re-anchor on the newest one
				long[] newest = window.peekLast();
				window.clear();
				if (newest != null) window.addLast(newest);
			}
			lastMsPerTick = mpt;
			speed = nominal;
		}
		long active = wallNanos - pausedNanos;
		prune(active);
		double measured = fitSlope(nominal);
		speed += (measured - speed) * Math.min(1, dw / SPEED_TAU_MS);

		double target = target(active, speed);
		lastTarget = target;
		double e = target - (display + dw * speed);
		if (e > SNAP_MS) {
			display = target;
			lastSnap = true;
			lastRate = 1;
			return;
		}
		double rate;
		if (e >= -AHEAD_SLOW_MS) {
			rate = 1 + Math.max(-SLEW, Math.min(SLEW, e / GAIN_MS));
		} else {
			double k = Math.min(1, (-e - AHEAD_SLOW_MS) / AHEAD_SLOW_MS);
			rate = (1 - SLEW) + (AHEAD_RATE - (1 - SLEW)) * k;
		}
		lastRate = rate;
		double step = dw * rate * speed;
		if (dw > MAX_STEP_MS) step = Math.min(step, Math.max(0, target - display));
		display += step;
	}

	private void prune(long activeNanos) {
		long horizon = activeNanos - (long) (WINDOW_MS * NANOS_PER_MS);
		while (window.size() > 1 && window.peekFirst()[1] < horizon) window.pollFirst();
	}

	/** Least-squares server speed over the window, or {@code nominal} when the fit is short, poor or near nominal. */
	private double fitSlope(double nominal) {
		int n = window.size();
		if (n < 4) return nominal;
		long[] first = window.peekFirst();
		long[] last = window.peekLast();
		double span = (last[1] - first[1]) / NANOS_PER_MS;
		if (span < FIT_SPAN_MS) return nominal;
		double sx = 0, sy = 0, sxx = 0, sxy = 0;
		for (long[] o : window) {
			double x = (o[1] - first[1]) / NANOS_PER_MS;
			double y = (o[0] - first[0]) * 50.0;
			sx += x;
			sy += y;
			sxx += x * x;
			sxy += x * y;
		}
		double den = n * sxx - sx * sx;
		if (den <= 0) return nominal;
		double b = (n * sxy - sx * sy) / den;
		double a = (sy - b * sx) / n;
		double ss = 0;
		for (long[] o : window) {
			double x = (o[1] - first[1]) / NANOS_PER_MS;
			double r = (o[0] - first[0]) * 50.0 - (a + b * x);
			ss += r * r;
		}
		if (Math.sqrt(ss / n) > FIT_RMS_MS) return nominal;
		// SYW: never faster than nominal. Under late/bunched time packets a step in the client's lag reads as a slope of up to
		// ~1.35 (a good fit), which ran the clock past its ±SLEW contract; a server ahead of us is caught up by the rate
		// (+SLEW) and the snap instead.
		b = Math.max(0, Math.min(nominal, b));
		return Math.abs(b / nominal - 1) <= SPEED_DEADBAND ? nominal : b;
	}

	private double target(long activeNanos, double speed) {
		double best = Double.NEGATIVE_INFINITY;
		for (long[] o : window) {
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
		lastMsPerTick = Double.NaN;
		speed = 1;
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

	/** The rate multiplier of the last {@link #advance} (1 = nominal; 0 paused; down to {@link #AHEAD_RATE} ahead). */
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

	/** The current server speed estimate in server-ms per wall-ms (1 = 20 TPS at msPerTick 50). */
	public double speed() {
		return speed;
	}
}
