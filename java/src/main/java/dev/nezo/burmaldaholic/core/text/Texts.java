package dev.nezo.burmaldaholic.core.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The ONLY place allowed to create literal components (whitelisted in config/literal-whitelist.txt).
 * Everything user-facing is {@code Component.translatable(key, args...)}.
 */
public final class Texts {
	private Texts() {}

	/** A number rendered as-is (language neutral). */
	public static MutableComponent number(long n) {
		return Component.literal(Long.toString(n));
	}

	/**
	 * Pluralised message, e.g. {@code plural("burmaldaholic.core.chips", 5)} -> "5 фишек" / "5 chips".
	 * Safe to build on the server: the key choice does not depend on the language. The number is the
	 * first argument ({@code %s} / {@code %1$s}), followed by {@code extraArgs}.
	 */
	public static MutableComponent plural(String baseKey, long n, Object... extraArgs) {
		Object[] args = new Object[extraArgs.length + 1];
		args[0] = number(n);
		System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
		return Component.translatable(Plural.key(baseKey, n), args);
	}
}
