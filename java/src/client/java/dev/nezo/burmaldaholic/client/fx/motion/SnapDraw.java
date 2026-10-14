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
 */
public final class SnapDraw {
	private SnapDraw() {}

	/** Atlas sprite {@code sprite} at float (x, y), {@code w × h}, snapped to {@code 1/k}. */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, int k) {
		blitF(g, sprite, x, y, w, h, k, 0xFFFFFFFF);
	}

	/** Tinted / faded atlas sprite at float (x, y). */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, int k, int argb) {
		if ((argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, k), PixelSnap.snapF(y, k));
		FxSprites.blit(g, sprite, 0, 0, w, h, argb);
		g.pose().popMatrix();
	}

	/** Atlas sprite of {@code w × h} ART px at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void blitF(GuiGraphicsExtractor g, Identifier sprite, double x, double y, int w, int h, double scale, int k, int argb) {
		if ((argb >>> 24) == 0) return;
		push(g, x, y, scale, k);
		FxSprites.blit(g, sprite, 0, 0, w, h, argb);
		g.pose().popMatrix();
	}

	/** Region of {@code w × h} ART px at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void regionF(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, double x, double y, int argb, double scale,
			int k) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		push(g, x, y, scale, k);
		TableGfx.region(g, tex, texW, texH, u, v, w, h, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Strip frame ({@code fw × fh} ART px) at float (x, y), scaled by {@code scale} layout px per art px. */
	public static void frameF(GuiGraphicsExtractor g, String spritePath, int fw, int fh, int frames, int index, double x, double y, int argb, double scale,
			int k) {
		if ((argb >>> 24) == 0) return;
		push(g, x, y, scale, k);
		TableGfx.frame(g, spritePath, fw, fh, frames, index, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Pushes the pose, translates to the snapped (x, y), then scales (snap before scale). */
	private static void push(GuiGraphicsExtractor g, double x, double y, double scale, int k) {
		if (!(scale > 0)) throw new IllegalArgumentException("scale " + scale);
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, k), PixelSnap.snapF(y, k));
		if (scale != 1) g.pose().scale((float) scale, (float) scale);
	}

	/** Region {@code (u, v, w, h)} of texture {@code tex} ({@code texW × texH}) at float (x, y) (see {@link TableGfx#region}). */
	public static void regionF(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, double x, double y, int argb, int k) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, k), PixelSnap.snapF(y, k));
		TableGfx.region(g, tex, texW, texH, u, v, w, h, 0, 0, argb);
		g.pose().popMatrix();
	}

	/** Frame {@code index} of a vertical sprite strip at float (x, y) (see {@link TableGfx#frame}). */
	public static void frameF(GuiGraphicsExtractor g, String spritePath, int fw, int fh, int frames, int index, double x, double y, int argb, int k) {
		if ((argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, k), PixelSnap.snapF(y, k));
		TableGfx.frame(g, spritePath, fw, fh, frames, index, 0, 0, argb);
		g.pose().popMatrix();
	}
}
