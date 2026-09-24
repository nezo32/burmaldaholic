package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Locale;

/** The three generated casino types (GAME_DESIGN §16.1–16.3). */
public enum CasinoKind {
	VILLAGE_CASINO, PIGLIN_PARLOR, HIGH_ROLLER;

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
