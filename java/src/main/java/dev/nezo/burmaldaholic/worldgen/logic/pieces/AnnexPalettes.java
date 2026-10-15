package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;

/**
 * Palettes of the village annex "Lucky Villager" picture palace (casino redesign CR-2, findings/CASINO-REDESIGN.md
 * §4.1): one deco cinema plan, the village's own materials for the walls, order and roof — plains oak and cobble,
 * desert sandstone, savanna acacia and terracotta, taiga spruce, snowy spruce and white. The deco skin (black sign
 * board, froglight neon, lit copper bulbs, waxed-copper gilt, red/black casino carpet) is shared, so every annex reads
 * as the same casino brand. No valuable blocks (Checks FORBIDDEN).
 */
public final class AnnexPalettes {
	private AnnexPalettes() {}

	private static Material m(String full, String stairs, String slab) {
		return Material.of("minecraft:" + full, stairs == null ? null : "minecraft:" + stairs, slab == null ? null : "minecraft:" + slab);
	}

	private static Material m(String full) {
		return Material.of("minecraft:" + full);
	}

	/** Plains: cream plaster body, cobble plinth, stripped-oak order, dark-oak roof. */
	public static final Palette PLAINS = Palette.builder("annex_plains")
		.put(Role.FOUNDATION, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.put(Role.PLINTH, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.FLOOR, m("smooth_stone", null, "smooth_stone_slab"))
		.put(Role.FLOOR_ACCENT, m("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab"))
		.put(Role.CARPET, m("red_carpet"))
		.put(Role.CARPET_ACCENT, m("black_carpet"))
		.put(Role.CARPET_PATTERN, m("yellow_carpet"))
		.put(Role.WALL, m("white_terracotta"))
		.put(Role.WALL_ALT, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.WALL_BASE, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.FRAME, m("stripped_oak_log"))
		.put(Role.COLUMN, m("stripped_oak_log"))
		.put(Role.CAPITAL, m("chiseled_stone_bricks"))
		.put(Role.BEAM, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.CEILING, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.COFFER, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.ROOF_TRIM, m("smooth_stone", null, "smooth_stone_slab"))
		.put(Role.CORNICE, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.BAND, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.ARCH, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.TRIM, m("chiseled_stone_bricks"))
		.put(Role.GILT, m("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"))
		.put(Role.BALUSTER, m("stone_brick_wall"))
		.put(Role.RAIL, m("smooth_stone", null, "smooth_stone_slab"))
		.put(Role.GLASS, m("glass"))
		.put(Role.PANE, m("glass_pane"))
		.put(Role.LIGHT, m("pearlescent_froglight"))
		.put(Role.UPLIGHT, m("pearlescent_froglight"))
		.put(Role.LIGHT_HANGING, m("lantern"))
		.put(Role.NEON_A, m("ochre_froglight"))
		.put(Role.NEON_B, m("pearlescent_froglight"))
		.put(Role.SIGN_BACK, m("black_concrete"))
		.put(Role.BULB, GrandPalettes.BULB)
		.put(Role.ACCENT, m("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab"))
		.put(Role.COUNTER, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.STOOL, m("crimson_planks", "crimson_stairs", "crimson_slab"))
		.put(Role.PLANTER, m("potted_azalea_bush"))
		.put(Role.PATH, m("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab"))
		.build();

	/** Desert: sandstone deco with red-sandstone bands, smooth sandstone order, terracotta roof, blue board. */
	public static final Palette DESERT = PLAINS.derive("annex_desert")
		.put(Role.FOUNDATION, m("sandstone", "sandstone_stairs", "sandstone_slab"))
		.put(Role.PLINTH, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.FLOOR, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.FLOOR_ACCENT, m("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab"))
		.put(Role.WALL, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.WALL_ALT, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.WALL_BASE, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.FRAME, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.COLUMN, m("chiseled_sandstone"))
		.put(Role.CAPITAL, m("chiseled_red_sandstone"))
		.put(Role.BEAM, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.CEILING, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.COFFER, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.ROOF, m("orange_terracotta"))
		.put(Role.ROOF_TRIM, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.CORNICE, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.BAND, m("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab"))
		.put(Role.ARCH, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.TRIM, m("chiseled_red_sandstone"))
		.put(Role.GILT, m("orange_glazed_terracotta"))
		.put(Role.BALUSTER, m("sandstone_wall"))
		.put(Role.RAIL, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.SIGN_BACK, m("blue_terracotta"))
		.put(Role.ACCENT, m("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab"))
		.put(Role.COUNTER, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.STOOL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.PLANTER, m("potted_cactus"))
		.put(Role.PATH, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.build();

	/** Savanna: terracotta body, acacia order and roof, mud-brick plinth. */
	public static final Palette SAVANNA = PLAINS.derive("annex_savanna")
		.put(Role.FOUNDATION, m("mud_bricks", "mud_brick_stairs", "mud_brick_slab"))
		.put(Role.PLINTH, m("mud_bricks", "mud_brick_stairs", "mud_brick_slab"))
		.put(Role.FLOOR, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.FLOOR_ACCENT, m("stripped_acacia_wood"))
		.put(Role.WALL, m("terracotta"))
		.put(Role.WALL_ALT, m("yellow_terracotta"))
		.put(Role.WALL_BASE, m("mud_bricks", "mud_brick_stairs", "mud_brick_slab"))
		.put(Role.FRAME, m("stripped_acacia_log"))
		.put(Role.COLUMN, m("stripped_acacia_log"))
		.put(Role.CAPITAL, m("chiseled_sandstone"))
		.put(Role.BEAM, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.CEILING, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.COFFER, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.ROOF_TRIM, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.CORNICE, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.BAND, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.ARCH, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.TRIM, m("yellow_glazed_terracotta"))
		.put(Role.GILT, m("yellow_terracotta"))
		.put(Role.BALUSTER, m("mud_brick_wall"))
		.put(Role.RAIL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.SIGN_BACK, m("brown_concrete"))
		.put(Role.NEON_B, m("shroomlight"))
		.put(Role.ACCENT, m("packed_mud"))
		.put(Role.COUNTER, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.STOOL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.PLANTER, m("potted_acacia_sapling"))
		.put(Role.PATH, m("packed_mud"))
		.build();

	/** Taiga: spruce timber body on a cobble plinth, dark spruce order, deepslate roof, white neon. */
	public static final Palette TAIGA = PLAINS.derive("annex_taiga")
		.put(Role.FOUNDATION, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.put(Role.PLINTH, m("mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab"))
		.put(Role.FLOOR, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.FLOOR_ACCENT, m("stripped_spruce_wood"))
		.put(Role.WALL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.WALL_ALT, m("stripped_spruce_wood"))
		.put(Role.WALL_BASE, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.put(Role.FRAME, m("spruce_log"))
		.put(Role.COLUMN, m("spruce_log"))
		.put(Role.CAPITAL, m("chiseled_tuff"))
		.put(Role.BEAM, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.CEILING, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.COFFER, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.ROOF, m("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"))
		.put(Role.ROOF_TRIM, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.CORNICE, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.BAND, m("stripped_spruce_wood"))
		.put(Role.ARCH, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.TRIM, m("chiseled_tuff_bricks"))
		.put(Role.BALUSTER, m("cobblestone_wall"))
		.put(Role.RAIL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.NEON_A, m("pearlescent_froglight"))
		.put(Role.NEON_B, m("pearlescent_froglight"))
		.put(Role.ACCENT, m("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab"))
		.put(Role.COUNTER, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.STOOL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.PLANTER, m("potted_spruce_sapling"))
		.put(Role.PATH, m("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab"))
		.build();

	/** Snowy: white quartz body, spruce order and roof, navy board with white letters, blue carpet. */
	public static final Palette SNOWY = PLAINS.derive("annex_snowy")
		.put(Role.FOUNDATION, m("stone_bricks", "stone_brick_stairs", "stone_brick_slab"))
		.put(Role.PLINTH, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.FLOOR, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.FLOOR_ACCENT, m("packed_ice"))
		.put(Role.WALL, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.WALL_ALT, m("quartz_bricks"))
		.put(Role.WALL_BASE, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.FRAME, m("stripped_spruce_log"))
		.put(Role.COLUMN, m("stripped_spruce_log"))
		.put(Role.CAPITAL, m("chiseled_quartz_block"))
		.put(Role.BEAM, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.CEILING, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.COFFER, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.ROOF, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.ROOF_TRIM, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.CORNICE, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.BAND, m("quartz_bricks"))
		.put(Role.ARCH, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.TRIM, m("light_blue_glazed_terracotta"))
		.put(Role.GILT, m("light_blue_glazed_terracotta"))
		.put(Role.BALUSTER, m("diorite_wall"))
		.put(Role.RAIL, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.GLASS, m("light_blue_stained_glass"))
		.put(Role.SIGN_BACK, m("blue_concrete"))
		.put(Role.NEON_A, m("pearlescent_froglight"))
		.put(Role.NEON_B, m("sea_lantern"))
		.put(Role.ACCENT, m("quartz_bricks"))
		.put(Role.COUNTER, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.STOOL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.CARPET, m("blue_carpet"))
		.put(Role.CARPET_ACCENT, m("black_carpet"))
		.put(Role.CARPET_PATTERN, m("light_blue_carpet"))
		.put(Role.PLANTER, m("potted_spruce_sapling"))
		.put(Role.PATH, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.build();

	/**
	 * Look of one village annex: its palette and the letter banners' cloth ({@code bannerBase}) and ink colours, and
	 * the colour of the wall banners flanking the door.
	 */
	public record Look(Palette palette, String bannerBase, String bannerInk) {}

	public static Look look(VillageStyle v) {
		return switch (v) {
			case PLAINS -> new Look(PLAINS, "black", "yellow");
			case DESERT -> new Look(DESERT, "blue", "yellow");
			case SAVANNA -> new Look(SAVANNA, "brown", "yellow");
			case TAIGA -> new Look(TAIGA, "green", "white");
			case SNOWY -> new Look(SNOWY, "blue", "white");
		};
	}
}
