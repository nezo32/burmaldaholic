package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Where the two status lines go (review T-K2 D1, R3): the Dock's status column holds up to two 1× rows; the hint strip
 * above the Dock holds one row the whole Dock wide. Every word stays readable whenever the strip is open, and the strip
 * opens exactly when the column is narrower than {@link #want} (DockLayout). Pure: the client draws the plan with the
 * font's widths, the tests check it with a stub font.
 *
 * <ol>
 *   <li>Both lines fit the column on one row each → both in the Dock.</li>
 *   <li>A lone line wraps onto the column's two rows when it fits them, else it goes whole into the strip.</li>
 *   <li>Two lines that do not both fit: line 1 keeps the Dock (wrapped onto both rows when needed) and line 2 goes into
 *       the strip; when line 1 does not fit the column's two rows either, both go into the strip ("line 1 · line 2").</li>
 * </ol>
 *
 * @param dock       the Dock column's rows, top first (each with {@link #dockGold} telling line 1 from line 2)
 * @param dockGold   per Dock row: true = line 1 (gold / its colour), false = line 2 (muted)
 * @param stripL1    line 1 drawn in the strip (null = none)
 * @param stripL2    line 2 drawn in the strip after line 1 (null = none)
 * @param stripRows  1, or 2 when line 1 and line 2 are too long to share the strip's row: line 1 on a row above line 2
 *                   (the strip grows up into the PlayArea by one row; rare: two long lines over a minimum column)
 */
public record StatusPlan(List<String> dock, List<Boolean> dockGold, @Nullable String stripL1, @Nullable String stripL2, int stripRows) {
	public static final int ROW_H = 11;
	/** The gap drawn between line 1 and line 2 when both share the strip. */
	public static final String JOIN = "  ·  "; // literal-ok: punctuation
	public static final StatusPlan EMPTY = new StatusPlan(List.of(), List.of(), null, null, 1);

	/** The column width at which everything fits in the Dock (DockLayout opens the strip below it); 0 = no text. */
	public static int want(@Nullable String l1, @Nullable String l2, ToIntFunction<String> width) {
		if (l1 == null && l2 == null) return 0;
		if (l2 == null) return TextFit.twoLineWidth(l1, width) + 2;
		if (l1 == null) return width.applyAsInt(l2) + 2;
		return Math.max(width.applyAsInt(l1), width.applyAsInt(l2)) + 2;
	}

	/**
	 * The plan for a column {@code boxW × boxH} and a strip {@code stripW} wide (0 = no strip open).
	 */
	public static StatusPlan of(@Nullable String l1, @Nullable String l2, int boxW, int boxH, int stripW, ToIntFunction<String> width) {
		if (l1 != null && l1.isEmpty()) l1 = null;
		if (l2 != null && l2.isEmpty()) l2 = null;
		if (l1 == null && l2 == null || boxW <= 0) return EMPTY;
		boolean twoRows = boxH >= 2 * ROW_H + 2;
		List<String> rows = new ArrayList<>(2);
		List<Boolean> gold = new ArrayList<>(2);
		if (l2 == null || l1 == null) {
			String only = l1 != null ? l1 : l2;
			boolean isL1 = l1 != null;
			if (width.applyAsInt(only) <= boxW) return single(only, isL1);
			if (twoRows && fitsRows(only, boxW, width)) return wrapped(only, isL1, boxW, width);
			if (stripW > 0) return new StatusPlan(List.of(), List.of(), isL1 ? only : null, isL1 ? null : only, 1);
			return twoRows ? wrapped(only, isL1, boxW, width) : single(TextFit.fit(only, boxW, width), isL1); // no strip: ellipsis
		}
		if (twoRows && width.applyAsInt(l1) <= boxW && width.applyAsInt(l2) <= boxW) {
			rows.add(l1);
			gold.add(true);
			rows.add(l2);
			gold.add(false);
			return new StatusPlan(rows, gold, null, null, 1);
		}
		if (stripW <= 0) { // no strip (the column is as wide as the status asked): ellipsise, never overlap
			rows.add(TextFit.fit(l1, boxW, width));
			gold.add(true);
			if (twoRows) {
				rows.add(TextFit.fit(l2, boxW, width));
				gold.add(false);
			}
			return new StatusPlan(rows, gold, null, null, 1);
		}
		if (width.applyAsInt(l1) <= boxW) return new StatusPlan(List.of(l1), List.of(true), null, l2, 1);
		if (twoRows && fitsRows(l1, boxW, width)) {
			StatusPlan w = wrapped(l1, true, boxW, width);
			return new StatusPlan(w.dock, w.dockGold, null, l2, 1);
		}
		StatusPlan both = new StatusPlan(List.of(), List.of(), l1, l2, 1);
		if (width.applyAsInt(both.stripText()) + 8 <= stripW) return both;
		// the two together are wider than the strip: one line up, the other on the column's two rows
		if (twoRows && fitsRows(l2, boxW, width) && width.applyAsInt(l1) + 8 <= stripW) {
			StatusPlan w = wrapped(l2, false, boxW, width);
			return new StatusPlan(w.dock, w.dockGold, l1, null, 1);
		}
		if (twoRows && fitsRows(l1, boxW, width) && width.applyAsInt(l2) + 8 <= stripW) {
			StatusPlan w = wrapped(l1, true, boxW, width);
			return new StatusPlan(w.dock, w.dockGold, null, l2, 1);
		}
		return new StatusPlan(List.of(), List.of(), l1, l2, 2); // two strip rows: line 1 above line 2
	}

	/** Whether {@code s} shows whole on two rows: broken at a space (never mid-word), every row within the column. */
	static boolean fitsRows(String s, int boxW, ToIntFunction<String> width) {
		String[] two = TextFit.wrap2(s, boxW, width);
		if (!String.join(" ", two).equals(s)) return false;
		for (String r : two) if (width.applyAsInt(r) > boxW) return false;
		return true;
	}

	private static StatusPlan single(String s, boolean l1) {
		return new StatusPlan(List.of(s), List.of(l1), null, null, 1);
	}

	private static StatusPlan wrapped(String s, boolean l1, int boxW, ToIntFunction<String> width) {
		String[] two = TextFit.wrap2(s, boxW, width);
		return new StatusPlan(List.of(two), two.length == 2 ? List.of(l1, l1) : List.of(l1), null, null, 1);
	}

	/** The strip's text as drawn on one row (line 1, the join, line 2), or null; with two rows, the wider row. */
	public @Nullable String stripText() {
		if (stripL1 == null && stripL2 == null) return null;
		if (stripRows == 2) return stripL1.length() >= stripL2.length() ? stripL1 : stripL2;
		if (stripL1 == null) return stripL2;
		if (stripL2 == null) return stripL1;
		return stripL1 + JOIN + stripL2;
	}

	/**
	 * What would be cut when drawn ("" = every word readable): a Dock row wider than the column or ending with an added
	 * ellipsis, a strip text wider than the strip (its band adds 8 px), a third row, or text that went nowhere.
	 */
	public String lost(@Nullable String l1, @Nullable String l2, int boxW, int stripW, ToIntFunction<String> width) {
		List<String> out = new ArrayList<>();
		for (String r : dock) {
			if (width.applyAsInt(r) > boxW) out.add("row '" + r + "' " + width.applyAsInt(r) + " > " + boxW);
			if (r.endsWith(TextFit.ELLIPSIS) && !((l1 != null && l1.endsWith(TextFit.ELLIPSIS)) || (l2 != null && l2.endsWith(TextFit.ELLIPSIS)))) {
				out.add("row '" + r + "' ellipsised");
			}
		}
		String s = stripText();
		List<String> stripRowsText = s == null ? List.of() : stripRows == 2 ? List.of(stripL1, stripL2) : List.of(s);
		for (String r : stripRowsText) if (width.applyAsInt(r) + 8 > stripW) out.add("strip '" + r + "' " + (width.applyAsInt(r) + 8) + " > " + stripW);
		String shown = String.join(" ", dock) + " " + String.join(" ", stripRowsText);
		for (String l : new String[] {l1, l2}) {
			if (l == null || l.isEmpty()) continue;
			for (String word : l.split(" ")) if (!word.isEmpty() && !shown.contains(word)) out.add("word '" + word + "' not shown");
		}
		return String.join("; ", out);
	}
}
