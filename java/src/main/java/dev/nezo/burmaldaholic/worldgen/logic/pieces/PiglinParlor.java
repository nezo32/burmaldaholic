package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Ornament;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.SignFont;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.TableClearance;

/**
 * Piglin Parlor "Bastion Baroque" (casino redesign CR-2, findings/CASINO-REDESIGN.md §4.2), placed beside a bastion by
 * {@code CasinoStructures.appendParlor}. The building is the MIT-licensed "Piglin Manor" from Bygone Nether by izofar
 * ({@link PiglinManorShell}: rotated so its entrance stair faces +z, widened by two wall bays per side); this class
 * guts its interior and fits the casino into it. Envelope {@value #SX} × {@value #SY} × {@value #SZ}, axis x =
 * {@value #CX}, front = +z.
 *
 * <p>Rows: 0..1 blackstone foundation, 2 the manor platform / forecourt paving, 3 floor blocks, floor row {@value #FR}.
 * The manor: four crenellated corner towers (each crowned by a soul-fire brazier), a back and a front wing (lobby,
 * ceiling row 9) and between them the double-height central hall under the crimson roof (x 5..21, z 5..13) with the
 * tables; open verandas along both sides. In front: the entrance stair between two glass-cased lava falls, the paved
 * forecourt, and the gate — two polished-basalt obelisks with soul fires carrying the "CASINO" board.
 *
 * <p>Games: two rows of three full multi-block tables (blackjack, roulette, baccarat + Piglin Dealer; craps, poker +
 * Piglin Dealer, UTH) in the hall; the nether cashier (Piglin Dealer teller) in the back wing, the Piglin Moneylender's
 * barred vault beside it; Wheel, Plinko and four gold slots in the front lobby. Every felt is lit ≥ 12 (lantern
 * chandeliers from the roof + floor uplights). No valuable blocks (no gold, no gilded blackstone).
 */
public final class PiglinParlor {
	public static final String ID = "casino/parlor";
	public static final int SX = PiglinManorShell.SX;
	public static final int SY = PiglinManorShell.SY;
	public static final int SZ = PiglinManorShell.SZ;
	/** Floor block row and floor row (first air row). */
	static final int FLOOR = 3;
	static final int FR = 4;
	/** Front wall of the manor; the forecourt is z 18..27. */
	static final int FZ = 17;
	static final int CX = 13;
	static final int AX2 = 2 * CX;
	/** Lobby / wing ceiling row. */
	static final int WING_CEIL = 9;
	/** Row of the gate board's bottom (chaser) row. */
	static final int SIGN_Y = 9;
	/** Plane of the gate board. */
	static final int GATE_Z = 25;

	private PiglinParlor() {}

	public static Piece build() {
		return build(OtherworldPalettes.PARLOR);
	}

	static Piece build(Palette p) {
		Blueprint b = Blueprint.of(ID, CasinoKind.PIGLIN_PARLOR, SX, SY, SZ, p).compactTables(false);
		shell(b, p);
		gut(b, p);
		games(b);
		interior(b, p);
		b.uplightGames();
		exterior(b, p);
		gate(b, p);
		b.interior(2, FR, 2, SX - 3, WING_CEIL - 1, 4);
		b.interior(2, FR, 14, SX - 3, WING_CEIL - 1, 16);
		b.interior(5, FR, 5, 21, 12, 13);
		b.entry(CX, FLOOR, SZ - 1);
		b.anchor(CX, 7, 15);
		return b.build();
	}

	static final BlockSpec ROD_UP = BlockSpec.mc("end_rod", "facing", "up");

	static BlockSpec lantern(String id, boolean hanging) {
		return BlockSpec.mc(id, "hanging", Boolean.toString(hanging), "waterlogged", "false");
	}

	static BlockSpec chain() {
		return BlockSpec.mc("iron_chain", "axis", "y", "waterlogged", "false");
	}

	// ---- the manor shell --------------------------------------------------------------------------------------------

	/** Carves the envelope (the Nether rock around the manor), lays the foundation and stamps the manor. */
	static void shell(Blueprint b, Palette p) {
		b.clear(0, 0, 0, SX - 1, SY - 1, SZ - 1);
		b.fill(0, 0, 0, SX - 1, 1, SZ - 1, Role.FOUNDATION);
		PiglinManorShell.stamp(b, 0, 0, 0, s -> {
			String n = s.name();
			if (n.equals("minecraft:netherrack") || n.equals("minecraft:crimson_nylium")) {
				return p.block(Role.FOUNDATION);
			}
			return s;
		});
		// the forecourt: paved terrace on row 2, air above (whatever the manor template had there is replaced)
		b.clear(0, FLOOR, FZ + 1, SX - 1, SY - 1, SZ - 1);
		b.fill(0, 0, FZ + 1, SX - 1, 1, SZ - 1, Role.FOUNDATION);
		b.fill(0, 2, FZ + 1, SX - 1, 2, SZ - 1, Role.PATH);
		for (int y = 0; y <= 2; y++) { // the manor's platform corners outside the towers
			b.set(0, y, 0, Role.FOUNDATION);
			b.set(SX - 1, y, 0, Role.FOUNDATION);
		}
	}

	/** Strips the manor's furnishing: the hall is opened to the roof, wings and towers get a coffered ceiling. */
	static void gut(Blueprint b, Palette p) {
		// ground storey of both wings and the hall, inner partitions included
		b.clear(2, FR, 2, SX - 3, WING_CEIL - 1, 4);
		b.clear(2, FR, 14, SX - 3, WING_CEIL - 1, 16);
		b.clear(5, FR, 5, 21, 12, 13);
		// the hall's side walls stay; the stem columns at mid-length become the bay's slender columns
		for (int x : new int[] {4, SX - 5}) {
			for (int y = FR; y <= 12; y++) {
				b.set(x, y, 9, Ornament.upright(b, Role.BEAM));
			}
		}
		// the four tower-corner columns of the hall stand on the floor (polished basalt)
		for (int[] c : new int[][] {{5, 5}, {21, 5}, {5, 13}, {21, 13}}) {
			for (int y = FR; y <= WING_CEIL - 1; y++) {
				b.set(c[0], y, c[1], Ornament.upright(b, Role.COLUMN));
			}
			b.set(c[0], WING_CEIL - 1, c[1], Role.CAPITAL);
		}
		// floors
		b.fill(2, FLOOR, 2, SX - 3, FLOOR, 4, Role.FLOOR);
		b.fill(2, FLOOR, 14, SX - 3, FLOOR, 16, Role.FLOOR);
		b.fill(5, FLOOR, 5, 21, FLOOR, 13, Role.FLOOR);
		b.fill(CX - 1, FLOOR, FZ, CX + 1, FLOOR, FZ, Role.FLOOR); // the threshold, flush with the floor
		// coffered wing ceilings (they close the tower shafts too)
		Ornament.coffers(b, 2, 2, SX - 3, 4, WING_CEIL, 3, p.block(Role.LIGHT));
		Ornament.coffers(b, 2, 14, SX - 3, 16, WING_CEIL, 3, p.block(Role.LIGHT));
		for (int[] c : new int[][] {{5, 5}, {21, 5}, {5, 13}, {21, 13}}) {
			b.set(c[0], WING_CEIL, c[1], Role.CEILING);
		}
	}

	// ---- games --------------------------------------------------------------------------------------------------

	static void games(Blueprint b) {
		Kits.tablePod(Game.BLACKJACK, null).apply(b, 8, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.ROULETTE, null).apply(b, CX, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.BACCARAT, NpcRole.PIGLIN_DEALER).apply(b, 18, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.CRAPS, null).apply(b, 8, FR, 11, Facing.SOUTH);
		Kits.tablePod(Game.POKER, NpcRole.PIGLIN_DEALER).apply(b, CX, FR, 11, Facing.SOUTH);
		Kits.tablePod(Game.UTH, null).apply(b, 18, FR, 11, Facing.SOUTH);
		// the nether cashier in the back wing: a teller's booth with grilles, the Piglin Dealer behind the window
		b.game(Game.NETHER_CASHIER, CX, FR, 3, Facing.SOUTH);
		Material counter = b.palette().get(Role.COUNTER);
		for (int x : new int[] {CX - 2, CX + 2}) {
			b.set(x, FR, 3, counter.slab("double"));
			b.set(x, FR, 2, counter.slab("double"));
			b.set(x, FR + 1, 3, BlockSpec.mc("iron_bars"));
		}
		b.npc(NpcRole.PIGLIN_DEALER, CX, FR, 2, Facing.SOUTH);
		for (int x = CX - 2; x <= CX + 2; x++) {
			b.set(x, FR + 2, 3, counter.slab("top"));
			b.set(x, FR + 2, 2, counter.slab("top"));
			b.set(x, FR + 3, 3, x == CX ? b.palette().block(Role.NEON_A) : b.palette().block(Role.TRIM));
		}
		// front lobby: Wheel and Plinko in the tower bays, four gold slots flanking the door
		b.game(Game.WHEEL, 4, FR, 16, Facing.NORTH);
		b.game(Game.PLINKO, SX - 5, FR, 16, Facing.NORTH);
		Kits.slotBank(Game.SLOTS_GOLD, 2).apply(b, 9, FR, 16, Facing.NORTH);
		Kits.slotBank(Game.SLOTS_GOLD, 2).apply(b, 18, FR, 16, Facing.NORTH);
		// a lantern chandelier over every table, hanging from the roof (felt lit ≥ 12)
		for (var s : b.games()) {
			if (s.game().tableGame() == null) {
				continue;
			}
			int x = s.pos().x(), z = s.pos().z();
			int top = 13;
			while (top < SY - 1 && (b.at(x, top + 1, z) == null || b.at(x, top + 1, z).is("minecraft:air"))) {
				top++;
			}
			for (int y = FR + 5; y <= top; y++) {
				b.set(x, y, z, chain());
			}
			b.set(x, FR + 4, z, lantern("lantern", true));
			for (Facing f : Facing.values()) {
				b.set(x + f.dx(), FR + 5, z + f.dz(), BlockSpec.mc("crimson_trapdoor", "facing", f.id(), "half", "top", "open",
					"true", "powered", "false", "waterlogged", "false"));
			}
		}
	}

	// ---- interior -------------------------------------------------------------------------------------------------

	static void interior(Blueprint b, Palette p) {
		// one deep red field over the hall with a gilt border; the dealer cells stay bare floor
		for (int x = 5; x <= 21; x++) {
			for (int z = 5; z <= 13; z++) {
				BlockSpec at = b.at(x, FR, z);
				if (at != null && !at.is("minecraft:air") || dealerCell(b, x, z)) {
					continue;
				}
				boolean border = x == 6 || x == 20 || z == 13;
				if (x >= 6 && x <= 20) {
					b.set(x, FR, z, border ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
				}
			}
		}
		// lobby runner from the door, flanked by sealed lava channels under glass
		for (int z = 13; z <= FZ; z++) {
			for (int x = CX - 1; x <= CX + 1; x++) {
				b.set(x, FR, z, x == CX ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
			}
		}
		for (int x : new int[] {CX - 3, CX + 3}) {
			for (int z = 14; z <= 16; z++) {
				b.set(x, FLOOR - 1, z, BlockSpec.mc("lava", "level", "0"));
				b.set(x, FLOOR, z, BlockSpec.mc("orange_stained_glass"));
			}
		}
		// the Moneylender's barred vault (back-left tower bay): barrels, the loot chest, copper "ingots"
		Material gilt = p.get(Role.GILT);
		b.npc(NpcRole.PIGLIN_MONEYLENDER, 6, FR, 3, Facing.EAST);
		b.chest(CasinoLoot.Table.PIGLIN_PARLOR, 2, FR, 2, Facing.EAST);
		b.set(2, FR, 3, BlockSpec.mc("barrel", "facing", "up", "open", "false"));
		b.set(2, FR + 1, 3, gilt.slab("bottom"));
		b.set(3, FR, 2, BlockSpec.mc("barrel", "facing", "up", "open", "false"));
		b.set(3, FR + 1, 2, gilt.slab("bottom"));
		b.set(2, FR, 4, BlockSpec.mc("barrel", "facing", "east", "open", "false"));
		for (int z = 2; z <= 4; z++) {
			b.set(4, FR + 1, z, BlockSpec.mc("iron_bars"));
		}
		b.set(4, FR, 2, counter(p));
		b.set(4, FR, 4, counter(p));
		b.set(4, FR, 3, p.get(Role.COUNTER).slab("top")); // the lending desk
		b.set(4, FR + 1, 3, BlockSpec.mc("candle", "candles", "2", "lit", "true", "waterlogged", "false"));
		// a crimson bar in the back-right tower bay
		for (int z = 2; z <= 4; z++) {
			b.set(SX - 5, FR, z, p.get(Role.COUNTER).stairs(Facing.WEST, true));
			b.set(SX - 3, FR, z, BlockSpec.mc("barrel", "facing", "west", "open", "false"));
			b.set(SX - 3, FR + 1, z, z == 3 ? p.block(Role.LIGHT) : BlockSpec.mc("brewing_stand", "has_bottle_0", "true",
				"has_bottle_1", "true", "has_bottle_2", "false"));
		}
		b.set(SX - 5, FR + 1, 3, BlockSpec.mc("candle", "candles", "3", "lit", "true", "waterlogged", "false"));
		// planters (crimson fungus) by the slot banks
		for (int x : new int[] {6, SX - 7}) {
			Kits.pottedPlant().apply(b, x, FR, 16, Facing.NORTH);
		}
	}

	static BlockSpec counter(Palette p) {
		return p.get(Role.COUNTER).slab("double");
	}

	private static boolean dealerCell(Blueprint b, int x, int z) {
		for (var s : b.games()) {
			int[] d = TableClearance.dealer(s);
			if (d != null && d[0] == x && d[1] == z) {
				return true;
			}
		}
		return false;
	}

	// ---- exterior: entrance stair, lava falls, tower braziers, lights -----------------------------------------------

	static void exterior(Blueprint b, Palette p) {
		Material wall = p.get(Role.WALL);
		// entrance stair from the forecourt
		for (int x = CX - 1; x <= CX + 1; x++) {
			b.set(x, FLOOR, FZ + 1, wall.stairs(Facing.NORTH, false));
		}
		b.mirrorX(AX2, m -> {
			// a glass-cased lava fall either side of the gate (sealed: glass, the wall behind, a cap and a sill)
			int fx = CX - 4;
			for (int y = FLOOR; y <= 10; y++) {
				boolean end = y == FLOOR || y == 10;
				m.set(fx, y, FZ + 1, end ? p.block(Role.CAPITAL) : BlockSpec.mc("lava", "level", "0"));
				m.set(fx - 1, y, FZ + 1, end ? p.block(Role.CAPITAL) : BlockSpec.mc("orange_stained_glass"));
				m.set(fx + 1, y, FZ + 1, end ? p.block(Role.CAPITAL) : BlockSpec.mc("orange_stained_glass"));
				m.set(fx, y, FZ + 2, end ? p.block(Role.CAPITAL) : BlockSpec.mc("orange_stained_glass"));
				m.set(fx, y, FZ, end ? m.at(fx, y, FZ) : p.block(Role.WALL));
			}
			m.set(fx, 11, FZ + 1, BlockSpec.mc("soul_campfire", "facing", "south", "lit", "true", "signal_fire", "false", "waterlogged",
				"false"));
			// soul-fire braziers on both towers of this side
			for (int z : new int[] {3, 15}) {
				m.fill(2, 15, z - 1, 4, 15, z + 1, Role.FOUNDATION);
				m.set(3, 16, z, BlockSpec.mc("soul_campfire", "facing", "south", "lit", "true", "signal_fire", "false", "waterlogged",
					"false"));
			}
			// soul lanterns hanging along the veranda and at the tower feet
			for (int z : new int[] {6, 12}) {
				m.set(2, 7, z, lantern("soul_lantern", true));
			}
			m.set(2, FLOOR, 9, p.block(Role.UPLIGHT));
		});
	}

	// ---- the gate: obelisks with soul fires carrying the CASINO board ------------------------------------------------

	static void gate(Blueprint b, Palette p) {
		SignFont.Plan plan = SignFont.SMALL.line("CASINO");
		var box = Ornament.neon(b, plan, Facing.SOUTH, CX, SIGN_Y, GATE_Z, (SX - 2 - plan.width()) / 2, false);
		for (int x = box.alongFrom(); x <= box.alongTo(); x++) {
			for (int y = box.y0(); y <= box.y1(); y++) {
				b.set(x, y, GATE_Z - 1, Role.WALL); // the board's back
			}
			b.set(x, box.y1() + 1, GATE_Z, p.get(Role.WALL).slab("bottom"));
			b.set(x, box.y1() + 1, GATE_Z - 1, p.get(Role.WALL).slab("bottom"));
			b.set(x, box.y0() - 1, GATE_Z, p.get(Role.WALL).stairs(Facing.NORTH, true)); // a moulded soffit
		}
		b.mirrorX(AX2, m -> {
			int x = 0;
			m.set(x, FLOOR, GATE_Z, Role.CAPITAL);
			m.set(x, FLOOR, GATE_Z - 1, Role.CAPITAL);
			for (int y = FLOOR + 1; y <= box.y1() + 1; y++) {
				m.set(x, y, GATE_Z, Ornament.upright(m, Role.COLUMN));
				m.set(x, y, GATE_Z - 1, Ornament.upright(m, Role.COLUMN));
			}
			m.set(x, box.y1() + 2, GATE_Z, Role.CAPITAL);
			m.set(x, box.y1() + 2, GATE_Z - 1, Role.CAPITAL);
			m.set(x, box.y1() + 3, GATE_Z, BlockSpec.mc("soul_campfire", "facing", "south", "lit", "true", "signal_fire", "false",
				"waterlogged", "false"));
			// lamp bollards along the runner
			for (int z : new int[] {20, 23}) {
				m.set(9, FLOOR, z, p.block(Role.BALUSTER));
				m.set(9, FLOOR + 1, z, lantern("soul_lantern", false));
			}
			// a glowing crimson fungus "tree" in a blackstone planter on each side of the forecourt
			int tx = 4, tz = 21;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					m.set(tx + dx, FLOOR, tz + dz, dx == 0 && dz == 0 ? BlockSpec.mc("crimson_nylium")
						: p.get(Role.WALL).slab("bottom"));
				}
			}
			for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
				m.set(tx, y, tz, BlockSpec.mc("crimson_stem", "axis", "y"));
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					m.set(tx + dx, FLOOR + 4, tz + dz, dx == 0 && dz == 0 ? BlockSpec.mc("shroomlight") : BlockSpec.mc("nether_wart_block"));
				}
			}
			m.set(tx, FLOOR + 5, tz, BlockSpec.mc("nether_wart_block"));
			m.set(tx - 1, FLOOR + 3, tz, BlockSpec.mc("weeping_vines_plant"));
			m.set(tx + 1, FLOOR + 3, tz + 1, BlockSpec.mc("weeping_vines", "age", "20"));
		});
		// paving: a checker of polished blackstone and bricks, a red runner with gilt edges from the gate to the stair
		Ornament.checker(b, 1, FZ + 1, SX - 2, SZ - 1, 2, p.block(Role.FLOOR), p.block(Role.PATH));
		for (int z = FZ + 2; z <= SZ - 1; z++) {
			for (int x = CX - 2; x <= CX + 2; x++) {
				b.set(x, FLOOR, z, Math.abs(x - CX) == 2 ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
			}
		}
	}
}
