package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

/**
 * The style palettes (architecture doc §5). Skeleton: only {@link #TUDOR} (plains / forest / meadow) is
 * filled in here (lane CB-2); lane CB-3 adds the other Overworld styles in pieces/OverworldPalettes, lane CB-4
 * the Nether / End ones in pieces/OtherworldPalettes.
 */
public final class Palettes {
	private Palettes() {}

	public static final Palette TUDOR = Palette.builder("tudor")
		.put(Role.FOUNDATION, Material.of("minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab"))
		.put(Role.FLOOR, Material.of("minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab"))
		.put(Role.FLOOR_ACCENT, "minecraft:polished_andesite")
		.put(Role.CARPET, "minecraft:red_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:yellow_carpet")
		.put(Role.WALL, "minecraft:white_terracotta")
		.put(Role.WALL_BASE, Material.of("minecraft:bricks", "minecraft:brick_stairs", "minecraft:brick_slab"))
		.put(Role.FRAME, "minecraft:stripped_dark_oak_log")
		.put(Role.BEAM, "minecraft:dark_oak_log")
		.put(Role.CEILING, Material.of("minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_slab"))
		.put(Role.ROOF, Material.of("minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab"))
		.put(Role.ROOF_TRIM, Material.of("minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab"))
		.put(Role.TRIM, "minecraft:gold_block")
		.put(Role.GLASS, "minecraft:glass")
		.put(Role.PANE, "minecraft:glass_pane")
		.put(Role.LIGHT, "minecraft:ochre_froglight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:red_concrete")
		.put(Role.NEON_B, "minecraft:yellow_concrete")
		.put(Role.SIGN_BACK, "minecraft:black_concrete")
		.put(Role.BULB, "minecraft:pearlescent_froglight")
		.put(Role.ACCENT, Material.of("minecraft:quartz_pillar"))
		.put(Role.COUNTER, Material.of("minecraft:polished_deepslate", "minecraft:polished_deepslate_stairs", "minecraft:polished_deepslate_slab"))
		.put(Role.STOOL, Material.of("minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab"))
		.put(Role.PLANTER, "minecraft:potted_red_tulip")
		.put(Role.PATH, Material.of("minecraft:polished_andesite", "minecraft:polished_andesite_stairs", "minecraft:polished_andesite_slab"))
		.build();
}
