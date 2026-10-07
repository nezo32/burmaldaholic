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
 * <p>Footprint 17 × 17 × 17 (x × y × z). Front = +z: the street connector sits in the porch floor at the
 * front edge (like v0.1, {@code minecraft:street}), the floor block row is y = 0 (village terrain adaptation
 * beards the ground under it). Plan at the floor row (y = 1), door at the bottom:
 *
 * <pre>
 *  z 0   back wall (froglight band)
 *  z 1   chest  [lectern]   [Baccarat Dealer]   [lectern]   barrel      dealer row
 *  z 2   ch[BJ]ch          ch[BA]ch            ch[RO]ch                row 1 tables (players south)
 *  z 3,4 ═══════════ table aisle: players of both rows ═══════════════
 *  z 5   ch[CR]ch          ch[PK]ch            ch[UT]ch                row 2 tables (players north)
 *  z 6,7 dealer cells / cross aisle
 *  z 8         ╔═ bars [CASHIER] bars ═╗   (Croupier behind at z 7)
 *  z 9   [WH]                              [PL]      wheel / plinko
 *  z10   [$ ]        lobby, carpet         [G ]      copper / gold slots
 *  z11   [$ ]          runner              [G ]
 *  z12,13 signature decor, planters      Loan Shark
 *  z14   ───── facade: 3 × 3 door, windows, marquee "CASINO" on the porch line (y 6..12) ─────
 *  z15,16 porch / canopy / lamp posts; street jigsaw at (8, 0, 16)
 * </pre>
 */
public final class VillageAnnex {
	private VillageAnnex() {}

	public static final int SX = 17;
	public static final int SY = 17;
	public static final int SZ = 17;
	/** Floor row (first air row above the floor blocks). */
	public static final int Y = 1;
	/** Ceiling row of the hall. */
	public static final int CEILING = 7;
	/** Front wall plane. */
	public static final int FRONT = 14;

	public static String id(VillageStyle v) {
		return "casino/annex/" + v.id();
	}

	public static Piece build(VillageStyle v) {
		Style style = OverworldStyles.forVillage(v);
		Blueprint b = Blueprint.of(id(v), CasinoKind.VILLAGE_CASINO, SX, SY, SZ, style.palette());

		// clear the whole volume above the floor (grass, flowers, tree canopies), then build
		b.clear(0, 1, 0, SX - 1, SY - 1, SZ - 1);
		b.room(0, 0, 0, SX - 1, CEILING, FRONT)
			.frame(4)
			.windows(Facing.WEST, 4, 2, 2)
			.windows(Facing.EAST, 4, 2, 2)
			.done();
		// side walls: TRIM band under the eaves; back wall: froglight band behind the dealers
		b.fill(0, CEILING - 1, 0, 0, CEILING - 1, FRONT, Role.BEAM);
		b.fill(SX - 1, CEILING - 1, 0, SX - 1, CEILING - 1, FRONT, Role.BEAM);
		b.fill(0, CEILING - 1, 0, SX - 1, CEILING - 1, 0, Role.BEAM);
		for (int x = 2; x <= 14; x += 3) {
			b.set(x, 4, 0, Role.LIGHT);
		}

		// floor: carpeted gaming hall, chequered lobby floor, accent runner door → cashier
		b.fill(1, Y, 1, 15, Y, 7, Role.CARPET);
		b.fill(1, Y, 3, 15, Y, 4, Role.CARPET_ACCENT);
		for (int x = 1; x <= 15; x++) {
			for (int z = 8; z <= 13; z++) {
				if ((x + z) % 2 == 0) {
					b.set(x, 0, z, Role.FLOOR_ACCENT);
				}
			}
		}
		b.fill(7, Y, 9, 9, Y, 13, Role.CARPET_ACCENT);

		// roof (behind the facade), then the facade itself
		Roofs.roof(b, style.roof(), 0, 0, SX - 1, FRONT, CEILING + 1, SY - 1);
		if (style.roof() == RoofKind.HIP) {
			// cross gable running from the front gable back into the hip
			Roofs.gable(b, 4, 9, 12, FRONT, 12, SY - 1, false, Role.ROOF, Role.ROOF, Role.WALL);
		}
		Facades.front(b, style.facade(), new Facades.Front(0, SX - 1, FRONT, 0, 8, 3, 3, 5, SY - 1, 2, "CASINO"));

		// ---- games ----
		pod(b, Game.BLACKJACK, 3, 2, Facing.SOUTH, null);
		pod(b, Game.BACCARAT, 8, 2, Facing.SOUTH, NpcRole.BACCARAT_DEALER);
		pod(b, Game.ROULETTE, 13, 2, Facing.SOUTH, null);
		pod(b, Game.CRAPS, 3, 5, Facing.NORTH, null);
		pod(b, Game.POKER, 8, 5, Facing.NORTH, null);
		pod(b, Game.UTH, 13, 5, Facing.NORTH, null);
		b.kit(Kits.cashierCage(Game.CASHIER), 8, Y, 8, Facing.SOUTH);
		machine(b, Game.WHEEL, 1, 9, Facing.EAST);
		machine(b, Game.PLINKO, 15, 9, Facing.WEST);
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 2), 1, Y, 11, Facing.EAST);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 2), 15, Y, 10, Facing.WEST);

		// ---- dealer stations, back-of-house ----
		for (int x : new int[] {3, 13}) {
			b.set(x, Y, 1, BlockSpec.mc("lectern", "facing", "south", "has_book", "false", "powered", "false"));
		}
		b.set(15, Y, 1, BlockSpec.mc("barrel", "facing", "up", "open", "false"));
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, 1, Y, 1, Facing.SOUTH);

		// ---- lights: chandeliers over the tables, ceiling lights over the lobby ----
		for (int x : new int[] {3, 8, 13}) {
			for (int z : new int[] {2, 5}) {
				b.kit(Kits.chandelier(2), x, CEILING, z, Facing.SOUTH);
			}
		}
		for (int[] c : new int[][] {{4, 11}, {12, 11}, {8, 12}, {1, 7}, {15, 7}}) {
			b.set(c[0], CEILING, c[1], Role.LIGHT);
		}

		// ---- lobby decor ----
		style.signature().apply(b, 4, Y, 12, Facing.SOUTH);
		b.set(13, Y, 13, Role.PLANTER);
		b.set(15, Y, 13, Role.PLANTER);
		b.set(1, Y, 13, Role.PLANTER);
		b.npc(NpcRole.LOAN_SHARK, 14, Y, 12, Facing.WEST);

		// ---- markers, clearances, connector ----
		b.anchor(8, 3, 11);
		b.keepClear(7, Y, 9, 9, Y + 2, 16); // door throat → cashier
		b.jigsaw(8, 0, SZ - 1, Facing.SOUTH, "minecraft:street", "minecraft:street", "minecraft:empty", style.palette().block(Role.PATH));
		return b.build();
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
