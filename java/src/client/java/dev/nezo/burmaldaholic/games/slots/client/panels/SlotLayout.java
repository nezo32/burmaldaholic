package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.games.slots.v2.present.SlotGeometry;

/**
 * Screen geometry of the slot screen: the pure {@link SlotGeometry} (400 × 240 with 44 px cells, or the compact
 * 320 × 220 with 32 px cells when the GUI is smaller than 400 × 240). All coordinates are absolute GUI px.
 */
public final class SlotLayout extends SlotGeometry {
	private SlotLayout(boolean compact, int screenW, int screenH) {
		super(compact, screenW, screenH);
	}

	private SlotLayout(int px, int py, int pw, int ph) {
		super(px, py, pw, ph);
	}

	/** The cabinet in the v2 PlayArea {@code play} (owner B6; the controls are in the kit Dock). */
	public static SlotLayout docked(dev.nezo.burmaldaholic.core.ui.v2.Box play) {
		return new SlotLayout(play.x(), play.y(), play.w(), play.h());
	}

	public static SlotLayout of(int screenW, int screenH) {
		return new SlotLayout(isCompact(screenW, screenH), screenW, screenH);
	}

	public static SlotLayout forceCompact(int screenW, int screenH, boolean compact) {
		return new SlotLayout(compact, screenW, screenH);
	}
}
