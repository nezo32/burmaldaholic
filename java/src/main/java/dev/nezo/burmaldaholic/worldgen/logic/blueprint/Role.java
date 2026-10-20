package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

/**
 * Material roles of the blueprint DSL (docs/architecture/casino-buildings.md §3.2). Pieces never name a
 * block directly for structural parts: they name a role and the {@link Palette} of the variant resolves it,
 * so one piece generator produces every biome variant.
 *
 * <p>The base roles (FOUNDATION … PATH) must be mapped by every palette. The architectural roles added by the
 * casino redesign (CR-1: COLUMN … WATER_BASIN) are optional: a palette that does not map one falls back to
 * {@link #fallback()}, so the v0.2 palettes written before them keep working unchanged.
 */
public enum Role {
	FOUNDATION, FLOOR, FLOOR_ACCENT, CARPET, CARPET_ACCENT, WALL, WALL_BASE, FRAME, BEAM, CEILING,
	ROOF, ROOF_TRIM, TRIM, GLASS, PANE, LIGHT, LIGHT_HANGING, NEON_A, NEON_B, SIGN_BACK, BULB, ACCENT,
	COUNTER, STOOL, PLANTER, PATH,
	/** Column shaft (an axis block: quartz pillar, stripped log …). */
	COLUMN(FRAME),
	/** Column capital and base block. */
	CAPITAL(ACCENT),
	/** Plinth / column base stairs (dark). */
	PLINTH(FOUNDATION),
	/** Cornice and entablature (stairs + slab). */
	CORNICE(ROOF_TRIM),
	/** Arch voussoirs (stairs + slab). */
	ARCH(TRIM),
	/** String courses and banding (stairs + slab). */
	BAND(TRIM),
	/** Balustrade posts (a wall block). */
	BALUSTER(ACCENT),
	/** Balustrade hand rail (slab). */
	RAIL(ROOF_TRIM),
	/** Dome shell and copper roofs (stairs + slab). */
	DOME(ROOF),
	/** Dome ribs / roof hips (stairs + slab). */
	RIB(ROOF_TRIM),
	/** Gilt trim — the gold substitute (waxed cut copper, glazed terracotta). */
	GILT(TRIM),
	/** Glowing floor tiles at the table rings and machine corners (a full light block). */
	UPLIGHT(LIGHT),
	/** Water basin floor / walls of fountains. */
	WATER_BASIN(COUNTER),
	/** Rim of fountains and pools (slab). */
	BASIN_RIM(COUNTER),
	/** Second wall material for gradients and bands (rusticated base, string courses). */
	WALL_ALT(WALL),
	/** Coffer panels of the ceiling. */
	COFFER(CEILING),
	/** The subtle pattern colour woven into the casino carpet (a lattice on CARPET). */
	CARPET_PATTERN(CARPET_ACCENT);

	private final Role fallback;

	Role() {
		this.fallback = null;
	}

	Role(Role fallback) {
		this.fallback = fallback;
	}

	/** The base role an unmapped architectural role resolves to; null for the base roles (they are mandatory). */
	public Role fallback() {
		return fallback;
	}
}
