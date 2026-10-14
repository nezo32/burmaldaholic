package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;

/**
 * The stickman's return of the dice (CS §4.7): at {@link CrapsBeats#STICK} the stick reaches in from beyond the back
 * wall to just behind the dice (160 ms, out-cubic), hooks them — the far die is pulled in first, then both sit side by
 * side (60 ms) — drags the pair to the shooter's dice home (420 ms, in-out-cubic; the dice slide and keep their top
 * faces) and withdraws (140 ms). Positions are in the caller's space (layout px or print px); y grows toward the
 * shooter. Pure and allocation-free.
 */
public final class CrapsStick {
	private CrapsStick() {}

	/** Phases of {@link Out#phase}. */
	public static final int BEFORE = 0, REACH = 1, HOOK = 2, DRAG = 3, WITHDRAW = 4, DONE = 5;

	/** Mutable sample. */
	public static final class Out {
		public int phase;
		/** The stick is drawn. */
		public boolean visible;
		/** The hook's contact point (bottom centre of the blade). */
		public double hookX, hookY;
		/** Die centres. */
		public final double[] x = new double[2], y = new double[2];
		/** 0..1 progress of the drag (the yaw squares up at its middle). */
		public double drag;
	}

	/**
	 * Samples the return at roll time {@code t}.
	 *
	 * @param rest  die centres at rest {x0, y0, x1, y1}
	 * @param home  die centres at home {x0, y0, x1, y1}
	 * @param topY  where the stick enters (above the back wall)
	 * @param die   die size (the pair sits {@code die + 2} apart, the hook {@code die / 2 + 1} behind them)
	 */
	public static void sample(double t, CrapsBeats b, double[] rest, double[] home, double topY, double die, Out o) {
		double hook = b.stickHook();
		double drag = b.stickDrag();
		double out = b.stickOut();
		double gone = b.stickGone();
		// the pair's line-up: the dice keep the left / right order they have at home
		boolean d0Left = home[0] <= home[2];
		double midX = (rest[0] + rest[2]) / 2;
		double pairY = Math.max(rest[1], rest[3]);
		double half = die / 2 + 1;
		double p0x = midX + (d0Left ? -half : half);
		double p1x = midX + (d0Left ? half : -half);
		double behind = Math.min(rest[1], rest[3]) - die / 2 - 1;
		double hookY0 = Math.min(behind, pairY - die / 2 - 1);
		double homeMidX = (home[0] + home[2]) / 2;
		double homeY = (home[1] + home[3]) / 2;
		o.drag = 0;
		if (t < b.STICK) {
			o.phase = BEFORE;
			o.visible = false;
			set(o, rest[0], rest[1], rest[2], rest[3]);
			o.hookX = midX;
			o.hookY = topY;
			return;
		}
		if (t < hook) {
			o.phase = REACH;
			o.visible = true;
			double e = Ease.OUT_CUBIC.apply((t - b.STICK) / (hook - b.STICK));
			set(o, rest[0], rest[1], rest[2], rest[3]);
			o.hookX = midX;
			o.hookY = topY + (hookY0 - topY) * e;
			return;
		}
		if (t < drag) {
			o.phase = HOOK;
			o.visible = true;
			double p = (t - hook) / (drag - hook);
			// the far die (larger distance to its pair spot) moves over the whole hook; the near one in its second half
			double d0 = Math.hypot(rest[0] - p0x, rest[1] - pairY);
			double d1 = Math.hypot(rest[2] - p1x, rest[3] - pairY);
			double e0 = Ease.OUT_CUBIC.apply(d0 >= d1 ? p : Math.max(0, p * 2 - 1));
			double e1 = Ease.OUT_CUBIC.apply(d1 > d0 ? p : Math.max(0, p * 2 - 1));
			set(o, rest[0] + (p0x - rest[0]) * e0, rest[1] + (pairY - rest[1]) * e0, rest[2] + (p1x - rest[2]) * e1, rest[3] + (pairY - rest[3]) * e1);
			o.hookX = midX;
			o.hookY = hookY0;
			return;
		}
		if (t < out) {
			o.phase = DRAG;
			o.visible = true;
			double p = (t - drag) / (out - drag);
			double e = Ease.IN_OUT_CUBIC.apply(p);
			o.drag = p;
			// the pair slides from its line-up to home: each die eases from its pair spot to its home spot
			set(o, p0x + (home[0] - p0x) * e, pairY + (home[1] - pairY) * e, p1x + (home[2] - p1x) * e, pairY + (home[3] - pairY) * e);
			o.hookX = midX + (homeMidX - midX) * e;
			o.hookY = hookY0 + (homeY - half - hookY0) * e;
			return;
		}
		set(o, home[0], home[1], home[2], home[3]);
		o.drag = 1;
		o.hookX = homeMidX;
		if (t < gone) {
			o.phase = WITHDRAW;
			o.visible = true;
			double e = Ease.IN_CUBIC.apply((t - out) / (gone - out));
			double y0 = homeY - half;
			o.hookY = y0 + (topY - y0) * e;
			return;
		}
		o.phase = DONE;
		o.visible = false;
		o.hookY = topY;
	}

	private static void set(Out o, double x0, double y0, double x1, double y1) {
		o.x[0] = x0;
		o.y[0] = y0;
		o.x[1] = x1;
		o.y[1] = y1;
	}
}
