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
}
