package dev.nezo.burmaldaholic.games.slots.client.fx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Small drawing helpers for the slot screen (lane J-L9): colour maths, rotated fills (way paths, rays, wedges),
 * bevelled panels, glow frames and scaled outlined text. Everything is a {@code fill} / {@code text} with the pose
 * stack, so it works with or without the generated sprite sheets (placeholders until SX1 art lands).
 */
public final class SlotDraw {
	private SlotDraw() {}

	// ---- colours ----------------------------------------------------------------------------------------------

	public static int alpha(int argb, double a) {
		int base = argb >>> 24;
		int na = (int) Math.round(Math.max(0, Math.min(1, a)) * base);
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	public static int withAlpha(int argb, double a) {
		int na = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	public static int lerp(int c0, int c1, double t) {
		double u = Math.max(0, Math.min(1, t));
		int a = (int) Math.round(((c0 >>> 24) & 0xFF) * (1 - u) + ((c1 >>> 24) & 0xFF) * u);
		int r = (int) Math.round(((c0 >> 16) & 0xFF) * (1 - u) + ((c1 >> 16) & 0xFF) * u);
		int g = (int) Math.round(((c0 >> 8) & 0xFF) * (1 - u) + ((c1 >> 8) & 0xFF) * u);
		int b = (int) Math.round((c0 & 0xFF) * (1 - u) + (c1 & 0xFF) * u);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Multiplies RGB by {@code f} (brightness), keeping alpha. */
	public static int shade(int argb, double f) {
		int a = argb >>> 24;
		int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * f);
		int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * f);
		int b = (int) Math.min(255, (argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** HSV rainbow (Grand rim cycle), hue in [0, 1). */
	public static int rainbow(double hue) {
		double h = (hue % 1 + 1) % 1 * 6;
		int i = (int) h;
		double f = h - i;
		int q = (int) (255 * (1 - f));
		int t = (int) (255 * f);
		return switch (i) {
			case 0 -> 0xFF000000 | 255 << 16 | t << 8;
			case 1 -> 0xFF000000 | q << 16 | 255 << 8;
			case 2 -> 0xFF000000 | 255 << 8 | t;
			case 3 -> 0xFF000000 | q << 8 | 255;
			case 4 -> 0xFF000000 | t << 16 | 255;
			default -> 0xFF000000 | 255 << 16 | q;
		};
	}

	// ---- shapes -----------------------------------------------------------------------------------------------

	/** A {@code thickness}-px line from (x0, y0) to (x1, y1) as a rotated fill. */
	public static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, float thickness, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5f) return;
		g.pose().pushMatrix();
		g.pose().translate((float) x0, (float) y0);
		g.pose().rotate((float) Math.atan2(dy, dx));
		int h = Math.max(1, Math.round(thickness));
		g.fill(0, -h / 2, Math.round(len), -h / 2 + h, color);
		g.pose().popMatrix();
	}

	/** A segment of a line: from fraction {@code a} to {@code b} (0..1) of its length. */
	public static void lineSegment(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, double a, double b, float thickness, int color) {
		double sa = Math.max(0, Math.min(1, a));
		double sb = Math.max(0, Math.min(1, b));
		if (sb <= sa) return;
		line(g, x0 + (x1 - x0) * sa, y0 + (y1 - y0) * sa, x0 + (x1 - x0) * sb, y0 + (y1 - y0) * sb, thickness, color);
	}

	/** Light rays around (cx, cy): {@code count} thin wedges (tapered quads) rotating with {@code angle} (rad). */
	public static void rays(GuiGraphicsExtractor g, float cx, float cy, float radius, int count, double angle, int color) {
		for (int i = 0; i < count; i++) {
			double a = angle + i * Math.PI * 2 / count;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) a);
			// taper: three stacked fills of decreasing width
			int r = Math.round(radius);
			g.fill(6, -3, r, 3, alpha(color, 0.35));
			g.fill(6, -2, r * 5 / 6, 2, alpha(color, 0.45));
			g.fill(6, -1, r * 2 / 3, 1, alpha(color, 0.6));
			g.pose().popMatrix();
		}
	}

	/** Bevelled panel: fill, 1-px dark border, 1-px light top/left highlight. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border, int highlight) {
		g.fill(x, y, x + w, y + h, border);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
		g.fill(x + 1, y + 2, x + 2, y + h - 1, alpha(highlight, 0.6));
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shade(fill, 0.6));
	}

	/** Gradient-filled plate with a bright rim (meters, counters, banners). */
	public static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom, int rim) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, shade(rim, 0.45));
		g.fill(x, y, x + w, y + h, rim);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, alpha(0xFFFFFFFF, 0.25));
	}

	/** A hollow frame of {@code t} px. */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
		g.fill(x, y, x + w, y + t, color);
		g.fill(x, y + h - t, x + w, y + h, color);
		g.fill(x, y + t, x + t, y + h - t, color);
		g.fill(x + w - t, y + t, x + w, y + h - t, color);
	}

	/** Soft glow: concentric frames fading outwards. */
	public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, int size, double strength) {
		for (int i = 1; i <= size; i++) {
			frame(g, x - i, y - i, w + 2 * i, h + 2 * i, 1, alpha(color, strength * (1 - (i - 1) / (double) size) * 0.6));
		}
	}

	/** Filled disc (approximated by horizontal spans). */
	public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	/** Diamond gem (jackpot badges): a rhombus of half-size {@code r}. */
	public static void gem(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = r - Math.abs(dy);
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, dy < 0 ? shade(color, 1.25) : shade(color, 0.85));
		}
		g.fill(cx - 1, cy - r / 2, cx + 1, cy - r / 2 + 1, 0xCCFFFFFF);
	}

	// ---- text -------------------------------------------------------------------------------------------------

	/** Centred text at a scale with a 1-px ink outline (4 offsets) for readability over busy art. */
	public static void outlined(GuiGraphicsExtractor g, Font font, Component text, float cx, float cy, float scale, int color, int ink) {
		FormattedCharSequence seq = text.getVisualOrderText();
		int w = font.width(seq);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		int x = -w / 2;
		int y = -4;
		g.text(font, seq, x - 1, y, ink, false);
		g.text(font, seq, x + 1, y, ink, false);
		g.text(font, seq, x, y - 1, ink, false);
		g.text(font, seq, x, y + 1, ink, false);
		g.text(font, seq, x, y, color, false);
		g.pose().popMatrix();
	}

	/** Largest integer scale (3 → 2 → 1, never fractional) at which {@code text} fits {@code maxWidth}. */
	public static int fitScale(Font font, Component text, int preferred, int maxWidth) {
		int w = font.width(text);
		for (int s = preferred; s > 1; s--) if (w * s <= maxWidth) return s;
		return 1;
	}

	/** Left-aligned text clipped with an ellipsis-free cut so it never exceeds {@code maxWidth}. */
	public static void textFit(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color) {
		textFit(g, font, text, x, y, maxWidth, color, true);
	}

	/** {@link #textFit} with or without the drop shadow (dark text on gold faces has none). */
	public static void textFit(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color, boolean shadow) {
		if (font.width(text) <= maxWidth) {
			g.text(font, text, x, y, color, shadow);
			return;
		}
		float s = Math.max(0.5f, maxWidth / (float) Math.max(1, font.width(text)));
		g.pose().pushMatrix();
		g.pose().translate(x, y + (1 - s) * 4);
		g.pose().scale(s, s);
		g.text(font, text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Right-aligned {@link #textFit}. */
	public static void textRight(GuiGraphicsExtractor g, Font font, Component text, int right, int y, int maxWidth, int color) {
		int w = Math.min(maxWidth, font.width(text));
		textFit(g, font, text, right - w, y, maxWidth, color, true);
	}

	/** A small casino chip glyph (value plates): gold rim, red face, white notches. */
	public static void chip(GuiGraphicsExtractor g, int cx, int cy) {
		disc(g, cx, cy, 4, 0xFF180A28);
		disc(g, cx, cy, 3, 0xFFFFD640);
		disc(g, cx, cy, 2, 0xFFD83440);
		g.fill(cx, cy - 3, cx + 1, cy - 2, 0xFFFFFFFF);
		g.fill(cx, cy + 2, cx + 1, cy + 3, 0xFFFFFFFF);
		g.fill(cx - 3, cy, cx - 2, cy + 1, 0xFFFFFFFF);
		g.fill(cx + 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
	}

	/** Word-wrapped text (textWithWordWrap changed its return type between 26.2 and 26.3); returns the next y. */
	public static int wrap(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y;
	}

	/** Centred text that shrinks (never below 0.5) to fit {@code maxWidth}. */
	public static void centeredFit(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int maxWidth, int color) {
		int w = font.width(text);
		if (w <= maxWidth) {
			g.centeredText(font, text, cx, y, color);
			return;
		}
		float s = Math.max(0.5f, maxWidth / (float) w);
		g.pose().pushMatrix();
		g.pose().translate(cx, y + (1 - s) * 4);
		g.pose().scale(s, s);
		g.centeredText(font, text, 0, 0, color);
		g.pose().popMatrix();
	}
}
