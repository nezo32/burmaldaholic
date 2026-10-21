package dev.nezo.burmaldaholic.core.config;

import com.google.gson.JsonObject;

/**
 * Optional: a config object that renames / moves legacy keys before binding (e.g. {@code economy.emeraldBuyRate} →
 * {@code economy.exchange.emerald.toChips}). {@link #migrate} edits the raw JSON of this object in place; whatever it
 * leaves is bound as usual (unknown keys are still reported).
 */
public interface Migrates {
	void migrate(JsonObject json);
}
