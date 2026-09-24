package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.core.anim.TextFit;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Big text for banners and tier words (global.md §2.2): a 1 px {@code ink} outline drawn as 4 offset copies
 * (not the vanilla drop shadow), integer scales only, Russian widths measured at runtime (3 → 2 → 1, then wrap
 * to at most 2 lines).
 */
public final class FxText {
	private FxText() {}

	/**
	 * Draws {@code text} centred on {@code cx} with its top at {@code y}, at {@code scale} (float: animation
	 * multipliers like a scale-in are applied on top of the fitted integer scale), outlined in {@code ink}.
	 */
	public static void outlinedCentered(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, float scale, int color, int ink) {
		FormattedCharSequence seq = text.getVisualOrderText();
		outlinedCentered(g, font, seq, font.width(seq), cx, y, scale, color, ink);
	}

	/** Same with a pre-measured sequence (steady-state frames reuse the measurement). */
	public static void outlinedCentered(GuiGraphicsExtractor g, Font font, FormattedCharSequence seq, int width, int cx, int y, float scale,
			int color, int ink) {
		if (scale <= 0.01f || (color >>> 24) < 4) return;
		int inkA = CasinoPalette.withAlpha(ink, ((color >>> 24) / 255f) * ((ink >>> 24) / 255f));
		g.pose().pushMatrix();
		g.pose().translate(cx, y + font.lineHeight * scale / 2f);
		g.pose().scale(scale, scale);
		int x = -width / 2;
		int ty = -font.lineHeight / 2;
		g.text(font, seq, x - 1, ty, inkA, false);
		g.text(font, seq, x + 1, ty, inkA, false);
		g.text(font, seq, x, ty - 1, inkA, false);
		g.text(font, seq, x, ty + 1, inkA, false);
		g.text(font, seq, x, ty, color, false);
		g.pose().popMatrix();
	}

	/** Outlined, left-aligned at 1× (small labels over busy backgrounds). */
	public static void outlined(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, int ink) {
		FormattedCharSequence seq = text.getVisualOrderText();
		int inkA = CasinoPalette.withAlpha(ink, ((color >>> 24) / 255f) * ((ink >>> 24) / 255f));
		g.text(font, seq, x - 1, y, inkA, false);
		g.text(font, seq, x + 1, y, inkA, false);
		g.text(font, seq, x, y - 1, inkA, false);
		g.text(font, seq, x, y + 1, inkA, false);
		g.text(font, seq, x, y, color, false);
	}

	/** Largest integer scale ≤ {@code preferred} at which {@code text} fits {@code maxWidth} (1 when nothing fits). */
	public static int fitScale(Font font, Component text, int preferred, int maxWidth) {
		return TextFit.bannerScale(font.width(text), preferred, maxWidth);
	}

	/**
	 * Draws a word-wrapped block (≤ {@code maxLines}) centred at 1× when even 1× overflows; returns the height used.
	 */
	public static int wrappedCentered(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int maxWidth, int maxLines, int color,
			int ink) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(20, maxWidth));
		int n = Math.min(maxLines, lines.size());
		for (int i = 0; i < n; i++) {
			FormattedCharSequence l = lines.get(i);
			outlinedCentered(g, font, l, font.width(l), cx, y + i * (font.lineHeight + 1), 1f, color, ink);
		}
		return n * (font.lineHeight + 1);
	}
}
