package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Ornament;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Ornament.Flare;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.SignFont;
import java.util.List;

/**
 * "Grand Casino Burmaldo" — the flagship standalone casino (findings/CASINO-REDESIGN.md §3; casino redesign CR-1): a
 * Monte-Carlo body with an art-deco crown. One plan, biome palettes ({@link Variant}).
 *
 * <p>Designed as ONE building ({@link #whole}, {@value #SX} × {@value #SY} × {@value #SZ}, axis x = {@value #CX}, front
 * = +z) and cut into the three jigsaw pieces the standalone structure needs ({@link #pieces}):
 * <pre>
 *   z 0..6    bar      the bar under the VIP gallery (VIP games upstairs)                 pool bar
 *   z 7..21   floor    the double-height hall: two table pits, colonnades, side galleries,  pool floor
 *                      twin stairs, slot banks, the dome above
 *   z 22..40  entrance lobby (cashier, wheel, plinko), loggia + balcony, crown sign,     pool entrance (start)
 *                      two pavilions (duel pit, gold slots), forecourt with fountains
 * </pre>
 * Rows: foundation y 0..1, floor blocks y {@value #FLOOR_Y}, floor row {@value #ROW}; gallery / VIP floor y
 * {@value #MEZZ_Y} (row {@value #ROW2}); hall ceiling y {@value #CEIL_Y}; dome crown near the top.
 *
 * <p>Games (26): 8 hall tables, 3 VIP tables + 2 netherite slots, 6 copper + 4 gold slots, cashier, wheel, plinko. Every
 * table is a full 3 × 2 multi-block table obeying MT-DESIGN §6 R1–R6; light ≥ 10 over every game comes from glowing
 * floor tiles ({@link Blueprint#uplightGames()}). NPCs: Croupier, Baccarat Dealer, Loan Shark; one loot chest.
 */
public final class GrandCasino {
	private GrandCasino() {}

	public static final int SX = 41;
	public static final int SY = 37;
	public static final int SZ = 41;
	/** The symmetry axis x and twice it (for {@link Blueprint#mirrorX}). */
	public static final int CX = 20;
	public static final int AX2 = 2 * CX;

	public static final int FLOOR_Y = 2;
	public static final int ROW = FLOOR_Y + 1;
	/** Gallery / VIP floor block row and the floor row above it. */
	public static final int MEZZ_Y = 8;
	public static final int ROW2 = MEZZ_Y + 1;
	/** Hall, gallery and pavilion ceiling row. */
	public static final int CEIL_Y = 14;
	/** Top wall row of the pavilion towers. */
	static final int TOWER_TOP = CEIL_Y + 8;
	/** Top row of the copper mansard; the glazed drum starts above it. */
	static final int MANSARD_TOP = CEIL_Y + 5;

	// z bands (front = +z)
	static final int Z_BACK = 1;
	static final int Z_REAR_ARCADE = 7;
	static final int Z_HALL0 = 8;
	static final int Z_HALL1 = 21;
	static final int Z_INNER = 22;
	static final int Z_FRONT = 29;
	static final int Z_LOGGIA = 33;
	static final int Z_PAVILION = 34;
	static final int Z_TOWER = 28;
	// x bands
	static final int X_WALL = 1;
	static final int X_COLONNADE = 7;
	/** Dome centre. */
	static final int DOME_Z = 14;

	/** Slices (inclusive z) of the three jigsaw pieces. */
	public static final int BAR_Z0 = 0;
	public static final int BAR_Z1 = 6;
	public static final int FLOOR_Z0 = 7;
	public static final int FLOOR_Z1 = 21;
	public static final int ENTRANCE_Z0 = 22;
	public static final int ENTRANCE_Z1 = 40;

	/** Jigsaw names. */
	public static final String J_ENTRANCE_BACK = "burmaldaholic:grand/entrance_back";
	public static final String J_FLOOR_FRONT = "burmaldaholic:grand/floor_front";
	public static final String J_FLOOR_BACK = "burmaldaholic:grand/floor_back";
	public static final String J_BAR_FRONT = "burmaldaholic:grand/bar_front";
	public static final String EMPTY_POOL = "minecraft:empty";

	/**
	 * Biome looks. {@code slot} is the standalone structure (style id, {@code casino_<slot>}, biome tag
	 * {@code has_structure/casino_<slot>}) the variant generates as; the slot ids predate the redesign (Riviera takes
	 * the plains slot that Tudor had). {@link #CHERRY} has no slot yet (cherry groves are in the alpine biome list).
	 */
	public enum Variant {
		RIVIERA("tudor", GrandPalettes.RIVIERA),
		OASIS("oasis", GrandPalettes.OASIS),
		SAFARI("safari", GrandPalettes.SAFARI),
		ALPINE("alpine", GrandPalettes.ALPINE),
		ICE_PALACE("ice_palace", GrandPalettes.ICE_PALACE),
		CHERRY(null, GrandPalettes.CHERRY);

		private final String slot;
		private final Palette palette;

		Variant(String slot, Palette palette) {
			this.slot = slot;
			this.palette = palette;
		}

		public String slot() {
			return slot;
		}

		public Palette palette() {
			return palette;
		}

		/** Variants that generate in the world (have a structure slot). */
		public static List<Variant> active() {
			return List.of(RIVIERA, OASIS, SAFARI, ALPINE, ICE_PALACE);
		}

		/** The layout-id style segment: the slot, or {@code grand_<name>} for a variant without one. */
		public String style() {
			return slot != null ? slot : "grand_" + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static String id(Variant v, String piece) {
		return "casino/" + v.style() + "/" + piece;
	}

	/** The three jigsaw pieces (entrance, floor, bar) of a variant. */
	public static List<Piece> pieces(Variant v) {
		Blueprint whole = whole(v).finish();
		String pool = "burmaldaholic:" + PackFiles.stylePoolPath(v.style(), "");
		Blueprint entrance = whole.slice(id(v, "entrance"), ENTRANCE_Z0, ENTRANCE_Z1);
		entrance.entry(CX, ROW, 0);
		entrance.jigsaw(CX, FLOOR_Y, 0, Facing.NORTH, J_ENTRANCE_BACK, J_FLOOR_FRONT, pool + "floor", entrance.at(CX, FLOOR_Y, 0));
		Blueprint floor = whole.slice(id(v, "floor"), FLOOR_Z0, FLOOR_Z1);
		int fl = FLOOR_Z1 - FLOOR_Z0;
		floor.entry(CX, ROW, fl);
		floor.entry(CX, ROW, 0);
		floor.jigsaw(CX, FLOOR_Y, fl, Facing.SOUTH, J_FLOOR_FRONT, J_ENTRANCE_BACK, EMPTY_POOL, floor.at(CX, FLOOR_Y, fl));
		floor.jigsaw(CX, FLOOR_Y, 0, Facing.NORTH, J_FLOOR_BACK, J_BAR_FRONT, pool + "bar", floor.at(CX, FLOOR_Y, 0));
		Blueprint bar = whole.slice(id(v, "bar"), BAR_Z0, BAR_Z1);
		int bl = BAR_Z1 - BAR_Z0;
		bar.entry(CX, ROW, bl);
		// the VIP gallery is reached by the twin stairs in the hall piece: its gallery ends count as entries here
		bar.entry(4, ROW2, bl);
		bar.entry(AX2 - 4, ROW2, bl);
		bar.jigsaw(CX, FLOOR_Y, bl, Facing.SOUTH, J_BAR_FRONT, J_FLOOR_BACK, EMPTY_POOL, bar.at(CX, FLOOR_Y, bl));
		return List.of(entrance.build(), floor.build(), bar.build());
	}

	/** The whole building as one blueprint (id = the entrance's, which holds the casino anchor). */
	public static Blueprint whole(Variant v) {
		Blueprint b = Blueprint.of(id(v, "entrance"), OverworldCasino.kind(), SX, SY, SZ, v.palette()).compactTables(false);
		ground(b);
		shell(b);
		hall(b);
		rear(b);
		galleries(b);
		front(b);
		pavilions(b);
		roofs(b);
		forecourt(b);
		facades(b);
		games(b);
		b.uplightGames();
		registerSpaces(b);
		b.anchor(CX, 10, 26);
		return b;
	}

	// =====================================================================================================
	// helpers

	private static BlockSpec pane() {
		return BlockSpec.mc("glass_pane");
	}

	/** A wall ring (outer cells of the rectangle) for rows y0..y1. */
	static void ring(Blueprint b, int x0, int z0, int x1, int z1, int y0, int y1, BlockSpec s) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				b.set(x, y, z0, s);
				b.set(x, y, z1, s);
			}
			for (int z = z0; z <= z1; z++) {
				b.set(x0, y, z, s);
				b.set(x1, y, z, s);
			}
		}
	}

	/** An arched window: an {@link Ornament#arch} whose opening is glazed with panes from {@code sill} up. */
	static void archWindow(Blueprint b, boolean alongX, int a0, int a1, int plane, int sill, int spring, double rise) {
		Ornament.arch(b, alongX, a0, a1, plane, plane, sill, spring, rise, null);
		for (int a = a0; a <= a1; a++) {
			for (int y = sill; y <= spring + (int) Math.ceil(rise); y++) {
				int x = alongX ? a : plane, z = alongX ? plane : a;
				BlockSpec c = b.at(x, y, z);
				if (c != null && c.is("minecraft:air")) {
					b.set(x, y, z, pane());
				}
			}
		}
		// sill: a slab ledge under the window, outside and inside
		for (int a = a0; a <= a1; a++) {
			int x = alongX ? a : plane, z = alongX ? plane : a;
			b.set(x, sill - 1, z, Role.BAND);
		}
	}

	/** A vertical end rod: stacked, they read as a neon tube. */
	static final BlockSpec ROD_UP = BlockSpec.mc("end_rod", "facing", "up");

	/** One ring of a hipped roof: stairs (high side inward) or full blocks, the four hip corners in {@code rib}. */
	static void pyramidRing(Blueprint b, int x0, int z0, int x1, int z1, int y, Material roof, Material rib, boolean stairs) {
		for (int x = x0; x <= x1; x++) {
			Material m = x == x0 || x == x1 ? rib : roof;
			b.set(x, y, z0, stairs ? m.stairs(Facing.SOUTH, false) : m.block());
			b.set(x, y, z1, stairs ? m.stairs(Facing.NORTH, false) : m.block());
		}
		for (int z = z0 + 1; z < z1; z++) {
			b.set(x0, y, z, stairs ? roof.stairs(Facing.EAST, false) : roof.block());
			b.set(x1, y, z, stairs ? roof.stairs(Facing.WEST, false) : roof.block());
		}
	}

	static BlockSpec lantern(Blueprint b, boolean hanging) {
		return BlockSpec.of(b.palette().get(Role.LIGHT_HANGING).full(), "hanging", Boolean.toString(hanging), "waterlogged", "false");
	}

	static BlockSpec chain() {
		return BlockSpec.mc("iron_chain", "axis", "y", "waterlogged", "false");
	}

	// =====================================================================================================
	// ground

	static void ground(Blueprint b) {
		b.fill(0, 0, 0, SX - 1, 1, SZ - 1, Role.FOUNDATION);
		b.fill(0, FLOOR_Y, 0, SX - 1, FLOOR_Y, SZ - 1, Role.PATH);
		b.fill(X_WALL, FLOOR_Y, Z_BACK, SX - 1 - X_WALL, FLOOR_Y, Z_PAVILION, Role.FLOOR);
		// open air around the building (apron, forecourt, the loggia front): no terrain or trees stand there
		b.clear(0, ROW, Z_PAVILION + 1, SX - 1, SY - 1, SZ - 1);
		b.clear(X_COLONNADE + 1, ROW, Z_PAVILION, SX - 2 - X_COLONNADE, SY - 1, Z_PAVILION);
		b.clear(0, ROW, 0, 0, SY - 1, Z_PAVILION);
		b.clear(SX - 1, ROW, 0, SX - 1, SY - 1, Z_PAVILION);
		b.clear(0, ROW, 0, SX - 1, SY - 1, 0);
	}

	// =====================================================================================================
	// shell: outer walls of the hall block, the front block and the pavilions

	static void shell(Blueprint b) {
		BlockSpec wall = b.palette().block(Role.WALL);
		BlockSpec base = b.palette().block(Role.WALL_BASE);
		// hall + rear block: x1..39, z1..21 (front side z21 is open to the front block)
		for (int y = ROW; y <= CEIL_Y; y++) {
			BlockSpec s = y <= ROW + 1 ? base : wall;
			for (int z = Z_BACK; z <= Z_HALL1; z++) {
				b.set(X_WALL, y, z, s);
				b.set(SX - 1 - X_WALL, y, z, s);
			}
			for (int x = X_WALL; x <= SX - 1 - X_WALL; x++) {
				b.set(x, y, Z_BACK, s);
			}
		}
		// inner arcade wall z22 (lobby back), front wall z29 up to the sign
		for (int y = ROW; y <= CEIL_Y; y++) {
			for (int x = X_COLONNADE + 1; x <= SX - 2 - X_COLONNADE; x++) {
				b.set(x, y, Z_INNER, wall);
			}
		}
		for (int y = ROW; y <= MEZZ_Y + 1; y++) {
			for (int x = X_COLONNADE + 1; x <= SX - 2 - X_COLONNADE; x++) {
				b.set(x, y, Z_FRONT, y <= ROW + 1 ? base : wall);
			}
		}
		// lobby attic (z23..28 above the lobby ceiling): closed, lit from the coffer lights
		b.fill(X_COLONNADE + 1, MEZZ_Y, Z_INNER + 1, SX - 2 - X_COLONNADE, MEZZ_Y, Z_FRONT - 1, Role.CEILING);
		b.clear(X_COLONNADE + 1, MEZZ_Y + 1, Z_INNER + 1, SX - 2 - X_COLONNADE, CEIL_Y, Z_FRONT - 1);
		b.fill(X_COLONNADE + 1, MEZZ_Y + 1, Z_FRONT, SX - 2 - X_COLONNADE, CEIL_Y, Z_FRONT, wall);
		b.fill(X_COLONNADE + 1, CEIL_Y + 1, Z_INNER, SX - 2 - X_COLONNADE, CEIL_Y + 1, Z_FRONT, Role.ROOF);
		// interiors: hall + rear (x2..38, z2..21), lobby (x8..32, z23..28) are air from the floor row up
		b.clear(X_WALL + 1, ROW, Z_BACK + 1, SX - 2 - X_WALL, CEIL_Y - 1, Z_HALL1);
		b.clear(X_COLONNADE + 1, ROW, Z_INNER + 1, SX - 2 - X_COLONNADE, MEZZ_Y - 1, Z_FRONT - 1);
	}

	// =====================================================================================================
	// hall: floor patterns, colonnades, rear arcade, ceiling with oculus, chandelier, stairs, slot banks

	static void hall(Blueprint b) {
		BlockSpec floorAccent = b.palette().block(Role.FLOOR_ACCENT);
		BlockSpec red = b.palette().block(Role.CARPET);
		BlockSpec gold = BlockSpec.mc("yellow_carpet");
		// the pits (both sides): casino carpet, the CARPET colour with a gilt diamond lattice, bordered in gilt; the tables'
		// walkways (CARPET_ACCENT, dark) frame every table
		b.mirrorX(AX2, m -> {
			for (int x = 9; x <= 17; x++) {
				for (int z = 9; z <= 20; z++) {
					m.set(x, ROW, z, Math.floorMod(x + z, 4) == 0 || Math.floorMod(x - z, 4) == 0 ? gold : red);
				}
			}
			for (int x = 9; x <= 17; x++) {
				m.set(x, ROW, 9, gold);
				m.set(x, ROW, 20, gold);
			}
			for (int z = 9; z <= 20; z++) {
				m.set(9, ROW, z, gold);
				m.set(17, ROW, z, gold);
			}
		});
		// runner with gilt edges from the lobby arch to the rear arcade, broken by the medallion under the dome
		for (int z = Z_HALL0; z <= Z_HALL1; z++) {
			b.set(18, ROW, z, gold);
			b.set(22, ROW, z, gold);
			for (int x = 19; x <= 21; x++) {
				b.set(x, ROW, z, red);
			}
		}
		// medallion under the dome: a deepslate diamond ring with a glowing core
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				int md = Math.abs(dx) + Math.abs(dz);
				if (md <= 4) {
					b.set(CX + dx, ROW, DOME_Z + dz, Blueprint.AIR);
				}
			}
		}
		Ornament.diamond(b, CX, DOME_Z, 4, FLOOR_Y, floorAccent, true);
		Ornament.diamond(b, CX, DOME_Z, 3, FLOOR_Y, b.palette().block(Role.FLOOR), false);
		Ornament.diamond(b, CX, DOME_Z, 1, FLOOR_Y, b.palette().block(Role.UPLIGHT), false);
		Ornament.diamond(b, CX, DOME_Z, 2, FLOOR_Y, b.palette().block(Role.GILT), true);
		// colonnades (x7, x33): columns every 4, an architrave carrying the gallery floor
		b.mirrorX(AX2, m -> {
			for (int z = 9; z <= Z_HALL1; z += 4) {
				Ornament.column(m, X_COLONNADE, z, ROW, MEZZ_Y - 1, Flare.NONE);
			}
			m.fill(X_COLONNADE, MEZZ_Y, Z_HALL0 - 1, X_COLONNADE, MEZZ_Y, Z_HALL1, Role.BAND);
		});
		// rear arcade z7 under the VIP gallery: five arches
		for (int y = ROW; y <= MEZZ_Y - 1; y++) {
			for (int x = X_COLONNADE + 1; x <= SX - 2 - X_COLONNADE; x++) {
				b.set(x, y, Z_REAR_ARCADE, Role.WALL);
			}
		}
		b.mirrorX(AX2, m -> {
			Ornament.arch(m, true, 9, 11, Z_REAR_ARCADE, Z_REAR_ARCADE, ROW, ROW + 3, 1.5, Role.WALL);
			Ornament.arch(m, true, 13, 15, Z_REAR_ARCADE, Z_REAR_ARCADE, ROW, ROW + 3, 1.5, Role.WALL);
			Ornament.arch(m, true, 18, 22, Z_REAR_ARCADE, Z_REAR_ARCADE, ROW, ROW + 3, 2, Role.WALL);
		});
		// coffered ceiling with the oculus under the drum
		Ornament.coffers(b, X_WALL + 1, Z_BACK + 1, SX - 2 - X_WALL, Z_HALL1, CEIL_Y, 4, b.palette().block(Role.LIGHT));
		Ornament.disc(b, CX, DOME_Z, 4, CEIL_Y, Blueprint.AIR);
		Ornament.ring(b, CX, DOME_Z, 5, CEIL_Y, b.palette().block(Role.GILT));
		// twin stairs to the galleries along the side walls (climbing north), the stairwells cut out of the gallery
		b.mirrorX(AX2, m -> {
			Ornament.flight(m, 2, ROW, 14, Facing.NORTH, Facing.EAST, 2, MEZZ_Y - ROW + 1, Role.FLOOR, ROW);
		});
	}

	// =====================================================================================================
	// rear: the bar under the VIP gallery

	static void rear(Blueprint b) {
		// bar along the back wall, centred: counter z4, stools z5, bartender aisle z3, back bar z2
		Kits.bar(15).apply(b, 13, ROW, 4, Facing.SOUTH);
		b.set(28, ROW, 4, b.palette().get(Role.COUNTER).slab("double"));
		b.set(28, ROW, 3, b.palette().get(Role.COUNTER).slab("double"));
		b.set(28, ROW + 1, 4, BlockSpec.mc("candle", "candles", "3", "lit", "true", "waterlogged", "false"));
		// a mirror-glass back wall over the back bar, lit shelves
		for (int x = 13; x <= 27; x++) {
			b.set(x, ROW + 3, Z_BACK, x % 2 == 0 ? b.palette().block(Role.GILT) : BlockSpec.mc("tinted_glass"));
			b.set(x, ROW + 4, Z_BACK, Role.BAND);
		}
		// Loan Shark's corner booth (east end)
		b.set(31, ROW, 3, b.palette().get(Role.COUNTER).slab("top"));
		b.set(31, ROW + 1, 3, BlockSpec.mc("candle", "candles", "2", "lit", "true", "waterlogged", "false"));
		b.npc(NpcRole.LOAN_SHARK, 31, ROW, 4, Facing.SOUTH);
		b.set(32, ROW, 2, BlockSpec.mc("barrel", "facing", "up", "open", "false"));
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, 30, ROW, 2, Facing.SOUTH);
		// rear lounge corners: sofas facing the hall, planters
		b.mirrorX(AX2, m -> {
			Kits.vipSofa(3).apply(m, 9, ROW, 2, Facing.SOUTH);
			Kits.pottedPlant().apply(m, 2, ROW, 2, Facing.SOUTH);
		});
	}

	// =====================================================================================================
	// galleries: floors, balustrades, VIP furniture

	static void galleries(Blueprint b) {
		Material floor = b.palette().get(Role.FLOOR);
		// gallery floor: rear strip (z2..7) and both side strips (x2..7), the stairwells left open
		b.fill(X_WALL + 1, MEZZ_Y, Z_BACK + 1, SX - 2 - X_WALL, MEZZ_Y, Z_REAR_ARCADE, floor.block());
		b.mirrorX(AX2, m -> {
			m.fill(X_WALL + 1, MEZZ_Y, Z_HALL0, X_COLONNADE - 1, MEZZ_Y, Z_HALL1, floor.block());
			m.clear(2, MEZZ_Y, 9, 3, MEZZ_Y, 12);
			// balustrades: along the colonnade (looking into the hall) and round the stairwell
			Ornament.balustrade(m, X_COLONNADE, Z_HALL0, X_COLONNADE, Z_HALL1, ROW2, 4);
			Ornament.balustrade(m, 4, 9, 4, 12, ROW2, 0);
			Ornament.balustrade(m, 2, 13, 3, 13, ROW2, 0);
		});
		Ornament.balustrade(b, X_COLONNADE, Z_REAR_ARCADE, SX - 1 - X_COLONNADE, Z_REAR_ARCADE, ROW2, 4);
		// VIP sofas between the VIP tables (dealer row z2)
		b.mirrorX(AX2, m -> {
			Kits.pottedPlant().apply(m, 16, ROW2, 2, Facing.SOUTH);
			m.set(17, ROW2, 2, m.palette().get(Role.STOOL).stairs(Facing.NORTH, false));
		});
	}

	// =====================================================================================================
	// front block: lobby, loggia, balcony, crown sign

	static void front(Blueprint b) {
		BlockSpec red = b.palette().block(Role.CARPET);
		BlockSpec gold = BlockSpec.mc("yellow_carpet");
		// lobby floor: checker marble with the runner
		Ornament.checker(b, X_COLONNADE + 1, Z_INNER + 1, SX - 2 - X_COLONNADE, Z_FRONT - 1, FLOOR_Y, b.palette().block(Role.FLOOR),
			b.palette().block(Role.FLOOR_ACCENT));
		for (int z = Z_INNER; z <= Z_LOGGIA; z++) {
			b.set(18, ROW, z, gold);
			b.set(22, ROW, z, gold);
			for (int x = 19; x <= 21; x++) {
				b.set(x, ROW, z, red);
			}
		}
		// lobby ceiling: coffers with lights (they also light the closed attic above)
		Ornament.coffers(b, X_COLONNADE + 1, Z_INNER + 1, SX - 2 - X_COLONNADE, Z_FRONT - 1, MEZZ_Y, 3, b.palette().block(Role.LIGHT));
		// inner arcade z22: centre arch 5 wide, side arches 3 wide
		b.mirrorX(AX2, m -> {
			Ornament.arch(m, true, 18, 22, Z_INNER, Z_INNER, ROW, ROW + 3, 2, Role.WALL);
			Ornament.arch(m, true, 12, 14, Z_INNER, Z_INNER, ROW, ROW + 3, 1.5, Role.WALL);
			// the hall side of the inner wall above the arcade: a blind arcade of gilt panels
			for (int x = 10; x <= 16; x += 3) {
				m.fill(x, MEZZ_Y + 2, Z_INNER, x + 1, MEZZ_Y + 4, Z_INNER, Role.WALL_ALT);
			}
		});
		// front wall: the main door (arched, 5 wide) and two arched windows each side
		b.mirrorX(AX2, m -> {
			Ornament.arch(m, true, 18, 22, Z_FRONT, Z_FRONT, ROW, ROW + 3, 2, Role.WALL);
			archWindow(m, true, 10, 12, Z_FRONT, ROW + 1, ROW + 3, 1.5);
			archWindow(m, true, 14, 15, Z_FRONT, ROW + 1, ROW + 3, 1);
		});
		// loggia z30..33: marble floor, coffered ceiling (balcony deck above), arcade of paired columns on z33
		Ornament.checker(b, X_COLONNADE + 1, Z_FRONT + 1, SX - 2 - X_COLONNADE, Z_LOGGIA, FLOOR_Y, b.palette().block(Role.FLOOR),
			b.palette().block(Role.FLOOR_ACCENT));
		b.clear(X_COLONNADE + 1, ROW, Z_FRONT + 1, SX - 2 - X_COLONNADE, ROW2 - 1, Z_LOGGIA);
		b.mirrorX(AX2, m -> {
			for (int z = Z_FRONT; z <= Z_LOGGIA; z++) {
				m.set(18, ROW, z, gold);
				for (int x = 19; x <= 20; x++) {
					m.set(x, ROW, z, red);
				}
			}
		});
		int deck = ROW2;
		b.fill(X_COLONNADE + 1, deck, Z_FRONT + 1, SX - 2 - X_COLONNADE, deck, Z_LOGGIA, Role.FLOOR);
		b.fill(X_COLONNADE + 1, ROW2 - 1, Z_LOGGIA, SX - 2 - X_COLONNADE, ROW2 - 1, Z_LOGGIA, Role.WALL);
		b.mirrorX(AX2, m -> {
			for (int x : new int[] {8, 12, 16, 17}) {
				Ornament.column(m, x, Z_LOGGIA, ROW, ROW + 3, Flare.NONE);
			}
			Ornament.arch(m, true, 9, 11, Z_LOGGIA, Z_LOGGIA, ROW, ROW + 4, 1.5, Role.WALL);
			Ornament.arch(m, true, 13, 15, Z_LOGGIA, Z_LOGGIA, ROW, ROW + 4, 1.5, Role.WALL);
			Ornament.arch(m, true, 18, 22, Z_LOGGIA, Z_LOGGIA, ROW, ROW + 4, 2, Role.WALL);
			// a lantern pair hanging in every bay
			for (int x : new int[] {10, 14, 19}) {
				m.set(x, ROW2 - 1, Z_LOGGIA - 2, lantern(m, true));
			}
		});
		// the marquee façade over the loggia (the sign reads from the diagonals too: only one block behind the towers):
		// a solid attic behind it, a gilt entablature on the arcade, the CASINO board in neon froglight with a bulb chaser
		b.fill(X_COLONNADE + 1, ROW2, Z_FRONT, SX - 2 - X_COLONNADE, ROW2 + 7, Z_LOGGIA - 1, Role.WALL);
		for (int x = X_COLONNADE + 1; x <= SX - 2 - X_COLONNADE; x++) {
			b.set(x, ROW2, Z_LOGGIA, Role.BAND);
			b.set(x, ROW2, Z_LOGGIA + 1, b.palette().get(Role.CORNICE).stairs(Facing.NORTH, true));
		}
		SignFont.Plan plan = SignFont.SMALL.line("CASINO");
		Ornament.neon(b, plan, Facing.SOUTH, CX, ROW2 + 1, Z_LOGGIA, 1, false);
		// a proud gilt frame round the board: a hood of upside-down stairs on top, gilt jambs at both ends
		for (int x = X_COLONNADE + 1; x <= SX - 2 - X_COLONNADE; x++) {
			b.set(x, ROW2 + 8, Z_LOGGIA + 1, b.palette().get(Role.GILT).stairs(Facing.NORTH, true));
		}
		for (int y = ROW2 + 1; y <= ROW2 + 7; y++) {
			b.set(X_COLONNADE + 1, y, Z_LOGGIA + 1, b.palette().get(Role.GILT).block());
			b.set(SX - 2 - X_COLONNADE, y, Z_LOGGIA + 1, b.palette().get(Role.GILT).block());
		}
		crown(b);
	}

	/**
	 * Art-deco stepped crown above the sign (two blocks thick): four receding tiers of WALL fluted with vertical GILT
	 * strips on the axis rhythm, cornice-slab treads, a BULB on every step corner, a CAPITAL keystone fin with end-rod
	 * rays on the top.
	 */
	static void crown(Blueprint b) {
		int y = ROW2 + 8; // first row above the sign board
		int[][] tiers = {{9, 2}, {12, 2}, {15, 2}, {18, 1}}; // {x0, rows} per tier, symmetric about CX
		Material gilt = b.palette().get(Role.GILT);
		for (int[] t : tiers) {
			int x0 = t[0], x1 = AX2 - t[0];
			for (int r = 0; r < t[1]; r++, y++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1;
					boolean flute = Math.floorMod(x - CX, 3) == 0;
					BlockSpec face = edge ? gilt.block() : flute ? b.palette().block(Role.NEON_A) : b.palette().block(Role.SIGN_BACK);
					b.set(x, y, Z_LOGGIA, face);
					b.set(x, y, Z_LOGGIA - 1, face); // double-faced: the crown glows toward the back too
				}
			}
			for (int x = x0; x <= x1; x++) {
				b.set(x, y, Z_LOGGIA, gilt.slab("bottom"));
				b.set(x, y, Z_LOGGIA - 1, gilt.slab("bottom"));
			}
			b.set(x0, y, Z_LOGGIA, b.palette().block(Role.BULB));
			b.set(x1, y, Z_LOGGIA, b.palette().block(Role.BULB));
		}
		// keystone fin and rays
		b.set(CX, y, Z_LOGGIA, Role.CAPITAL);
		b.set(CX, y, Z_LOGGIA - 1, Role.WALL);
		b.set(CX, y + 1, Z_LOGGIA, ROD_UP);
		b.set(CX, y + 2, Z_LOGGIA, ROD_UP);
		for (int s : new int[] {-1, 1}) {
			b.set(CX + 2 * s, y, Z_LOGGIA, ROD_UP);
		}
	}

	// =====================================================================================================
	// pavilions (x1..7 and x33..39, z22..34): tall towers at the front with copper pyramids and belvederes

	static void pavilions(Blueprint b) {
		b.mirrorX(AX2, m -> {
			BlockSpec wall = m.palette().block(Role.WALL);
			BlockSpec base = m.palette().block(Role.WALL_BASE);
			int x0 = X_WALL, x1 = X_COLONNADE, z0 = Z_INNER, z1 = Z_PAVILION;
			int towerTop = TOWER_TOP;
			for (int y = ROW; y <= towerTop; y++) {
				BlockSpec s = y <= ROW + 1 ? base : wall;
				for (int x = x0; x <= x1; x++) {
					m.set(x, y, z1, s);
					if (y <= CEIL_Y + 1) {
						m.set(x, y, z0, s);
					}
					if (y > CEIL_Y) {
						m.set(x, y, Z_TOWER, s);
					}
				}
				for (int z = z0; z <= z1; z++) {
					if (z >= Z_TOWER || y <= CEIL_Y + 1) {
						m.set(x0, y, z, s);
						m.set(x1, y, z, s);
					}
				}
			}
			// interior: rows 3..13, ceiling y14 (coffers), floor checker
			m.clear(x0 + 1, ROW, z0 + 1, x1 - 1, CEIL_Y - 1, z1 - 1);
			Ornament.checker(m, x0 + 1, z0 + 1, x1 - 1, z1 - 1, FLOOR_Y, m.palette().block(Role.FLOOR), m.palette().block(Role.FLOOR_ACCENT));
			Ornament.coffers(m, x0 + 1, z0 + 1, x1 - 1, z1 - 1, CEIL_Y, 3, m.palette().block(Role.LIGHT));
			m.fill(x0 + 1, CEIL_Y + 1, z0 + 1, x1 - 1, CEIL_Y + 1, Z_TOWER - 1, Role.ROOF);
			m.clear(x0 + 1, CEIL_Y + 1, Z_TOWER + 1, x1 - 1, towerTop, z1 - 1);
			m.fill(x0 + 1, CEIL_Y + 1, Z_TOWER + 1, x1 - 1, CEIL_Y + 1, z1 - 1, Role.CEILING);
			m.fill(x0 + 1, towerTop, Z_TOWER + 1, x1 - 1, towerTop, z1 - 1, Role.CEILING);
			m.set(4, towerTop - 1, 31, lantern(m, true));
			// door from the lobby
			m.clear(x1, ROW, Z_TOWER, x1, ROW + 2, Z_TOWER);
			m.set(x1, ROW + 3, Z_TOWER, Role.CAPITAL);
			// front: tall arched window with a balconette, the ♦ medallion above
			archWindow(m, true, 3, 5, z1, ROW + 2, ROW + 5, 1.5);
			Ornament.balustrade(m, 3, z1 + 1, 5, z1 + 1, ROW + 2, 0);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -2; dy <= 2; dy++) {
					if (Math.abs(dx) + Math.abs(dy) <= 2) {
						m.set(4 + dx, ROW + 9 + dy, z1, m.palette().block(Role.NEON_A));
					}
				}
			}
			// side window (outer wall) and back windows
			archWindow(m, false, 30, 32, x0, ROW + 2, ROW + 5, 1.5);
			archWindow(m, false, 24, 26, x0, ROW + 2, ROW + 5, 1.5);
			// upper storey: an arched window on the front and the outer side, the ♦ medallion between the storeys
			archWindow(m, true, 3, 5, z1, CEIL_Y + 3, CEIL_Y + 5, 1.5);
			archWindow(m, false, 30, 32, x0, CEIL_Y + 3, CEIL_Y + 5, 1.5);
			archWindow(m, true, 3, 5, Z_TOWER, CEIL_Y + 3, CEIL_Y + 5, 1.5);
			archWindow(m, false, 30, 32, x1, CEIL_Y + 3, CEIL_Y + 5, 1.5);
			Ornament.stringCourse(m, x0, z1, x1, z1, CEIL_Y + 1, Facing.SOUTH, Role.CORNICE, true);
			// cornice ring round the tower, a steep copper roof with fresh-copper hips, glazed belvedere, finial
			Ornament.cornice(m, x0, Z_TOWER, x1, z1, towerTop + 1);
			m.fill(x0, towerTop + 1, Z_TOWER, x1, towerTop + 1, z1, Role.BAND);
			for (int[] c : new int[][] {{x0 - 1, Z_TOWER - 1}, {x1 + 1, Z_TOWER - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}}) {
				m.set(c[0], towerTop + 2, c[1], lantern(m, false));
			}
			Material dome = m.palette().get(Role.DOME), rib = m.palette().get(Role.RIB);
			int ry = towerTop + 2;
			pyramidRing(m, x0, Z_TOWER, x1, z1, ry, dome, rib, true);
			m.fill(x0 + 1, ry, Z_TOWER + 1, x1 - 1, ry, z1 - 1, dome.block());
			pyramidRing(m, x0 + 1, Z_TOWER + 1, x1 - 1, z1 - 1, ry + 1, dome, rib, false);
			m.fill(x0 + 2, ry + 1, Z_TOWER + 2, x1 - 2, ry + 1, z1 - 2, dome.block());
			pyramidRing(m, x0 + 1, Z_TOWER + 1, x1 - 1, z1 - 1, ry + 2, dome, rib, true);
			m.fill(x0 + 2, ry + 2, Z_TOWER + 2, x1 - 2, ry + 2, z1 - 2, dome.block());
			int by = ry + 3;
			for (int x = 3; x <= 5; x++) {
				for (int z = 30; z <= 32; z++) {
					boolean corner = (x == 3 || x == 5) && (z == 30 || z == 32);
					for (int yy = by; yy <= by + 1; yy++) {
						m.set(x, yy, z, corner ? Ornament.upright(m, Role.COLUMN) : m.palette().block(Role.GLASS));
					}
					m.set(x, by + 2, z, corner ? rib.slab("bottom") : dome.slab("bottom"));
				}
			}
			m.set(4, by, 31, m.palette().block(Role.LIGHT));
			m.set(4, by + 1, 31, m.palette().block(Role.GLASS));
			m.set(4, by + 2, 31, rib.block());
			m.set(4, by + 3, 31, BlockSpec.mc("lightning_rod", "facing", "up", "powered", "false", "waterlogged", "false"));
			// back part (z22..27): cornice at the hall's height
			Ornament.corniceRun(m, x0, z0, x0, Z_TOWER - 1, CEIL_Y + 2, Facing.WEST);
		});
		// west pavilion: the duel pit; east pavilion: the gold slot bank (games() places the games)
		Kits.duelPit().apply(b, 4, ROW, 30, Facing.SOUTH);
	}

	// =====================================================================================================
	// roofs: entablature + copper mansard round the hall block, the drum and the ribbed dome

	static void roofs(Blueprint b) {
		int deck = CEIL_Y + 1;
		Material roof = b.palette().get(Role.DOME);
		// roof deck over the hall block (the light well round the drum)
		b.fill(X_WALL + 1, deck, Z_BACK + 1, SX - 2 - X_WALL, deck, Z_HALL1, Role.ROOF);
		// entablature on the side and back walls (band y14, frieze y15, cornice y16)
		b.mirrorX(AX2, m -> {
			Ornament.entablature(m, X_WALL, Z_BACK, X_WALL, Z_HALL1, CEIL_Y, Facing.WEST);
			Ornament.entablature(m, X_WALL, Z_BACK, CX, Z_BACK, CEIL_Y, Facing.NORTH);
		});
		// copper mansard strip (insets 1..6 from the walls) over the galleries: stair, steep full row, stair, flat cap
		b.mirrorX(AX2, m -> {
			for (int k = 1; k <= 6; k++) {
				int x = X_WALL + k, z = Z_BACK + k;
				int top = Math.min(CEIL_Y + 2 + k, MANSARD_TOP);
				for (int y = deck + 1; y <= top; y++) {
					BlockSpec s = y < top ? roof.block() : k == 1 || k == 3 ? null : roof.block();
					for (int zz = z; zz <= Z_HALL1; zz++) {
						m.set(x, y, zz, s != null ? s : roof.stairs(Facing.EAST, false));
					}
					for (int xx = x; xx <= CX; xx++) {
						m.set(xx, y, z, s != null ? s : roof.stairs(Facing.SOUTH, false));
					}
				}
			}
			// œil-de-bœuf dormers on the side slope
			for (int z = 5; z <= 20; z += 5) {
				m.set(X_WALL + 2, MANSARD_TOP - 1, z, m.palette().block(Role.GLASS));
				m.set(X_WALL + 1, MANSARD_TOP - 1, z, m.palette().get(Role.RIB).stairs(Facing.EAST, true));
			}
			for (int x = 6; x <= CX - 4; x += 5) {
				m.set(x, MANSARD_TOP - 1, Z_BACK + 2, m.palette().block(Role.GLASS));
				m.set(x, MANSARD_TOP - 1, Z_BACK + 1, m.palette().get(Role.RIB).stairs(Facing.SOUTH, true));
			}
		});
		// drum: a solid attic cylinder out of the light well, the glazed drum above the mansard, cornice, ribbed dome
		for (int y = deck; y < MANSARD_TOP; y++) {
			Ornament.ring(b, CX, DOME_Z, 7, y, b.palette().block(Role.WALL));
			Ornament.disc(b, CX, DOME_Z, 6, y, Blueprint.AIR);
		}
		Ornament.disc(b, CX, DOME_Z, 7, MANSARD_TOP, b.palette().block(Role.BAND));
		Ornament.disc(b, CX, DOME_Z, 5, MANSARD_TOP, Blueprint.AIR);
		Ornament.ring(b, CX, DOME_Z, 7, MANSARD_TOP + 1, b.palette().get(Role.RAIL).slab("bottom"));
		Ornament.disc(b, CX, DOME_Z, 4, CEIL_Y, Blueprint.AIR);
		int drum0 = MANSARD_TOP + 1;
		Ornament.drum(b, CX, DOME_Z, 6, drum0, drum0 + 3);
		Ornament.ring(b, CX, DOME_Z, 7, drum0 + 4, b.palette().get(Role.CORNICE).slab("bottom"));
		Ornament.ring(b, CX, DOME_Z, 6, drum0 + 4, b.palette().block(Role.BAND));
		int crown = Ornament.dome(b, CX, DOME_Z, 6, drum0 + 5, 6);
		Ornament.lantern(b, CX, crown + 1, DOME_Z);
		// light inside the drum: the glass ring glows at night
		for (int[] c : new int[][] {{CX - 5, DOME_Z}, {CX + 5, DOME_Z}, {CX, DOME_Z - 5}, {CX, DOME_Z + 5}}) {
			b.set(c[0], MANSARD_TOP, c[1], b.palette().block(Role.LIGHT));
		}
		// grand chandelier hanging on a chain from the dome down into the hall
		for (int y = MEZZ_Y + 3; y <= crown - 1; y++) {
			b.set(CX, y, DOME_Z, chain());
		}
		Kits.grandChandelier(1).apply(b, CX, MEZZ_Y + 3, DOME_Z, Facing.SOUTH);
		// front block roof (over the lobby attic, behind the crown)
		b.fill(X_COLONNADE + 1, CEIL_Y + 2, Z_INNER, SX - 2 - X_COLONNADE, CEIL_Y + 2, Z_FRONT - 2, b.palette().get(Role.ROOF).slab("bottom"));
	}

	// =====================================================================================================
	// forecourt (z35..40): paving with a sunburst, fountains, lamp posts, planters

	static void forecourt(Blueprint b) {
		int z0 = Z_PAVILION + 1;
		Ornament.sunburst(b, CX, Z_PAVILION, FLOOR_Y, 7, 2, 6.5, -60, 60, b.palette().block(Role.FLOOR_ACCENT));
		for (int z = z0; z < SZ; z++) {
			for (int x = 19; x <= 21; x++) {
				b.set(x, ROW, z, b.palette().block(Role.CARPET));
			}
		}
		b.mirrorX(AX2, m -> {
			// fountain: quartz rim, still water lit from below, a column with a lantern in the middle
			double fx = 5, fz = 37.5;
			for (int[] c : Ornament.discCells(fx, fz, 2)) {
				m.set(c[0], FLOOR_Y - 1, c[1], m.palette().block(Role.WATER_BASIN));
			}
			for (int[] c : Ornament.ringCells(fx, fz, 2)) {
				m.set(c[0], ROW, c[1], m.palette().get(Role.BASIN_RIM).slab("bottom"));
				m.set(c[0], FLOOR_Y, c[1], m.palette().block(Role.WATER_BASIN));
			}
			for (int[] c : Ornament.discCells(fx, fz, 1)) {
				m.set(c[0], FLOOR_Y, c[1], BlockSpec.mc("water", "level", "0"));
				m.set(c[0], FLOOR_Y - 1, c[1], BlockSpec.mc("sea_lantern"));
			}
			Ornament.column(m, 5, 37, FLOOR_Y, ROW + 1, Flare.NONE);
			m.set(5, ROW + 2, 37, lantern(m, false));
			// lamp post allée, planters at the pavilion feet
			Kits.lampPost(4).apply(m, 11, ROW, 37, Facing.SOUTH);
			Kits.planter().apply(m, 9, ROW, z0 + 1, Facing.SOUTH);
			m.set(16, ROW, 39, BlockSpec.mc("polished_deepslate_wall"));
			m.set(16, ROW + 1, 39, lantern(m, false));
			// clipped hedges along the forecourt sides, topiary on posts flanking the runner
			BlockSpec hedge = BlockSpec.mc("azalea_leaves", "distance", "1", "persistent", "true", "waterlogged", "false");
			for (int z = Z_PAVILION + 1; z < SZ - 1; z++) {
				m.set(0, ROW, z, hedge);
			}
			m.set(14, ROW, 36, BlockSpec.mc("spruce_fence"));
			m.set(14, ROW + 1, 36, BlockSpec.mc("flowering_azalea_leaves", "distance", "1", "persistent", "true", "waterlogged", "false"));
			m.set(14, ROW + 2, 36, hedge);
			// the terrace edge steps down to the terrain
			for (int x = 0; x <= CX; x++) {
				m.set(x, FLOOR_Y, SZ - 1, m.palette().get(Role.PLINTH).stairs(Facing.NORTH, false));
			}
		});
	}

	// =====================================================================================================
	// facades: pilasters with neon end-rod lines, sconces, windows on the sides and the back

	static void facades(Blueprint b) {
		b.mirrorX(AX2, m -> {
			// side walls (x1): pilasters at x0 every 5 from z1, windows between, the pavilion side continues the rhythm
			for (int z = Z_BACK; z <= Z_PAVILION; z += 5) {
				int zz = Math.min(z, Z_PAVILION);
				Ornament.pilaster(m, 0, zz, ROW, CEIL_Y - 1);
				for (int y = ROW + 2; y <= ROW + 8; y++) {
					if (zz - 1 >= 0) {
						m.set(0, y, zz - 1, ROD_UP);
					}
					if (zz + 1 < SZ) {
						m.set(0, y, zz + 1, ROD_UP);
					}
				}
			}
			for (int z = Z_BACK + 2; z <= Z_HALL1 - 2; z += 5) {
				archWindow(m, false, z, z + 2, X_WALL, ROW2 + 1, ROW2 + 3, 1.5);
				for (int a = z; a <= z + 2; a++) {
					m.set(X_WALL, ROW + 1, a, pane());
					m.set(X_WALL, ROW + 2, a, pane());
				}
				// sconce between the windows
				m.set(0, MEZZ_Y, z + 1, lantern(m, false));
				m.set(0, MEZZ_Y - 1, z + 1, m.palette().get(Role.CORNICE).slab("top"));
			}
			// back wall (z1): pilasters at z0, windows between, the gallery lit behind
			for (int x = X_WALL; x <= CX; x += 5) {
				Ornament.pilaster(m, x, 0, ROW, CEIL_Y - 1);
				for (int y = ROW + 2; y <= ROW + 8; y++) {
					if (x - 1 >= 0) {
						m.set(x - 1, y, 0, ROD_UP);
					}
					m.set(x + 1, y, 0, ROD_UP);
				}
			}
			for (int x = X_WALL + 2; x <= CX - 3; x += 5) {
				archWindow(m, true, x, x + 2, Z_BACK, ROW2 + 1, ROW2 + 3, 1.5);
				m.set(x + 1, MEZZ_Y, 0, lantern(m, false));
				m.set(x + 1, MEZZ_Y - 1, 0, m.palette().get(Role.CORNICE).slab("top"));
			}
			// front corners of the pavilions: pilasters with neon lines facing the forecourt
			for (int x : new int[] {X_WALL, X_COLONNADE}) {
				Ornament.pilaster(m, x, Z_PAVILION + 1, ROW, CEIL_Y + 3);
			}
		});
		// a service door in the back wall (closed, decorative)
		b.set(CX, ROW, Z_BACK, BlockSpec.mc("dark_oak_door", "facing", "north", "half", "lower", "hinge", "left", "open", "false",
			"powered", "false"));
		b.set(CX, ROW + 1, Z_BACK, BlockSpec.mc("dark_oak_door", "facing", "north", "half", "upper", "hinge", "left", "open", "false",
			"powered", "false"));
		b.set(CX, ROW + 2, 0, lantern(b, false));
		b.set(CX, ROW + 1, 0, b.palette().get(Role.CORNICE).slab("top"));
	}

	// =====================================================================================================
	// games: hall pits, VIP gallery, lobby machines, pavilion slots, under-gallery banks

	static void games(Blueprint b) {
		// west pit "Salle Europe": two roulettes facing the aisle (S), craps and baccarat facing them (N)
		Kits.tablePod(Game.ROULETTE, null).apply(b, 11, ROW, 11, Facing.SOUTH);
		Kits.tablePod(Game.ROULETTE, null).apply(b, 16, ROW, 11, Facing.SOUTH);
		Kits.tablePod(Game.CRAPS, null).apply(b, 11, ROW, 17, Facing.NORTH);
		Kits.tablePod(Game.BACCARAT, NpcRole.BACCARAT_DEALER).apply(b, 16, ROW, 17, Facing.NORTH);
		// east pit "Salle Amérique"
		Kits.tablePod(Game.BLACKJACK, null).apply(b, 24, ROW, 11, Facing.SOUTH);
		Kits.tablePod(Game.BLACKJACK, null).apply(b, 29, ROW, 11, Facing.SOUTH);
		Kits.tablePod(Game.UTH, null).apply(b, 24, ROW, 17, Facing.NORTH);
		Kits.tablePod(Game.POKER, null).apply(b, 29, ROW, 17, Facing.NORTH);
		// copper slot banks under the side galleries (3 + 3)
		Kits.slotBank(Game.SLOTS_COPPER, 3).apply(b, 2, ROW, 19, Facing.EAST);
		Kits.slotBank(Game.SLOTS_COPPER, 3).apply(b, SX - 3, ROW, 17, Facing.WEST);
		// VIP gallery (row 9): netherite slots, poker #2, player-banked UTH (centre) and baccarat
		b.game(Game.SLOTS_NETHERITE, 9, ROW2, 4, Facing.SOUTH);
		b.game(Game.SLOTS_NETHERITE, SX - 1 - 9, ROW2, 4, Facing.SOUTH);
		Kits.tablePod(Game.POKER, null).apply(b, 13, ROW2, 4, Facing.SOUTH);
		Kits.tablePod(Game.UTH_PLAYER_BANKED, null).apply(b, CX, ROW2, 4, Facing.SOUTH);
		Kits.tablePod(Game.BACCARAT_PLAYER_BANKED, null).apply(b, SX - 1 - 13, ROW2, 4, Facing.SOUTH);
		// lobby: the cashier's booth (west, 3 wide x 2 tall) with the Croupier behind its grille, facing the Wheel of
		// Fortune (east, 3 x 3 upright) in its lit alcove; Plinko (3 x 3) stands in the west pavilion by the duel pit
		Kits.cashierCage(Game.CASHIER).apply(b, 9, ROW, 25, Facing.EAST);
		Kits.machineAlcove(Game.WHEEL).apply(b, 31, ROW, 25, Facing.WEST);
		Kits.machineAlcove(Game.PLINKO).apply(b, 2, ROW, 25, Facing.EAST);
		// east pavilion: the gold slot bank against the outer wall
		Kits.slotBank(Game.SLOTS_GOLD, 4).apply(b, SX - 3, ROW, 26, Facing.WEST);
	}

	// =====================================================================================================
	// interior volumes (light / mob-spawn check), aisles, entries

	static void registerSpaces(Blueprint b) {
		b.interior(X_WALL + 1, ROW, Z_BACK + 1, SX - 2 - X_WALL, MEZZ_Y - 1, Z_HALL1);
		b.interior(X_WALL + 1, ROW2, Z_BACK + 1, SX - 2 - X_WALL, CEIL_Y - 1, Z_HALL1);
		b.interior(X_COLONNADE + 1, ROW, Z_INNER + 1, SX - 2 - X_COLONNADE, MEZZ_Y - 1, Z_FRONT - 1);
		b.mirrorX(AX2, m -> m.interior(X_WALL + 1, ROW, Z_INNER + 1, X_COLONNADE - 1, CEIL_Y - 1, Z_PAVILION - 1));
		b.aisle(19, ROW, Z_INNER, 21, Z_PAVILION);
		b.entry(CX, ROW, SZ - 1);
	}
}
