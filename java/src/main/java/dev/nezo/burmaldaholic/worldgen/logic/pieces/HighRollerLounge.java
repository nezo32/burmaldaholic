package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.bars;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.candles;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.endRod;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.endRodDown;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.endRodUp;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.facing;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.pendant;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Facades;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.SignFont;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;

/**
 * High Roller Lounge v2 "Void Penthouse" — 25 × 14 × 25 (architecture doc §4.4 / §7.4), placed centred on
 * the highest End City tower top by {@code CasinoStructures.appendLounge}, entrance towards a random side.
 *
 * <p>Rows: 0..2 the purpur "goblet" stem that seats the floor plate on the tower roof (stepped corbels, the
 * plate overhangs the tower), 3 floor plate, 4 floor row, hall walls 4..10 (starry black-glass curtain walls
 * between purpur pillars), 11 roof with a starry skylight, 12..13 crying-obsidian crown with end rods. The
 * front (+z) is a 3-deep terrace with an end-rod railing under a magenta "CASINO" marquee.
 *
 * <p>Every game family (§3.5): cashier (+ Shulker Croupier), High-Roller blackjack / roulette / baccarat
 * (+ Baccarat Dealer) / UTH, craps, poker, netherite slots ×2, wheel, plinko; plus a shulker-box bar, an
 * amethyst fountain under the skylight and purpur sofas.
 */
public final class HighRollerLounge {
	public static final String ID = "casino/lounge";
	public static final int SX = 25;
	public static final int SY = 14;
	public static final int SZ = 25;
	static final int FLOOR = 3;
	static final int FR = 4;
	static final int CEIL = 11;
	/** Front wall of the hall; the terrace is z 21..24. */
	static final int FZ = 20;
	static final int CX = 12;

	private HighRollerLounge() {}

	public static Piece build() {
		return build(OtherworldPalettes.LOUNGE);
	}

	static Piece build(Palette p) {
		Blueprint b = Blueprint.of(ID, CasinoKind.HIGH_ROLLER, SX, SY, SZ, p);
		stem(b, p);
		hall(b, p);
		roof(b, p);
		terrace(b, p);
		games(b, p);
		interior(b, p);
		b.anchor(CX, 7, 16);
		return b.build();
	}

	// ---- structure ---------------------------------------------------------------------------------

	/** Inverted stepped pyramid of purpur under the plate: 9×9 on the tower roof, 13×13, 17×17, plate 25×25. */
	private static void stem(Blueprint b, Palette p) {
		Material f = p.get(Role.FOUNDATION);
		b.fill(8, 0, 8, 16, 2, 16, Role.FOUNDATION);
		for (int[] c : new int[][] {{8, 8}, {16, 8}, {8, 16}, {16, 16}}) {
			b.fill(c[0], 0, c[1], c[0], 2, c[1], Role.FRAME);
		}
		ring(b, 6, 1, 6, 18, 18, f, true);
		ring(b, 4, 2, 4, 20, 20, f, true);
		b.fill(5, 2, 5, 19, 2, 19, Role.FOUNDATION);
		b.fill(7, 1, 7, 17, 1, 17, Role.FOUNDATION);
		// thin under-rim of the overhang and hanging end rods at the plate corners and mid sides
		for (int x = 0; x < SX; x++) {
			b.set(x, 2, 0, f.slab("top"));
			b.set(x, 2, SZ - 1, f.slab("top"));
		}
		for (int z = 1; z < SZ - 1; z++) {
			b.set(0, 2, z, f.slab("top"));
			b.set(SX - 1, 2, z, f.slab("top"));
		}
		for (int[] c : new int[][] {{0, 0}, {24, 0}, {0, 24}, {24, 24}, {12, 0}, {0, 12}, {24, 12}, {12, 24}}) {
			b.set(c[0], 2, c[1], endRodDown());
		}
		// purpur ribs from the stem out to the rim along both axes
		for (int i = 0; i < 4; i++) {
			b.set(i, 2, CX, OtherworldPieces.log("minecraft:purpur_pillar", "x"));
			b.set(SX - 1 - i, 2, CX, OtherworldPieces.log("minecraft:purpur_pillar", "x"));
			b.set(CX, 2, i, OtherworldPieces.log("minecraft:purpur_pillar", "z"));
			b.set(CX, 2, SZ - 1 - i, OtherworldPieces.log("minecraft:purpur_pillar", "z"));
		}
		b.fill(0, FLOOR, 0, SX - 1, FLOOR, SZ - 1, Role.FOUNDATION);
	}

	/** Ring of upside-down stairs, high side outwards (corbels under an overhang). */
	private static void ring(Blueprint b, int x0, int y, int z0, int x1, int z1, Material m, boolean top) {
		for (int x = x0; x <= x1; x++) {
			b.set(x, y, z0, m.stairs(Facing.NORTH, top));
			b.set(x, y, z1, m.stairs(Facing.SOUTH, top));
		}
		for (int z = z0 + 1; z < z1; z++) {
			b.set(x0, y, z, m.stairs(Facing.WEST, top));
			b.set(x1, y, z, m.stairs(Facing.EAST, top));
		}
	}

	private static void hall(Blueprint b, Palette p) {
		b.room(0, FLOOR, 0, SX - 1, CEIL, FZ).frame(4).door(Facing.SOUTH, CX, 3, 3);
		// starry curtain walls between the pillars: black glass with white / magenta "stars"
		for (int y = FR + 1; y <= 9; y++) {
			for (int x = 1; x < SX - 1; x++) {
				for (int z : new int[] {0, FZ}) {
					curtain(b, x, y, z);
				}
			}
			for (int z = 1; z < FZ; z++) {
				for (int x : new int[] {0, SX - 1}) {
					curtain(b, x, y, z);
				}
			}
		}
		// TRIM band under the roof, end-rod sconces on the inside of every pillar
		for (int x = 0; x < SX; x++) {
			b.set(x, 10, 0, Role.TRIM);
			b.set(x, 10, FZ, Role.TRIM);
		}
		for (int z = 0; z <= FZ; z++) {
			b.set(0, 10, z, Role.TRIM);
			b.set(SX - 1, 10, z, Role.TRIM);
		}
		// solid purpur behind the cashier
		b.fill(9, FR + 1, 0, 15, 9, 0, Role.WALL);
		b.fill(CX - 1, FR + 2, 0, CX + 1, FR + 4, 0, Role.ACCENT);
	}

	private static void curtain(Blueprint b, int x, int y, int z) {
		BlockSpec at = b.at(x, y, z);
		if (at == null || !at.equals(b.palette().block(Role.WALL))) {
			return; // pillars, openings
		}
		b.set(x, y, z, star(x, y, z, b.palette()));
	}

	private static void roof(Blueprint b, Palette p) {
		// raised starry-glass pavilion over the fountain: the hall rises to row 12 there
		b.clear(8, CEIL, 6, 16, CEIL + 1, 15);
		for (int x = 7; x <= 17; x++) {
			for (int z = 5; z <= 16; z++) {
				boolean edge = x == 7 || x == 17 || z == 5 || z == 16;
				if (edge) {
					b.set(x, CEIL, z, Role.TRIM);
					boolean post = (x - 7) % 5 == 0 && (z - 5) % 11 == 0 || (x == 7 || x == 17) && (z - 5) % 4 == 0
						|| (z == 5 || z == 16) && (x - 7) % 5 == 0;
					b.set(x, CEIL + 1, z, post ? p.block(Role.FRAME) : star(x, CEIL + 1, z, p));
					b.set(x, CEIL + 2, z, p.get(Role.ROOF_TRIM).slab("bottom"));
				} else {
					b.set(x, CEIL + 2, z, star(x, CEIL + 2, z, p));
				}
			}
		}
		for (int[] c : new int[][] {{7, 5}, {17, 5}, {7, 16}, {17, 16}}) {
			b.set(c[0], CEIL + 2, c[1], endRodUp());
		}
		// crying-obsidian crown along the roof edge, end rods on every other merlon, pillar spires at the corners
		for (int x = 0; x < SX; x++) {
			crown(b, x, 0);
			crown(b, x, FZ);
		}
		for (int z = 1; z < FZ; z++) {
			crown(b, 0, z);
			crown(b, SX - 1, z);
		}
		for (int[] c : new int[][] {{0, 0}, {SX - 1, 0}, {0, FZ}, {SX - 1, FZ}}) {
			b.set(c[0], CEIL + 1, c[1], Role.FRAME);
			b.set(c[0], CEIL + 2, c[1], endRodUp());
		}
	}

	private static BlockSpec star(int x, int y, int z, Palette p) {
		int h = Math.floorMod(x * 7 + y * 13 + z * 5, 17);
		return h == 0 ? BlockSpec.mc("white_stained_glass") : h == 9 ? BlockSpec.mc("magenta_stained_glass") : p.block(Role.GLASS);
	}

	private static void crown(Blueprint b, int x, int z) {
		boolean even = (x + z) % 2 == 0;
		b.set(x, CEIL + 1, z, even ? BlockSpec.mc("crying_obsidian") : BlockSpec.mc("obsidian"));
		if (even) {
			b.set(x, CEIL + 2, z, endRodUp());
		}
	}

	private static void terrace(Blueprint b, Palette p) {
		b.fill(1, FLOOR, FZ + 1, SX - 2, FLOOR, SZ - 2, Role.PATH);
		b.fill(CX - 1, FLOOR, FZ + 1, CX + 1, FLOOR, SZ - 1, Role.FLOOR_ACCENT);
		b.clear(0, FR, FZ + 1, SX - 1, 13, SZ - 1);
		// railing: purpur posts every 3, horizontal end rods between them; open landing in the middle
		for (int x = 0; x < SX; x++) {
			if (Math.abs(x - CX) <= 1) {
				continue;
			}
			b.set(x, FR, SZ - 1, x % 3 == 0 ? p.block(Role.FRAME) : endRod(Facing.EAST));
		}
		for (int z = FZ + 1; z < SZ; z++) {
			for (int x : new int[] {0, SX - 1}) {
				b.set(x, FR, z, (z - FZ) % 3 == 0 || z == SZ - 1 ? p.block(Role.FRAME) : endRod(Facing.NORTH));
			}
		}
		for (int x : new int[] {CX - 2, CX + 2}) {
			b.set(x, FR, SZ - 1, Role.FRAME);
			b.set(x, FR + 1, SZ - 1, endRodUp());
		}
		b.set(0, FR + 1, SZ - 1, endRodUp());
		b.set(SX - 1, FR + 1, SZ - 1, endRodUp());
		// marquee over the door, hung from the facade
		// CB-3 SignFont, 24 wide on the 25-wide facade (no padding, no side bulbs), rows 7..13
		Facades.marquee(b, SignFont.SMALL.line("CASINO"), Facing.SOUTH, CX, 7, FZ + 1, 0, false);
		// door surround: amethyst jambs, planters
		for (int x : new int[] {CX - 2, CX + 2}) {
			b.fill(x, FR, FZ, x, FR + 2, FZ, Role.ACCENT);
			b.set(x + (x < CX ? -1 : 1), FR, FZ + 1, Role.PLANTER);
		}
	}

	// ---- games -------------------------------------------------------------------------------------

	private static void games(Blueprint b, Palette p) {
		pod(b, Game.BLACKJACK_HIGH_ROLLER, null, 6, 5, Facing.SOUTH);
		pod(b, Game.ROULETTE_HIGH_ROLLER, null, 18, 5, Facing.SOUTH);
		pod(b, Game.CRAPS, null, 4, 11, Facing.EAST);
		pod(b, Game.UTH_HIGH_ROLLER, null, 20, 11, Facing.WEST);
		pod(b, Game.BACCARAT_HIGH_ROLLER, NpcRole.BACCARAT_DEALER, 6, 17, Facing.NORTH);
		pod(b, Game.POKER, null, 18, 17, Facing.NORTH);
		b.kit(Kits.slotBank(Game.SLOTS_NETHERITE, 2), 2, FR, 1, Facing.SOUTH);
		alcove(b, Game.WHEEL, 7);
		alcove(b, Game.PLINKO, 17);
		// cashier window in the back wall, Shulker Croupier behind it
		Material counter = p.get(Role.COUNTER);
		for (int x = CX - 2; x <= CX + 2; x++) {
			if (x == CX) {
				continue;
			}
			b.set(x, FR, 2, counter.slab("double"));
			b.set(x, FR + 1, 2, bars(true));
			b.set(x, FR + 2, 2, bars(true));
		}
		b.fill(CX - 2, FR + 3, 2, CX + 2, FR + 3, 2, Role.TRIM);
		b.set(CX, FR + 3, 2, Role.LIGHT);
		b.game(Game.CASHIER, CX, FR, 2, Facing.SOUTH);
		b.keepClear(CX, FR, 3, CX, FR + 1, 3);
		b.npc(NpcRole.SHULKER_CROUPIER, CX, FR, 1, Facing.SOUTH);
	}

	private static void pod(Blueprint b, Game g, NpcRole dealer, int x, int z, Facing front) {
		b.kit(Kits.tablePod(g, dealer), x, FR, z, front);
		if (dealer == null) {
			b.set(x - front.dx(), FR, z - front.dz(), b.palette().get(Role.STOOL).stairs(front.opposite(), false));
		}
		pendant(b, x, FR + Kits.BOARD_CLEARANCE + 1, z, endRodDown());
	}

	private static void alcove(Blueprint b, Game g, int x) {
		int z = 1;
		b.game(g, x, FR, z, Facing.SOUTH);
		b.keepClear(x, FR, z + 1, x, FR + 1, z + 1);
		b.keepClear(x, FR + 1, z, x, FR + 2, z);
		for (int jx : new int[] {x - 1, x + 1}) {
			b.fill(jx, FR, z, jx, FR + 3, z, Role.ACCENT);
		}
		b.fill(x - 1, FR + 4, z, x + 1, FR + 4, z, Role.TRIM);
		b.set(x, FR + 3, z, Role.LIGHT);
	}

	// ---- interior ----------------------------------------------------------------------------------

	private static void interior(Blueprint b, Palette p) {
		// purple runner door → fountain → cashier, purpur inlay border
		b.fill(CX - 1, FR, 13, CX + 1, FR, FZ - 1, Role.CARPET);
		b.fill(CX - 1, FR, 3, CX + 1, FR, 8, Role.CARPET);
		for (int x = 1; x < SX - 1; x++) {
			b.set(x, FLOOR, 8, Role.WALL);
			b.set(x, FLOOR, 14, Role.WALL);
		}
		// amethyst fountain under the skylight: purpur basin, amethyst heart, buds, end-rod corner posts
		Material wall = p.get(Role.WALL);
		int fx0 = CX - 2, fz0 = 9, fx1 = CX + 2, fz1 = 13;
		for (int x = fx0; x <= fx1; x++) {
			for (int z = fz0; z <= fz1; z++) {
				boolean edge = x == fx0 || x == fx1 || z == fz0 || z == fz1;
				b.set(x, FLOOR, z, edge ? p.block(Role.FOUNDATION) : p.block(Role.FLOOR_ACCENT));
				if (edge) {
					b.set(x, FR, z, wall.slab("bottom"));
				}
			}
		}
		for (int[] c : new int[][] {{fx0, fz0}, {fx1, fz0}, {fx0, fz1}, {fx1, fz1}}) {
			b.set(c[0], FR, c[1], Role.FRAME);
			b.set(c[0], FR + 1, c[1], endRodUp());
		}
		b.fill(CX, FR, 11, CX, FR + 1, 11, Role.ACCENT);
		b.set(CX, FR + 2, 11, BlockSpec.mc("amethyst_cluster", "facing", "up", "waterlogged", "false"));
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			b.set(CX + d[0], FR, 11 + d[1], BlockSpec.mc("large_amethyst_bud", "facing", "up", "waterlogged", "false"));
		}
		for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
			b.set(CX + d[0], FR, 11 + d[1], BlockSpec.mc("small_amethyst_bud", "facing", "up", "waterlogged", "false"));
		}
		// starburst chandelier hanging from the skylight
		pendant(b, CX, 8, 11, p.block(Role.LIGHT));
		b.set(CX - 1, 8, 11, endRod(Facing.WEST));
		b.set(CX + 1, 8, 11, endRod(Facing.EAST));
		b.set(CX, 8, 10, endRod(Facing.NORTH));
		b.set(CX, 8, 12, endRod(Facing.SOUTH));
		b.set(CX, 7, 11, endRodDown());

		// shulker-box bar in the back-right corner: shelf on z 1, counter on z 3, chest behind it
		Material counter = p.get(Role.COUNTER);
		String[] boxes = {"purple_shulker_box", "magenta_shulker_box", "white_shulker_box", "purple_shulker_box"};
		for (int x = 20; x <= 23; x++) {
			b.set(x, FR, 1, facing("minecraft:" + boxes[x - 20], Facing.SOUTH));
			b.set(x, FR + 1, 1, facing("minecraft:" + boxes[(x - 19) % 4], Facing.SOUTH));
			b.set(x, FR + 2, 1, counter.slab("bottom"));
			b.set(x, FR, 3, counter.slab("double"));
		}
		b.set(20, FR + 1, 3, BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "true", "has_bottle_2", "false"));
		b.set(22, FR + 1, 3, candles("minecraft:purple_candle", 3));
		b.set(23, FR + 1, 3, BlockSpec.mc("dragon_head", "rotation", "0", "powered", "false"));
		b.chest(CasinoLoot.Table.HIGH_ROLLER, 23, FR, 2, Facing.WEST);
		for (int x = 21; x <= 23; x += 2) {
			b.set(x, FR, 4, p.get(Role.STOOL).slab("bottom"));
		}

		// purpur sofas with shulker-box side tables in the front corners
		for (int x : new int[] {1, SX - 2}) {
			Facing back = x == 1 ? Facing.WEST : Facing.EAST;
			for (int z = 17; z <= 19; z++) {
				b.set(x, FR, z, p.get(Role.STOOL).stairs(back, false));
			}
			b.set(x, FR, 16, BlockSpec.mc("magenta_shulker_box", "facing", "up"));
		}
		// ceiling coffers and wall sconces
		for (int x = 2; x <= 22; x += 4) {
			for (int z = 2; z <= 18; z += 4) {
				if (b.at(x, CEIL, z) != null && b.at(x, CEIL, z).equals(p.block(Role.CEILING))) {
					b.set(x, CEIL, z, Role.LIGHT);
				}
			}
		}
	}
}
