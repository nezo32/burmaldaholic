package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Reel kinematics shared by the cabinet BER (slots.md §4.2, §2.1 F2/F4). PURE.
 *
 * <p>The reel is described by the strip index shown in the TOP row, {@code o(t)}; row {@code y} shows
 * {@code strip[o + y]}. The reel scrolls downward, so {@code o} decreases. {@code pos(t)} is the distance scrolled:
 * <pre>
 *   t &lt; SU:          −0.15·sin(π t / SU) + V t² / (2 SU)      back-kick, then accelerate
 *   t &lt; T − LAND:    V (t − SU / 2)                           full speed (V = 0.025 cells/ms at 100 %)
 *   else:            from + (target − from) · outBack(1.2, u)   landing, u = (t − (T − LAND)) / LAND
 * </pre>
 * {@code target = ceil(from + 2.4)}, so the landing distance d ∈ [2.4, 3.4) and the outBack(1.2) overshoot
 * (≈ 5.3 % of d) stays ≤ 0.18 cell (F4). The reel starts from the previous stop ({@code o = prev − pos}) and
 * lands exactly on the server stop ({@code o = stop + target − pos}); both expressions differ by an integer
 * number of cells, and the switch between them happens once, mid full-speed while the reel is blurred, so the
 * position is continuous and only the (blurred) symbol identities change. The last three cells are always the
 * paid window (F2).
 */
public final class ReelMotion {
	/** Spin-up (back-kick + acceleration), ms at 100 %. */
	public static final int SPIN_UP_MS = 120;
	/** Landing phase, ms at 100 % ({@code LAND}); anticipated reels use {@link #ANTICIPATE_LAND_MS}. */
	public static final int LAND_MS = 350;
	public static final int ANTICIPATE_LAND_MS = 600;
	/** Full speed, cells per ms at 100 % (25 cells/s). */
	public static final double V = 0.025;
	public static final double KICK = 0.15;
	public static final double MIN_LAND_CELLS = 2.4;
	public static final double OVERSHOOT = 1.2;
	/** Above this speed (cells/ms) the reel draws blur frames (12 cells/s). */
	public static final double BLUR_SPEED = 0.012;

	private ReelMotion() {}

	/** Spin-up length at {@code speedPct}. */
	public static double spinUp(int speedPct) {
		return SPIN_UP_MS * 100.0 / speedPct;
	}

	/** Full speed at {@code speedPct} (cells/ms). */
	public static double speed(int speedPct) {
		return V * speedPct / 100.0;
	}

	/** Landing duration at {@code speedPct}. */
	public static double land(int speedPct, boolean anticipated) {
		return (anticipated ? ANTICIPATE_LAND_MS : LAND_MS) * 100.0 / speedPct;
	}

	/** Start of the landing phase for a reel settling at {@code stopMs}. */
	public static double landStart(double stopMs, int speedPct, boolean anticipated) {
		double su = spinUp(speedPct);
		return Math.max(Math.min(su, stopMs), stopMs - land(speedPct, anticipated));
	}

	/** Distance scrolled before the landing (the {@code from} of the landing). */
	private static double free(double t, int speedPct) {
		double su = spinUp(speedPct);
		double v = speed(speedPct);
		if (t <= 0) return 0;
		if (t < su) return -KICK * Math.sin(Math.PI * t / su) + v * t * t / (2 * su);
		return v * (t - su / 2);
	}

	/** Landing target: an integer ≥ from + 2.4 (so the landing covers [2.4, 3.4) cells). */
	public static int target(double stopMs, int speedPct, boolean anticipated) {
		double from = free(landStart(stopMs, speedPct, anticipated), speedPct);
		return (int) Math.ceil(from + MIN_LAND_CELLS - 1e-9);
	}

	/** Distance scrolled at {@code t} ms after the spin start (monotonic after the back-kick; exact at the end). */
	public static double pos(double t, double stopMs, int speedPct, boolean anticipated) {
		double tl = landStart(stopMs, speedPct, anticipated);
		int target = target(stopMs, speedPct, anticipated);
		if (t >= stopMs) return target;
		if (t < tl) return free(t, speedPct);
		double from = free(tl, speedPct);
		double u = (t - tl) / Math.max(1e-6, stopMs - tl);
		return from + (target - from) * Ease.outBack(OVERSHOOT, u);
	}

	/** Moment the reel switches from the "from previous stop" to the "to the new stop" frame of reference. */
	public static double switchAt(double stopMs, int speedPct, boolean anticipated) {
		double su = spinUp(speedPct);
		double tl = landStart(stopMs, speedPct, anticipated);
		return tl <= su ? tl : (su + tl) / 2; // degenerate short schedule: switch at the landing start
	}

	/**
	 * Top-row strip index at {@code t} (real, not reduced mod L): {@code prev − pos(t)} before the switch,
	 * {@code stop + target − pos(t)} after; exactly {@code stop} once settled.
	 */
	public static double offset(double t, int prevStop, int stop, double stopMs, int speedPct, boolean anticipated) {
		if (t >= stopMs) return stop;
		double p = pos(t, stopMs, speedPct, anticipated);
		if (t < switchAt(stopMs, speedPct, anticipated)) return prevStop - p;
		return stop + target(stopMs, speedPct, anticipated) - p;
	}

	/** Scroll speed (cells/ms, absolute) at {@code t}; 0 before the start and once settled. */
	public static double velocity(double t, double stopMs, int speedPct, boolean anticipated) {
		if (t <= 0 || t >= stopMs) return 0;
		double h = 0.5;
		return Math.abs(pos(Math.min(stopMs, t + h), stopMs, speedPct, anticipated) - pos(Math.max(0, t - h), stopMs, speedPct, anticipated))
			/ (Math.min(stopMs, t + h) - Math.max(0, t - h));
	}

	/** Landing squash (slots.md §2.3 {@code SQUASH}): scale-Y of the landed cells {@code dt} ms after the stop. */
	public static float squash(double dt, int speedPct) {
		double down = 80 * 100.0 / speedPct;
		double back = 120 * 100.0 / speedPct;
		if (dt < 0 || dt >= down + back) return 1f;
		if (dt < down) return (float) (1 - 0.10 * Ease.OUT_QUAD.apply(dt / down));
		return (float) (0.90 + 0.10 * Ease.OUT_BACK.apply((dt - down) / back));
	}
}
