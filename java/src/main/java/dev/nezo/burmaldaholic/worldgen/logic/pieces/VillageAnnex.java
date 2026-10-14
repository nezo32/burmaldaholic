package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Facades;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Roofs;

/**
 * Village annex "Lucky Villager" v2 (architecture doc §4.2 / §7.5, lane CB-3): a compact but FULL casino at
 * the end of a village street, one template per {@link VillageStyle}, dressed in the Overworld style of that
 * village type. Layout id {@code casino/annex/<village style>}.
 *
 * <p>Footprint 17 × 17 × 17 (x × y × z). The hall (walls x 1..15, z 0..14) is one block narrower than the
 * template so the roof's eaves and the side posts stand out; the facade on z = 14 spans the full 17 as a
 * false front carrying the marquee. Front = +z: the street connector sits in the porch floor at the front
 * edge (like v0.1, {@code minecraft:street}); the floor block row is y = 0 (the village's terrain adaptation
 * beards the ground under it). Plan at the floor row (y = 1), door at the bottom:
 *
 * <pre>
 *  z 0   back wall (froglight band)
 *  z 1   chest [lectern]     [Baccarat Dealer]     [lectern] barrel        dealer row
 *  z 2     ch[BJ]ch          ch[BA]ch          ch[RO]ch                    row 1 tables (players south)
 *  z 3,4 ═══════════ table aisle: players of both rows ═══════════
 *  z 5     ch[CR]ch          ch[PK]ch          ch[UT]ch                    row 2 tables (players north)
 *  z 6,7 dealer cells / cross aisle (Croupier at z 7)
 *  z 8            bars bars [CASHIER] bars bars
 *  z 9   [WH]                                  [PL]     wheel / plinko
 *  z10   [$ ]     chequered lobby, runner      [G ]     copper / gold slots
 *  z11   [$ ]                                  [G ]
 *  z12,13   signature decor       Loan Shark, planters
 *  z14   ───── facade: 3 × 3 door, windows, marquee "CASINO" on the porch line (y 5..11), crown to y 16 ─────
 *  z15,16 porch / canopy / lamp posts; street jigsaw at (8, 0, 16)
 * </pre>
 */
public final class VillageAnnex {
	private VillageAnnex() {}

	public static final int SX = 17;
	public static final int SY = 25;
	/** Top row of the hall roof and the facade crown (the tower rises above it to SY - 1). */
	public static final int HALL_TOP = 16;
	/** Top row of the facade crown above the two-line marquee. */
	public static final int FACADE_TOP = 21;
	public static final int SZ = 17;
	/** Floor row (first air row above the floor blocks). */
	public static final int Y = 1;
	/** Ceiling row of the hall. */
	public static final int CEILING = 7;
	/** Front wall plane. */
	public static final int FRONT = 14;
	/** Hall side walls. */
	public static final int WEST = 1;
	public static final int EAST = 15;

	public static String id(VillageStyle v) {
		return "casino/annex/" + v.id();
	}

	public static Piece build(VillageStyle v) {
		Style style = OverworldStyles.forVillage(v);
		Blueprint b = Blueprint.of(id(v), CasinoKind.VILLAGE_CASINO, SX, SY, SZ, style.palette());

		// clear the whole volume above the floor (grass, flowers, tree canopies), then build
		b.clear(0, 1, 0, SX - 1, SY - 1, SZ - 1);
		b.room(WEST, 0, 0, EAST, CEILING, FRONT)
			.frame(4)
			.windows(Facing.WEST, 4, 2, 3)
			.windows(Facing.EAST, 4, 2, 3)
			.done();
		// BEAM band under the eaves; froglight band behind the dealers
		b.fill(WEST, CEILING - 1, 0, WEST, CEILING - 1, FRONT, Role.BEAM);
		b.fill(EAST, CEILING - 1, 0, EAST, CEILING - 1, FRONT, Role.BEAM);
		b.fill(WEST, CEILING - 1, 0, EAST, CEILING - 1, 0, Role.BEAM);
		for (int x = 3; x <= 13; x += 5) {
			b.set(x, 4, 0, Role.LIGHT);
		}
		// side posts under the eaves (x 0 / x 16), between the windows
		for (int z = 0; z <= FRONT - 2; z += 4) {
			for (int x : new int[] {0, SX - 1}) {
				b.fill(x, 0, z, x, 0, z, Role.FOUNDATION);
				b.fill(x, 1, z, x, CEILING - 1, z, Role.FRAME);
			}
		}

		// floor: carpeted gaming hall, chequered lobby floor, accent runner door → cashier
		b.fill(WEST + 1, Y, 1, EAST - 1, Y, 7, Role.CARPET);
		for (int x = WEST + 1; x <= EAST - 1; x++) { // accent border round the gaming hall
			b.set(x, Y, 1, Role.CARPET_ACCENT);
			b.set(x, Y, 7, Role.CARPET_ACCENT);
		}
		b.fill(WEST + 1, Y, 1, WEST + 1, Y, 7, Role.CARPET_ACCENT);
		b.fill(EAST - 1, Y, 1, EAST - 1, Y, 7, Role.CARPET_ACCENT);
		b.fill(WEST + 1, Y, 3, EAST - 1, Y, 4, Role.CARPET_ACCENT);
		for (int x = WEST + 1; x <= EAST - 1; x++) {
			for (int z = 8; z <= FRONT - 1; z++) {
				if ((x + z) % 2 == 0) {
					b.set(x, 0, z, Role.FLOOR_ACCENT);
				}
			}
		}
		b.fill(7, Y, 9, 9, Y, FRONT - 1, Role.CARPET_ACCENT);

		// roof with eaves over the posts, then the facade
		Roofs.roof(b, style.roof(), 0, 0, SX - 1, FRONT, CEILING, HALL_TOP);
		if (style.roof() == RoofKind.HIP) {
			// front cross gable meeting the facade's half-timbered gable, and a lit cupola on the ridge
			Roofs.gable(b, 4, 9, 12, FRONT, 12, HALL_TOP, false, Role.ROOF, Role.ROOF, Role.WALL);
			Roofs.cupola(b, 8, 7, 14, HALL_TOP);
		}
		if (style.roof() == RoofKind.STEEP_GABLE) {
			// back gable end: collar beam, king post and a pair of windows
			b.fill(4, 9, 0, 12, 9, 0, Role.BEAM);
			b.fill(8, 10, 0, 8, 13, 0, Role.FRAME);
			for (int x : new int[] {7, 9}) {
				b.fill(x, 10, 0, x, 11, 0, BlockSpec.of(style.palette().get(Role.PANE).full(), "east", "true", "west", "true",
					"north", "false", "south", "false", "waterlogged", "false"));
			}
		}
		tower(b, style);
		// lanterns hanging under the eaves between the side posts (night glow along the walls)
		for (int z = 2; z <= FRONT - 2; z += 4) {
			b.set(0, CEILING - 1, z, HANGING_LANTERN);
			b.set(SX - 1, CEILING - 1, z, HANGING_LANTERN);
		}
		Facades.front(b, style.facade(), new Facades.Front(0, SX - 1, FRONT, 0, 8, 3, 3, 5, FACADE_TOP, 2, "CASINO"));

		// ---- games ----
		pod(b, Game.BLACKJACK, 3, 2, Facing.SOUTH, null);
		pod(b, Game.BACCARAT, 8, 2, Facing.SOUTH, NpcRole.BACCARAT_DEALER);
		pod(b, Game.ROULETTE, 13, 2, Facing.SOUTH, null);
		pod(b, Game.CRAPS, 3, 5, Facing.NORTH, null);
		pod(b, Game.POKER, 8, 5, Facing.NORTH, null);
		pod(b, Game.UTH, 13, 5, Facing.NORTH, null);
		b.kit(Kits.cashierCage(Game.CASHIER), 8, Y, 8, Facing.SOUTH);
		machine(b, Game.WHEEL, WEST + 1, 9, Facing.EAST);
		machine(b, Game.PLINKO, EAST - 1, 9, Facing.WEST);
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 2), WEST + 1, Y, 11, Facing.EAST);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 2), EAST - 1, Y, 10, Facing.WEST);

		// ---- dealer stations, back-of-house ----
		for (int x : new int[] {3, 13}) {
			b.set(x, Y, 1, BlockSpec.mc("lectern", "facing", "south", "has_book", "false", "powered", "false"));
		}
		b.set(EAST - 1, Y, 1, BlockSpec.mc("barrel", "facing", "up", "open", "false"));
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, WEST + 1, Y, 1, Facing.SOUTH);
		// back bars between the dealer stations: counter, bottles (brewing stands) and plants, shelves in the wall
		for (int[] bar : new int[][] {{5, 6}, {11, 10}}) {
			b.set(bar[0], Y, 1, Role.COUNTER);
			b.set(bar[1], Y, 1, Role.COUNTER);
			b.set(bar[0], Y + 1, 1, BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "true", "has_bottle_2", "false"));
			b.set(bar[1], Y + 1, 1, Role.PLANTER);
			for (int x : bar) {
				b.fill(x, Y + 1, 0, x, Y + 2, 0, BlockSpec.mc("bookshelf"));
			}
		}
		// columns framing the cashier, rope barrier along the runner, a sofa by the door
		for (int x : new int[] {6, 10}) {
			b.fill(x, Y, 7, x, CEILING - 1, 7, Role.ACCENT);
		}
		b.set(10, Y, 10, Facades.post("minecraft:dark_oak_fence"));
		b.set(10, Y, 11, BlockSpec.mc("iron_chain", "axis", "z", "waterlogged", "false"));
		b.set(10, Y, 12, Facades.post("minecraft:dark_oak_fence"));
		for (int x : new int[] {12, 13}) {
			b.set(x, Y, FRONT - 1, style.palette().get(Role.STOOL).stairs(Facing.SOUTH, false));
		}

		// ---- lights: chandeliers over the tables, ceiling lights over the lobby ----
		for (int x : new int[] {3, 8, 13}) {
			for (int z : new int[] {2, 5}) {
				b.kit(Kits.chandelier(2), x, CEILING, z, Facing.SOUTH);
			}
		}
		for (int[] c : new int[][] {{4, 11}, {12, 11}, {8, 12}, {WEST + 1, 7}, {EAST - 1, 7}}) {
			b.set(c[0], CEILING, c[1], Role.LIGHT);
		}

		// ---- lobby decor ----
		style.signature().centrepiece(b, 5, Y, 12, Facing.SOUTH);
		b.set(WEST + 1, Y, FRONT - 1, Role.PLANTER);
		b.set(EAST - 1, Y, FRONT - 1, Role.PLANTER);
		b.set(11, Y, FRONT - 1, Role.PLANTER);
		b.npc(NpcRole.LOAN_SHARK, 12, Y, 12, Facing.WEST);

		// ---- markers, clearances, connector ----
		b.anchor(8, 3, 11);
		b.keepClear(7, Y, 9, 9, Y + 2, SZ - 1); // door throat → cashier
		b.jigsaw(8, 0, SZ - 1, Facing.SOUTH, "minecraft:street", "minecraft:street", "minecraft:empty", style.palette().block(Role.PATH));
		return b.build();
	}

	static final BlockSpec HANGING_LANTERN = BlockSpec.mc("lantern", "hanging", "true", "waterlogged", "false");

	/**
	 * Landmark tower over the front-left corner of the hall (x 1..5, z 8..12, rising from the ceiling to row 17)
	 * with the style's roof on top (rows 18..SY-1): lit windows, a glowing diamond emblem facing the street and
	 * the west, so the casino reads from across the village day and night.
	 */
	static void tower(Blueprint b, Style style) {
		int x0 = 1, x1 = 5, z0 = 8, z1 = 12, y0 = CEILING + 1, y1 = 20;
		b.clear(x0, y0, z0, x1, y1, z1);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
					if (edgeX && edgeZ) {
						b.set(x, y, z, Role.FRAME);
					} else if (edgeX || edgeZ) {
						b.set(x, y, z, y == 15 || y == y1 ? Role.BEAM : Role.WALL);
					}
				}
			}
		}
		BlockSpec pane = BlockSpec.of(style.palette().get(Role.PANE).full(), "east", "true", "west", "true", "north", "false", "south",
			"false", "waterlogged", "false");
		BlockSpec paneZ = BlockSpec.of(style.palette().get(Role.PANE).full(), "east", "false", "west", "false", "north", "true", "south",
			"true", "waterlogged", "false");
		b.fill(3, 17, z0, 3, 18, z0, pane);
		b.fill(x1, 17, 10, x1, 18, 10, paneZ);
		b.set(3, 17, 10, Role.LIGHT);
		// glowing diamond emblems (stained glass lit from behind) on the street side and the west side
		BlockSpec glass = Facades.glassOf(style.palette().block(Role.NEON_A));
		int[][] diamond = {{0, 19}, {-1, 18}, {0, 18}, {1, 18}, {-1, 17}, {0, 17}, {1, 17}, {0, 16}};
		for (int[] d : diamond) {
			b.set(3 + d[0], d[1], z1, glass);
			b.set(3 + d[0], d[1], z1 - 1, Role.LIGHT);
			b.set(x0, d[1], 10 + d[0], glass);
			b.set(x0 + 1, d[1], 10 + d[0], Role.LIGHT);
		}
		Roofs.roof(b, style.roof(), x0 - 1, z0 - 1, x1 + 1, z1 + 1, y1 + 1, SY - 1);
	}

	/**
	 * Compact table pod for the annex: the game, accent carpet on the three player cells, chairs (STOOL stairs)
	 * at both ends of the table, an optional dealer NPC behind it, the board clearance above it.
	 */
	static void pod(Blueprint b, Game g, int x, int z, Facing front, NpcRole dealer) {
		Facing across = front.rotate(1);
		b.game(g, x, Y, z, front);
		for (int t = -1; t <= 1; t++) {
			b.set(x + front.dx() + t * across.dx(), Y, z + front.dz() + t * across.dz(), Role.CARPET_ACCENT);
		}
		Material stool = b.palette().get(Role.STOOL);
		for (int t : new int[] {-1, 1}) {
			Facing back = t < 0 ? across.opposite() : across;
			b.set(x + t * across.dx(), Y, z + t * across.dz(), stool.stairs(back, false));
		}
		if (dealer != null) {
			b.npc(dealer, x - front.dx(), Y, z - front.dz(), front);
		}
		b.keepClear(x - 1, Y + 1, z - 1, x + 1, Y + Kits.BOARD_CLEARANCE, z + 1);
		b.keepClear(x + front.dx(), Y, z + front.dz(), x + front.dx(), Y + 1, z + front.dz());
	}

	/** A single machine against a side wall with a neon frame above it; its player cell stays clear. */
	static void machine(Blueprint b, Game g, int x, int z, Facing front) {
		b.game(g, x, Y, z, front);
		b.set(x, Y + 3, z, Role.NEON_B);
		b.set(x, Y + 4, z, Role.LIGHT);
		b.keepClear(x + front.dx(), Y, z + front.dz(), x + front.dx(), Y + 1, z + front.dz());
		b.keepClear(x, Y + 1, z, x, Y + 2, z);
	}
}
