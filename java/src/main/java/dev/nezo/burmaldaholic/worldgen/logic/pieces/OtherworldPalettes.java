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

	/**
	 * Piglin Parlor "Bastion Baroque" (CR-2): the Piglin Manor's blackstone and crimson, polished-basalt order, red
	 * nether-brick trim, waxed-copper gilt, shroomlight neon on blackstone with an ochre froglight chaser, a deep red
	 * casino field with a gilt border. No gold or gilded blackstone (Checks FORBIDDEN / no valuables).
	 */
	public static final Palette PARLOR = Palette.builder("parlor")
		.put(Role.FOUNDATION, Material.of("minecraft:blackstone", "minecraft:blackstone_stairs", "minecraft:blackstone_slab"))
		.put(Role.FLOOR, Material.of("minecraft:polished_blackstone", "minecraft:polished_blackstone_stairs", "minecraft:polished_blackstone_slab"))
		.put(Role.FLOOR_ACCENT, Material.of("minecraft:polished_blackstone_bricks"))
		.put(Role.CARPET, "minecraft:red_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:red_carpet")
		.put(Role.CARPET_PATTERN, "minecraft:yellow_carpet")
		.put(Role.WALL, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.put(Role.WALL_BASE, Material.of("minecraft:polished_blackstone", "minecraft:polished_blackstone_stairs",
			"minecraft:polished_blackstone_slab"))
		.put(Role.FRAME, "minecraft:chiseled_polished_blackstone")
		.put(Role.COLUMN, "minecraft:polished_basalt")
		.put(Role.CAPITAL, "minecraft:chiseled_polished_blackstone")
		.put(Role.BEAM, "minecraft:stripped_crimson_stem")
		.put(Role.CEILING, Material.of("minecraft:crimson_planks", "minecraft:crimson_stairs", "minecraft:crimson_slab"))
		.put(Role.COFFER, Material.of("minecraft:nether_wart_block"))
		.put(Role.ROOF, Material.of("minecraft:blackstone", "minecraft:blackstone_stairs", "minecraft:blackstone_slab"))
		.put(Role.ROOF_TRIM, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.put(Role.TRIM, Material.of("minecraft:red_nether_bricks", "minecraft:red_nether_brick_stairs", "minecraft:red_nether_brick_slab"))
		.put(Role.GILT, Material.of("minecraft:waxed_cut_copper", "minecraft:waxed_cut_copper_stairs", "minecraft:waxed_cut_copper_slab"))
		.put(Role.BALUSTER, "minecraft:polished_blackstone_wall")
		.put(Role.RAIL, Material.of("minecraft:polished_blackstone_slab"))
		.put(Role.GLASS, "minecraft:orange_stained_glass")
		.put(Role.PANE, "minecraft:orange_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:shroomlight")
		.put(Role.UPLIGHT, "minecraft:shroomlight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:shroomlight")
		.put(Role.NEON_B, "minecraft:shroomlight")
		.put(Role.SIGN_BACK, "minecraft:blackstone")
		.put(Role.BULB, "minecraft:ochre_froglight")
		.put(Role.ACCENT, Material.of("minecraft:red_nether_bricks", "minecraft:red_nether_brick_stairs", "minecraft:red_nether_brick_slab"))
		.put(Role.COUNTER, Material.of("minecraft:crimson_planks", "minecraft:crimson_stairs", "minecraft:crimson_slab"))
		.put(Role.STOOL, Material.of("minecraft:warped_planks", "minecraft:warped_stairs", "minecraft:warped_slab"))
		.put(Role.PLANTER, "minecraft:potted_crimson_fungus")
		.put(Role.PATH, Material.of("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs",
			"minecraft:polished_blackstone_brick_slab"))
		.build();

	/**
	 * High Roller Lounge "Void Penthouse" (CR-2): purpur body and order, black-glass curtain walls, obsidian floor with an
	 * end-stone border, pearlescent froglight neon on obsidian, crying-obsidian glow trim, a black casino field with a gilt
	 * border. No shulker boxes, no dragon heads (Checks FORBIDDEN).
	 */
	public static final Palette LOUNGE = Palette.builder("lounge")
		.put(Role.FOUNDATION, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.FLOOR, Material.of("minecraft:obsidian"))
		.put(Role.FLOOR_ACCENT, Material.of("minecraft:crying_obsidian"))
		.put(Role.CARPET, "minecraft:black_carpet")
		.put(Role.CARPET_ACCENT, "minecraft:black_carpet")
		.put(Role.CARPET_PATTERN, "minecraft:yellow_carpet")
		.put(Role.WALL, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.WALL_BASE, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.FRAME, "minecraft:purpur_pillar")
		.put(Role.COLUMN, "minecraft:purpur_pillar")
		.put(Role.CAPITAL, "minecraft:crying_obsidian")
		.put(Role.BEAM, "minecraft:purpur_pillar")
		.put(Role.CEILING, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.COFFER, Material.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab"))
		.put(Role.ROOF, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.DOME, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.ROOF_TRIM, Material.of("minecraft:obsidian"))
		.put(Role.RAIL, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.BALUSTER, "minecraft:end_stone_brick_wall")
		.put(Role.TRIM, Material.of("minecraft:purpur_block", "minecraft:purpur_stairs", "minecraft:purpur_slab"))
		.put(Role.GLASS, "minecraft:black_stained_glass")
		.put(Role.PANE, "minecraft:purple_stained_glass_pane")
		.put(Role.LIGHT, "minecraft:pearlescent_froglight")
		.put(Role.UPLIGHT, "minecraft:pearlescent_froglight")
		.put(Role.LIGHT_HANGING, "minecraft:lantern")
		.put(Role.NEON_A, "minecraft:pearlescent_froglight")
		.put(Role.NEON_B, "minecraft:pearlescent_froglight")
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
