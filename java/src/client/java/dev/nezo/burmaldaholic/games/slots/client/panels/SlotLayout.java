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

	public static SlotLayout of(int screenW, int screenH) {
		return new SlotLayout(isCompact(screenW, screenH), screenW, screenH);
	}

	public static SlotLayout forceCompact(int screenW, int screenH, boolean compact) {
		return new SlotLayout(compact, screenW, screenH);
	}
}
