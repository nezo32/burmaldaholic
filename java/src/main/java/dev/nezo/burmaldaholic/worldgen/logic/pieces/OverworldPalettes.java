package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palettes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.List;

/**
 * Palettes of the Overworld casino styles (design doc CASINO_BUILDINGS.md §5, architecture doc §7.2).
 * TUDOR lives in {@link Palettes} (lane CB-2); this class adds the four biome styles.
 *
 * <p>Rules: no gravity blocks above the foundation, no redstone lamps, no valuable storage blocks
 * (gold / diamond / emerald / netherite blocks would turn every village into a mine), leaves only
 * with {@code persistent=true} (decor verbs handle that). Every id is a vanilla block of 26.2.
 */
public final class OverworldPalettes {
	private OverworldPalettes() {}

	private static Material m(String full, String stairs, String slab) {
		return Material.of("minecraft:" + full, stairs == null ? null : "minecraft:" + stairs, slab == null ? null : "minecraft:" + slab);
	}

	/** Desert / badlands: art-deco "Oasis" — sandstone, turquoise glass, orange + lime neon on a dark band. */
	public static final Palette OASIS = Palette.builder("oasis")
		.put(Role.FOUNDATION, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.FLOOR, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.FLOOR_ACCENT, "minecraft:cyan_terracotta")
		.put(Role.CARPET, "minecraft:cyan_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:orange_carpet")
		.put(Role.WALL, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.WALL_BASE, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.put(Role.FRAME, "minecraft:chiseled_sandstone")
		.put(Role.BEAM, "minecraft:orange_terracotta")
		.put(Role.CEILING, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF, m("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab"))
		.put(Role.ROOF_TRIM, m("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab"))
		.put(Role.TRIM, m("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab"))
		.put(Role.GLASS, "minecraft:cyan_stained_glass")
		.put(Role.PANE, "minecraft:cyan_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:sea_lantern")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:orange_concrete")
		.put(Role.NEON_B, "minecraft:lime_concrete")
		.put(Role.SIGN_BACK, "minecraft:black_terracotta")
		.put(Role.BULB, "minecraft:ochre_froglight")
		.put(Role.ACCENT, m("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab"))
		.put(Role.COUNTER, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.STOOL, m("jungle_planks", "jungle_stairs", "jungle_slab"))
		.put(Role.PLANTER, "minecraft:potted_cactus")
		.put(Role.PATH, m("smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab"))
		.build();

	/** Savanna: safari lodge — mud-brick and packed-mud walls, acacia frame, hay thatch, warm lights. */
	public static final Palette SAFARI = Palette.builder("safari")
		.put(Role.FOUNDATION, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.put(Role.FLOOR, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.FLOOR_ACCENT, "minecraft:terracotta")
		.put(Role.CARPET, "minecraft:orange_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:brown_carpet")
		.put(Role.WALL, "minecraft:packed_mud")
		.put(Role.WALL_BASE, m("mud_bricks", "mud_brick_stairs", "mud_brick_slab"))
		.put(Role.FRAME, "minecraft:acacia_log")
		.put(Role.BEAM, "minecraft:stripped_acacia_log")
		.put(Role.CEILING, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.ROOF, "minecraft:hay_block")
		.put(Role.ROOF_TRIM, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.TRIM, m("orange_terracotta", null, null))
		.put(Role.GLASS, "minecraft:orange_stained_glass")
		.put(Role.PANE, "minecraft:glass_pane")
		.put(Role.LIGHT, "minecraft:shroomlight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:yellow_concrete")
		.put(Role.NEON_B, "minecraft:orange_concrete")
		.put(Role.SIGN_BACK, "minecraft:dark_oak_planks")
		.put(Role.BULB, "minecraft:ochre_froglight")
		.put(Role.ACCENT, "minecraft:bone_block")
		.put(Role.COUNTER, m("stripped_acacia_wood", "acacia_stairs", "acacia_slab"))
		.put(Role.STOOL, m("acacia_planks", "acacia_stairs", "acacia_slab"))
		.put(Role.PLANTER, "minecraft:potted_acacia_sapling")
		.put(Role.PATH, m("polished_granite", "polished_granite_stairs", "polished_granite_slab"))
		.build();

	/** Taiga / cherry grove: alpine lodge — spruce timber on a mossy stone base, copper ridge, hearth. */
	public static final Palette ALPINE = Palette.builder("alpine")
		.put(Role.FOUNDATION, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.put(Role.FLOOR, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.FLOOR_ACCENT, "minecraft:stripped_spruce_wood")
		.put(Role.CARPET, "minecraft:green_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:red_carpet")
		.put(Role.WALL, "minecraft:stripped_spruce_wood")
		.put(Role.WALL_BASE, m("mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab"))
		.put(Role.FRAME, "minecraft:spruce_log")
		.put(Role.BEAM, "minecraft:stripped_dark_oak_log")
		.put(Role.CEILING, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.ROOF, m("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"))
		.put(Role.ROOF_TRIM, m("waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"))
		.put(Role.TRIM, m("white_terracotta", null, null))
		.put(Role.GLASS, "minecraft:glass")
		.put(Role.PANE, "minecraft:glass_pane")
		.put(Role.LIGHT, "minecraft:ochre_froglight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:yellow_concrete")
		.put(Role.NEON_B, "minecraft:white_concrete")
		.put(Role.SIGN_BACK, "minecraft:green_terracotta")
		.put(Role.BULB, "minecraft:pearlescent_froglight")
		.put(Role.ACCENT, m("mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab"))
		.put(Role.COUNTER, m("stripped_spruce_wood", "spruce_stairs", "spruce_slab"))
		.put(Role.STOOL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.PLANTER, "minecraft:potted_spruce_sapling")
		.put(Role.PATH, m("cobblestone", "cobblestone_stairs", "cobblestone_slab"))
		.build();

	/** Snowy: ice palace — packed ice between quartz pillars, blue-ice dome, cyan + white neon on navy. */
	public static final Palette ICE_PALACE = Palette.builder("ice_palace")
		.put(Role.FOUNDATION, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.FLOOR, m("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab"))
		.put(Role.FLOOR_ACCENT, "minecraft:quartz_bricks")
		.put(Role.CARPET, "minecraft:light_blue_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:white_carpet")
		.put(Role.WALL, "minecraft:packed_ice")
		.put(Role.WALL_BASE, m("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab"))
		.put(Role.FRAME, "minecraft:quartz_pillar")
		.put(Role.BEAM, "minecraft:chiseled_quartz_block")
		.put(Role.CEILING, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.ROOF, "minecraft:blue_ice")
		.put(Role.ROOF_TRIM, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.TRIM, m("snow_block", null, null))
		.put(Role.GLASS, "minecraft:light_blue_stained_glass")
		.put(Role.PANE, "minecraft:light_blue_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:sea_lantern")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:white_concrete")
		.put(Role.NEON_B, "minecraft:light_blue_concrete")
		.put(Role.SIGN_BACK, "minecraft:blue_concrete")
		.put(Role.BULB, "minecraft:pearlescent_froglight")
		.put(Role.ACCENT, m("quartz_bricks", null, null))
		.put(Role.COUNTER, m("quartz_block", "quartz_stairs", "quartz_slab"))
		.put(Role.STOOL, m("spruce_planks", "spruce_stairs", "spruce_slab"))
		.put(Role.PLANTER, "minecraft:potted_azure_bluet")
		.put(Role.PATH, m("calcite", null, null))
		.build();

	/** The four palettes of this class (TUDOR excluded), in style order. */
	public static List<Palette> all() {
		return List.of(OASIS, SAFARI, ALPINE, ICE_PALACE);
	}
}
