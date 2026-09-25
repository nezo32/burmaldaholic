package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Drawing primitives of the table kit (lane J-L6; docs/design/visual/tables.md §11): atlas sprites
 * ({@code burmaldaholic:tables/<owner>/<name>}, nine-slices and looping {@code .mcmeta} animations handled by the GUI
 * atlas), code-picked frames of sprite strips and regions of the UV sheets ({@code textures/gui/tables/…}).
 *
 * <p>Tints and alpha go through {@code blit(RenderPipeline, …, argb)} bound at runtime (the pipeline class moved
 * package between 26.2 and 26.3, so a direct call would not link on 26.3 — the same approach as
 * {@link FxSprites}); when that binding is unavailable a draw without tint is used and nearly transparent draws are
 * skipped. Nothing is scaled by a fraction: every region is drawn 1:1 (or mirrored).
 */
public final class TableGfx {
	private TableGfx() {}

	/** Atlas sprite id {@code burmaldaholic:tables/<path>}. */
	public static Identifier sprite(String path) {
		return Burmaldaholic.id("tables/" + path);
	}

	/** Texture of a sheet {@code textures/gui/tables/<path>.png}. */
	public static Identifier sheet(String path) {
		return Burmaldaholic.id("textures/gui/tables/" + path + ".png");
	}

	/** Texture of an atlas sprite file (for code-picked frames of a strip). */
	public static Identifier spriteFile(String path) {
		return Burmaldaholic.id("textures/gui/sprites/tables/" + path + ".png");
	}

	/** Draws an atlas sprite (nine-slice / animation from its .mcmeta); returns false when it is missing. */
	public static boolean blit(GuiGraphicsExtractor g, String path, int x, int y, int w, int h) {
		return FxSprites.blit(g, sprite(path), x, y, w, h, 0xFFFFFFFF);
	}

	/** Tinted / faded atlas sprite. */
	public static boolean blit(GuiGraphicsExtractor g, String path, int x, int y, int w, int h, int argb) {
		if ((argb >>> 24) == 0) {
			return true;
		}
		return FxSprites.blit(g, sprite(path), x, y, w, h, argb);
	}

	/**
	 * Region {@code (u, v, w, h)} of texture {@code tex} ({@code texW × texH}) drawn 1:1 at (x, y), multiplied by
	 * {@code argb}.
	 */
	public static void region(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, int x, int y, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		if (argb != 0xFFFFFFFF && Compat.tinted(g, tex, x, y, u, v, w, h, texW, texH, argb)) {
			return;
		}
		if ((argb >>> 24) < 0x60) {
			return; // no tint binding: skip nearly invisible draws rather than drawing them opaque
		}
		g.blit(tex, x, y, x + w, y + h, u / (float) texW, (u + w) / (float) texW, v / (float) texH, (v + h) / (float) texH);
	}

	/** Region drawn mirrored left ↔ right (the right-lane dice cup). */
	public static void regionMirrored(GuiGraphicsExtractor g, Identifier tex, int texW, int texH, int u, int v, int w, int h, int x, int y) {
		g.blit(tex, x, y, x + w, y + h, (u + w) / (float) texW, u / (float) texW, v / (float) texH, (v + h) / (float) texH);
	}

	/** Frame {@code index} of a vertical sprite strip ({@code fw × fh} frames) drawn at (x, y). */
	public static void frame(GuiGraphicsExtractor g, String spritePath, int fw, int fh, int frames, int index, int x, int y, int argb) {
		int i = Math.max(0, Math.min(frames - 1, index));
		region(g, spriteFile(spritePath), fw, fh * frames, 0, i * fh, fw, fh, x, y, argb);
	}

	/** Tiles {@code tex} ({@code tw × th}) over the rectangle (x, y, w, h), clipped at its right and bottom edges. */
	public static void tile(GuiGraphicsExtractor g, Identifier tex, int tw, int th, int x, int y, int w, int h) {
		for (int yy = 0; yy < h; yy += th) {
			for (int xx = 0; xx < w; xx += tw) {
				int cw = Math.min(tw, w - xx);
				int ch = Math.min(th, h - yy);
				region(g, tex, tw, th, 0, 0, cw, ch, x + xx, y + yy, 0xFFFFFFFF);
			}
		}
	}

	/** A filled disc (spans). */
	public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	/** Multiplies a colour's alpha by {@code a} (0–1). */
	public static int alpha(int argb, double a) {
		int na = (int) Math.round(Math.max(0, Math.min(1, a)) * (argb >>> 24));
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	/** White with alpha {@code a} (tint for fading sprites). */
	public static int fade(double a) {
		return alpha(0xFFFFFFFF, a);
	}

	/** Linear colour mix. */
	public static int lerp(int c0, int c1, double t) {
		double u = Math.max(0, Math.min(1, t));
		int a = (int) Math.round(((c0 >>> 24) & 0xFF) * (1 - u) + ((c1 >>> 24) & 0xFF) * u);
		int r = (int) Math.round(((c0 >> 16) & 0xFF) * (1 - u) + ((c1 >> 16) & 0xFF) * u);
		int gg = (int) Math.round(((c0 >> 8) & 0xFF) * (1 - u) + ((c1 >> 8) & 0xFF) * u);
		int b = (int) Math.round((c0 & 0xFF) * (1 - u) + (c1 & 0xFF) * u);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	/** Runtime binding of the tinted UV blit (see the class comment). */
	static final class Compat {
		private static final MethodHandle BLIT;
		private static final Object PIPELINE;

		static {
			MethodHandle mh = null;
			Object pipeline = null;
			try {
				Class<?> pipelines = Class.forName("net.minecraft.client.renderer.RenderPipelines");
				pipeline = pipelines.getField("GUI_TEXTURED").get(null);
				for (Method m : GuiGraphicsExtractor.class.getMethods()) {
					Class<?>[] p = m.getParameterTypes();
					// blit(pipeline, id, x, y, u, v, w, h, texW, texH, argb)
					if (m.getName().equals("blit") && p.length == 11 && p[0].isInstance(pipeline) && p[1] == Identifier.class && p[2] == int.class
						&& p[4] == float.class && p[5] == float.class && p[10] == int.class) {
						mh = MethodHandles.publicLookup().unreflect(m);
						break;
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				mh = null;
			}
			BLIT = mh;
			PIPELINE = pipeline;
		}

		static boolean tinted(GuiGraphicsExtractor g, Identifier id, int x, int y, int u, int v, int w, int h, int texW, int texH, int argb) {
			if (BLIT == null) {
				return false;
			}
			try {
				BLIT.invoke(g, PIPELINE, id, x, y, (float) u, (float) v, w, h, texW, texH, argb);
				return true;
			} catch (Throwable t) {
				return false;
			}
		}
	}
}
