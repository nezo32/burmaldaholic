package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.function.ToIntFunction;

/**
 * Ellipsising of plates, status lines and pills (REDESIGN.md §3.3: "nothing wraps into a neighbour"). The width
 * function is the font's ({@code font::width}) on the client and a fixed-advance stub in the tests.
 */
public final class TextFit {
	public static final String ELLIPSIS = "…"; // literal-ok: punctuation

	private TextFit() {}

	/** {@code text} cut to fit {@code maxW} px with an ellipsis (code-point safe; "" when not even "…" fits). */
	public static String fit(String text, int maxW, ToIntFunction<String> width) {
		if (width.applyAsInt(text) <= maxW) return text;
		int ell = width.applyAsInt(ELLIPSIS);
		if (ell > maxW) return "";
		int end = text.length();
		while (end > 0) {
			end = text.offsetByCodePoints(end, -1);
			String s = text.substring(0, end).stripTrailing() + ELLIPSIS;
			if (width.applyAsInt(s) <= maxW) return s;
		}
		return ELLIPSIS;
	}

	/**
	 * {@code text} on at most two lines of {@code maxW} px: line 1 breaks at the last space that fits (or mid-word when one
	 * word is too long), line 2 carries the rest and ends with an ellipsis when it overflows — no word is ever dropped
	 * silently (review T-K2 D1). One element when the text fits one line.
	 */
	public static String[] wrap2(String text, int maxW, ToIntFunction<String> width) {
		if (width.applyAsInt(text) <= maxW || maxW <= 0) return new String[] {fit(text, maxW, width)};
		int cut = -1;
		for (int i = text.indexOf(' '); i > 0; i = text.indexOf(' ', i + 1)) {
			if (width.applyAsInt(text.substring(0, i)) <= maxW) cut = i;
			else break;
		}
		String first;
		String rest;
		if (cut > 0) {
			first = text.substring(0, cut);
			rest = text.substring(cut + 1).stripLeading();
		} else { // one word longer than the line: break it
			int end = 0;
			while (end < text.length()) {
				int next = text.offsetByCodePoints(end, 1);
				if (width.applyAsInt(text.substring(0, next)) > maxW) break;
				end = next;
			}
			if (end == 0) end = text.offsetByCodePoints(0, 1);
			first = text.substring(0, end);
			rest = text.substring(end);
		}
		return rest.isEmpty() ? new String[] {first} : new String[] {first, fit(rest, maxW, width)};
	}

	/** Whether {@link #wrap2} shows the whole text in at most two lines of {@code maxW} (no ellipsis added). */
	public static boolean fitsTwoLines(String text, int maxW, ToIntFunction<String> width) {
		String[] two = wrap2(text, maxW, width);
		return !two[two.length - 1].endsWith(ELLIPSIS) || text.endsWith(ELLIPSIS);
	}

	/** The narrowest width at which {@link #wrap2} shows the whole text on two lines (the best break at a space). */
	public static int twoLineWidth(String text, ToIntFunction<String> width) {
		int best = width.applyAsInt(text);
		for (int i = text.indexOf(' '); i > 0; i = text.indexOf(' ', i + 1)) {
			int w = Math.max(width.applyAsInt(text.substring(0, i)), width.applyAsInt(text.substring(i + 1).stripLeading()));
			best = Math.min(best, w);
		}
		return best;
	}
}
