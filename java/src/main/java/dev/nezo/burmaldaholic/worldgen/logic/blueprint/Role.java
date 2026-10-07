package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

/**
 * Material roles of the blueprint DSL (docs/architecture/casino-buildings.md §3.2). Pieces never name a
 * block directly for structural parts: they name a role and the {@link Palette} of the variant resolves it,
 * so one piece generator produces every biome variant.
 */
public enum Role {
	FOUNDATION, FLOOR, FLOOR_ACCENT, CARPET, CARPET_ACCENT, WALL, WALL_BASE, FRAME, BEAM, CEILING,
	ROOF, ROOF_TRIM, TRIM, GLASS, PANE, LIGHT, LIGHT_HANGING, NEON_A, NEON_B, SIGN_BACK, BULB, ACCENT,
	COUNTER, STOOL, PLANTER, PATH
}
