package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Motion curves of the slot features (slots.md §4.5–§4.10; PURE): Nether tumbles (explode, gravity fall with a
 * bounce, ladder pop), End expanding sticky wilds, free-spin intro / counter / retrigger, Piglin's Hoard mini reels
 * and collect sweep. Times are ms since the relevant beat start; sizes in GUI px or cells as documented.
 */
public final class FeatureMotion {
	/** Tumble gravity, px per ms² (≈ 44 px in 200 ms). */
	public static final double GRAVITY = 0.0022;
	public static final int FALL_REEL_DELAY_MS = 30;
	public static final int BOUNCE_MS = 100;
	public static final double BOUNCE_PX = 4;

	private FeatureMotion() {}

	// ---- Nether tumbles ----------------------------------------------------------------------------------------

	/** Exploding cell: scale 1 → 1.25 (inCubic) over 250 ms. */
	public static double explodeScale(double ms) {
		return 1 + 0.25 * Ease.IN_CUBIC.apply(ms / 250.0);
	}

	/** Exploding cell alpha 1 → 0 (inCubic); reduce motion: fade out in 150 ms. */
	public static double explodeAlpha(double ms, boolean reduceMotion) {
		if (reduceMotion) return 1 - Math.min(1, Math.max(0, ms) / 150.0);
		return 1 - Ease.IN_CUBIC.apply(ms / 250.0);
	}

	/** Burn flipbook frame (5 frames over 250 ms). */
	public static int burnFrame(double ms) {
		return Math.min(4, Math.max(0, (int) (ms / 50)));
	}

	/**
	 * Fall offset of a cell (px ABOVE its target, ≥ 0 → 0; negative = bounce below the target is not used; the bounce
	 * lifts the cell back up by ≤ 4 px).
	 *
	 * @param ms       ms since the fall beat start
	 * @param reel     reel index (each reel is delayed by 30 ms × r)
	 * @param rowOrder 0 for the lowest falling cell of the reel, 1 for the next … (lower cells first, +20 ms each)
	 * @param distance start height above the target, px (&gt; 0)
	 */
	public static double fallOffset(double ms, int reel, int rowOrder, double distance) {
		double t = ms - FALL_REEL_DELAY_MS * reel - 20.0 * rowOrder;
		if (distance <= 0) return 0;
		if (t <= 0) return distance;
		double fallen = 0.5 * GRAVITY * t * t;
		if (fallen < distance) return distance - fallen;
		double tHit = Math.sqrt(2 * distance / GRAVITY);
		double since = t - tHit;
		if (since >= BOUNCE_MS) return 0;
		// one outBounce settle: a small hop of ≤ 4 px
		double u = since / BOUNCE_MS;
		return BOUNCE_PX * Math.sin(Math.PI * u) * (1 - u);
	}

	/** Ladder plate pop 1 → 1.2 → 1 (200 ms outBack). */
	public static double platePop(double ms) {
		if (ms <= 0 || ms >= 200) return 1;
		double u = ms / 200.0;
		return 1 + 0.2 * Math.sin(Math.PI * Ease.OUT_BACK.apply(u) * 0.999);
	}

	/** Floating step amount: rise 16 px over 600 ms (outCubic), fade in the last 200 ms. Returns {rise, alpha}. */
	public static double floatRise(double ms) {
		return 16 * Ease.OUT_CUBIC.apply(ms / 600.0);
	}

	public static double floatAlpha(double ms) {
		if (ms <= 0) return 1;
		if (ms >= 600) return 0;
		return ms < 400 ? 1 : 1 - (ms - 400) / 200.0;
	}

	// ---- End expanding sticky wilds ----------------------------------------------------------------------------

	/** Expanded wild vertical scale 1/3 → 1 with outBack(1.4) over the WILD_EXPAND beat; reduce motion: instant. */
	public static double expandScale(double ms, double durMs, boolean reduceMotion) {
		if (reduceMotion || ms >= durMs) return 1;
		if (ms <= 0) return 1 / 3.0;
		return 1 / 3.0 + (2 / 3.0) * Ease.outBack(1.4, ms / durMs);
	}

	/** Sticky chain frame: alpha 0 → 1 and clamp scale 1.1 → 1 (outBack) over the WILD_STICK beat. */
	public static double stickAlpha(double ms, double durMs) {
		return Math.min(1, Math.max(0, ms / durMs));
	}

	public static double stickScale(double ms, double durMs) {
		if (ms <= 0) return 1.1;
		if (ms >= durMs) return 1;
		return 1.1 - 0.1 * Ease.OUT_BACK.apply(ms / durMs);
	}

	/** Crack flash on the egg before it expands (3 frames, 120 ms, starting 150 ms after the stop). */
	public static int crackFrame(double msSinceStop) {
		if (msSinceStop < 150 || msSinceStop >= 270) return -1;
		return (int) ((msSinceStop - 150) / 40);
	}

	/** Sticky reel shiver on other reels' spin-up: ±1 px once over 50 ms. */
	public static int shiver(double msSinceSpinUp) {
		if (msSinceSpinUp <= 0 || msSinceSpinUp >= 50) return 0;
		return msSinceSpinUp < 25 ? 1 : -1;
	}

	// ---- free spins ------------------------------------------------------------------------------------------

	/** Scatter pop in the intro: i-th scatter pops at 120·i ms (1 → 1.3 → 1). */
	public static double introPop(double ms, int i) {
		double t = ms - 120.0 * i;
		if (t <= 0 || t >= 240) return t >= 240 ? 1.3 : 1;
		return 1 + 0.3 * Ease.OUT_BACK.apply(t / 240.0);
	}

	/**
	 * Point on a quadratic arc from (x0, y0) to (x1, y1) lifted by {@code lift} px at the middle, at progress u (use
	 * the eased value). Writes x, y into {@code out}.
	 */
	public static void arc(double x0, double y0, double x1, double y1, double lift, double u, double[] out) {
		double cx = (x0 + x1) / 2;
		double cy = Math.min(y0, y1) - lift;
		double a = (1 - u) * (1 - u);
		double b = 2 * (1 - u) * u;
		double c = u * u;
		out[0] = a * x0 + b * cx + c * x1;
		out[1] = a * y0 + b * cy + c * y1;
	}

	/** Intro banner scale: 0 → 1 outBack between 600 and 900 ms; shrinks away 1800 → 2000 ms. */
	public static double introBanner(double ms) {
		if (ms < 600) return 0;
		if (ms < 900) return Ease.OUT_BACK.apply((ms - 600) / 300.0);
		if (ms < 1800) return 1;
		if (ms < 2000) return 1 - Ease.IN_CUBIC.apply((ms - 1800) / 200.0);
		return 0;
	}

	/** Awarded spins counting 0 → N in 400 ms from 600 ms (integer steps). */
	public static int introCount(double ms, int awarded) {
		if (ms < 600) return 0;
		if (ms >= 1000) return awarded;
		return (int) Math.floor(awarded * (ms - 600) / 400.0);
	}

	/** Iris wipe radius fraction (0 → 1) from 1200 to 1800 ms, inOutQuad; reduce motion = instant at 1200. */
	public static double iris(double ms, boolean reduceMotion) {
		if (ms < 1200) return 0;
		if (reduceMotion || ms >= 1800) return 1;
		return Ease.IN_OUT_QUAD.apply((ms - 1200) / 600.0);
	}

	/** Veil alpha during intro / outro (0 → 0.5). */
	public static double veil(double ms, double fadeInAt, double fadeMs) {
		if (ms <= fadeInAt) return 0;
		return 0.5 * Math.min(1, (ms - fadeInAt) / fadeMs);
	}

	/** Counter digit flip: 0 → 1 over 150 ms (old slides up and out, new slides in from below). */
	public static double digitFlip(double ms) {
		if (ms <= 0) return 0;
		return Math.min(1, ms / 150.0);
	}

	/** Counter punch 1.25 → 1 on a retrigger (300 ms). */
	public static double punch(double ms) {
		if (ms <= 0 || ms >= 300) return 1;
		return 1.25 - 0.25 * Ease.OUT_CUBIC.apply(ms / 300.0);
	}

	// ---- Piglin's Hoard ----------------------------------------------------------------------------------------

	/** Landing time of the i-th empty cell's mini reel in a respin (500 + 30·i ms). */
	public static int miniReelStop(int i) {
		return 500 + 30 * i;
	}

	/** Mini reel scroll (cells) of the ember blur before its stop; lands with a small bounce. */
	public static double miniReelOffset(double ms, int stopMs) {
		if (ms >= stopMs + 120) return 0;
		if (ms >= stopMs) {
			double u = (ms - stopMs) / 120.0;
			return -0.12 * Math.sin(Math.PI * u) * (1 - u);
		}
		return (ms * 0.02) % 1.0;
	}

	/** Coin lock frame (3 frames over 150 ms, staggered 60 ms by coin index). */
	public static int lockFrame(double ms, int coinIndex) {
		double t = ms - 60.0 * coinIndex;
		if (t < 0) return 0;
		return Math.min(2, (int) (t / 50));
	}

	/** Collect sweep: the coin index lit at {@code ms} since the collect start (120 ms each), or -1 after. */
	public static int collectIndex(double ms, int coins) {
		if (ms < 0) return -1;
		int i = (int) (ms / 120);
		return i >= coins ? -1 : i;
	}

	/** Pip fill shrink 1 → 0 over 150 ms. */
	public static double pipDrain(double ms) {
		return 1 - Math.min(1, Math.max(0, ms) / 150.0);
	}
}
