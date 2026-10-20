package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.List;

/**
 * Biome palettes of the Grand Casino (findings/CASINO-REDESIGN.md §3.4 / §3.7): one plan, six looks. {@link #RIVIERA}
 * is the full table ("gilt on dark": deepslate plinth, sandstone body, quartz order, verdigris copper roofs, froglight
 * neon); the others {@link Palette#derive} it and swap the roles the biome changes. No valuable blocks (Checks
 * FORBIDDEN): gilt is waxed cut copper / glazed terracotta, bulbs are lit waxed copper bulbs.
 */
public final class GrandPalettes {
	private GrandPalettes() {}

	private static Material m(String full, String stairs, String slab) {
		return Material.of("minecraft:" + full, stairs == null ? null : "minecraft:" + stairs, slab == null ? null : "minecraft:" + slab);
	}

	private static Material m(String full) {
		return Material.of("minecraft:" + full);
	}

	/** Lit copper bulb: light 15, stays lit without redstone (no neighbour signal toggles it). */
	static final Material BULB = m("waxed_copper_bulb[lit=true,powered=false]");

	/** Riviera (plains, forest, meadow, beach): the default Monte-Carlo look. */
	public static final Palette RIVIERA = Palette.builder("grand_riviera")
		.put(Role.FOUNDATION, m("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"))
		.put(Role.FLOOR, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.FLOOR_ACCENT, m("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"))
		.put(Role.CARPET, m("red_carpet"))
		.put(Role.CARPET_ACCENT, m("red_carpet"))
		.put(Role.CARPET_PATTERN, m("brown_carpet"))
		.put(Role.WALL, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.WALL_ALT, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.WALL_BASE, m("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"))
		.put(Role.FRAME, m("stripped_dark_oak_log"))
		.put(Role.BEAM, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.CEILING, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.COFFER, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.ROOF, m("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"))
		.put(Role.DOME, m("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"))
		.put(Role.RIB, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.ROOF_TRIM, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.CORNICE, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.ARCH, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.BAND, m("cut_sandstone", "smooth_sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.TRIM, m("chiseled_sandstone"))
		.put(Role.GILT, m("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"))
		.put(Role.COLUMN, m("quartz_pillar"))
		.put(Role.CAPITAL, m("chiseled_quartz_block"))
		.put(Role.PLINTH, m("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"))
		.put(Role.BALUSTER, m("sandstone_wall"))
		.put(Role.RAIL, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.GLASS, m("white_stained_glass"))
		.put(Role.PANE, m("glass_pane"))
		.put(Role.LIGHT, m("pearlescent_froglight"))
		.put(Role.UPLIGHT, m("pearlescent_froglight"))
		.put(Role.LIGHT_HANGING, m("lantern"))
		.put(Role.NEON_A, m("ochre_froglight"))
		.put(Role.NEON_B, m("ochre_froglight"))
		.put(Role.SIGN_BACK, m("black_concrete"))
		.put(Role.BULB, BULB)
		.put(Role.ACCENT, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.COUNTER, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.STOOL, m("crimson_planks", "crimson_stairs", "crimson_slab"))
		.put(Role.PLANTER, m("potted_azalea_bush"))
		.put(Role.PATH, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.WATER_BASIN, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.BASIN_RIM, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.build();

	/** Oasis Deco (desert, badlands): sandstone with red-sandstone bands, a teal prismarine dome, letters on blue. */
	public static final Palette OASIS = RIVIERA.derive("grand_oasis")
		.put(Role.WALL, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.WALL_ALT, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.BAND, m("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab"))
		.put(Role.TRIM, m("chiseled_red_sandstone"))
		.put(Role.WALL_BASE, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.ROOF, m("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab"))
		.put(Role.DOME, m("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab"))
		.put(Role.RIB, m("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab"))
		.put(Role.GILT, m("orange_glazed_terracotta"))
		.put(Role.BALUSTER, m("red_sandstone_wall"))
		.put(Role.SIGN_BACK, m("blue_terracotta"))
		.put(Role.NEON_A, m("pearlescent_froglight"))
		.put(Role.NEON_B, m("ochre_froglight"))
		.put(Role.GLASS, m("light_blue_stained_glass"))
		.put(Role.STOOL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.PLANTER, m("potted_cactus"))
		.put(Role.PATH, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.build();

	/** Safari Deco (savanna): mud-brick body, acacia order, terracotta-red roofs, a shroomlight chaser. */
	public static final Palette SAFARI = RIVIERA.derive("grand_safari")
		.put(Role.WALL, m("mud_bricks", "mud_brick_stairs", "mud_brick_slab"))
		.put(Role.WALL_ALT, m("packed_mud"))
		.put(Role.BAND, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.WALL_BASE, m("bricks", "brick_stairs", "brick_slab"))
		.put(Role.COLUMN, m("stripped_acacia_log"))
		.put(Role.CAPITAL, m("chiseled_sandstone"))
		.put(Role.FRAME, m("stripped_acacia_log"))
		.put(Role.BEAM, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.CEILING, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.COFFER, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF, m("bricks", "brick_stairs", "brick_slab"))
		.put(Role.DOME, m("bricks", "brick_stairs", "brick_slab"))
		.put(Role.RIB, m("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"))
		.put(Role.CORNICE, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF_TRIM, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ARCH, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.RAIL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.BALUSTER, m("mud_brick_wall"))
		.put(Role.GILT, m("yellow_terracotta"))
		.put(Role.SIGN_BACK, m("brown_concrete"))
		.put(Role.NEON_A, m("ochre_froglight"))
		.put(Role.NEON_B, m("shroomlight"))
		.put(Role.BULB, m("shroomlight"))
		.put(Role.COUNTER, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.PLANTER, m("potted_acacia_sapling"))
		.put(Role.PATH, m("packed_mud"))
		.build();

	/** Alpine Grand Hotel (taiga, snowy taiga, windswept): tuff body, spruce order, slate roofs, white letters. */
	public static final Palette ALPINE = RIVIERA.derive("grand_alpine")
		.put(Role.WALL, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.WALL_ALT, m("tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab"))
		.put(Role.WALL_BASE, m("tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab"))
		.put(Role.BAND, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.TRIM, m("chiseled_tuff_bricks"))
		.put(Role.COLUMN, m("stripped_spruce_log"))
		.put(Role.CAPITAL, m("chiseled_tuff"))
		.put(Role.FRAME, m("stripped_spruce_log"))
		.put(Role.BEAM, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.CEILING, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.COFFER, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.ROOF, m("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"))
		.put(Role.DOME, m("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"))
		.put(Role.CORNICE, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.ROOF_TRIM, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.ARCH, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.RIB, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.RAIL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.BALUSTER, m("tuff_brick_wall"))
		.put(Role.NEON_A, m("pearlescent_froglight"))
		.put(Role.NEON_B, m("pearlescent_froglight"))
		.put(Role.STOOL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.COUNTER, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.PLANTER, m("potted_spruce_sapling"))
		.put(Role.PATH, m("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab"))
		.build();

	/** Ice Palace Deco (snowy plains, ice spikes): white quartz, packed-ice order, prismarine dome, white letters on navy. */
	public static final Palette ICE_PALACE = RIVIERA.derive("grand_ice_palace")
		.put(Role.WALL, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.WALL_ALT, m("quartz_bricks"))
		.put(Role.BAND, m("quartz_bricks", "quartz_stairs", "quartz_slab"))
		.put(Role.TRIM, m("light_blue_glazed_terracotta"))
		.put(Role.WALL_BASE, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.COLUMN, m("packed_ice"))
		.put(Role.ROOF, m("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab"))
		.put(Role.DOME, m("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab"))
		.put(Role.RIB, m("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab"))
		.put(Role.GILT, m("light_blue_glazed_terracotta"))
		.put(Role.GLASS, m("light_blue_stained_glass"))
		.put(Role.SIGN_BACK, m("blue_concrete"))
		.put(Role.NEON_A, m("pearlescent_froglight"))
		.put(Role.NEON_B, m("sea_lantern"))
		.put(Role.FLOOR_ACCENT, m("packed_ice"))
		.put(Role.BALUSTER, m("diorite_wall"))
		.put(Role.CEILING, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.BEAM, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.FRAME, m("stripped_spruce_log"))
		.put(Role.COUNTER, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.STOOL, m("warped_planks", "warped_stairs", "warped_slab"))
		.put(Role.CARPET, m("blue_carpet"))
		.put(Role.CARPET_PATTERN, m("cyan_carpet"))
		.put(Role.PLANTER, m("potted_spruce_sapling"))
		.put(Role.PATH, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.build();

	/** Cherry Pavilion (cherry grove; optional sixth look, no structure slot yet — see CR-1 handoff). */
	public static final Palette CHERRY = RIVIERA.derive("grand_cherry")
		.put(Role.WALL, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.WALL_ALT, m("cherry_planks", "cherry_stairs", "cherry_slab"))
		.put(Role.COLUMN, m("stripped_cherry_log"))
		.put(Role.FRAME, m("stripped_cherry_log"))
		.put(Role.BEAM, m("cherry_planks", "cherry_stairs", "cherry_slab"))
		.put(Role.CEILING, m("cherry_planks", "cherry_stairs", "cherry_slab"))
		.put(Role.GILT, m("pink_glazed_terracotta"))
		.put(Role.TRIM, m("pink_glazed_terracotta"))
		.put(Role.BALUSTER, m("diorite_wall"))
		.put(Role.STOOL, m("cherry_planks", "cherry_stairs", "cherry_slab"))
		.put(Role.COUNTER, m("cherry_planks", "cherry_stairs", "cherry_slab"))
		.put(Role.CARPET, m("pink_carpet"))
		.put(Role.CARPET_PATTERN, m("magenta_carpet"))
		.put(Role.PLANTER, m("potted_cherry_sapling"))
		.build();

	public static List<Palette> all() {
		return List.of(RIVIERA, OASIS, SAFARI, ALPINE, ICE_PALACE, CHERRY);
	}
}
