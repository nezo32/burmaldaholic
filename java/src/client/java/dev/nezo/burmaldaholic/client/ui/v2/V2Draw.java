package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing helpers of the v2 kit: boxes, sprites with flat fallbacks, 1× / 2× / 3× text, and the conversions of the pure
 * layer's keys and gates into components. Everything takes {@link Box}es in layout px (the chrome scales the pose).
 */
public final class V2Draw {
	private V2Draw() {}

	/** Fills {@code b}. */
	public static void fill(GuiGraphicsExtractor g, Box b, int argb) {
		if (!b.isEmpty()) g.fill(b.x(), b.y(), b.right(), b.bottom(), argb);
	}

	/** 1-px outline inside {@code b}. */
	public static void outline(GuiGraphicsExtractor g, Box b, int argb) {
		if (!b.isEmpty()) g.outline(b.x(), b.y(), b.w(), b.h(), argb);
	}

	/** A pill: fill, 1-px rim, a darker bottom row (the kit's flat style, also the sprite fallback). */
	public static void pill(GuiGraphicsExtractor g, Box b, int fill, int rim) {
		if (b.isEmpty()) return;
		g.fill(b.x() + 1, b.y(), b.right() - 1, b.bottom(), rim);
		g.fill(b.x(), b.y() + 1, b.right(), b.bottom() - 1, rim);
		g.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, fill);
		g.fill(b.x() + 1, b.bottom() - 2, b.right() - 1, b.bottom() - 1, darker(fill, 0.75f));
	}

	/** The sprite if present, else {@link #pill}. */
	public static void sprite(GuiGraphicsExtractor g, Identifier id, Box b, int fallbackFill, int fallbackRim) {
		if (b.isEmpty()) return;
		if (!(forceFallback || !FxSprites.blit(g, id, b.x(), b.y(), b.w(), b.h(), 0xFFFFFFFF))) return;
		pill(g, b, fallbackFill, fallbackRim);
	}

	/** True when the sprite is drawn (tinted {@code argb}); false = the caller draws its fallback. */
	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, Box b, int argb) {
		return !forceFallback && !b.isEmpty() && FxSprites.blit(g, id, b.x(), b.y(), b.w(), b.h(), argb);
	}

	/** Gallery / tests: draw every component with its flat-colour fallback (ui-v2.md §4.2 "the gallery asserts both"). */
	public static boolean forceFallback;

	/** Text at an integer {@code scale} with its top-left at {@code x, y}. */
	public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence seq, int x, int y, int scale, int color, boolean shadow) {
		if (scale == 1) {
			g.text(font, seq, x, y, color, shadow);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, seq, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int scale, int color, boolean shadow) {
		text(g, font, c.getVisualOrderText(), x, y, scale, color, shadow);
	}

	/** Text centred in {@code b} (both axes) at {@code scale}, ellipsised to the box width. */
	public static void centred(GuiGraphicsExtractor g, Font font, Component c, Box b, int scale, int color, boolean shadow) {
		int maxW = Math.max(0, b.w() / scale);
		FormattedCharSequence seq = CasinoUi.fit(font, c, maxW);
		int w = font.width(seq) * scale;
		int h = (font.lineHeight - 1) * scale;
		text(g, font, seq, b.x() + (b.w() - w) / 2 + (scale > 1 ? 0 : 0), b.y() + (b.h() - h + 1) / 2, scale, color, shadow);
	}

	/** Left-aligned, vertically centred in {@code b}, ellipsised. */
	public static void left(GuiGraphicsExtractor g, Font font, Component c, Box b, int pad, int scale, int color, boolean shadow) {
		FormattedCharSequence seq = CasinoUi.fit(font, c, Math.max(0, (b.w() - 2 * pad) / scale));
		int h = (font.lineHeight - 1) * scale;
		text(g, font, seq, b.x() + pad, b.y() + (b.h() - h + 1) / 2, scale, color, shadow);
	}

	// ---- keys and gates --------------------------------------------------------------------------------------------

	/** A pure-layer argument as a component: numbers formatted, strings raw (names), components as they are. */
	public static Object arg(Object a) {
		if (a instanceof Component c) return c;
		if (a instanceof Number n) return Texts.number(n.longValue());
		return Texts.raw(String.valueOf(a));
	}

	public static Component tr(String key, Object... args) {
		Object[] out = new Object[args.length];
		for (int i = 0; i < args.length; i++) out[i] = arg(args[i]);
		return Component.translatable(key, out);
	}

	public static Component label(Gate g) {
		return tr(g.labelKey(), g.labelArgs());
	}

	/** The gate's reason, or null when enabled. */
	public static Component reason(Gate g) {
		return g.enabled() ? null : tr(g.reasonKey(), g.reasonArgs());
	}

	/** {@code +75} / {@code −50} / {@code 50} for a net. */
	public static Component net(long net) {
		if (net > 0) return Component.translatable(V2Keys.NET_PLUS, Texts.number(net));
		if (net < 0) return Component.translatable(V2Keys.NET_MINUS, Texts.number(-net));
		return Texts.number(0);
	}

	/** The colour of a net (win green / lose red / push slate). */
	public static int netColor(long net) {
		return net > 0 ? CasinoPalette.V2_WIN : net < 0 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_PUSH;
	}

	public static int darker(int argb, float f) {
		int a = argb >>> 24;
		int r = (int) (((argb >> 16) & 0xFF) * f);
		int gg = (int) (((argb >> 8) & 0xFF) * f);
		int b = (int) ((argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	public static int alpha(int argb, float a) {
		return CasinoPalette.withAlpha(argb, ((argb >>> 24) / 255f) * a);
	}
}
