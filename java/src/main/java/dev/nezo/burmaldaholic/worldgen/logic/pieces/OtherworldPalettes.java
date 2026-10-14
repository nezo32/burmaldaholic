package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.List;

/**
 * Palettes of the Nether and End casinos (architecture doc §4.3 / §4.4, design doc §5): lane CB-4.
 * Only blocks that exist in both 26.2 and 26.3 and keep working when a template is placed without power
 * (no redstone lamps); lights are shroomlight / froglights / lanterns / end rods.
 */
public final class OtherworldPalettes {
	private OtherworldPalettes() {}

	/** Piglin Parlor "Gold Vault": blackstone hall, gold trim, crimson ceiling, warped seats, lava light. */
	public static final Palette PARLOR = Palette.builder("parlor")
		.put(Role.FOUNDATION, Material.of("minecraft:blackstone", "minecraft:blackstone_stairs", "minecraft:blackstone_slab"))
		.put(Role.FLOOR, Material.of("minecraft:polished_blackstone", "minecraft:polished_blackstone_stairs", "minecraft:polished_blackstone_slab"))
		.put(Role.FLOOR_ACCENT, Material.of("minecraft:gilded_blackstone"))
		.put(Role.CARPET, "minecraft:red_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:yellow_carpet")
		.put(Role.WALL, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.put(Role.WALL_BASE, Material.of("minecraft:polished_blackstone", "minecraft:polished_blackstone_stairs",
			"minecraft:polished_blackstone_slab"))
		.put(Role.FRAME, "minecraft:chiseled_polished_blackstone")
		.put(Role.BEAM, "minecraft:crimson_stem")
		.put(Role.CEILING, Material.of("minecraft:crimson_planks", "minecraft:crimson_stairs", "minecraft:crimson_slab"))
		.put(Role.ROOF, Material.of("minecraft:blackstone", "minecraft:blackstone_stairs", "minecraft:blackstone_slab"))
		.put(Role.ROOF_TRIM, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.put(Role.TRIM, "minecraft:gold_block")
		.put(Role.GLASS, "minecraft:orange_stained_glass")
		.put(Role.PANE, "minecraft:orange_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:shroomlight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:gold_block")
		.put(Role.NEON_B, "minecraft:shroomlight")
		.put(Role.SIGN_BACK, "minecraft:polished_blackstone")
		.put(Role.BULB, "minecraft:ochre_froglight")
		.put(Role.ACCENT, "minecraft:gilded_blackstone")
		.put(Role.COUNTER, Material.of("minecraft:crimson_planks", "minecraft:crimson_stairs", "minecraft:crimson_slab"))
		.put(Role.STOOL, Material.of("minecraft:warped_planks", "minecraft:warped_stairs", "minecraft:warped_slab"))
		.put(Role.PLANTER, "minecraft:potted_crimson_fungus")
		.put(Role.PATH, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.build();

	/** High Roller Lounge "Void Penthouse": purpur shell, obsidian floor, starry black glass, end rods. */
	public static final Palette LOUNGE = Palette.builder("lounge")
		.put(Role.FOUNDATION, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.FLOOR, Material.of("minecraft:obsidian"))
		.put(Role.FLOOR_ACCENT, Material.of("minecraft:crying_obsidian"))
		.put(Role.CARPET, "minecraft:purple_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:magenta_carpet")
		.put(Role.WALL, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.WALL_BASE, Material.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab"))
		.put(Role.FRAME, "minecraft:purpur_pillar")
		.put(Role.BEAM, "minecraft:purpur_pillar")
		.put(Role.CEILING, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.ROOF, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.ROOF_TRIM, Material.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab"))
		.put(Role.TRIM, "minecraft:end_stone_bricks")
		.put(Role.GLASS, "minecraft:black_stained_glass")
		.put(Role.PANE, "minecraft:purple_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:pearlescent_froglight")
		.put(Role.LIGHT_HANGING, "minecraft:soul_lantern")
		.put(Role.NEON_A, "minecraft:magenta_concrete")
		.put(Role.NEON_B, "minecraft:white_concrete")
		.put(Role.SIGN_BACK, "minecraft:obsidian")
		.put(Role.BULB, "minecraft:pearlescent_froglight")
		.put(Role.ACCENT, "minecraft:amethyst_block")
		.put(Role.COUNTER, Material.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab"))
		.put(Role.STOOL, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.PLANTER, "minecraft:potted_allium")
		.put(Role.PATH, Material.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab"))
		.build();

	public static List<Palette> all() {
		return List.of(PARLOR, LOUNGE);
	}
}
