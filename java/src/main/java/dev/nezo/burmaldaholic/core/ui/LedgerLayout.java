package dev.nezo.burmaldaholic.core.ui;

/**
 * Casino Menu "ledger" shell geometry (docs/design/visual/extras.md §8.2), panel-local, pure:
 * header plate 112 × 20 at (16, 4); balance plaque 90 × 20 at the top right; bookmark tabs from (16, 28) (unselected
 * icon-only 26 × 20, the selected one 22 high at y 26 with icon + name); the page nine-slice at (16, 47), 368 × 177.
 */
public final class LedgerLayout {
	public static final int HEADER_X = 16;
	public static final int HEADER_Y = 4;
	public static final int HEADER_W = 112;
	public static final int PLAQUE_W = 90;
	public static final int PLATE_H = 20;
	public static final int TABS_X = 16;
	public static final int TAB_Y = 28;
	public static final int TAB_SELECTED_Y = 26;
	public static final int TAB_W = 26;
	public static final int TAB_H = 20;
	public static final int TAB_SELECTED_H = 22;
	public static final int TAB_GAP = 2;
	/** Icon (16) + gap + name + right padding of the selected tab. */
	public static final int TAB_LABEL_EXTRA = 5 + 16 + 4 + 6;
	public static final int PAGE_X = 16;
	public static final int PAGE_Y = 47;
	public static final int PAD = 8;

	private LedgerLayout() {}

	/** Tab x positions and widths; the selected tab carries its name when everything fits, else it is icon-only too. */
	public record Tabs(int[] x, int[] w, boolean labelShown) {}

	/**
	 * Lays out {@code count} tabs from {@code startX} within {@code maxWidth}; the selected tab is
	 * {@code TAB_LABEL_EXTRA + labelWidth} wide when the row still fits, else {@link #TAB_W}. When even icon-only tabs
	 * overflow, they are narrowed evenly (never below 18 px, the 16-px icon + 1 px each side).
	 */
	public static Tabs tabs(int count, int selected, int labelWidth, int startX, int maxWidth) {
		int[] x = new int[count];
		int[] w = new int[count];
		if (count == 0) return new Tabs(x, w, false);
		int base = TAB_W;
		int plain = count * base + (count - 1) * TAB_GAP;
		int withLabel = plain - base + Math.max(base, TAB_LABEL_EXTRA + labelWidth);
		boolean label = selected >= 0 && selected < count && withLabel <= maxWidth;
		if (!label && plain > maxWidth) base = Math.max(18, (maxWidth - (count - 1) * TAB_GAP) / count);
		int cx = startX;
		for (int i = 0; i < count; i++) {
			w[i] = label && i == selected ? Math.max(TAB_W, TAB_LABEL_EXTRA + labelWidth) : base;
			x[i] = cx;
			cx += w[i] + TAB_GAP;
		}
		return new Tabs(x, w, label);
	}

	/** Page size inside a panel of {@code panelW × panelH}: from (16, 47) to 16 px from the right and bottom. */
	public static int pageW(int panelW) {
		return panelW - 2 * PAGE_X;
	}

	public static int pageH(int panelH) {
		return panelH - PAGE_Y - 16;
	}
}
