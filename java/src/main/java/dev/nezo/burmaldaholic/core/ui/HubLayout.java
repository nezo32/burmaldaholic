package dev.nezo.burmaldaholic.core.ui;

/**
 * Layout of the PvP hub page of the Casino Menu (visual/extras.md §7.2 "Hub"; page content coordinates, x from the
 * content's left, y from its top): the heading and record line, the nemesis line, a 3 × 2 grid of mode cards (48 px
 * high: the 40 px mode icon and the mode's name), then the text sections and the open lobbies as 22 px plaques with the
 * Join control on their right. PURE and integer: every rectangle lies inside {@code [0, width)}, cards never overlap.
 */
public final class HubLayout {
	public static final int COLS = 3;
	public static final int ROWS = 2;
	public static final int CARDS = COLS * ROWS;
	public static final int GAP = 4;
	public static final int CARD_H = 48;
	public static final int ICON = 40;
	/** Top of the grid below the heading (and the nemesis line, when there is one). */
	public static final int HEAD_H = 14;
	public static final int NEMESIS_H = 12;
	public static final int LOBBY_H = 22;
	public static final int LOBBY_GAP = 3;
	/** A card is too narrow for the icon beside its name below this width: the icon then sits alone (name as tooltip). */
	public static final int MIN_NAMED_W = ICON + 8 + 36;

	private HubLayout() {}

	public static int gridTop(boolean nemesis) {
		return HEAD_H + (nemesis ? NEMESIS_H : 0) + 2;
	}

	public static int cardW(int width) {
		return Math.max(ICON + 8, (width - (COLS - 1) * GAP) / COLS);
	}

	/** Card {@code i} (0…5, row-major): {x, y, w, h}. */
	public static int[] card(int i, int width, boolean nemesis) {
		int w = cardW(width);
		int col = i % COLS;
		int row = i / COLS;
		return new int[] {col * (w + GAP), gridTop(nemesis) + row * (CARD_H + GAP), w, CARD_H};
	}

	public static int gridBottom(boolean nemesis) {
		return gridTop(nemesis) + ROWS * CARD_H + (ROWS - 1) * GAP;
	}

	/** Whether a card of {@code width} shows its name beside the icon. */
	public static boolean named(int cardW) {
		return cardW >= MIN_NAMED_W;
	}

	/** Text width inside a card beside the icon. */
	public static int cardTextW(int cardW) {
		return Math.max(0, cardW - ICON - 12);
	}

	/**
	 * A lobby plaque of the page width: {icon x, text x, text width, join x, join width} for a Join control of
	 * {@code joinW} (a button, or a field + button) — the text never runs under the control.
	 */
	public static int[] lobby(int width, int joinW) {
		int jw = Math.min(joinW, width / 2);
		int joinX = width - jw - 3;
		int textX = 4 + 16 + 4;
		return new int[] {4, textX, Math.max(0, joinX - 4 - textX), joinX, jw};
	}
}
