package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Motion of one scrolling reel (slots.md §4.2, SLOTS.md §10.2; PURE). The reel scrolls DOWNWARD through the real
 * strip {@code S_r} (F2): the strip index shown in the top row is {@link #top(double)} (a real number; row y shows
 * {@code S[floor(top) + y]} offset by {@code frac(top)} cells). Rules implemented here:
 *
 * <ul>
 *   <li><b>Spin-up</b> (0 → {@code spinUpMs}): back-kick up 0.15 cell, then accelerate to {@link #V} (continuous
 *       position and speed at the end of the spin-up).</li>
 *   <li><b>Cruise</b>: constant {@link #V} = 25 cells/s, read from the rest position {@code start} (continuity with the
 *       previous window).</li>
 *   <li><b>Landing</b> (the REEL_LAND beat): {@code outBack(s)} over the beat's duration with the landing distance
 *       chosen so the speed is continuous ({@code D = v·dur/(s+3)}); the overshoot is ≤ 0.11 cell with s = 1.2 and
 *       ≤ 0.14 cell with the anticipated s = 1.0 — inside the 0.18 cell of F4 — and settles in ≤ 200 ms.</li>
 *   <li><b>Phase splice</b>: at the landing start the reading re-anchors on the drawn stop so that {@code top}
 *       reaches exactly {@code stop} at the stop time and the last cells scrolled are the strip sequence ending at
 *       {@code t_r} (SLOTS.md §10.3 (c)). The splice happens at full speed, where only blur frames are drawn
 *       (speed 25 cells/s &gt; {@link #BLUR_SPEED}), so it is invisible; both sides of it are real strip cells.
 *       This replaces the spec's "target rounded up to ≥ from + 4" rule, which forced landing speeds of up to
 *       128 cells/s on the 45-stop End strips.</li>
 * </ul>
 *
 * One instance per reel per spin (immutable); sampling allocates nothing.
 */
public final class ReelMotion {
	/** Full speed, cells per ms (25 cells/s). */
	public static final double V = 0.025;
	/** Speed above which blur frames are drawn (12 cells/s). */
	public static final double BLUR_SPEED = 0.012;
	public static final double BACK_KICK = 0.15;
	/** Landing overshoot parameters: normal and anticipated reels. */
	public static final double LAND_S = 1.2;
	public static final double LAND_S_ANTICIPATED = 1.0;
	/** Reduce-motion cross-fade per reel (SLOTS.md §10). */
	public static final int CROSSFADE_MS = 300;
	/** Squash on landing: 80 ms down, 120 ms back (slots.md §2.3). */
	public static final int SQUASH_DOWN_MS = 80;
	public static final int SQUASH_BACK_MS = 120;
	/** Special-symbol land pop (scale 1 → 1.2 → 1). */
	public static final int POP_MS = 220;
	/** Reject: decelerate onto the previous window (F1). */
	public static final int REJECT_MS = 300;

	private final int spinUpMs;
	private final int landAt;
	private final int landDur;
	private final double s;
	private final int start;
	private final int stop;
	private final int length;
	private final double landDistance;

	/**
	 * @param spinUpMs spin-up duration (SPIN_UP beat)
	 * @param landAt   landing start (REEL_LAND.at), ms since the spin-up start
	 * @param landDur  landing duration (REEL_LAND.dur); {@code landAt + landDur} = stop time
	 * @param anticipated anticipated landing (softer overshoot)
	 * @param start    strip index at rest before the spin (the previous window's stop)
	 * @param stop     drawn stop
	 * @param length   strip length
	 */
	public ReelMotion(int spinUpMs, int landAt, int landDur, boolean anticipated, int start, int stop, int length) {
		this.spinUpMs = Math.max(1, spinUpMs);
		this.landAt = Math.max(0, landAt);
		this.landDur = Math.max(1, landDur);
		this.s = anticipated ? LAND_S_ANTICIPATED : LAND_S;
		this.length = Math.max(1, length);
		this.start = Math.floorMod(start, this.length);
		this.stop = Math.floorMod(stop, this.length);
		double v = Math.max(cruiseVel(this.landAt), V * 0.25);
		this.landDistance = v * this.landDur / (s + 3);
	}

	public int stopMs() {
		return landAt + landDur;
	}

	public int landAt() {
		return landAt;
	}

	public int stop() {
		return stop;
	}

	public int start() {
		return start;
	}

	/** Cells scrolled at {@code t} during spin-up and cruise (before the landing). */
	public double cruisePos(double t) {
		if (t <= 0) return 0;
		if (t < spinUpMs) return -BACK_KICK * Math.sin(Math.PI * t / spinUpMs) + V * t * t / (2.0 * spinUpMs);
		return V * (t - spinUpMs / 2.0);
	}

	/** Speed (cells/ms) during spin-up and cruise. */
	public double cruiseVel(double t) {
		if (t <= 0) return 0;
		if (t < spinUpMs) return -BACK_KICK * Math.PI / spinUpMs * Math.cos(Math.PI * t / spinUpMs) + V * t / spinUpMs;
		return V;
	}

	/** Landing progress u ∈ [0, 1] (0 before the landing, 1 after the stop). */
	public double landU(double t) {
		if (t <= landAt) return 0;
		return Math.min(1, (t - landAt) / landDur);
	}

	/**
	 * Strip index shown in the top row at {@code t} (ms since the spin-up start), as a real number. Exactly
	 * {@code start} before the spin and exactly {@code stop} from the stop time on.
	 */
	public double top(double t) {
		if (t < landAt) return start - cruisePos(t);
		if (t >= landAt + landDur) return stop;
		double u = (t - landAt) / landDur;
		return stop + landDistance * (1 - Ease.outBack(s, u));
	}

	/** Absolute scroll speed at {@code t} (cells/ms). */
	public double speed(double t) {
		if (t < landAt) return Math.abs(cruiseVel(t));
		if (t >= landAt + landDur) return 0;
		double h = 1.0;
		return Math.abs(top(t + h) - top(t)) / h;
	}

	/** Blur frames at {@code t}. */
	public boolean blurred(double t) {
		return speed(t) > BLUR_SPEED;
	}

	public boolean landed(double t) {
		return t >= landAt + landDur;
	}

	/** Landing distance in cells (for tests: the overshoot is {@code ≤ 0.18}). */
	public double landDistance() {
		return landDistance;
	}

	/** Largest overshoot (cells past the stop) of this landing. */
	public double maxOvershoot() {
		double m = 0;
		for (int i = 0; i <= 200; i++) m = Math.max(m, stop - top(landAt + landDur * i / 200.0));
		return m;
	}

	// ---- helpers shared by the reel views --------------------------------------------------------------------

	/** Strip index for a real top position and visible row {@code k} (−1 … 3). */
	public static int index(double top, int k, int length) {
		return Math.floorMod((int) Math.floor(top) + k, length);
	}

	/** Fraction of a cell the reel is moved DOWN at a real top position (0 ≤ f &lt; 1): y of row k = (k − f)·cell. */
	public static double offset(double top) {
		return top - Math.floor(top);
	}

	/**
	 * Squash of a landed symbol ({@code ms} since its reel's stop): vertical scale 1 → 0.90 → 1 and horizontal 1 →
	 * 1.06 → 1 (slots.md §2.3). Returns the vertical scale; horizontal = {@code 2 − y × …} via {@link #squashX}.
	 */
	public static double squashY(double ms) {
		if (ms <= 0 || ms >= SQUASH_DOWN_MS + SQUASH_BACK_MS) return 1;
		if (ms < SQUASH_DOWN_MS) return 1 - 0.10 * Ease.OUT_QUAD.apply(ms / SQUASH_DOWN_MS);
		return 0.90 + 0.10 * Ease.outBack(1.4, (ms - SQUASH_DOWN_MS) / SQUASH_BACK_MS);
	}

	public static double squashX(double ms) {
		double y = squashY(ms);
		return 1 + (1 - y) * 0.6;
	}

	/** Special-symbol land pop scale (1 → 1.2 → 1 over 220 ms, outBack feel). */
	public static double landPop(double ms) {
		if (ms <= 0 || ms >= POP_MS) return 1;
		double u = ms / POP_MS;
		return u < 0.4 ? 1 + 0.2 * Ease.OUT_BACK.apply(u / 0.4) : 1 + 0.2 * (1 - Ease.IN_OUT_QUAD.apply((u - 0.4) / 0.6));
	}

	/** Reduce motion: alpha of the landed window over the blur at {@code t} (0 → 1 in the last 300 ms before the stop). */
	public double crossfade(double t) {
		double from = stopMs() - CROSSFADE_MS;
		if (t <= from) return 0;
		return Math.min(1, (t - from) / CROSSFADE_MS);
	}

	/**
	 * Reject path (F1): a spinning reel decelerates onto the PREVIOUS window in 300 ms with no bounce. {@code u} =
	 * progress of the 300 ms, {@code speedAtReject} = the current speed (cells/ms). Same splice rule as the landing.
	 */
	public static double rejectTop(int previousStop, double u, double speedAtReject) {
		double d = Math.max(0, speedAtReject) * REJECT_MS / 3.0; // outCubic'(0) = 3 → speed-continuous
		return previousStop + d * (1 - Ease.OUT_CUBIC.apply(u));
	}

	/** Anticipation heartbeat of the trigger symbols: scale 1 ↔ 1.08 at 2 Hz. */
	public static double heartbeat(double ms) {
		if (ms <= 0) return 1;
		return 1 + 0.04 * (1 - Math.cos(2 * Math.PI * 2 * ms / 1000.0));
	}
}
