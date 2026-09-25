package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The craps roll storyboard (docs/design/animation/tables.md §2.4–§2.5), ms after the roll (the synced
 * {@code roll_time}). The throw and the puck are SHARED time; the chip resolution and the stick-back are presentation of
 * an already settled roll. The server holds every text line for {@link #REVEAL_DELAY_TICKS} (text trails the dice) and
 * opens the next betting window only after it. Pure.
 */
public final class CrapsBeats {
	/** Chat / action bar / titles wait this long after the roll (32 t = 1600 ms). */
	public static final int REVEAL_DELAY_TICKS = 32;
	public static final int THROW = 0;
	public static final int BADGE = 1350;
	public static final int BANNER = 1400;
	public static final int PUCK = 1500;
	public static final int CHIPS = 1600;
	public static final int STICK = 2400;
	public static final int IDLE = 2700;
	/** Puck sub-beats (ms from {@link #PUCK}). */
	public static final int PUCK_RISE = 80;
	public static final int PUCK_FLIP = 120;
	public static final int PUCK_FLY = 260;
	public static final int PUCK_LAND = 120;
	public static final int PUCK_TOTAL = PUCK_RISE + PUCK_FLIP + PUCK_FLY + PUCK_LAND;
	/** Puck sprite frames: OFF, flip (off side), edge, flip (on side), ON. */
	public static final int FRAME_OFF = 0;
	public static final int FRAME_ON = 4;

	private CrapsBeats() {}

	/** Puck sample (mutable, reused). */
	public static final class Puck {
		public double x;
		public double y;
		/** Height above the felt (px): the shadow separates, the puck rises. */
		public double lift;
		public int frame;
	}

	/**
	 * The puck flipping and flying from {@code from} (top-left of the sprite, point {@code fromPoint}) to {@code to}
	 * ({@code toPoint}), {@code t} ms after {@link #PUCK}. It always ends exactly at {@code to} with the face of
	 * {@code toPoint} (tables.md §2.5 faithfulness: the destination is the state's point).
	 */
	public static void puck(int[] from, int fromPoint, int[] to, int toPoint, double t, Puck out) {
		boolean fromOn = fromPoint != 0;
		boolean toOn = toPoint != 0;
		if (t >= PUCK_TOTAL || (from[0] == to[0] && from[1] == to[1] && fromOn == toOn)) {
			out.x = to[0];
			out.y = to[1];
			out.lift = 0;
			out.frame = toOn ? FRAME_ON : FRAME_OFF;
			return;
		}
		if (t < 0) {
			t = 0;
		}
		int startFrame = fromOn ? FRAME_ON : FRAME_OFF;
		int endFrame = toOn ? FRAME_ON : FRAME_OFF;
		if (t < PUCK_RISE) {
			out.x = from[0];
			out.y = from[1];
			out.lift = 3 * Ease.OUT_CUBIC.apply(t / PUCK_RISE);
			out.frame = startFrame;
			return;
		}
		double tf = t - PUCK_RISE;
		if (tf < PUCK_FLIP) {
			out.x = from[0];
			out.y = from[1];
			out.lift = 3;
			if (startFrame == endFrame) {
				out.frame = startFrame;
			} else {
				int step = (int) Math.min(4, Math.floor(tf / PUCK_FLIP * 5));
				out.frame = startFrame < endFrame ? step : 4 - step;
			}
			return;
		}
		out.frame = endFrame;
		double ty = tf - PUCK_FLIP;
		if (ty < PUCK_FLY) {
			double e = Ease.IN_OUT_CUBIC.apply(ty / PUCK_FLY);
			out.x = from[0] + (to[0] - from[0]) * e;
			out.y = from[1] + (to[1] - from[1]) * e;
			out.lift = 3 + 4 * Math.sin(Math.PI * ty / PUCK_FLY);
			return;
		}
		out.x = to[0];
		out.y = to[1];
		double tl = (ty - PUCK_FLY) / PUCK_LAND;
		out.lift = 3 * (1 - Ease.OUT_BOUNCE.apply(tl));
	}
}
