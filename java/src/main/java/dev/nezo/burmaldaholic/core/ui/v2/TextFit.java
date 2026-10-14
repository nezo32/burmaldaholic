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
}
