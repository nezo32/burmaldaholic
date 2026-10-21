package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Locale;

/**
 * The generated casino types (GAME_DESIGN §16.1–16.3) + the v0.2.0 standalone Overworld casino
 * ({@link #CASINO}, structure type {@code burmaldaholic:casino}, architecture doc casino-buildings.md §4.5).
 * Append only: the ordinal is sent to clients ({@code CasinoViewPayload}).
 */
public enum CasinoKind {
	VILLAGE_CASINO, PIGLIN_PARLOR, HIGH_ROLLER, CASINO;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Display name key (STRINGS.md worldgen section). */
	public String nameKey() {
		return "gui.burmaldaholic.worldgen." + id();
	}

	public static CasinoKind byId(String id) {
		for (CasinoKind k : values()) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		throw new IllegalArgumentException("unknown casino kind '" + id + "'");
	}
}
