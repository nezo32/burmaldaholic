package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.List;

/**
 * The Nether and End casino pieces (lane CB-4): the Piglin Parlor (bastion annex, §4.3) and the High Roller
 * Lounge (End City annex, §4.4). Both are single templates placed by {@code CasinoStructures}; this class is
 * the registry {@code CasinoPack} reads, plus small decor helpers both buildings share.
 */
public final class OtherworldPieces {
	private OtherworldPieces() {}

	private static List<Piece> cache;

	/** Every Nether / End piece, built once (generation is deterministic, the result immutable). */
	public static synchronized List<Piece> all() {
		if (cache == null) {
			cache = List.of(PiglinParlor.build(), HighRollerLounge.build());
		}
		return cache;
	}

	public static Piece parlor() {
		return all().get(0);
	}

	public static Piece lounge() {
		return all().get(1);
	}

	// ---- decor block states shared by both buildings (role-independent decor, §3.2) -------------------

	static BlockSpec bars(boolean alongX) {
		return BlockSpec.mc("iron_bars", "east", Boolean.toString(alongX), "west", Boolean.toString(alongX),
			"north", Boolean.toString(!alongX), "south", Boolean.toString(!alongX), "waterlogged", "false");
	}

	static BlockSpec chain() {
		return BlockSpec.mc("iron_chain", "axis", "y", "waterlogged", "false");
	}

	/** A lantern hanging from the block above; {@code lantern} is a path ({@code soul_lantern}) or a full id. */
	static BlockSpec hanging(String lantern) {
		return BlockSpec.of(id(lantern), "hanging", "true", "waterlogged", "false");
	}

	static BlockSpec standing(String lantern) {
		return BlockSpec.of(id(lantern), "hanging", "false", "waterlogged", "false");
	}

	private static String id(String block) {
		return block.contains(":") ? block : "minecraft:" + block;
	}

	static BlockSpec endRod(Facing f) {
		return BlockSpec.mc("end_rod", "facing", f.id());
	}

	static BlockSpec endRodUp() {
		return BlockSpec.mc("end_rod", "facing", "up");
	}

	static BlockSpec endRodDown() {
		return BlockSpec.mc("end_rod", "facing", "down");
	}

	static BlockSpec campfire(Facing f) {
		return BlockSpec.mc("campfire", "facing", f.id(), "lit", "true", "signal_fire", "false", "waterlogged", "false");
	}

	static BlockSpec wallBanner(String colour, Facing f) {
		return BlockSpec.mc(colour + "_wall_banner", "facing", f.id());
	}

	static BlockSpec log(String id, String axis) {
		return BlockSpec.of(id(id), "axis", axis);
	}

	static BlockSpec facing(String id, Facing f) {
		return BlockSpec.of(id(id), "facing", f.id());
	}

	static BlockSpec candles(String id, int count) {
		return BlockSpec.of(id(id), "candles", Integer.toString(count), "lit", "true", "waterlogged", "false");
	}

	/**
	 * Pendant light: {@code lamp} at {@code (x, y, z)} hanging from a chain that runs up to the first
	 * non-air cell above (the ceiling). Pendants sit just above a table's board clearance so the in-world
	 * board stays unobstructed while the table gets light ≥ 10.
	 */
	static void pendant(Blueprint b, int x, int y, int z, BlockSpec lamp) {
		b.set(x, y, z, lamp);
		for (int cy = y + 1; cy < b.size().y(); cy++) {
			BlockSpec at = b.at(x, cy, z);
			if (at == null || !at.is("minecraft:air")) {
				break;
			}
			b.set(x, cy, z, chain());
		}
	}
}
