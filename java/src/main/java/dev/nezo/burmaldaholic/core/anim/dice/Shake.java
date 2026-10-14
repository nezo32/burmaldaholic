package dev.nezo.burmaldaholic.core.anim.dice;

import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.MotionRng;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic cup shake before a throw (DI §3.2). Pure; every query is a closed function of {@code ms} on the
 * SHAKE clock (0 = shaking starts). Two cups of one duel use the same reversal times; the second cup is built with
 * {@code counterPhase = true} and its offsets are the exact negation (the rattle jitter still comes from its own seed).
 *
 * <pre>
 *   shake    reversal intervals 150 ms ×0.9 each, floor 80 ms; cup x ±2 px (ramping in over 120 ms) and lean ±6°;
 *            at each reversal die 0 hits the cup wall 8–20 ms later, die 1 22–40 ms later (RATTLE events)
 *   waiting  the shake keeps looping at the floor interval until the result is in and SHAKE_MIN has passed
 *   wind-up  starts ON a reversal ({@link #windUpAt}): 70 ms, the cup eases to x 0 and lifts 3 px, no rattle
 *   flick    90 ms, the cup tips 0 → 70° (TIP20, TIP45, TIP70); die 0 leaves 40 ms in ({@link #releaseAt})
 *   recover  220 ms back to rest (out-back)
 * </pre>
 * The release of further dice follows the solved roll ({@link DiceRoll#releaseMs}).
 */
public final class Shake {
	public static final int SHAKE_MIN = 420;
	public static final int FIRST_INTERVAL = 150;
	public static final double INTERVAL_RATIO = 0.9;
	public static final int FLOOR_INTERVAL = 80;
	public static final int RAMP_MS = 120;
	public static final int WIND_UP = 70;
	public static final int FLICK = 90;
	/** Die 0 leaves this long after the flick starts. */
	public static final int RELEASE_IN_FLICK = 40;
	public static final int RECOVER = 220;
	public static final double AMP_PX = 2;
	public static final double LEAN_DEG = 6;
	public static final double LIFT_PX = 3;
	public static final double TIP_DEG = 70;

	/** Cup frames (DI §8 cup sheet order). */
	public static final int F_REST = 0, F_SHAKE_L2 = 1, F_SHAKE_L1 = 2, F_SHAKE_R1 = 3, F_SHAKE_R2 = 4, F_UP = 5, F_TIP20 = 6, F_TIP45 = 7,
		F_TIP70 = 8, F_RECOVER = 9;

	private static final int MAX_REV = 512;
	private static final double[] REV = new double[MAX_REV];

	static {
		double t = 0;
		for (int i = 0; i < MAX_REV; i++) {
			t += interval(i);
			REV[i] = t;
		}
	}

	private final int seed;
	private final boolean counter;

	private Shake(int seed, boolean counter) {
		this.seed = seed;
		this.counter = counter;
	}

	/** The shake of the cup seeded by {@code seed}; {@code counterPhase} for the opponent / dealer cup. */
	public static Shake of(int seed, boolean counterPhase) {
		return new Shake(seed, counterPhase);
	}

	/** Length of the interval ending at reversal {@code i}, ms. */
	public static double interval(int i) {
		return Math.max(FLOOR_INTERVAL, FIRST_INTERVAL * Math.pow(INTERVAL_RATIO, i));
	}

	/** Time of reversal {@code i} (0-based), ms. */
	public static double reversal(int i) {
		if (i < MAX_REV) return REV[i];
		return REV[MAX_REV - 1] + (i - MAX_REV + 1) * (double) FLOOR_INTERVAL;
	}

	/** Index of the first reversal at or after {@code ms}. */
	public static int reversalAtOrAfter(double ms) {
		if (ms <= REV[0]) return 0;
		if (ms > REV[MAX_REV - 1]) return MAX_REV + (int) Math.ceil((ms - REV[MAX_REV - 1]) / FLOOR_INTERVAL) - 1;
		int lo = 0;
		int hi = MAX_REV - 1;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (REV[mid] >= ms) hi = mid;
			else lo = mid + 1;
		}
		return lo;
	}

	/** Wind-up start: the first reversal at or after {@code max(readyMs, SHAKE_MIN)} (readyMs = result arrived). */
	public static double windUpAt(double readyMs) {
		return reversal(reversalAtOrAfter(Math.max(readyMs, SHAKE_MIN)));
	}

	/** Release of die 0 on the shake clock for a wind-up at {@code windUpMs}. */
	public static double releaseAt(double windUpMs) {
		return windUpMs + WIND_UP + RELEASE_IN_FLICK;
	}

	/** Cup x offset in px at {@code ms} (positive = right); {@code windUpMs} = +∞ while still waiting. */
	public double offsetX(double ms, double windUpMs) {
		if (ms <= 0) return 0;
		double x;
		if (ms < windUpMs) {
			x = raw(ms);
		} else if (ms < windUpMs + WIND_UP) {
			double s = (ms - windUpMs) / WIND_UP;
			x = raw(windUpMs) * (1 - s) * (1 - s);
		} else {
			x = 0;
		}
		return counter ? -x : x;
	}

	private static double raw(double ms) {
		int k = reversalAtOrAfter(ms);
		double r0 = k == 0 ? 0 : reversal(k - 1);
		double r1 = reversal(k);
		double u = (ms - r0) / (r1 - r0);
		double sigma = (k & 1) == 0 ? 1 : -1;
		double amp = AMP_PX * Math.min(1, ms / RAMP_MS);
		return amp * sigma * -Math.cos(Math.PI * u);
	}

	/** Cup lean in degrees at {@code ms} (shake: ±6° with the offset; flick: the tip toward the throw, 0 → 70°). */
	public double lean(double ms, double windUpMs) {
		if (ms < windUpMs) return LEAN_DEG * offsetX(ms, windUpMs) / AMP_PX;
		double f = ms - windUpMs - WIND_UP;
		if (f < 0) return 0;
		if (f < FLICK) {
			double s = f / FLICK;
			return TIP_DEG * s * s * (3 - 2 * s);
		}
		double r = f - FLICK;
		if (r < RECOVER) {
			double s = r / RECOVER;
			// out-back from 70° to 0 with a small overshoot
			double c1 = 1.70158;
			double e = 1 + (c1 + 1) * Math.pow(s - 1, 3) + c1 * Math.pow(s - 1, 2);
			return TIP_DEG * (1 - e);
		}
		return 0;
	}

	/** Cup lift in px (wind-up: eases up 3 px and holds through the flick, then drops back with the recover). */
	public double lift(double ms, double windUpMs) {
		double f = ms - windUpMs;
		if (f <= 0) return 0;
		if (f < WIND_UP) {
			double s = f / WIND_UP;
			return LIFT_PX * (1 - (1 - s) * (1 - s));
		}
		if (f < WIND_UP + FLICK) return LIFT_PX;
		double r = f - WIND_UP - FLICK;
		return r < RECOVER ? LIFT_PX * (1 - r / RECOVER) : 0;
	}

	/** Cup frame ({@link #F_REST} … {@link #F_RECOVER}) at {@code ms}. */
	public int frame(double ms, double windUpMs) {
		if (ms <= 0) return F_REST;
		if (ms < windUpMs) {
			double x = offsetX(ms, windUpMs) / AMP_PX;
			if (Math.abs(x) < 0.08) return F_REST;
			return x <= -0.6 ? F_SHAKE_L2 : x < 0 ? F_SHAKE_L1 : x < 0.6 ? F_SHAKE_R1 : F_SHAKE_R2;
		}
		double f = ms - windUpMs;
		if (f < WIND_UP) return F_UP;
		if (f < WIND_UP + FLICK) {
			double a = lean(ms, windUpMs);
			return a < 30 ? F_TIP20 : a < 57 ? F_TIP45 : F_TIP70;
		}
		return f < WIND_UP + FLICK + RECOVER ? F_RECOVER : F_REST;
	}

	/**
	 * RATTLE events of {@code dice} dice for every reversal before {@code untilMs} (exclusive; pass the wind-up time so
	 * the rattle stops for the wind-up silence). obj = die, aux = reversal index, strength 0.35–0.55, x = the cup side
	 * (±1, already negated for the counter-phase cup).
	 */
	public MotionEvent[] rattle(double untilMs, int dice) {
		List<MotionEvent> l = new ArrayList<>();
		for (int k = 0; reversal(k) < untilMs; k++) {
			MotionRng r = MotionRng.of(seed, "RATTLE", k);
			double side = ((k & 1) == 0 ? 1 : -1) * (counter ? -1 : 1);
			for (int d = 0; d < dice; d++) {
				double lo = d == 0 ? 8 : 22 + 18 * (d - 1);
				double hi = d == 0 ? 20 : 40 + 18 * (d - 1);
				double t = reversal(k) + r.range(lo, hi);
				float strength = (float) r.range(0.35, 0.55);
				if (t < untilMs) l.add(new MotionEvent(t, DiceEvent.RATTLE, d, strength, side, 0, k));
			}
		}
		return MotionEvent.sort(l.toArray(new MotionEvent[0]));
	}
}
