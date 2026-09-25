package dev.nezo.burmaldaholic.core.anim;

/**
 * Layout helpers for Russian-width text (global.md §2.2, UI.md §0.1). Pure integer maths so JUnit can
 * test every screen's worst case; the client screens measure with {@code Font#width} and call these.
 */
public final class TextFit {
	/** Fixed boxes are budgeted at 1.45 × the English width. */
	public static final double RU_BUDGET = 1.45;

	private TextFit() {}

	/** Button width rule: {@code max(minWidth, textWidth + 8)}. */
	public static int buttonWidth(int minWidth, int textWidth) {
		return Math.max(minWidth, textWidth + 8);
	}

	/**
	 * Largest allowed integer scale (3 → 2 → 1) for a banner/tier word so that
	 * {@code textWidth × scale ≤ maxWidth}; returns 1 when even 1× overflows (the caller then wraps to at
	 * most 2 lines). No fractional scales.
	 */
	public static int bannerScale(int textWidth, int preferredScale, int maxWidth) {
		for (int s = Math.min(3, Math.max(1, preferredScale)); s > 1; s--) if (textWidth * s <= maxWidth) return s;
		return 1;
	}

	/** English-width budget of a fixed box that must also hold the Russian text. */
	public static int enBudget(int boxWidth) {
		return (int) Math.floor(boxWidth / RU_BUDGET);
	}

	/**
	 * Flow layout: how many items go on each row when widths are laid out left to right with {@code gap}
	 * and wrapped at {@code maxWidth}. Returns the row index of every item.
	 */
	public static int[] flowRows(int[] widths, int maxWidth, int gap) {
		int[] rows = new int[widths.length];
		int row = 0;
		int x = 0;
		for (int i = 0; i < widths.length; i++) {
			int w = widths[i];
			if (x > 0 && x + gap + w > maxWidth) {
				row++;
				x = 0;
			}
			x += (x > 0 ? gap : 0) + w;
			rows[i] = row;
		}
		return rows;
	}
}
