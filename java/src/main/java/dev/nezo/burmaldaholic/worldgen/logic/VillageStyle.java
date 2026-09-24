package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Locale;
import java.util.Optional;

/** Vanilla village types; each has its own casino template. */
public enum VillageStyle {
	PLAINS, DESERT, SAVANNA, TAIGA, SNOWY;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Vanilla pool the casino joins for this village type: the street-end pool
	 * {@code minecraft:village/<type>/terminators}. (The {@code houses} pool the spec names cannot
	 * hold a 17×17 building: house lots lie inside the 16-block street pieces — the biggest vanilla
	 * house is 11×17 — while street ends open onto free space.)
	 */
	public String casinoPool() {
		return "minecraft:village/" + id() + "/terminators";
	}

	/** The style whose (non-zombie) casino pool this is, if any. */
	public static Optional<VillageStyle> byCasinoPool(String poolId) {
		for (VillageStyle s : values()) {
			if (s.casinoPool().equals(poolId)) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	/** Style for a biome id, used when an op builds a village casino by command. */
	public static VillageStyle forBiome(String biomeId) {
		String b = biomeId.replaceFirst("^minecraft:", "");
		if (b.matches(".*(snow|ice|frozen).*")) {
			return SNOWY;
		}
		if (b.matches(".*(desert|badlands).*")) {
			return DESERT;
		}
		if (b.contains("savanna")) {
			return SAVANNA;
		}
		if (b.matches(".*(taiga|grove).*")) {
			return TAIGA;
		}
		return PLAINS;
	}
}
