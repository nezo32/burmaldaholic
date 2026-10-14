package dev.nezo.burmaldaholic.games.craps.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * The craps roll storyboard relative to the throw's rest time (CS §5, PHASE2B §4 CM), ms after the synced
 * {@code roll_time}. The throw and the puck are SHARED time (never scaled; the server reveals against them); the chip
 * take / travel / pay / collect, the stickman and the idle beat are LOCAL presentation of an already settled roll and
 * follow the player's speed setting ({@link #of(int, double)}: TURBO × 1/1.5, SLOW × 1.4 capped so {@code IDLE ≤ rest +
 * 2600}). The server holds every text line until {@code revealTicks = ceil((restMs + 330) / 50)} = {@link #CHIPS}.
 * Pure.
 */
public final class CrapsBeats {
	/** The total pill pops this long after the later die rests. */
	public static final int AFTER_BADGE = 80, AFTER_BANNER = 130, AFTER_PUCK = 230;
	/** CS §3.2: the take (and the chat lines) start this long after the rest. */
	public static final int AFTER_CHIPS = 330;
	/** Chip / stick / idle offsets from {@link #CHIPS} at Normal speed. */
	public static final int TRAVEL_AFTER = 300, COLLECT_AFTER = 900, STICK_AFTER = 1000, IDLE_AFTER = 1770;
	/** Stickman sub-beats (Normal speed): reach in, hook (the far die first), drag home, withdraw. */
	public static final int STICK_IN = 160, STICK_HOOK = 60, STICK_DRAG = 420, STICK_OUT = 140;
	/** SLOW is capped so the table is idle at most this long after the rest. */
	public static final int IDLE_CAP = 2600;
	/** Reduced motion: the dice fade in at their rest points and the total pops at this time (CS §4.8). */
	public static final int REDUCED_BADGE = 200;

	public final int rest;
	public final int BADGE;
	public final int BANNER;
	public final int PUCK;
	public final int CHIPS;
	/** Come bets travel and winners are paid. */
	public final int TRAVEL;
	/** Won chips fly to the balance. */
	public final int COLLECT;
	public final int STICK;
	public final int IDLE;
	/** Local time factor applied after {@link #CHIPS} (1 = Normal). */
	public final double f;

	private CrapsBeats(int rest, double f) {
		this.rest = rest;
		this.f = f;
		BADGE = rest + AFTER_BADGE;
		BANNER = rest + AFTER_BANNER;
		PUCK = rest + AFTER_PUCK;
		CHIPS = rest + AFTER_CHIPS;
		TRAVEL = CHIPS + (int) Math.round(TRAVEL_AFTER * f);
		COLLECT = CHIPS + (int) Math.round(COLLECT_AFTER * f);
		STICK = CHIPS + (int) Math.round(STICK_AFTER * f);
		IDLE = CHIPS + (int) Math.round(IDLE_AFTER * f);
	}

	/** Normal-speed beats of a throw resting at {@code restMs}. */
	public static CrapsBeats of(int restMs) {
		return new CrapsBeats(restMs, 1);
	}

	/**
	 * Beats at a speed setting ({@code speed} = the FxSettings multiplier: 1 Normal, 1.5 TURBO, 1/1.4 SLOW). Only the
	 * local part after {@link #CHIPS} is scaled; SLOW is capped by {@link #IDLE_CAP}.
	 */
	public static CrapsBeats of(int restMs, double speed) {
		double f = speed <= 0 ? 1 : 1 / speed;
		f = Math.min(f, (IDLE_CAP - AFTER_CHIPS) / (double) IDLE_AFTER);
		return new CrapsBeats(restMs, f);
	}

	/** A local duration scaled like the beats after {@link #CHIPS}. */
	public double local(double ms) {
		return ms * f;
	}

	/** Stick sub-beat starts (absolute ms): hook, drag, out, gone. */
	public double stickHook() {
		return STICK + local(STICK_IN);
	}

	public double stickDrag() {
		return stickHook() + local(STICK_HOOK);
	}

	public double stickOut() {
		return stickDrag() + local(STICK_DRAG);
	}

	public double stickGone() {
		return stickOut() + local(STICK_OUT);
	}

	/** Server reveal delay (ticks) of a throw resting at {@code restMs}: the chat lines arrive at {@link #CHIPS}. */
	public static int revealTicks(int restMs) {
		return (int) Math.ceil((restMs + AFTER_CHIPS) / 50.0);
	}

	@Override
	public String toString() {
		return "CrapsBeats[rest " + rest + " badge " + BADGE + " puck " + PUCK + " chips " + CHIPS + " stick " + STICK + " idle " + IDLE + " f " + f + "]";
	}

	// ---- the puck (SHARED time, CS §4.5) -----------------------------------------------------------------------------

	/** Puck sub-beats (ms from {@link #PUCK}): rise, flip (7 frames), glide, land. */
	public static final int PUCK_RISE = 80;
	public static final int PUCK_FLIP = 160;
	public static final int PUCK_FLY = 260;
	public static final int PUCK_LAND = 80;
	public static final int PUCK_TOTAL = PUCK_RISE + PUCK_FLIP + PUCK_FLY + PUCK_LAND;
	/** Puck sprite frames: OFF, 2 tweens, edge, 2 tweens, ON. */
	public static final int FRAMES = 7;
	public static final int FRAME_OFF = 0;
	public static final int FRAME_EDGE = 3;
	public static final int FRAME_ON = 6;
	/** Height of the lifted puck and of the glide's arc (px of the caller's space at T2; scale with the puck size). */
	public static final double LIFT = 3, ARC = 6;

	/** Puck sample (mutable, reused). */
	public static final class Puck {
		public double x;
		public double y;
		/** Height above the felt (px): the shadow separates, the puck rises. */
		public double lift;
		public int frame;
		/** Flip phase 0..1 (0 = start face flat, 0.5 = edge-on, 1 = end face flat); in-world rotation. */
		public double flip;
	}

	/**
	 * The puck flipping and flying from {@code from} (top-left of the sprite, point {@code fromPoint}) to {@code to}
	 * ({@code toPoint}), {@code t} ms after {@link #PUCK}, with heights in units of {@code k} (1 = T2 px). It always ends
	 * exactly at {@code to} with the face of {@code toPoint} (the destination is the state's point).
	 */
	public static void puck(double[] from, int fromPoint, double[] to, int toPoint, double t, double k, Puck out) {
		boolean fromOn = fromPoint != 0;
		boolean toOn = toPoint != 0;
		int startFrame = fromOn ? FRAME_ON : FRAME_OFF;
		int endFrame = toOn ? FRAME_ON : FRAME_OFF;
		out.flip = 0;
		if (t >= PUCK_TOTAL || (from[0] == to[0] && from[1] == to[1] && fromOn == toOn)) {
			out.x = to[0];
			out.y = to[1];
			out.lift = 0;
			out.frame = endFrame;
			out.flip = startFrame == endFrame ? 0 : 1;
			return;
		}
		if (t < 0) t = 0;
		if (t < PUCK_RISE) {
			out.x = from[0];
			out.y = from[1];
			out.lift = LIFT * k * Ease.OUT_CUBIC.apply(t / PUCK_RISE);
			out.frame = startFrame;
			return;
		}
		double tf = t - PUCK_RISE;
		if (tf < PUCK_FLIP) {
			out.x = from[0];
			out.y = from[1];
			out.lift = LIFT * k + 1.5 * k * Math.sin(Math.PI * tf / PUCK_FLIP);
			if (startFrame == endFrame) {
				out.frame = startFrame;
			} else {
				double p = tf / PUCK_FLIP;
				out.flip = p;
				int step = (int) Math.min(FRAMES - 1, Math.floor(p * FRAMES));
				out.frame = startFrame < endFrame ? step : FRAMES - 1 - step;
			}
			return;
		}
		out.frame = endFrame;
		out.flip = startFrame == endFrame ? 0 : 1;
		double ty = tf - PUCK_FLIP;
		if (ty < PUCK_FLY) {
			double e = Ease.IN_OUT_CUBIC.apply(ty / PUCK_FLY);
			out.x = from[0] + (to[0] - from[0]) * e;
			out.y = from[1] + (to[1] - from[1]) * e;
			out.lift = LIFT * k + ARC * k * Math.sin(Math.PI * ty / PUCK_FLY);
			return;
		}
		out.x = to[0];
		out.y = to[1];
		double tl = (ty - PUCK_FLY) / PUCK_LAND;
		out.lift = LIFT * k * (1 - Ease.OUT_BOUNCE.apply(tl));
	}

	/** {@link #puck(double[], int, double[], int, double, double, Puck)} for integer positions at T2 scale. */
	public static void puck(int[] from, int fromPoint, int[] to, int toPoint, double t, Puck out) {
		puck(new double[] {from[0], from[1]}, fromPoint, new double[] {to[0], to[1]}, toPoint, t, 1, out);
	}

	/** True while the puck moves at {@code t} ms after {@link #PUCK}. */
	public static boolean puckMoving(double t) {
		return t >= 0 && t < PUCK_TOTAL;
	}

	/** ms after {@link #PUCK} of the touchdown ({@code craps_puck}). */
	public static int puckTouchdown() {
		return PUCK_RISE + PUCK_FLIP + PUCK_FLY;
	}
}
