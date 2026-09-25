package dev.nezo.burmaldaholic.games.extras.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Tiny procedural drawings (no textures, no text): coin, dice faces, creeper face, triangles. */
final class Art {
	private Art() {}

	/** Filled ellipse from horizontal spans; {@code xScale} 0..1 squashes it (coin flip). */
	static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, double xScale, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy) * xScale);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static final int[][][] PIPS = {
		{},
		{{1, 1}},
		{{0, 0}, {2, 2}},
		{{0, 0}, {1, 1}, {2, 2}},
		{{0, 0}, {2, 0}, {0, 2}, {2, 2}},
		{{0, 0}, {2, 0}, {1, 1}, {0, 2}, {2, 2}},
		{{0, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {2, 2}},
	};

	/** A die showing {@code face} (1–6) at x, y with the given size (≥ 12). */
	static void die(GuiGraphicsExtractor g, int x, int y, int size, int face) {
		g.fill(x, y, x + size, y + size, 0xFF222222);
		g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFFF4F1E8);
		if (face < 1 || face > 6) {
			return;
		}
		int pip = Math.max(2, size / 6);
		int inner = size - 2 * pip - 2;
		for (int[] p : PIPS[face]) {
			int px = x + 1 + pip / 2 + p[0] * inner / 2 + pip / 2;
			int py = y + 1 + pip / 2 + p[1] * inner / 2 + pip / 2;
			g.fill(px, py, px + pip, py + pip, 0xFF1A1A1A);
		}
	}

	/** Creeper face in a {@code size}×{@code size} square (8×8 pixel grid). */
	static void creeper(GuiGraphicsExtractor g, int x, int y, int size) {
		g.fill(x, y, x + size, y + size, 0xFF3FA535);
		int c = size / 8;
		int ox = x + (size - 8 * c) / 2;
		int oy = y + (size - 8 * c) / 2;
		int black = 0xFF101010;
		g.fill(ox + c, oy + 2 * c, ox + 3 * c, oy + 4 * c, black);
		g.fill(ox + 5 * c, oy + 2 * c, ox + 7 * c, oy + 4 * c, black);
		g.fill(ox + 3 * c, oy + 4 * c, ox + 5 * c, oy + 6 * c, black);
		g.fill(ox + 2 * c, oy + 5 * c, ox + 3 * c, oy + 7 * c, black);
		g.fill(ox + 5 * c, oy + 5 * c, ox + 6 * c, oy + 7 * c, black);
	}

	/** Downward-pointing triangle (pointer) with its tip at (cx, tipY). */
	static void pointerDown(GuiGraphicsExtractor g, int cx, int tipY, int h, int color) {
		for (int i = 0; i < h; i++) {
			g.fill(cx - i, tipY - i - 1, cx + i + 1, tipY - i, color);
		}
	}

	/**
	 * Word-wrapped text with shadow; returns the y below it. (GuiGraphicsExtractor#textWithWordWrap does not
	 * exist in 26.3, so wrap with {@link Font#split} and draw each line.)
	 */
	static int wrap(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, true);
			y += font.lineHeight;
		}
		return y;
	}

	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}
}
