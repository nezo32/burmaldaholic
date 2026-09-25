package dev.nezo.burmaldaholic.core.util;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Value or a player-facing error (a translatable component, e.g. {@code gui.burmaldaholic.error.*}).
 * Returned by validation APIs (stakes, bets) so callers can show the reason on their screen.
 */
public record Result<T>(@Nullable T value, @Nullable Component error) {
	public static <T> Result<T> ok(T value) {
		return new Result<>(Objects.requireNonNull(value), null);
	}

	public static <T> Result<T> fail(Component error) {
		return new Result<>(null, Objects.requireNonNull(error));
	}

	public boolean isOk() {
		return error == null;
	}

	public T orElseThrow() {
		if (error != null) {
			throw new IllegalStateException("Result failed: " + error.getString());
		}
		return value;
	}
}
