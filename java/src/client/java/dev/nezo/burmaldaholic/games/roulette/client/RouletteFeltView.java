package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Layout;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws the roulette PlayArea of the v2 screen from {@link RouletteLayout}'s boxes (REDESIGN.md §7.5): the printed
 * layout (red / black number tiles with 1× numbers, the green zero, 2:1, dozens, the even-money row with the colour
 * diamonds), the felt stacks (≤ 4 discs, snapped off the numbers), value tags, the "Last numbers" pills and the call-bet
 * row. Procedural fills at whole GUI px — crisp at every size; nothing here decides anything (the layout and the
 * screen's state do).
 */
final class RouletteFeltView {
	static final int PRINT = 0xD8E8D9A0;
	static final int PRINT_DIM = 0x80E8D9A0;
	static final int INK = 0xFF180A28;
	static final int BONE = 0xFFF4ECF8;
	static final int GOLD = 0xFFFFD640;
	static final int MUTED = 0xFFC0B0DC;
	static final int YOU = 0xFF5CE8E0;
	static final int[] RED = {0xFFE05058, 0xFFC8303C, 0xFF8C1834};
	static final int[] BLACK = {0xFF3E3646, 0xFF26202C, 0xFF120E18};
	static final int[] GREEN = {0xFF4CB866, 0xFF2E8C48, 0xFF1A5A2C};
	static final int FELT_SHADE = 0x30000000;

	private RouletteFeltView() {}

	static int[] tile(int n) {
		return switch (Wheel.color(n)) {
			case RED -> RED;
			case BLACK -> BLACK;
			case GREEN -> GREEN;
		};
	}

	private static void rect(GuiGraphicsExtractor g, Box b, int c) {
		g.fill(b.x(), b.y(), b.right(), b.bottom(), c);
	}

	private static void outline(GuiGraphicsExtractor g, Box b, int t, int c) {
		g.fill(b.x(), b.y(), b.right(), b.y() + t, c);
		g.fill(b.x(), b.bottom() - t, b.right(), b.bottom(), c);
		g.fill(b.x(), b.y() + t, b.x() + t, b.bottom() - t, c);
		g.fill(b.right() - t, b.y() + t, b.right(), b.bottom() - t, c);
	}

	private static void centred(GuiGraphicsExtractor g, Font font, Component text, Box b, int color, boolean shadow) {
		FormattedCharSequence seq = text.getVisualOrderText();
		int w = font.width(seq);
		if (w > b.w() - 2) {
			seq = TableChrome.fit(font, text, b.w() - 2);
			w = font.width(seq);
		}
		g.text(font, seq, b.x() + (b.w() - w + 1) / 2, b.y() + (b.h() - 8) / 2 + 1, color, shadow);
	}

	/**
	 * The betting layout. {@code dim} (0..1) darkens it during the spin; {@code lit} are numbers lit by the hover
	 * (straight-up / line bets) or a call bet; {@code litOutside} an outside spot lit by the hover; {@code win} the
	 * drawn number (gold frame) or -1.
	 */
	static void layout(GuiGraphicsExtractor g, Font font, RouletteLayout l, double dim, Set<Integer> lit, Spot litOutside, int win) {
		Box all = l.layout;
		int line = dim > 0 ? PRINT_DIM : PRINT;
		// felt shade under the print
		rect(g, new Box(all.x() - 1, all.y() - 1, all.w() + 2, all.h() + 2), FELT_SHADE);
		// zero
		Box z = l.cell(0);
		tileBox(g, z.inset(2), GREEN, lit.contains(0));
		centred(g, font, Texts.number(0), z, BONE, true);
		for (int n = 1; n <= 36; n++) {
			Box c = l.cell(n);
			tileBox(g, c.inset(2), tile(n), lit.contains(n));
			Box gl = l.glyph(n);
			g.text(font, Texts.number(n), gl.x(), gl.y(), BONE, true);
		}
		// grid lines (the printed layout)
		int gx = l.gx;
		int gy = l.gy;
		int cw = l.cells.cw();
		int ch = l.cells.ch();
		int zw = l.cells.zw();
		int right = l.layout.right();
		int gridBottom = gy + 3 * ch;
		outline(g, new Box(gx, gy, zw + 1, 3 * ch + 1), 1, line);
		for (int r = 0; r <= 3; r++) g.fill(gx + zw, gy + r * ch, right, gy + r * ch + 1, line);
		for (int k = 0; k <= 13; k++) g.fill(gx + zw + k * cw, gy, gx + zw + k * cw + 1, gridBottom + (k <= 12 ? 0 : 1), line);
		// column bets (2:1)
		for (int i = 1; i <= 3; i++) {
			Spot s = Spot.of(BetType.COLUMN, Spot.outsideNumbers(BetType.COLUMN, i));
			Box b = l.outside(s);
			if (s.equals(litOutside)) rect(g, b.inset(1), 0x40FFD640);
			centred(g, font, Texts.raw("2:1"), b, line | 0xFF000000, true);
		}
		// dozens
		int dozenY = gy + l.cells.dozenY();
		int evenY = gy + l.cells.evenY();
		int bottom = l.layout.bottom();
		g.fill(gx + zw, dozenY, gx + zw + 12 * cw + 1, dozenY + 1, line);
		g.fill(gx + zw, evenY, gx + zw + 12 * cw + 1, evenY + 1, line);
		g.fill(gx + zw, bottom - 1, gx + zw + 12 * cw + 1, bottom, line);
		String[] dozen = {"1–12", "13–24", "25–36"};
		for (int i = 1; i <= 3; i++) {
			Spot s = Spot.of(BetType.DOZEN, Spot.outsideNumbers(BetType.DOZEN, i));
			Box b = l.outside(s);
			g.fill(b.x(), b.y(), b.x() + 1, b.bottom(), line);
			if (s.equals(litOutside)) rect(g, b.inset(1), 0x40FFD640);
			label(g, font, Texts.raw(dozen[i - 1]), b, l.labelArea(s), line | 0xFF000000);
		}
		g.fill(gx + zw + 12 * cw, dozenY, gx + zw + 12 * cw + 1, bottom, line);
		// even-money row
		for (BetType t : Layout.EVEN_ROW) {
			Spot s = Spot.of(t, Spot.outsideNumbers(t, 0));
			Box b = l.outside(s);
			g.fill(b.x(), b.y(), b.x() + 1, b.bottom(), line);
			if (s.equals(litOutside)) rect(g, b.inset(1), 0x40FFD640);
			Box la = l.labelArea(s);
			switch (t) {
				case RED, BLACK -> diamond(g, new Box(la.x(), b.y(), la.w(), b.h()), t == BetType.RED ? RED : BLACK, line);
				case LOW -> label(g, font, Texts.raw("1–18"), b, la, line | 0xFF000000);
				case HIGH -> label(g, font, Texts.raw("19–36"), b, la, line | 0xFF000000);
				case EVEN -> label(g, font, Component.translatable("gui.burmaldaholic.roulette.layout.even"), b, la, line | 0xFF000000);
				default -> label(g, font, Component.translatable("gui.burmaldaholic.roulette.layout.odd"), b, la, line | 0xFF000000);
			}
		}
		if (dim > 0) rect(g, all, TableGfx.alpha(0xFF0A0612, 0.25 * dim));
		if (win >= 0) {
			Box c = l.cell(win);
			outline(g, new Box(c.x() - 1, c.y() - 1, c.w() + 3, c.h() + 3), 2, GOLD);
		}
	}

	/**
	 * T-C2 V4: the winning cell alone (its tile, number and gold frame), drawn again OVER a celebration so a big win never
	 * hides the number it is about. The box drawn is {@link RouletteLayout#winCellPunch}.
	 */
	static void winCell(GuiGraphicsExtractor g, Font font, RouletteLayout l, int win) {
		Box c = l.cell(win);
		rect(g, l.winCellPunch(win), FELT_SHADE);
		tileBox(g, c.inset(2), tile(win), false);
		if (win == 0) centred(g, font, Texts.number(0), c, BONE, true);
		else {
			Box gl = l.glyph(win);
			g.text(font, Texts.number(win), gl.x(), gl.y(), BONE, true);
		}
		outline(g, l.winCellPunch(win), 2, GOLD);
	}

	/** An outside label centred in its label area (left of the stack column); a long word keeps its right edge there. */
	private static void label(GuiGraphicsExtractor g, Font font, Component text, Box box, Box area, int color) {
		int w = font.width(text);
		int x = w <= area.w() ? area.x() + (area.w() - w) / 2 : Math.max(box.x() + 1, area.right() - w);
		g.text(font, text, x, box.cy() - 4, color, true);
	}

	private static void tileBox(GuiGraphicsExtractor g, Box b, int[] pc, boolean lit) {
		rect(g, b, pc[1]);
		g.fill(b.x(), b.y(), b.right(), b.y() + 1, pc[0]);
		g.fill(b.x(), b.bottom() - 1, b.right(), b.bottom(), pc[2]);
		if (lit) {
			rect(g, b, 0x50FFE680);
			outline(g, b, 1, GOLD);
		}
	}

	private static void diamond(GuiGraphicsExtractor g, Box b, int[] pc, int line) {
		int cx = b.cx();
		int cy = b.cy();
		int hw = Math.max(4, b.w() * 2 / 5);
		int hh = Math.max(2, b.h() / 2 - 3);
		for (int dy = -hh; dy <= hh; dy++) {
			int w = (int) Math.round(hw * (1 - Math.abs(dy) / (double) (hh + 1)));
			g.fill(cx - w - 1, cy + dy, cx + w + 2, cy + dy + 1, line | 0xFF000000);
		}
		for (int dy = -hh + 1; dy <= hh - 1; dy++) {
			int w = (int) Math.round(hw * (1 - Math.abs(dy) / (double) (hh + 1))) - 1;
			if (w >= 0) g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, dy < 0 ? pc[0] : pc[1]);
		}
	}

	/** A felt stack on a spot: discs by greedy denominations, {@code tint} ≠ 0 = another player's colour, faded by {@code alpha}. */
	static void stack(GuiGraphicsExtractor g, RouletteLayout l, Spot s, long amount, int tint, double alpha) {
		int[] d = new int[ChipStacks.MAX_DISCS];
		int n = Math.min(RouletteLayout.MAX_DISCS, ChipStacks.discs(amount, d));
		if (n == 0 || alpha <= 0.02) return;
		Box b = l.stack(s, n);
		int argb = TableGfx.fade(alpha);
		for (int k = 0; k < n; k++) {
			int y = b.bottom() - l.chip - k * l.rise;
			String sprite = tint != 0 ? "core/stack/chip_grey" : "core/stack/chip_" + d[k];
			int c = tint != 0 ? mul(tint, argb) : argb;
			TableGfx.blit(g, sprite, b.x(), y, l.chip, l.chip - 1, c);
		}
	}

	/** {@link #stack} at a float offset (drops, the rake), snapped to the physical grid. */
	static void stackAt(GuiGraphicsExtractor g, RouletteLayout l, Spot s, long amount, int tint, double alpha, double dx, double dy) {
		if (dx == 0 && dy == 0) {
			stack(g, l, s, amount, tint, alpha);
			return;
		}
		int k = Math.max(1, l.grid.viewport.k());
		g.pose().pushMatrix();
		g.pose().translate(dev.nezo.burmaldaholic.core.anim.motion.PixelSnap.snapF(dx, k), dev.nezo.burmaldaholic.core.anim.motion.PixelSnap.snapF(dy, k));
		stack(g, l, s, amount, tint, alpha);
		g.pose().popMatrix();
	}

	/** One disc of a denomination centred on a point (flights, the ghost chip on hover). */
	static void disc(GuiGraphicsExtractor g, int chip, int denom, int cx, int cy, int argb) {
		TableGfx.blit(g, "core/stack/chip_" + denom, cx - chip / 2, cy - chip / 2, chip, chip - 1, argb);
	}

	/** A value tag (1× number on an ink pill, cyan edge for the viewer's own chips). */
	static void tag(GuiGraphicsExtractor g, Font font, Box b, Component text, boolean mine) {
		rect(g, b, 0xE6180A28);
		outline(g, b, 1, mine ? YOU : 0xFF783CBE);
		centred(g, font, text, b, mine ? YOU : GOLD, false);
	}

	static int tagW(Font font, Component text) {
		return font.width(text) + 6;
	}

	/** "Last numbers" caption and pills (newest first, gold-edged). {@code newAlpha} fades the newest one in. */
	static void history(GuiGraphicsExtractor g, Font font, RouletteLayout l, int[] hist, double newAlpha) {
		if (hist.length == 0) return; // the empty state shows nothing (REDESIGN §3.3)
		Component cap = Component.translatable("gui.burmaldaholic.roulette.v2.last_numbers");
		Box hb = l.historyLabel;
		g.text(font, TableChrome.fit(font, cap, hb.w()), hb.x(), hb.y(), MUTED, true);
		for (int i = 0; i < Math.min(hist.length, l.pills.size()); i++) {
			Box p = l.pills.get(i);
			int n = hist[i];
			double a = i == 0 ? newAlpha : 1;
			if (a <= 0.02) continue;
			String col = Wheel.color(n).id();
			TableGfx.blit(g, "roulette/history_pill_" + col + (i == 0 ? "_new" : ""), p.x(), p.y(), p.w(), p.h(), TableGfx.fade(a));
			Component num = Texts.number(n);
			int w = font.width(num);
			g.text(font, num, p.x() + 9 + (p.w() - 9 - w) / 2, p.y() + 3, TableGfx.alpha(BONE, a), true);
		}
		if (l.pills.size() > 0) {
			g.fill(hb.x(), hb.bottom() + 1, hb.x() + font.width(TableChrome.fit(font, cap, hb.w())), hb.bottom() + 2, 0xFFB07010);
		}
	}

	/** The call-bet row (Tier / Orphelins / Voisins / Zero). */
	static void calls(GuiGraphicsExtractor g, Font font, RouletteLayout l, Racetrack.Section hover, boolean enabled) {
		for (Racetrack.Section s : Racetrack.Section.values()) {
			Box b = l.calls.get(s.ordinal());
			boolean h = enabled && s == hover;
			int fill = !enabled ? 0xFF2E1F42 : h ? 0xFF7046A4 : 0xFF4E287C;
			rect(g, b, fill);
			g.fill(b.x(), b.y(), b.right(), b.y() + 1, enabled ? 0xFF8A60C0 : 0xFF3A2A50);
			g.fill(b.x(), b.bottom() - 1, b.right(), b.bottom(), 0xFF22102F);
			outline(g, new Box(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2), 1, h ? GOLD : INK);
			centred(g, font, Component.translatable("gui.burmaldaholic.roulette.racetrack." + s.id()), b, enabled ? BONE : 0xFF8070A0, true);
		}
	}

	static int mul(int a, int b) {
		int aa = ((a >>> 24) * (b >>> 24)) / 255;
		int r = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
		int gg = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
		int bb = ((a & 0xFF) * (b & 0xFF)) / 255;
		return (aa << 24) | (r << 16) | (gg << 8) | bb;
	}
}
