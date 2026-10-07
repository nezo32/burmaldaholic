package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.bars;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.campfire;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.candles;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.facing;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.hanging;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.log;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.pendant;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.standing;
import static dev.nezo.burmaldaholic.worldgen.logic.pieces.OtherworldPieces.wallBanner;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;

/**
 * Piglin Parlor v2 "Gold Vault" — 33 × 16 × 33 (architecture doc §4.3 / §7.3), placed beside a bastion by
 * {@code CasinoStructures.appendParlor}: the main gate (+z) faces away from the bastion, the back door (−z)
 * faces it.
 *
 * <p>Rows: 0..1 blackstone foundation (row 1 holds the sealed lava pool under the glass pit), 2 floor,
 * 3 floor row, hall walls 3..9, stepped crimson vault ceiling at 10 / 11 / 12. Rows 13..15 stay structure
 * void (the Nether rock above is kept) except the front crown: the gold "CASINO" marquee and the two gate
 * towers with their braziers. The forecourt (z 31..32) is carved free so the facade shows even when the
 * parlor is buried in netherrack.
 *
 * <p>Every game family (§3.5): nether cashier (Piglin Dealer teller), blackjack, roulette, craps, baccarat
 * (+ Piglin Dealer), Ultimate Texas Hold'em, poker (+ Piglin Dealer), gold slots ×8, wheel, plinko; plus the
 * Piglin Moneylender's office, a barred gold vault with the loot chest and a crimson bar.
 */
public final class PiglinParlor {
	public static final String ID = "casino/parlor";
	public static final int SX = 33;
	public static final int SY = 16;
	public static final int SZ = 33;
	/** Floor block row and floor row (first air row). */
	static final int FLOOR = 2;
	static final int FR = 3;
	/** Front wall of the hall; the forecourt is z 31..32. */
	static final int FZ = 30;
	static final int CX = 16;

	private PiglinParlor() {}

	public static Piece build() {
		return build(OtherworldPalettes.PARLOR);
	}

	static Piece build(Palette p) {
		Blueprint b = Blueprint.of(ID, CasinoKind.PIGLIN_PARLOR, SX, SY, SZ, p);
		shell(b, p);
		vaultCeiling(b, p);
		roofline(b, p);
		windows(b, p);
		floor(b);
		facade(b, p);
		games(b, p);
		backRooms(b, p);
		foyer(b, p);
		decor(b, p);
		lights(b, p);
		b.anchor(CX, 6, 25);
		return b.build();
	}

	// ---- shell -------------------------------------------------------------------------------------

	private static void shell(Blueprint b, Palette p) {
		b.foundation(0, 0, SX - 1, SZ - 1, 1);
		// sealed lava pool under the glass pit (solid on every side, glass on top: it can neither flow nor ignite)
		b.fill(13, 1, 12, 19, 1, 18, BlockSpec.mc("lava", "level", "0"));
		// roof mass above the ceiling; the stepped vault is carved out of it below
		b.fill(0, 11, 0, SX - 1, 12, FZ, Role.ROOF);
		b.room(0, FLOOR, 0, SX - 1, 10, FZ)
			.frame(4)
			.door(Facing.SOUTH, CX, 5, 5)
			.door(Facing.NORTH, CX, 3, 3);
		// wall articulation: gilded band on row 8 between the pilasters, gold capitals on row 9
		for (int x = 0; x < SX; x++) {
			for (int z : new int[] {0, FZ}) {
				articulate(b, x, z);
			}
		}
		for (int z = 1; z < FZ; z++) {
			for (int x : new int[] {0, SX - 1}) {
				articulate(b, x, z);
			}
		}
		// gate arch: rounded top corners
		Material wall = b.palette().get(Role.WALL);
		b.set(CX - 2, 7, FZ, wall.stairs(Facing.EAST, true));
		b.set(CX + 2, 7, FZ, wall.stairs(Facing.WEST, true));
		b.set(CX - 1, 5, 0, wall.stairs(Facing.EAST, true));
		b.set(CX + 1, 5, 0, wall.stairs(Facing.WEST, true));
	}

	private static void articulate(Blueprint b, int x, int z) {
		BlockSpec at = b.at(x, 5, z);
		if (at == null || at.is("minecraft:air")) {
			return;
		}
		if (at.equals(b.palette().block(Role.FRAME))) {
			b.set(x, 9, z, Role.TRIM);
			b.set(x, FR, z, Role.TRIM);
		} else {
			b.set(x, 8, z, Role.ACCENT);
		}
	}

	/** Stepped vault: ring A at y 10 near the walls, B at y 11, C at y 12 over the lava pit. */
	private static void vaultCeiling(Blueprint b, Palette p) {
		Material ceil = p.get(Role.CEILING);
		b.clear(4, 10, 4, 28, 10, 26);
		b.fill(4, 11, 4, 28, 11, 26, Role.CEILING);
		b.clear(9, 10, 9, 23, 11, 21);
		b.fill(9, 12, 9, 23, 12, 21, Role.CEILING);
		stepRing(b, ceil, 4, 10, 4, 28, 26);
		stepRing(b, ceil, 9, 11, 9, 23, 21);
		// crimson beams across the vault (B level) and a gilded frame around the oculus (C level)
		for (int x = 8; x <= 24; x += 8) {
			for (int z = 5; z <= 25; z++) {
				if (z < 9 || z > 21) {
					b.set(x, 10, z, log("minecraft:crimson_stem", "z"));
				}
			}
		}
		for (int z = 7; z <= 23; z += 8) {
			for (int x = 5; x <= 27; x++) {
				if (x < 9 || x > 23) {
					b.set(x, 10, z, log("minecraft:crimson_stem", "x"));
				}
			}
		}
	}

	/**
	 * Exterior massing above the hall: battlemented outer ring (top 11 + merlons on 12), raised middle deck
	 * (top 12, sloped edge) and a stepped blackstone ziggurat with a gold ridge over the oculus (13..15).
	 * Solid blocks only above row 12, so a buried parlor never carves the rock above it.
	 */
	private static void roofline(Blueprint b, Palette p) {
		Material roof = p.get(Role.ROOF);
		for (int x = 0; x < SX; x++) {
			for (int z = 0; z <= FZ; z++) {
				if (x < 4 || x > 28 || z < 4 || z > 26) {
					b.set(x, 12, z, Blueprint.AIR);
				}
			}
		}
		for (int x = 0; x < SX; x++) {
			merlon(b, x, 0);
			merlon(b, x, FZ);
		}
		for (int z = 1; z < FZ; z++) {
			merlon(b, 0, z);
			merlon(b, SX - 1, z);
		}
		b.fill(5, 12, 5, 27, 12, 25, Role.ROOF_TRIM);
		ring(b, roof, 4, 12, 4, 28, 26, false);
		for (int[] c : new int[][] {{6, 6}, {26, 6}, {6, 24}, {26, 24}}) {
			b.set(c[0], 12, c[1], Role.TRIM);
			b.set(c[0], 13, c[1], campfire(Facing.SOUTH));
		}
		int[][] steps = {{9, 13, 9, 23, 21}, {11, 14, 11, 21, 19}, {13, 15, 13, 19, 17}};
		for (int[] st : steps) {
			b.fill(st[0] + 1, st[1], st[2] + 1, st[3] - 1, st[1], st[4] - 1, Role.ROOF);
			ring(b, roof, st[0], st[1], st[2], st[3], st[4], false);
		}
		b.fill(14, 15, 14, 18, 15, 16, Role.TRIM);
		for (int[] c : new int[][] {{9, 9}, {23, 9}, {9, 21}, {23, 21}, {11, 11}, {21, 11}, {11, 19}, {21, 19}}) {
			b.set(c[0], c[0] == 9 || c[0] == 23 ? 13 : 14, c[1], Role.TRIM);
		}
		b.set(CX, 15, 15, Role.LIGHT);
	}

	private static void merlon(Blueprint b, int x, int z) {
		if ((x + z) % 2 == 0) {
			b.set(x, 12, z, (x % 8 == 0 && (z == 0 || z == FZ)) || (z % 8 == 0 && (x == 0 || x == SX - 1)) ? b.palette().block(Role.TRIM)
				: b.palette().block(Role.ROOF));
		}
	}

	/** Stairs ring, high side towards the centre (a sloped edge); {@code top} = upside down. */
	private static void ring(Blueprint b, Material m, int x0, int y, int z0, int x1, int z1, boolean top) {
		for (int x = x0; x <= x1; x++) {
			b.set(x, y, z0, m.stairs(Facing.SOUTH, top));
			b.set(x, y, z1, m.stairs(Facing.NORTH, top));
		}
		for (int z = z0 + 1; z < z1; z++) {
			b.set(x0, y, z, m.stairs(Facing.EAST, top));
			b.set(x1, y, z, m.stairs(Facing.WEST, top));
		}
	}

	/** Orange-glass slit windows in the bays of the side and back walls (gold sills). */
	private static void windows(Blueprint b, Palette p) {
		for (int t = 2; t < SZ; t += 4) {
			if (t <= 26 && t != 14) {
				for (int x : new int[] {0, SX - 1}) {
					b.fill(x, 5, t, x, 7, t, Role.GLASS);
					b.set(x, 4, t, Role.TRIM);
				}
			}
			if (t != 14 && t != 18) {
				b.fill(t, 5, 0, t, 7, 0, Role.GLASS);
				b.set(t, 4, 0, Role.TRIM);
			}
		}
	}

	/** Upside-down stairs around the inside edge of a raised ceiling step (high side towards the walls). */
	private static void stepRing(Blueprint b, Material m, int x0, int y, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			b.set(x, y, z0, m.stairs(Facing.NORTH, true));
			b.set(x, y, z1, m.stairs(Facing.SOUTH, true));
		}
		for (int z = z0 + 1; z < z1; z++) {
			b.set(x0, y, z, m.stairs(Facing.WEST, true));
			b.set(x1, y, z, m.stairs(Facing.EAST, true));
		}
	}

	// ---- floor -------------------------------------------------------------------------------------

	private static void floor(Blueprint b) {
		// gold-rimmed glass pit over the lava pool
		b.fill(12, FLOOR, 11, 20, FLOOR, 19, Role.TRIM);
		b.fill(13, FLOOR, 12, 19, FLOOR, 18, Role.GLASS);
		// gilded inlay border of the gaming floor
		for (int x = 3; x <= 29; x++) {
			b.set(x, FLOOR, 7, Role.FLOOR_ACCENT);
			b.set(x, FLOOR, 23, Role.FLOOR_ACCENT);
		}
		for (int z = 7; z <= 23; z++) {
			b.set(3, FLOOR, z, Role.FLOOR_ACCENT);
			b.set(29, FLOOR, z, Role.FLOOR_ACCENT);
		}
		// red runner gate → pit and pit → back door, gilded edges
		for (int[] run : new int[][] {{20, FZ}, {0, 10}}) {
			b.fill(CX - 1, FR, run[0], CX + 1, FR, run[1], Role.CARPET);
			for (int z = run[0]; z <= run[1]; z++) {
				b.set(CX - 2, FLOOR, z, Role.FLOOR_ACCENT);
				b.set(CX + 2, FLOOR, z, Role.FLOOR_ACCENT);
			}
		}
	}

	// ---- facade ------------------------------------------------------------------------------------

	private static void facade(Blueprint b, Palette p) {
		Material wall = p.get(Role.WALL);
		// forecourt carved out of the rock in front of the gate
		b.fill(0, FLOOR, FZ + 1, SX - 1, FLOOR, SZ - 1, Role.PATH);
		b.clear(5, FR, FZ + 1, SX - 6, 15, SZ - 1);
		b.fill(CX - 2, FLOOR, FZ + 1, CX + 2, FLOOR, SZ - 1, Role.FLOOR_ACCENT);
		b.fill(CX - 1, FR, FZ, CX + 1, FR, FZ, Role.CARPET);
		// gate towers with sealed lava windows and brazier tops
		for (int x0 : new int[] {0, SX - 5}) {
			int x1 = x0 + 4;
			b.fill(x0, FLOOR, FZ, x1, 13, SZ - 1, Role.WALL);
			b.fill(x0, FR, FZ, x1, FR, SZ - 1, Role.WALL_BASE);
			for (int x : new int[] {x0, x1}) {
				b.fill(x, FR, SZ - 1, x, 13, SZ - 1, Role.FRAME);
				b.set(x, 9, SZ - 1, Role.TRIM);
			}
			b.fill(x0, 8, SZ - 1, x1, 8, SZ - 1, Role.ACCENT);
			b.fill(x0 + 1, 8, SZ - 1, x1 - 1, 8, SZ - 1, Role.ACCENT);
			int mx = x0 + 2;
			b.fill(mx, 4, SZ - 2, mx, 7, SZ - 2, BlockSpec.mc("lava", "level", "0"));
			b.fill(mx, 4, SZ - 1, mx, 7, SZ - 1, Role.GLASS);
			b.set(mx, 9, SZ - 1, facing("minecraft:piglin_wall_head", Facing.SOUTH));
			b.set(mx, 10, SZ - 1, Role.FRAME);
			// crenellated cap + brazier
			b.fill(x0, 14, FZ, x1, 14, SZ - 1, Role.ROOF);
			b.fill(x0, 14, SZ - 1, x1, 14, SZ - 1, Role.ACCENT);
			b.fill(x0, 12, SZ - 1, x1, 12, SZ - 1, Role.ACCENT);
			for (int x = x0; x <= x1; x += 2) {
				b.set(x, 15, SZ - 1, Role.FOUNDATION);
				b.set(x, 15, FZ, Role.FOUNDATION);
			}
			b.set(x0, 15, FZ + 1, Role.FOUNDATION);
			b.set(x1, 15, FZ + 1, Role.FOUNDATION);
			b.set(mx, 15, FZ + 1, campfire(Facing.SOUTH));
		}
		// portal: chiseled columns, gold capitals, corbelled lintel under the marquee
		for (int x : new int[] {CX - 3, CX + 3}) {
			b.fill(x, FR, FZ + 1, x, 7, FZ + 1, Role.FRAME);
			b.set(x, 8, FZ + 1, Role.TRIM);
			b.set(x, FR, FZ + 1, Role.TRIM);
		}
		for (int x = 7; x <= SX - 8; x++) {
			if (x != CX - 3 && x != CX + 3) {
				b.set(x, 8, FZ + 1, p.get(Role.ROOF_TRIM).stairs(Facing.NORTH, true));
			}
		}
		b.marquee("CASINO", CX, 14, FZ + 1);
		// banners flanking the portal (plain dyes; patterns come with the DSL banner verb)
		for (int x : new int[] {CX - 5, CX + 5}) {
			b.set(x, 7, FZ + 1, wallBanner("red", Facing.SOUTH));
		}
		for (int x : new int[] {CX - 7, CX + 7}) {
			b.set(x, 7, FZ + 1, wallBanner("black", Facing.SOUTH));
		}
		// gold braziers on the forecourt
		for (int x : new int[] {CX - 7, CX + 7}) {
			b.set(x, FR, SZ - 1, Role.FRAME);
			b.set(x, FR + 1, SZ - 1, Role.TRIM);
			b.set(x, FR + 2, SZ - 1, campfire(Facing.SOUTH));
		}
		b.set(CX - 2, 6, FZ + 1, wall.stairs(Facing.EAST, true));
		b.set(CX + 2, 6, FZ + 1, wall.stairs(Facing.WEST, true));
	}

	// ---- games -------------------------------------------------------------------------------------

	private static void games(Blueprint b, Palette p) {
		pod(b, p, Game.BLACKJACK, null, 9, 9, Facing.SOUTH);
		pod(b, p, Game.ROULETTE, null, 23, 9, Facing.SOUTH);
		pod(b, p, Game.CRAPS, null, 6, 15, Facing.EAST);
		pod(b, p, Game.BACCARAT, NpcRole.PIGLIN_DEALER, 26, 15, Facing.WEST);
		pod(b, p, Game.POKER, NpcRole.PIGLIN_DEALER, 9, 21, Facing.NORTH);
		pod(b, p, Game.UTH, null, 23, 21, Facing.NORTH);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 4), 1, FR, 16, Facing.EAST);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 4), SX - 2, FR, 13, Facing.WEST);
		machineAlcove(b, Game.WHEEL, 11);
		machineAlcove(b, Game.PLINKO, 21);
	}

	/** Table pod + a pendant lantern above the board clearance + a dealer seat when no dealer NPC stands there. */
	private static void pod(Blueprint b, Palette p, Game g, NpcRole dealer, int x, int z, Facing front) {
		b.kit(Kits.tablePod(g, dealer), x, FR, z, front);
		if (dealer == null) {
			b.set(x - front.dx(), FR, z - front.dz(), p.get(Role.COUNTER).stairs(front.opposite(), false));
		}
		pendant(b, x, FR + Kits.BOARD_CLEARANCE + 1, z, hanging(p.block(Role.LIGHT_HANGING).name()));
	}

	/** Machine against the front wall facing the hall: chiseled jambs, gold lintel, shroomlight above. */
	private static void machineAlcove(Blueprint b, Game g, int x) {
		int z = FZ - 1;
		b.game(g, x, FR, z, Facing.NORTH);
		b.keepClear(x, FR, z - 1, x, FR + 1, z - 1);
		b.keepClear(x, FR + 1, z, x, FR + 2, z);
		for (int jx : new int[] {x - 1, x + 1}) {
			b.fill(jx, FR, z, jx, 6, z, Role.FRAME);
		}
		b.fill(x - 1, 7, z, x + 1, 7, z, Role.TRIM);
		b.set(x, 6, z, Role.LIGHT);
		b.set(x - 1, FLOOR, z - 1, Role.TRIM);
		b.set(x + 1, FLOOR, z - 1, Role.TRIM);
		b.set(x, FLOOR, z - 1, Role.TRIM);
	}

	// ---- back rooms: gold vault (left), crimson bar (right), back lobby -------------------------------

	private static void backRooms(Blueprint b, Palette p) {
		// vault x 1..8, z 1..4 behind iron bars, gilded roof, loot chest among the gold
		b.fill(9, FR, 1, 9, 6, 5, Role.WALL);
		b.set(9, FR, 5, Role.FRAME);
		b.fill(1, 7, 1, 9, 7, 5, Role.ACCENT);
		for (int x = 1; x <= 8; x++) {
			b.fill(x, FR, 5, x, 6, 5, (x == 1 || x == 5) ? p.block(Role.FRAME) : bars(true));
		}
		b.fill(1, FLOOR, 1, 8, FLOOR, 4, Role.FLOOR_ACCENT);
		int[][] gold = {{1, 1}, {2, 1}, {1, 2}, {3, 1}, {7, 1}, {8, 1}, {8, 2}, {6, 1}, {1, 3}, {8, 3}};
		for (int[] g : gold) {
			b.set(g[0], FR, g[1], Role.TRIM);
		}
		b.set(1, FR + 1, 1, Role.TRIM);
		b.set(2, FR + 1, 1, BlockSpec.mc("raw_gold_block"));
		b.set(8, FR + 1, 1, Role.TRIM);
		b.set(7, FR + 1, 1, BlockSpec.mc("raw_gold_block"));
		b.set(1, FR + 2, 1, BlockSpec.mc("raw_gold_block"));
		b.set(3, FR, 2, BlockSpec.mc("raw_gold_block"));
		b.chest(CasinoLoot.Table.PIGLIN_PARLOR, 5, FR, 2, Facing.SOUTH);
		b.set(4, 6, 2, hanging("lantern"));
		b.set(4, FR, 1, BlockSpec.mc("gold_block"));

		// crimson bar x 24..30: counter on z 4, barrel shelf on the back wall, warped stools on z 5
		Material counter = p.get(Role.COUNTER);
		for (int x = 24; x <= 30; x++) {
			b.set(x, FR, 4, counter.slab("double"));
			b.set(x, FR, 1, facing("minecraft:barrel", Facing.SOUTH));
			b.set(x, FR + 1, 1, x % 2 == 0 ? facing("minecraft:barrel", Facing.SOUTH) : p.block(Role.LIGHT));
			b.set(x, FR + 2, 1, counter.slab("bottom"));
		}
		b.set(23, FR, 4, counter.stairs(Facing.EAST, false));
		b.set(25, FR + 1, 4, BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "false", "has_bottle_2", "true"));
		b.set(27, FR + 1, 4, candles("minecraft:red_candle", 3));
		b.set(29, FR + 1, 4, BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "true", "has_bottle_2", "false"));
		b.set(30, FR + 1, 4, Role.PLANTER);
		for (int x = 25; x <= 29; x += 2) {
			b.set(x, FR, 5, p.get(Role.STOOL).slab("bottom"));
		}
		// back lobby: piglin statues on gold plinths beside the back door, banners
		for (int x : new int[] {CX - 3, CX + 3}) {
			b.set(x, FR, 1, Role.FRAME);
			b.set(x, FR + 1, 1, Role.TRIM);
			b.set(x, FR + 2, 1, BlockSpec.mc("piglin_head", "rotation", "0", "powered", "false"));
		}
		for (int x : new int[] {CX - 5, CX + 5}) {
			b.set(x, 7, 1, wallBanner("red", Facing.SOUTH));
		}
	}

	// ---- foyer: moneylender's office (left), cashier booth (right) ---------------------------------

	private static void foyer(Blueprint b, Palette p) {
		Material counter = p.get(Role.COUNTER);
		// cashier booth x 26..31, z 24..29 (wall on z 23), teller behind the window
		b.fill(26, FR, 23, 31, 6, 23, Role.WALL);
		b.set(26, FR, 23, Role.FRAME);
		b.fill(26, 7, 23, 31, 7, 29, Role.ACCENT);
		for (int z = 24; z <= 29; z++) {
			if (z == 26) {
				continue;
			}
			b.set(26, FR, z, counter.slab("double"));
			b.set(26, FR + 1, z, bars(false));
			b.set(26, FR + 2, z, bars(false));
		}
		b.fill(26, 6, 24, 26, 6, 29, Role.TRIM);
		b.game(Game.NETHER_CASHIER, 26, FR, 26, Facing.WEST);
		b.keepClear(25, FR, 26, 25, FR + 1, 26);
		b.set(26, FR + 2, 26, hanging("lantern"));
		b.npc(NpcRole.PIGLIN_DEALER, 27, FR, 26, Facing.WEST);
		for (int z = 24; z <= 29; z++) {
			b.set(31, FR, z, z % 2 == 0 ? p.block(Role.TRIM) : BlockSpec.mc("barrel", "facing", "west", "open", "false"));
		}
		b.set(29, 7, 26, Role.LIGHT);

		// moneylender's office x 1..6, z 24..29: crimson desk on x 6, the Moneylender behind it
		b.fill(1, FR, 23, 6, 6, 23, Role.WALL);
		b.set(6, FR, 23, Role.FRAME);
		b.fill(1, 7, 23, 6, 7, 29, Role.ACCENT);
		b.fill(6, 6, 24, 6, 6, 29, Role.TRIM);
		for (int z = 24; z <= 29; z++) {
			b.set(6, FR, z, z == 26 ? counter.stairs(Facing.WEST, false) : counter.slab("double"));
		}
		b.set(6, FR + 1, 24, standing("lantern"));
		b.set(6, FR + 1, 28, candles("minecraft:yellow_candle", 4));
		b.set(6, FR + 1, 25, BlockSpec.mc("gold_block"));
		b.npc(NpcRole.PIGLIN_MONEYLENDER, 4, FR, 26, Facing.EAST);
		for (int z = 24; z <= 29; z++) {
			b.set(1, FR, z, z % 2 == 0 ? p.block(Role.TRIM) : BlockSpec.mc("chiseled_bookshelf", "facing", "east"));
			b.set(1, FR + 1, z, z % 2 == 0 ? BlockSpec.mc("barrel", "facing", "east", "open", "false") : p.block(Role.TRIM));
		}
		b.set(3, 7, 26, Role.LIGHT);
	}

	// ---- decor and lights --------------------------------------------------------------------------

	private static void decor(Blueprint b, Palette p) {
		// four columns at the pit corners carry the raised oculus: gold base + capital, crimson core
		for (int[] c : new int[][] {{12, 11}, {20, 11}, {12, 19}, {20, 19}}) {
			b.set(c[0], FR, c[1], Role.TRIM);
			b.fill(c[0], FR + 1, c[1], c[0], 10, c[1], log("minecraft:crimson_stem", "y"));
			b.set(c[0], 7, c[1], Role.ACCENT);
			b.set(c[0], 11, c[1], Role.TRIM);
		}
		// planters with crimson / warped fungi at the floor corners
		for (int[] c : new int[][] {{2, 8}, {30, 8}, {2, 22}, {30, 22}}) {
			b.set(c[0], FR, c[1], Role.PLANTER);
		}
		b.set(12, FR, 8, BlockSpec.mc("potted_warped_fungus"));
		b.set(20, FR, 8, BlockSpec.mc("potted_warped_fungus"));
		b.set(12, FR, 22, BlockSpec.mc("potted_warped_fungus"));
		b.set(20, FR, 22, BlockSpec.mc("potted_warped_fungus"));
		// side-wall banners between pilasters
		for (int z : new int[] {4, 8, 20, 24}) {
			b.set(1, 7, z, wallBanner(z % 4 == 0 ? "black" : "red", Facing.EAST));
			b.set(SX - 2, 7, z, wallBanner(z % 4 == 0 ? "black" : "red", Facing.WEST));
		}
	}

	private static void lights(Blueprint b, Palette p) {
		// chandelier over the lava pit: gold cross + shroomlight heart on a chain, four lanterns below
		pendant(b, CX, 8, 15, p.block(Role.LIGHT));
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			b.set(CX + d[0], 8, 15 + d[1], Role.TRIM);
			b.set(CX + d[0], 7, 15 + d[1], hanging("lantern"));
		}
		// shroomlight coffers in every vault level
		for (int x = 6; x <= 26; x += 4) {
			for (int z : new int[] {6, 24}) {
				b.set(x, 11, z, Role.LIGHT);
			}
		}
		for (int z = 10; z <= 20; z += 4) {
			b.set(6, 11, z, Role.LIGHT);
			b.set(26, 11, z, Role.LIGHT);
		}
		for (int x = 11; x <= 21; x += 5) {
			for (int z = 11; z <= 19; z += 4) {
				if (!(x == CX && z == 15)) {
					b.set(x, 12, z, Role.LIGHT);
				}
			}
		}
		for (int x = 2; x <= 30; x += 4) {
			b.set(x, 10, 2, Role.LIGHT);
			b.set(x, 10, FZ - 2, Role.LIGHT);
		}
		for (int z = 6; z <= 22; z += 4) {
			b.set(2, 10, z, Role.LIGHT);
			b.set(SX - 3, 10, z, Role.LIGHT);
		}
		// soul lanterns over the aisles
		for (int[] c : new int[][] {{CX, 4}, {CX, 26}, {4, 12}, {28, 12}, {4, 18}, {28, 18}}) {
			pendant(b, c[0], 7, c[1], hanging("soul_lantern"));
		}
	}
}
