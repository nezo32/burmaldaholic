package dev.nezo.burmaldaholic.core.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The ONLY place allowed to create literal components (whitelisted in config/literal-whitelist.txt).
 * Everything user-facing is {@code Component.translatable(key, args...)}; numbers are passed as
 * pre-formatted strings ({@link #number}) and counted nouns as nested plural components ({@link #chips}).
 */
public final class Texts {
	public static final String CHIP = "unit.burmaldaholic.chip";
	public static final String CHIP_ACC = "unit.burmaldaholic.chip_acc";

	private Texts() {}

	/** A number rendered with LOCALIZATION.md §4 grouping ({@code 12 500}). Language neutral. */
	public static MutableComponent number(long n) {
		return Component.literal(Numbers.format(n));
	}

	/** Language-neutral raw text (ids, symbols, player-typed values). Never for words. */
	public static MutableComponent raw(String text) {
		return Component.literal(text);
	}

	/**
	 * Pluralised component, e.g. {@code plural("unit.burmaldaholic.chip", 5)} -> "5 chips" / "5 фишек".
	 * Safe on the server: the key choice does not depend on the language. The formatted number is
	 * {@code %1$s}, followed by {@code extraArgs}.
	 */
	public static MutableComponent plural(String baseKey, long n, Object... extraArgs) {
		Object[] args = new Object[extraArgs.length + 1];
		args[0] = number(n);
		System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
		return Component.translatable(Plural.key(baseKey, n), args);
	}

	/** "5 chips" (nominative, STRINGS.md argument type {@code chips}). */
	public static MutableComponent chips(long n) {
		return plural(CHIP, n);
	}

	/** "5 chips" (accusative, argument type {@code chips_acc}: after win/lose/pay/bet/get/take). */
	public static MutableComponent chipsAcc(long n) {
		return plural(CHIP_ACC, n);
	}
}
