package dev.nezo.burmaldaholic.core.text;

/**
 * Language-agnostic plural keys (shared with the Bedrock edition). Every pluralised string defines
 * FOUR keys in EVERY language; the language file decides the wording:
 *
 * <pre>
 * suffix  chosen when                                  en_us        ru_ru
 * .p1     n == 1                                       "%s chip"    "%s фишка"
 * .p21    n%10 == 1 && n%100 != 11 && n != 1 (21, 101) "%s chips"   "%s фишка"
 * .p2     n%10 in 2..4 && n%100 not in 12..14          "%s chips"   "%s фишки"
 * .p5     everything else (0, 5..20, 11..14, 25...)    "%s chips"   "%s фишек"
 * </pre>
 *
 * Negative numbers use their absolute value. The build ({@code mergeLang}) fails if a key with one
 * of these suffixes lacks any of its three siblings. Pure Java; see {@link Texts} for components.
 */
public final class Plural {
	public enum Form {
		P1, P21, P2, P5;

		public String suffix() {
			return switch (this) {
				case P1 -> "p1";
				case P21 -> "p21";
				case P2 -> "p2";
				case P5 -> "p5";
			};
		}
	}

	private Plural() {}

	public static Form form(long n) {
		long abs = Math.abs(n);
		long mod10 = abs % 10;
		long mod100 = abs % 100;
		if (abs == 1) {
			return Form.P1;
		}
		if (mod10 == 1 && mod100 != 11) {
			return Form.P21;
		}
		if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
			return Form.P2;
		}
		return Form.P5;
	}

	/** {@code key("burmaldaholic.core.chips", 21)} -> {@code "burmaldaholic.core.chips.p21"}. */
	public static String key(String base, long n) {
		return base + "." + form(n).suffix();
	}
}
