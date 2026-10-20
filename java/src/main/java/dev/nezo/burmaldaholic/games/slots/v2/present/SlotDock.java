package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * The slot machine's compact Dock (T-SLT D3): when the full Dock ({@code − bet +}, Buy, Auto, Turbo, Slot Showdown,
 * primary) does not fit the frame — 427 × 240 and 480 × 270 — the four secondary actions fold into one "More" button
 * ({@link SlotFlow#COMPACT}); pressing it opens a column of those actions just above the Dock ({@link #menu}). Every
 * control stays on screen and clickable. Pure: the screen and the unit tests run the same decision.
 */
public final class SlotDock {
	/** Gap between the menu rows, and between the menu and the Dock. */
	public static final int GAP = 2;

	private SlotDock() {}

	/** Whether the full Dock {@code d} must fold into More: anything reported as overflow or placed outside the frame. */
	public static boolean collapse(TableGrid g, DockLayout.Dock d) {
		if (d.overflow() > 0) return true;
		Box s = g.screen;
		if (!d.primary().inside(s)) return true;
		if (!d.stepper().isEmpty() && !d.stepper().inside(s)) return true;
		for (Box b : d.actions()) if (!b.inside(s)) return true;
		if (!d.status().isEmpty() && d.status().x() < s.x()) return true;
		return false;
	}

	/**
	 * The More menu: one row per folded action ({@code widths} = each label's button width), equal widths, stacked
	 * upwards from just above the Dock, right-aligned with the More button {@code anchor} and clamped inside the frame.
	 */
	public static List<Box> menu(TableGrid g, Box anchor, int[] widths) {
		int bh = g.tier.buttonH;
		int w = g.tier.actionW;
		for (int x : widths) w = Math.max(w, x);
		int m = g.tier.margin;
		w = Math.min(w, g.screen.w() - 2 * m);
		int x = Math.max(g.screen.x() + m, Math.min(anchor.right(), g.screen.right() - m) - w);
		int bottom = Math.min(anchor.y(), g.dock.y()) - GAP;
		List<Box> out = new ArrayList<>(widths.length);
		for (int i = 0; i < widths.length; i++) {
			int y = bottom - (widths.length - i) * (bh + GAP) + GAP;
			out.add(new Box(x, y, w, bh));
		}
		return out;
	}
}
