package dev.nezo.burmaldaholic.core.cashier.counter;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout of the counter screen (EC-SPEC §3 "UI": 480 × 270 design, v2 {@code Viewport}). The panel is the design
 * size centred in the virtual GUI, shrunk to the GUI when it is smaller (min 320 × 240). Everything is in layout px.
 *
 * <p>Shell: top bar (h 20) with the tabs on the left, the balance / withdrawable readout and the close button on the
 * right; the body below it belongs to the active tab.
 */
public final class CounterLayout {
	public static final int DESIGN_W = 480, DESIGN_H = 270;
	public static final int TOP_H = 20, TAB_H = 16, TAB_GAP = 3, CLOSE = 16, PAD = 8;

	private CounterLayout() {}

	/**
	 * @param panel    the whole counter panel
	 * @param top      the top bar
	 * @param tabs     one box per tab (same order as the widths given)
	 * @param readout  balance / withdrawable text area (between the tabs and the close button; may be empty)
	 * @param close    the close button
	 * @param body     the active tab's area
	 * @param result   one text line at the bottom of the body for the last action's result
	 */
	public record Shell(Box panel, Box top, List<Box> tabs, Box readout, Box close, Box body, Box result) {
		/** True when the panel is narrower than the design (store: 4-column grid, detail as a bottom strip). */
		public boolean narrow() {
			return panel.w() < DESIGN_W;
		}
	}

	/** The panel for a {@code guiW × guiH} virtual GUI. */
	public static Box panel(int guiW, int guiH) {
		int w = Math.min(guiW, DESIGN_W);
		int h = Math.min(guiH, DESIGN_H);
		return new Box((guiW - w) / 2, (guiH - h) / 2, w, h);
	}

	/** Shell layout; {@code tabWidths} = each tab label's width + padding (the client measures with its font). */
	public static Shell shell(int guiW, int guiH, int[] tabWidths) {
		Box p = panel(guiW, guiH);
		Box top = new Box(p.x(), p.y(), p.w(), TOP_H);
		Box close = new Box(p.right() - 2 - CLOSE, p.y() + 2, CLOSE, CLOSE);
		List<Box> tabs = new ArrayList<>();
		int x = p.x() + PAD;
		int maxRight = close.x() - 40; // leave room for at least a short readout
		for (int tw : tabWidths) {
			int w = Math.max(24, tw);
			if (x + w > maxRight) w = Math.max(16, maxRight - x);
			tabs.add(new Box(x, p.y() + 2, w, TAB_H));
			x += w + TAB_GAP;
		}
		int rx = x + 4;
		Box readout = new Box(rx, p.y() + 2, Math.max(0, close.x() - 4 - rx), TAB_H);
		Box body = new Box(p.x(), p.y() + TOP_H + 2, p.w(), p.h() - TOP_H - 2);
		Box result = new Box(body.x() + PAD, body.bottom() - 12, body.w() - 2 * PAD, 10);
		return new Shell(p, top, List.copyOf(tabs), readout, close, body, result);
	}

	// ---- Exchange tab ----------------------------------------------------------------------------------------------

	/** {@code RATE_W} is the minimum rate cell; the tab passes the widest rate text it draws ({@link #row(Box, int)}). */
	public static final int ROW_H = 22, BTN_H = 16, RATE_W = 16, X1_W = 16, X64_W = 22, ALL_W = 30, GAP = 1, ICON = 16;
	/** One direction group with the minimum rate cell: rate label + ×1 + ×64 + All/Max. */
	public static final int GROUP_W = groupW(RATE_W);

	/** One direction group's width for a rate cell of {@code rateW} px. */
	public static int groupW(int rateW) {
		return Math.max(RATE_W, rateW) + X1_W + X64_W + ALL_W + 3 * GAP;
	}

	/**
	 * Exchange tab: {@code money} = deposit / withdraw block, {@code rows} = the row column. Wide: money left
	 * (x 8–232) with the counting tray under it, rows right (x 240–472). Narrow: money on top, rows below, both full
	 * width, no tray.
	 */
	public record Exchange(Box money, Box rows, boolean stacked, Box tray) {}

	public static final int MONEY_H = 90, TRAY_H = 50;

	public static Exchange exchange(Box body, int rowCount) {
		int inner = body.h() - 14; // leave the result line
		if (body.w() >= DESIGN_W) {
			Box money = new Box(body.x() + PAD, body.y() + 4, 224, Math.min(MONEY_H, inner - 4));
			Box rows = new Box(body.x() + 240, body.y() + 4, body.w() - 240 - PAD, Math.min(rowCount * ROW_H, inner - 4));
			Box tray = new Box(money.x(), money.bottom() + 8, money.w(), TRAY_H);
			if (tray.bottom() > body.bottom() - 14) tray = Box.EMPTY;
			return new Exchange(money, rows, false, tray);
		}
		Box money = new Box(body.x() + PAD, body.y() + 4, body.w() - 2 * PAD, MONEY_H);
		Box rows = new Box(body.x() + PAD, money.bottom() + 4, body.w() - 2 * PAD, Math.min(rowCount * ROW_H, Math.max(0, body.bottom() - 14 - money.bottom() - 4)));
		return new Exchange(money, rows, true, Box.EMPTY); // narrow: no room for the counting tray (moves are told in chat)
	}

	/** Rows that fit in {@code rows} (the rest scrolls). */
	public static int visibleRows(Exchange e) {
		return Math.max(0, e.rows().h() / ROW_H);
	}

	/** Boxes of one exchange row: icon, "You have N", then the in group (→ chips) and the out group (← item). */
	public record RowBoxes(Box icon, Box have, Box inRate, Box in1, Box in64, Box inAll, Box outRate, Box out1, Box out64, Box outMax) {
		public List<Box> all() {
			return List.of(icon, have, inRate, in1, in64, inAll, outRate, out1, out64, outMax);
		}
	}

	public static RowBoxes row(Box row) {
		return row(row, RATE_W);
	}

	/** Row boxes with rate cells of {@code rateW} px (at least {@link #RATE_W}): the widest "→N" / "←N" text of the tab (T-EC1 D4a). */
	public static RowBoxes row(Box row, int rateW) {
		int rw = Math.max(RATE_W, rateW);
		int gw = groupW(rw);
		int by = row.y() + (ROW_H - BTN_H) / 2;
		int outX = row.right() - gw;
		int inX = outX - 4 - gw;
		Box icon = new Box(row.x() + 2, row.y() + (ROW_H - ICON) / 2, ICON, ICON);
		Box have = new Box(icon.right() + 3, row.y(), Math.max(0, inX - 3 - icon.right() - 3), ROW_H);
		Box[] in = group(inX, by, rw);
		Box[] out = group(outX, by, rw);
		return new RowBoxes(icon, have, in[0], in[1], in[2], in[3], out[0], out[1], out[2], out[3]);
	}

	private static Box[] group(int x, int y, int rateW) {
		Box rate = new Box(x, y, rateW, BTN_H);
		Box b1 = new Box(rate.right() + GAP, y, X1_W, BTN_H);
		Box b64 = new Box(b1.right() + GAP, y, X64_W, BTN_H);
		Box all = new Box(b64.right() + GAP, y, ALL_W, BTN_H);
		return new Box[] {rate, b1, b64, all};
	}

	/** The deposit / withdraw block: deposit buttons, amount line, chip "+" buttons + Max, Clear + Withdraw, withdrawable line. */
	public record Money(Box depositAll, Box depositHeld, Box amount, List<Box> chips, Box max, Box clear, Box withdraw, Box info) {}

	public static Money money(Box m, int chipCount) {
		int half = (m.w() - 4) / 2;
		Box depAll = new Box(m.x(), m.y(), half, 18);
		Box depHeld = new Box(m.x() + half + 4, m.y(), m.w() - half - 4, 18);
		Box amount = new Box(m.x(), m.y() + 22, m.w(), 12);
		int maxW = 30;
		int n = Math.max(0, chipCount);
		int cw = n == 0 ? 0 : Math.min(32, (m.w() - maxW - 2 * n) / n);
		List<Box> chips = new ArrayList<>();
		int x = m.x();
		for (int i = 0; i < n; i++) {
			chips.add(new Box(x, m.y() + 36, cw, 16));
			x += cw + 2;
		}
		Box max = new Box(x, m.y() + 36, Math.max(0, m.right() - x), 16);
		Box clear = new Box(m.x(), m.y() + 56, 50, 18);
		Box withdraw = new Box(m.x() + 54, m.y() + 56, m.w() - 54, 18);
		Box info = new Box(m.x(), m.y() + 78, m.w(), 10);
		return new Money(depAll, depHeld, amount, List.copyOf(chips), max, clear, withdraw, info);
	}

	// ---- Store tab -------------------------------------------------------------------------------------------------

	public static final int TILE = 36, TILE_GAP = 3, RAIL_W = 80, CAT_H = 15, SEARCH_H = 14, STRIP_H = 40;

	/**
	 * Store tab: category rail (search on top), the tile grid, and the detail panel (wide: right column x 338–472;
	 * narrow: a 40 px strip under the grid). {@code cols × rows} tiles are visible; more scroll.
	 */
	public record Store(Box rail, Box search, List<Box> categories, Box grid, int cols, int rows, Box detail, boolean strip) {
		public Box tile(int visibleIndex) {
			int c = visibleIndex % cols;
			int r = visibleIndex / cols;
			return new Box(grid.x() + c * (TILE + TILE_GAP), grid.y() + r * (TILE + TILE_GAP), TILE, TILE);
		}
	}

	public static Store store(Box body, int categoryCount) {
		boolean narrow = body.w() < DESIGN_W;
		int bottom = body.bottom() - 14;
		Box rail = new Box(body.x() + PAD, body.y() + 4, RAIL_W, bottom - body.y() - 4 - (narrow ? STRIP_H + 4 : 0));
		Box search = new Box(rail.x(), rail.y(), rail.w(), SEARCH_H);
		List<Box> cats = new ArrayList<>();
		for (int i = 0; i < categoryCount; i++) {
			Box b = new Box(rail.x(), search.bottom() + 3 + i * (CAT_H + 1), rail.w(), CAT_H);
			if (b.bottom() <= rail.bottom()) cats.add(b);
		}
		int cols = narrow ? 4 : 6;
		int gridX = rail.right() + PAD;
		int gridH = rail.h();
		int rows = Math.max(1, Math.min(4, (gridH + TILE_GAP) / (TILE + TILE_GAP)));
		Box grid = new Box(gridX, rail.y(), cols * TILE + (cols - 1) * TILE_GAP, rows * TILE + (rows - 1) * TILE_GAP);
		Box detail = narrow ? new Box(body.x() + PAD, bottom - STRIP_H, body.w() - 2 * PAD, STRIP_H)
			: new Box(body.x() + 338, rail.y(), body.right() - PAD - (body.x() + 338), rail.h());
		return new Store(rail, search, List.copyOf(cats), grid, cols, rows, detail, narrow);
	}

	/** The detail panel / strip: name, price + limit lines, qty buttons (×1 ×16 ×64 Max), total, Buy. */
	public record Detail(Box icon, Box name, Box line2, Box line3, List<Box> qty, Box total, Box buy) {}

	/** Narrow strip's minimum Buy width; the tab passes its widest Buy / "Confirm N?" label ({@link #detail(Store, int)}). */
	public static final int BUY_W = 56;

	public static Detail detail(Store s) {
		return detail(s, BUY_W);
	}

	/**
	 * @param buyW the narrow strip's Buy button width (clamped to {@link #BUY_W}…⅓ of the strip); the wide panel's Buy
	 *             always spans the panel (T-EC1 D4b)
	 */
	public static Detail detail(Store s, int buyW) {
		Box d = s.detail();
		if (!s.strip()) {
			Box icon = new Box(d.x() + 4, d.y() + 4, ICON, ICON);
			Box name = new Box(icon.right() + 4, d.y() + 4, d.right() - 4 - icon.right() - 4, 10);
			Box l2 = new Box(icon.right() + 4, d.y() + 15, name.w(), 10);
			Box l3 = new Box(d.x() + 4, d.y() + 28, d.w() - 8, 10);
			int qw = (d.w() - 8 - 3 * 2) / 4;
			List<Box> q = new ArrayList<>();
			for (int i = 0; i < 4; i++) q.add(new Box(d.x() + 4 + i * (qw + 2), d.y() + 42, qw, BTN_H));
			Box total = new Box(d.x() + 4, d.y() + 62, d.w() - 8, 10);
			Box buy = new Box(d.x() + 4, d.y() + 76, d.w() - 8, 20);
			return new Detail(icon, name, l2, l3, List.copyOf(q), total, buy);
		}
		Box icon = new Box(d.x() + 2, d.y() + (d.h() - ICON) / 2, ICON, ICON);
		buyW = Math.max(BUY_W, Math.min(buyW, d.w() / 3));
		int qw = 28;
		int qx = d.right() - buyW - 4 - 4 * qw - 3 * 2;
		Box buy = new Box(d.right() - buyW, d.y() + 10, buyW, 20);
		List<Box> q = new ArrayList<>();
		for (int i = 0; i < 4; i++) q.add(new Box(qx + i * (qw + 2), d.y() + 12, qw, BTN_H));
		int tw = Math.max(0, qx - 4 - icon.right() - 4);
		Box name = new Box(icon.right() + 4, d.y() + 4, tw, 10);
		Box l2 = new Box(icon.right() + 4, d.y() + 15, tw, 10);
		Box total = new Box(icon.right() + 4, d.y() + 26, tw, 10);
		return new Detail(icon, name, l2, Box.EMPTY, List.copyOf(q), total, buy);
	}
}
