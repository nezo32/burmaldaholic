package dev.nezo.burmaldaholic.games.extras.logic.anim;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The Lucky Coin toss (extras-pvp.md §1.2, §2.2; visual/extras.md §3). PURE: the screen, the duel screen and the
 * tests sample the same functions. The coin sheet {@code coin_spin.png} has 12 frames: 0 heads, 1–5 tilting, 6 edge-on,
 * 7–10 tilting, 11 tails. A position {@code h} in half-turns maps to a frame with {@link #frame}; heads is an even
 * {@code h}, tails an odd one, so a toss that must land on a face runs to {@link #target} half-turns and its last
 * frames approach that face from the same side (never passing it, global.md §6 item 3).
 *
 * <p>Solo timings (speed 100): anticipation 0–120 ms, flight 120–980, landing 980–1 240 (two vertical bounces,
 * squash at the first contact), reveal 1 240–1 500 (glint on a win, the face name pops). Everything after
 * {@link #TOTAL_MS} is the terminal pose, which only depends on the server's face.
 */
public final class CoinAnim {
	public static final int FRAMES = 12;
	public static final int HEADS_FRAME = 0;
	public static final int TAILS_FRAME = 11;
	public static final int EDGE_FRAME = 6;
	/** Idle rest frame (a slight tilt). */
	public static final int IDLE_FRAME = 1;

	public static final int ANTICIPATION_MS = 120;
	public static final int FLIGHT_END_MS = 980;
	public static final int LAND_END_MS = 1240;
	public static final int TOTAL_MS = 1500;
	/** Reduced motion: heads → edge → face at 100 ms each. */
	public static final int REDUCED_MS = 300;
	/** Apex height in GUI px (visual/extras.md §3.2). */
	public static final double APEX = 46;

	private CoinAnim() {}

	/** Sheet frame for a position in half-turns (extras-pvp.md §1.2). */
	public static int frame(double h) {
		double k = ((h % 2) + 2) % 2;
		return (int) Math.round(k <= 1 ? 11 * k : 11 * (2 - k));
	}

	/** The face's frame. */
	public static int faceFrame(boolean heads) {
		return heads ? HEADS_FRAME : TAILS_FRAME;
	}

	/** Half-turns a solo toss spins: 10 for heads, 11 for tails. */
	public static int target(boolean heads) {
		return heads ? 10 : 11;
	}

	/**
	 * One pose of the coin.
	 *
	 * @param dy          vertical offset in GUI px (negative = up) from the rest position
	 * @param scaleX      horizontal scale (squash)
	 * @param scaleY      vertical scale (squash / anticipation dip)
	 * @param frame       sheet frame
	 * @param shadow      shadow scale 0.5–1
	 * @param shadowAlpha shadow opacity
	 * @param airborne    in flight (ghost frames trail)
	 * @param landed      the frame is fixed on the face
	 * @param glint       glint progress 0–1 (−1 = none)
	 * @param name        face-name pop progress 0–1 (−1 = not shown yet)
	 */
	public record Pose(double dy, double scaleX, double scaleY, int frame, double shadow, double shadowAlpha, boolean airborne, boolean landed,
			double glint, double name) {}

	/** The resting coin before any toss (idle tilt). */
	public static final Pose IDLE = new Pose(0, 1, 1, IDLE_FRAME, 1, 1, false, false, -1, -1);

	/** The terminal pose of a toss (also skip, reduce motion end, late open). */
	public static Pose terminal(boolean heads) {
		return new Pose(0, 1, 1, faceFrame(heads), 1, 1, false, true, -1, 1);
	}

	/** Solo toss at {@code ms} since the result arrived (already scaled by the viewer's speed). */
	public static Pose toss(boolean heads, boolean win, double ms, boolean reduceMotion) {
		if (reduceMotion) {
			if (ms >= REDUCED_MS) return terminal(heads);
			int f = ms < 100 ? HEADS_FRAME : ms < 200 ? EDGE_FRAME : faceFrame(heads);
			return new Pose(0, 1, 1, f, 1, 1, false, ms >= 200, -1, -1);
		}
		if (ms >= TOTAL_MS) return terminal(heads);
		if (ms < ANTICIPATION_MS) {
			double e = Ease.OUT_CUBIC.apply(ms / ANTICIPATION_MS);
			return new Pose(3 * e, 1, 1 - 0.1 * e, IDLE_FRAME, 1, 1, false, false, -1, -1);
		}
		if (ms < FLIGHT_END_MS) {
			double p = (ms - ANTICIPATION_MS) / (FLIGHT_END_MS - ANTICIPATION_MS);
			double sin = Math.sin(Math.PI * p);
			double h = target(heads) * Ease.OUT_QUAD.apply(p);
			double s = 1 + 0.2 * sin;
			return new Pose(-APEX * 4 * p * (1 - p), s, s, frame(h), 1 - 0.5 * sin, 1 - 0.6 * sin, true, false, -1, -1);
		}
		int face = faceFrame(heads);
		if (ms < LAND_END_MS) {
			double t = ms - FLIGHT_END_MS;
			boolean squash = t < 60;
			return new Pose(-bounce(t / (LAND_END_MS - FLIGHT_END_MS)), squash ? 1.12 : 1, squash ? 0.88 : 1, face, 1, 1, false, true, -1, -1);
		}
		double r = (ms - LAND_END_MS) / (TOTAL_MS - LAND_END_MS);
		return new Pose(0, 1, 1, face, 1, 1, false, true, win ? r : -1, Math.min(1, (ms - LAND_END_MS) / 250.0));
	}

	/** Two vertical bounces after the first contact: 8 px then 2 px, then rest (u 0–1). */
	static double bounce(double u) {
		if (u < 0.55) {
			double q = u / 0.55;
			return 8 * 4 * q * (1 - q);
		}
		if (u < 0.85) {
			double q = (u - 0.55) / 0.3;
			return 2 * 4 * q * (1 - q);
		}
		return 0;
	}

	/** Contacts of the landing (for the {@code coin_land} sound): offsets in ms after {@link #FLIGHT_END_MS}. */
	public static final int[] CONTACTS_MS = {0, 143, 221};

	// ---- Coin Flip Duel (extras-pvp.md §2.2 C/D) ----------------------------------------------------------------

	/** Rise duration of the open-ended duel toss. */
	public static final int DUEL_RISE_MS = 400;
	/** The duel toss loops at 2 half-turns per second at the apex. */
	public static final double DUEL_SPIN_PER_MS = 2 / 1000.0;
	public static final int DUEL_LAND_MS = 1200;
	/** Duel apex (the duel panel is lower than the solo one). */
	public static final double DUEL_APEX = 40;

	/** Half-turns of the looping duel toss {@code ms} after the {@code spin} step arrived (the face is unknown). */
	public static double duelSpinH(double ms) {
		return Math.max(0, ms) * DUEL_SPIN_PER_MS;
	}

	/** Height of the looping toss: rises in 400 ms, then bobs ±3 px. */
	public static double duelSpinDy(double ms) {
		if (ms < DUEL_RISE_MS) return -DUEL_APEX * Ease.OUT_CUBIC.apply(ms / DUEL_RISE_MS);
		return -DUEL_APEX + 3 * Math.sin((ms - DUEL_RISE_MS) / 1000.0 * Math.PI * 2);
	}

	/** Landing target from the current spin position: the smallest H ≥ h0 + 1.5 whose parity is the face (even = heads). */
	public static double landTarget(double h0, boolean heads) {
		double min = h0 + 1.5;
		long base = (long) Math.ceil(min);
		boolean even = base % 2 == 0;
		if (even != heads) base++;
		return base;
	}

	/**
	 * The duel landing ({@code land} step): 0–500 ms decelerate onto the face while descending, 500–700 bounce,
	 * then the coin slides 40 px towards the winner (700–1 000). {@code slideDir} −1 left, +1 right, 0 none.
	 *
	 * @return {dx, dy, frame, landed(0/1), squash(0/1)}
	 */
	public static double[] duelLand(double h0, double dy0, boolean heads, double ms, int slideDir) {
		double target = landTarget(h0, heads);
		if (ms < 500) {
			double e = Ease.OUT_CUBIC.apply(ms / 500);
			double h = h0 + (target - h0) * e;
			double q = ms / 500;
			double dy = dy0 * (1 - q * q);
			return new double[] {0, dy, frame(h), 0, 0};
		}
		int face = faceFrame(heads);
		if (ms < 700) {
			double u = (ms - 500) / 200;
			return new double[] {0, -bounce(u), face, 1, ms < 560 ? 1 : 0};
		}
		double s = Ease.OUT_CUBIC.apply(Math.min(1, (ms - 700) / 300));
		return new double[] {40 * slideDir * s, 0, face, 1, 0};
	}
}
