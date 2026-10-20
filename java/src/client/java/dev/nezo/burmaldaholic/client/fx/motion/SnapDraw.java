package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Float-position drawing of MOVING sprites (PHASE2B §2.2.4, PL §3.1, RO §3.1): the position is snapped to
 * {@code 1/k} layout px ({@link PixelSnap}) and applied as a pose translation, then the sprite is blitted 1:1 at
 * (0, 0). The sprite moves in 1-physical-px steps and every texel stays crisp.
 *
 * <p>The {@code scale} overloads draw a sprite on an {@link dev.nezo.burmaldaholic.core.anim.motion.ArtScale} stage
 * (pose scale {@code s = n/k} layout px per art px: Plinko ball 8/9 at 4K GUI 9, dice 8/9 or 5/2, roulette ball ×2 on
 * the 208×2 focus wheel): the position is snapped FIRST, then the pose is scaled, so every texel edge lands on
 * {@code round(x·k) + i·n} physical px — whole physical px whenever {@code n} is an integer
 * ({@link PixelSnap#texelEdge}). Width, height and region sizes are then in ART px.
 *
 * <p>Argument order is the same everywhere: {@code ..., argb, [scale,] k}. Drawing never throws in render: a
 * non-positive scale draws nothing, {@code k < 1} is treated as 1, and a legacy call with {@code (k, argb)} swapped
 * (the MKa order of {@code blitF}) is detected ({@code k} outside 1..64 while {@code argb} is 1..64) and swapped back.
 */
public final class SnapDraw {
	/** Largest plausible physical-px-per-layout-px scale (4K at GUI 9 is 9). */
	static final int MAX_K = 64;

	private SnapDraw() {}

	/** Atlas sprite {@code sprite} at float (x, y), {@code w × h}, snapped to {@code 1/k}. */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, int k) {
		blitF(g, sprite, x, y, w, h, 0xFFFFFFFF, k);
	}

	/** Tinted / faded atlas sprite at float (x, y): {@code (..., argb, k)} like {@link #regionF} / {@link #frameF}. */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, int argb, int k) {
		if (legacySwapped(argb, k)) {
			int t = argb;
			argb = k;
			k = t;
		}
		if ((argb >>> 24) == 0) return;
		if (!push(g, x, y, 1, k)) return;
		FxSprites.blit(g, sprite, 0, 0, w, h, argb);
		g.pose().popMatrix();
	}

	/** Atlas sprite of {@code w × h} ART px at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, int argb, double scale, int k) {
		if ((argb >>> 24) == 0) return;
		if (!push(g, x, y, scale, k)) return;
		FxSprites.blit(g, sprite, 0, 0, w, h, argb);
		g.pose().popMatrix();
	}

	/** Region of {@code w × h} ART px at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void regionF(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, double x, double y, int argb, double scale,
			int k) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		if (!push(g, x, y, scale, k)) return;
		TableGfx.region(g, tex, texW, texH, u, v, w, h, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Strip frame ({@code fw × fh} ART px) at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void frameF(GuiGraphicsExtractor g, String spritePath, int fw, int fh, int frames, int index, double x, double y, int argb, double scale,
			int k) {
		if ((argb >>> 24) == 0) return;
		if (!push(g, x, y, scale, k)) return;
		TableGfx.frame(g, spritePath, fw, fh, frames, index, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Region {@code (u, v, w, h)} of texture {@code tex} ({@code texW × texH}) at float (x, y) (see {@link TableGfx#region}). */
	public static void regionF(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, double x, double y, int argb, int k) {
		regionF(g, tex, texW, texH, u, v, w, h, x, y, argb, 1.0, k);
	}

	/** Frame {@code index} of a vertical sprite strip at float (x, y) (see {@link TableGfx#frame}). */
	public static void frameF(GuiGraphicsExtractor g, String spritePath, int fw, int fh, int frames, int index, double x, double y, int argb, int k) {
		frameF(g, spritePath, fw, fh, frames, index, x, y, argb, 1.0, k);
	}

	/**
	 * Pushes the pose, translates to the snapped (x, y), then scales (snap before scale). Returns false (nothing
	 * pushed) for a non-positive or non-finite scale or position.
	 */
	private static boolean push(GuiGraphicsExtractor g, double x, double y, double scale, int k) {
		if (!(scale > 0) || Double.isInfinite(scale) || !Double.isFinite(x) || !Double.isFinite(y)) return false;
		int kk = Math.max(1, Math.min(MAX_K, k));
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, kk), PixelSnap.snapF(y, kk));
		if (scale != 1) g.pose().scale((float) scale, (float) scale);
		return true;
	}

	/** True for a call with {@code k} and {@code argb} swapped (MKa's {@code blitF(..., k, argb)} order). */
	static boolean legacySwapped(int argb, int k) {
		return (k < 1 || k > MAX_K) && argb >= 1 && argb <= MAX_K;
	}
}
