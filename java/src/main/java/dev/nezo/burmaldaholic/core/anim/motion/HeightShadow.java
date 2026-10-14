package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * One consistent pseudo-3D height look (RO §3.5, DI §4.2, CS §4.2): a height {@code z} becomes a sprite lift, a
 * shadow offset, a shadow alpha and a shadow size, so balls, dice, chips in flight and the dolly read alike. The
 * shadow stays on the floor; the sprite is lifted. Pure; writes into a reusable {@link Out} (no allocation).
 */
public final class HeightShadow {
	private HeightShadow() {}

	/**
	 * Look parameters; offsets are in art px per unit of z (then times the caller's {@code k}).
	 *
	 * @param liftX        sprite x offset per z
	 * @param liftY        sprite y offset per z (negative = up)
	 * @param shadowX      constant shadow x offset (art px)
	 * @param shadowY      constant shadow y offset (art px)
	 * @param shadowSlopeX shadow x offset per z (light from the top-left: positive)
	 * @param shadowSlopeY shadow y offset per z
	 * @param alpha0       shadow alpha at z = 0
	 * @param alphaZeroZ   z at which the alpha line reaches 0 (> 0)
	 * @param alphaMin     alpha floor
	 * @param scaleZeroZ   z at which the size line reaches 0; ≤ 0 = the shadow never shrinks
	 * @param scaleMin     size floor (fraction)
	 */
	public record Style(double liftX, double liftY, double shadowX, double shadowY, double shadowSlopeX, double shadowSlopeY, double alpha0,
			double alphaZeroZ, double alphaMin, double scaleZeroZ, double scaleMin) {}

	/** Roulette ball (RO §3.5): draw at ground + (−0.3z, −0.7z)·k; shadow at ground + (1, 1), α 0.45·(1 − z/8). */
	public static final Style ROULETTE = new Style(-0.3, -0.7, 1, 1, 0, 0, 0.45, 8, 0, 0, 1);
	/** Duel dice (DI §4.2, z = centre height in tray units): lift 0.9·z; shadow width 1 − z/12, α 0.45·(1 − z/10). */
	public static final Style DICE = new Style(0, -0.9, 0, 0, 0, 0, 0.45, 10, 0, 12, 0);
	/** Craps dice (CS §4.2, z in die units): shadow at (+0.25z, +0.15z), α 0.45 → 0.2 with height, 3 sizes. */
	public static final Style CRAPS = new Style(0, -1, 0, 0, 0.25, 0.15, 0.45, 4, 0.2, 5, 0.6);

	/** Result (mutable, reused). Offsets in the caller's units (art px × k). */
	public static final class Out {
		/** Sprite offset from the ground position. */
		public double dx, dy;
		/** Shadow offset from the ground position. */
		public double shadowDx, shadowDy;
		/** Shadow alpha 0–1. */
		public double shadowAlpha;
		/** Shadow size factor (1 = full). */
		public double shadowScale;
		/** Shadow sprite variant for 3-size sets: 0 = 100 %, 1 = 80 %, 2 = 60 % (nearest to {@link #shadowScale}). */
		public int shadowSize;
	}

	/** {@link #ROULETTE} look of height {@code z} (art px) at {@code k} layout px per art px. */
	public static void apply(double z, double k, Out o) {
		apply(ROULETTE, z, k, o);
	}

	/** The {@code style} look of height {@code z} at {@code k} (units of the result per art px). */
	public static void apply(Style s, double z, double k, Out o) {
		double h = Math.max(0, z);
		o.dx = s.liftX * h * k;
		o.dy = s.liftY * h * k;
		o.shadowDx = (s.shadowX + s.shadowSlopeX * h) * k;
		o.shadowDy = (s.shadowY + s.shadowSlopeY * h) * k;
		double a = s.alpha0 * (1 - h / s.alphaZeroZ);
		o.shadowAlpha = Math.max(0, Math.min(1, Math.max(s.alphaMin, a)));
		o.shadowScale = s.scaleZeroZ > 0 ? Math.max(s.scaleMin, Math.min(1, 1 - h / s.scaleZeroZ)) : 1;
		o.shadowSize = o.shadowScale > 0.9 ? 0 : o.shadowScale > 0.7 ? 1 : 2;
	}
}
