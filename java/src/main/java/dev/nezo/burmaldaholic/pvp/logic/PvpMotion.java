package dev.nezo.burmaldaholic.pvp.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Motion of the shared PvP presentation (extras-pvp.md §2.2-A/B, §9.3–§9.5, §11). PURE: the Java screens sample it;
 * JUnit checks the numbers. Everything server-paced (countdown, Final Reveal) is expressed in ticks since the cue the
 * server sent, so every participant sees the same beat; decoration after a cue is local time.
 */
public final class PvpMotion {
	/** Drumroll hits of the Final Reveal (ticks after "Final results…", §9.4): 8 accelerating hits. */
	public static final int[] DRUM_TICKS = {0, 10, 18, 25, 31, 35, 38, 40};
	public static final int FLIP_MS = 180;
	public static final int SETTLE_MS = 200;
	public static final int COUNT_STEP_MS = 1000;
	public static final int BANNER_IN_MS = 300;
	public static final int BANNER_HOLD_MS = 1500;
	public static final int BANNER_OUT_MS = 250;
	public static final int GRUDGE_SLIDE_MS = 300;
	public static final int GRUDGE_HOLD_END_MS = 1300;
	public static final int GRUDGE_END_MS = 1600;
	public static final int VS_POP_AT_MS = 350;
	public static final int PLATE_SLIDE_MS = 350;
	public static final int ROW_SLIDE_MS = 250;

	private PvpMotion() {}

	/** Drum hits due in (prevTick, tick]: how many to play now (a late frame plays the missed ones at once, max 2). */
	public static int drumHitsBetween(double prevTick, double tick) {
		int n = 0;
		for (int t : DRUM_TICKS) if (t > prevTick && t <= tick) n++;
		return Math.min(2, n);
	}

	/** Plaque tremble (px) in time with the drum: ±1 for 3 ticks after each hit, 0 afterwards. */
	public static int tremble(double tick, boolean reduceMotion) {
		if (reduceMotion || tick < 0 || tick > DRUM_TICKS[DRUM_TICKS.length - 1] + 3) return 0;
		for (int t : DRUM_TICKS) {
			double d = tick - t;
			if (d >= 0 && d < 3) return ((int) Math.floor(d * 2)) % 2 == 0 ? 1 : -1;
		}
		return 0;
	}

	/**
	 * Plaque flip: X-scale 1 → 0 → 1 over 180 ms; the face side shows from the midpoint. Returns
	 * {@code {scaleX, faceUp(0/1)}}.
	 */
	public static double[] flip(double msSinceFlip, boolean reduceMotion) {
		if (msSinceFlip < 0) return new double[] {1, 0};
		if (reduceMotion || msSinceFlip >= FLIP_MS) return new double[] {1, 1};
		double u = msSinceFlip / FLIP_MS;
		return u < 0.5 ? new double[] {1 - u * 2, 0} : new double[] {(u - 0.5) * 2, 1};
	}

	/** Countdown digit ({@code secondsLeft ≥ 1}) scale and alpha {@code ms} into its second. */
	public static double[] countdownDigit(double msIntoSecond, boolean reduceMotion) {
		double ms = Math.max(0, Math.min(COUNT_STEP_MS, msIntoSecond));
		double scale = reduceMotion ? 1 : ms < 250 ? 1.8 - 0.8 * Ease.OUT_BACK.apply(ms / 250) : 1;
		double alpha = ms > COUNT_STEP_MS - 200 ? (COUNT_STEP_MS - ms) / 200 : 1;
		return new double[] {scale, alpha};
	}

	/** Countdown colour step: gold (≥ 3 s), orange (2 s), red (1 s). */
	public static int countdownColor(int secondsLeft) {
		return secondsLeft >= 3 ? 0xFFFFD640 : secondsLeft == 2 ? 0xFFFF9A30 : 0xFFD83440;
	}

	/**
	 * Mode banner (No more bets, FINAL BALL, ALL SQUARE, UNDERDOG, EDGE, §11): {@code {dy, alpha, scale}} at
	 * {@code ms} since it was shown; alpha 0 once it is gone.
	 */
	public static double[] banner(double ms, boolean reduceMotion) {
		if (ms < 0) return new double[] {0, 0, 1};
		if (ms < BANNER_IN_MS) {
			double u = ms / BANNER_IN_MS;
			return reduceMotion ? new double[] {0, u, 1} : new double[] {-20 * (1 - Ease.OUT_BACK.apply(u)), Math.min(1, u * 1.5), 1};
		}
		double hold = BANNER_IN_MS + BANNER_HOLD_MS;
		if (ms < hold) return new double[] {0, 1, 1};
		double u = (ms - hold) / BANNER_OUT_MS;
		if (u >= 1) return new double[] {0, 0, 1};
		return new double[] {0, 1 - u, reduceMotion ? 1 : 1 - 0.05 * u};
	}

	/**
	 * Grudge clash (§9.5): {@code {slide 0–1 (1 = halves together), alpha, shakePx, subtitleChars/char-rate}}.
	 * The subtitle types on at 40 characters per second from the clash.
	 */
	public static double[] grudge(double ms, boolean reduceMotion) {
		if (ms < 0) return new double[] {0, 0, 0, 0};
		double slide = Ease.OUT_CUBIC.apply(Math.min(1, ms / GRUDGE_SLIDE_MS));
		double alpha = ms < GRUDGE_HOLD_END_MS ? 1 : Math.max(0, 1 - (ms - GRUDGE_HOLD_END_MS) / (GRUDGE_END_MS - GRUDGE_HOLD_END_MS));
		double since = ms - GRUDGE_SLIDE_MS;
		double shake = reduceMotion || since < 0 || since > 200 ? 0 : 4 * (1 - since / 200) * Math.sin(since / 200 * Math.PI * 6);
		double chars = since < 0 ? 0 : since * 40 / 1000;
		return new double[] {slide, alpha, shake, chars};
	}

	/** Plate slide-in (VS intro): offset factor 1 → 0 over 350 ms. */
	public static double plateSlide(double ms, boolean reduceMotion) {
		if (reduceMotion) return 0;
		return 1 - Ease.OUT_CUBIC.apply(Math.max(0, Math.min(1, ms / PLATE_SLIDE_MS)));
	}

	/** VS badge pop scale (0 before 350 ms, outBack to 1 over 250 ms). */
	public static double vsPop(double ms, boolean reduceMotion) {
		if (reduceMotion) return 1;
		if (ms < VS_POP_AT_MS) return 0;
		return Ease.OUT_BACK.apply(Math.min(1, (ms - VS_POP_AT_MS) / 250));
	}

	/** Lobby row slide-in: offset factor 1 → 0 over 250 ms {@code outCubic}. */
	public static double rowSlide(double ms, boolean reduceMotion) {
		if (reduceMotion) return 0;
		return 1 - Ease.OUT_CUBIC.apply(Math.max(0, Math.min(1, ms / ROW_SLIDE_MS)));
	}

	/** Empty-seat breathing alpha 0.4 ↔ 0.8 at 0.5 Hz. */
	public static double breathe(double ms, boolean reduceMotion) {
		if (reduceMotion) return 0.6;
		return 0.6 + 0.2 * Math.sin(ms / 1000.0 * Math.PI);
	}

	/** Timer ring colour: gold, red for the last 5 s. */
	public static int timerColor(long ticksLeft) {
		return ticksLeft <= 100 ? 0xFFD83440 : 0xFFFFD640;
	}

	/** Pot chip pile size: 0 small (&lt; 10× stake), 1 medium (&lt; 50×), 2 large (visual/extras.md §7.1). */
	public static int potPile(long pot, long stake) {
		long s = Math.max(1, stake);
		return pot < 10 * s ? 0 : pot < 50 * s ? 1 : 2;
	}

	/** Head-to-head chip: 0 lead, 1 trail, 2 even. */
	public static int recordChip(long wins, long losses) {
		return wins > losses ? 0 : wins < losses ? 1 : 2;
	}
}
