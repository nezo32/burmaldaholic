package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;

/**
 * Block-shape classification by id (PURE approximation of vanilla block shapes) for the finishing pass of
 * {@link Blueprint#build()} and for {@link Checks}. Errs on the side of "not a full block".
 */
public final class Shapes {
	private Shapes() {}

	public static boolean isFence(String n) {
		return n.endsWith("_fence");
	}

	public static boolean isPane(String n) {
		return n.endsWith("glass_pane") || n.equals("minecraft:iron_bars") || n.endsWith("_bars");
	}

	/** Wall blocks ({@code cobblestone_wall}, {@code sandstone_wall} …), not wall banners / torches / signs. */
	public static boolean isWall(String n) {
		return n.endsWith("_wall");
	}

	public static boolean isStairs(String n) {
		return n.endsWith("_stairs");
	}

	/** Blocks with an {@code axis} property (logs, wood, stems, pillars, hay, basalt, bone, chains). */
	public static boolean hasAxis(String n) {
		return n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem") || n.endsWith("_hyphae") || n.endsWith("pillar")
			|| n.equals("minecraft:hay_block") || n.endsWith("basalt") || n.equals("minecraft:bone_block") || n.endsWith("chain")
			|| n.equals("minecraft:bamboo_block") || n.endsWith("_froglight") || n.equals("minecraft:muddy_mangrove_roots");
	}

	public static boolean isCarpet(String n) {
		return n.endsWith("_carpet") || n.equals("minecraft:moss_carpet");
	}

	/** No collision box: people walk through (and stand in) these cells. */
	public static boolean passable(BlockSpec b) {
		if (b == null) {
			return false;
		}
		String n = b.name();
		return n.equals("minecraft:air") || isCarpet(n) || n.endsWith("_wall_banner") || n.endsWith("_pressure_plate")
			|| n.endsWith("_button") || n.endsWith("torch") || n.equals("minecraft:light");
	}

	/** A full, sturdy cube (fences and panes attach to it, mobs can stand on it). */
	public static boolean fullCube(BlockSpec b) {
		if (b == null) {
			return false;
		}
		String n = b.name();
		if (n.startsWith("burmaldaholic:")) {
			return false; // game blocks: custom shapes, never attach decor to them
		}
		if (n.endsWith("_slab")) {
			return "double".equals(b.properties().get("type"));
		}
		return !(passable(b) || isStairs(n) || isFence(n) || isPane(n) || n.endsWith("_fence_gate") || n.endsWith("_wall")
			|| n.endsWith("chain") || n.endsWith("lantern") && !n.equals("minecraft:sea_lantern") || n.endsWith("candle")
			|| n.endsWith("_banner") || n.contains("potted_") || n.equals("minecraft:flower_pot") || n.endsWith("_trapdoor")
			|| n.endsWith("_door") || n.equals("minecraft:end_rod") || n.endsWith("lightning_rod") || n.equals("minecraft:lectern")
			|| n.equals("minecraft:brewing_stand") || n.endsWith("cauldron") || n.endsWith("chest") || n.endsWith("campfire")
			|| n.endsWith("azalea") || n.endsWith("_sapling") || n.equals("minecraft:decorated_pot") || n.endsWith("_shelf")
			|| n.equals("minecraft:ladder") || n.endsWith("_bed") || n.equals("minecraft:anvil") || n.equals("minecraft:bell")
			|| n.endsWith("_head") || n.endsWith("_skull") || n.equals("minecraft:scaffolding") || n.endsWith("_carpet")
			|| n.equals("minecraft:snow") || n.equals("minecraft:sea_pickle") || n.equals("minecraft:flowering_azalea")
			|| n.equals("minecraft:big_dripleaf") || n.equals("minecraft:fern") || n.equals("minecraft:short_grass")
			|| n.endsWith("_tulip") || n.equals("minecraft:poppy") || n.equals("minecraft:grindstone")
			|| n.equals("minecraft:enchanting_table") || n.equals("minecraft:stonecutter") || n.equals("minecraft:hopper")
			|| n.endsWith("_sign") || n.equals("minecraft:cake") || n.equals("minecraft:composter")
			|| n.equals("minecraft:daylight_detector"));
	}

	/**
	 * Top of the block's COLLISION shape in blocks above its own bottom (vanilla values; 0 = no collision). Unknown
	 * blocks count as a full cube (1), structure void ({@code null}: whatever the world has there) as 1, fences, walls
	 * and fence gates 1.5.
	 */
	public static double collisionTop(BlockSpec b) {
		if (b == null) {
			return 1.0;
		}
		String n = b.name();
		if (passable(b) || n.endsWith("_sapling") || n.equals("minecraft:fern") || n.equals("minecraft:short_grass")
			|| n.endsWith("_tulip") || n.equals("minecraft:poppy") || n.endsWith("_sign") || n.equals("minecraft:big_dripleaf")) {
			return isCarpet(n) ? 1 / 16.0 : 0.0;
		}
		if (n.endsWith("_slab")) {
			return "bottom".equals(b.properties().get("type")) ? 0.5 : 1.0;
		}
		if (n.endsWith("_trapdoor")) {
			boolean open = "true".equals(b.properties().get("open"));
			return open || "top".equals(b.properties().get("half")) ? 1.0 : 3 / 16.0;
		}
		if (isFence(n) || n.endsWith("_wall") || n.endsWith("_fence_gate")) {
			return n.endsWith("_fence_gate") && "true".equals(b.properties().get("open")) ? 0.0 : 1.5;
		}
		if (n.endsWith("lantern") && !n.equals("minecraft:sea_lantern")) {
			return "true".equals(b.properties().get("hanging")) ? 10 / 16.0 : 9 / 16.0;
		}
		if (n.endsWith("candle")) {
			return 6 / 16.0;
		}
		if (n.contains("potted_") || n.equals("minecraft:flower_pot") || n.equals("minecraft:sea_pickle")
			|| n.equals("minecraft:daylight_detector")) {
			return 6 / 16.0;
		}
		if (n.equals("minecraft:snow")) {
			int layers = Integer.parseInt(b.properties().getOrDefault("layers", "1"));
			return (layers - 1) / 8.0;
		}
		return switch (n) {
			case "minecraft:lectern" -> 15 / 16.0;
			case "minecraft:brewing_stand" -> 14 / 16.0;
			case "minecraft:campfire", "minecraft:soul_campfire" -> 7 / 16.0;
			case "minecraft:chest", "minecraft:trapped_chest", "minecraft:ender_chest" -> 14 / 16.0;
			case "minecraft:enchanting_table" -> 12 / 16.0;
			case "minecraft:stonecutter" -> 9 / 16.0;
			case "minecraft:cake" -> 8 / 16.0;
			default -> n.endsWith("_bed") ? 9 / 16.0 : n.endsWith("_head") || n.endsWith("_skull") ? 0.5 : 1.0;
		};
	}

	/** Light passes (approximation; the GameTest measures the real value). */
	public static boolean transparent(BlockSpec b) {
		if (b == null) {
			return false; // structure void: unknown world block, assume solid
		}
		String n = b.name();
		return !fullCube(b) && !n.startsWith("burmaldaholic:") || n.endsWith("glass") || n.endsWith("_leaves");
	}

	/** Would a fence / pane at a cell connect to {@code n} on its side {@code towards} (n's face {@code face})? */
	static boolean connects(String self, BlockSpec n, Facing face) {
		if (n == null) {
			return false;
		}
		String o = n.name();
		if (isFence(self)) {
			boolean selfNether = self.contains("nether_brick");
			if (isFence(o)) {
				return selfNether == o.contains("nether_brick");
			}
			if (o.endsWith("_fence_gate")) {
				String f = n.properties().getOrDefault("facing", "north");
				boolean gateAlongX = f.equals("north") || f.equals("south");
				return gateAlongX == (face == Facing.EAST || face == Facing.WEST);
			}
			return fullCube(n) && !o.endsWith("_leaves");
		}
		if (isPane(o) || o.endsWith("_wall")) {
			return true;
		}
		return fullCube(n) && !o.endsWith("_leaves");
	}
}
