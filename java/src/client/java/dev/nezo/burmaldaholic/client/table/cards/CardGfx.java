package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing primitives of the card-table kit (lane J-L4): tinted texture blits by UV (atlases, pictures), tinted GUI
 * sprites (nine-slices and animated strips come from their {@code .mcmeta}), fills and text with alpha, and the
 * rotate / scale helpers of the motion primitives. The pipeline-taking {@code blit} overloads are bound at runtime
 * because {@code RenderPipeline} moved package between 26.2 and 26.3 (docs/architecture/java.md, checkLinkage); when
 * binding fails the untinted UV overload that links everywhere is used (colours with alpha &lt; ½ then skip the draw).
 */
public final class CardGfx {
	private static final MethodHandle BLIT13;
	private static final Object PIPELINE;

	static {
		MethodHandle mh = null;
		Object pipeline = null;
		try {
			Class<?> pipelines = Class.forName("net.minecraft.client.renderer.RenderPipelines");
			pipeline = pipelines.getField("GUI_TEXTURED").get(null);
			for (Method m : GuiGraphicsExtractor.class.getMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (m.getName().equals("blit") && p.length == 13 && p[0].isInstance(pipeline) && p[1] == Identifier.class && p[4] == float.class
					&& p[12] == int.class) {
					mh = MethodHandles.publicLookup().unreflect(m);
					break;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			mh = null;
		}
		BLIT13 = mh;
		PIPELINE = pipeline;
	}

	private CardGfx() {}

	/** ARGB with its alpha multiplied by {@code a}. */
	public static int alpha(int argb, double a) {
		int base = argb >>> 24;
		int na = (int) Math.round(Math.max(0, Math.min(1, a)) * base);
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	/** Opaque white with alpha {@code a}. */
	public static int white(double a) {
		return alpha(0xFFFFFFFF, a);
	}

	/**
	 * Region (u, v, uw × vh texels) of a texture of {@code tw × th} into (x, y, w × h), multiplied by {@code argb}.
	 */
	public static void tex(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int u, int v, int uw, int vh, int tw, int th, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		if (BLIT13 != null) {
			try {
				BLIT13.invoke(g, PIPELINE, id, x, y, (float) u, (float) v, w, h, uw, vh, tw, th, argb);
				return;
			} catch (Throwable ignored) {
				// fall through to the untinted overload
			}
		}
		if ((argb >>> 24) < 0x80) return;
		g.blit(id, x, y, x + w, y + h, u / (float) tw, (u + uw) / (float) tw, v / (float) th, (v + vh) / (float) th);
	}

	/** A whole picture 1 : 1. */
	public static void picture(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb) {
		tex(g, id, x, y, w, h, 0, 0, w, h, w, h, argb);
	}

	/** A GUI sprite {@code burmaldaholic:core/<path>} tinted with {@code argb}; a missing sprite draws {@code fallback}. */
	public static void sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb, int fallback) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		if (!FxSprites.blit(g, id, x, y, w, h, argb) && (fallback >>> 24) != 0) g.fill(x, y, x + w, y + h, alpha(fallback, (argb >>> 24) / 255.0));
	}

	public static void sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb) {
		sprite(g, id, x, y, w, h, argb, 0);
	}

	/** Text without shadow in {@code argb} (alpha ≥ 4/255 draws). */
	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int argb, boolean shadow) {
		if ((argb >>> 24) < 4) return;
		g.text(font, text, x, y, argb, shadow);
	}

	public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
		if ((argb >>> 24) < 4) return;
		g.text(font, text, x, y, argb, shadow);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int argb, boolean shadow) {
		text(g, font, text, cx - font.width(text) / 2, y, argb, shadow);
	}

	/**
	 * Text fitted into {@code maxW}: scaled down (never below ½) when too wide, and cut with an ellipsis when even ½ does
	 * not fit (it never draws past {@code maxW}), left-aligned at (x, y).
	 */
	public static void fitted(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxW, int argb, boolean shadow) {
		int w = font.width(text);
		if (w <= maxW) {
			text(g, font, text, x, y, argb, shadow);
			return;
		}
		if (maxW <= 0) return;
		float s = Math.max(0.5f, maxW / (float) w);
		FormattedCharSequence seq = s * w <= maxW + 0.01f ? text.getVisualOrderText() : CasinoUi.fit(font, text, (int) Math.floor(maxW / s));
		g.pose().pushMatrix();
		g.pose().translate(x, y + (font.lineHeight * (1 - s)) / 2f);
		g.pose().scale(s, s);
		text(g, font, seq, 0, 0, argb, shadow);
		g.pose().popMatrix();
	}

	/** Width {@link #fitted} draws {@code text} at within {@code maxW} (GUI px, rounded up). */
	public static int fittedWidth(Font font, Component text, int maxW) {
		int w = font.width(text);
		if (w <= maxW) return w;
		if (maxW <= 0) return 0;
		float s = Math.max(0.5f, maxW / (float) w);
		if (s * w <= maxW + 0.01f) return Math.min(maxW, (int) Math.ceil(s * w));
		return Math.min(maxW, (int) Math.ceil(font.width(CasinoUi.fit(font, text, (int) Math.floor(maxW / s))) * s));
	}

	/** Pushes a transform that draws a {@code w × h} box at (x, y) rotated by {@code deg} about its centre and scaled. */
	public static void pushBox(GuiGraphicsExtractor g, double x, double y, int w, int h, double deg, double sx, double sy) {
		g.pose().pushMatrix();
		g.pose().translate((float) (x + w / 2.0), (float) (y + h / 2.0));
		if (deg != 0) g.pose().rotate((float) Math.toRadians(deg));
		if (sx != 1 || sy != 1) g.pose().scale((float) sx, (float) sy);
		g.pose().translate(-w / 2f, -h / 2f);
	}

	public static void pop(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	/** A 1 px frame. */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + 1, argb);
		g.fill(x, y + h - 1, x + w, y + h, argb);
		g.fill(x, y + 1, x + 1, y + h - 1, argb);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}
}
