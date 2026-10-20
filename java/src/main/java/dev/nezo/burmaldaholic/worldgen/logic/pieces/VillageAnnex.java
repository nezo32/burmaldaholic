package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.BannerFont;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Ornament;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;

/**
 * Village annex "Lucky Villager" — an art-deco picture palace at the end of a village street (casino redesign CR-2,
 * findings/CASINO-REDESIGN.md §4.1). One plan, the village's materials ({@link AnnexPalettes}). Layout id
 * {@code casino/annex/<village style>}.
 *
 * <p>Envelope {@value #SX} × {@value #SY} × {@value #SZ} (x × y × z), axis x = {@value #CX}, front = +z. The floor
 * blocks are row 0 at street level (the street jigsaw sits in the porch floor at (8, 0, 16)), the floor row is 1, the
 * hall ceiling row {@value #CEIL} (six free rows over every table). The hall walls stand one cell inside the envelope
 * (x 1 / 15, z 0..14) so the side elevations get real depth: pilasters, sconces and a cornice in the outer strip.
 *
 * <p>Front: two stepped corner pylons (x 0..2 / 14..16) each carrying a vertical "CASINO" blade — six letter banners
 * stacked on a glowing froglight spine between two end-rod neon tubes (a 5-row block letter per glyph would need 30
 * rows; a banner letter needs two) — a two-deep marquee canopy over the door with a lit-bulb fascia and a "♦777♦"
 * letter board, and a stepped deco parapet with glowing flutes up to row 14. Plan at the floor row:
 * <pre>
 *  z 0    back wall
 *  z 1    dealers' service corridor    (corner counter, chest)
 *  z 2,3  [BJ]   [BA + dealer]   [RO]  row 1, players south
 *  z 4    walkways
 *  z 5    dealers
 *  z 6,7  [CR]   [PK]            [UTH] row 2, players south
 *  z 8    walkways
 *  z 9,10 [WHEEL]   [CASHIER booth + Croupier]   [PLINKO]
 *  z 11,12    runner, Loan Shark, planter
 *  z 13   [copper slots ×3]   door throat   [gold slots ×3]
 *  z 14   facade: door x 7..9; z 15..16 porch under the marquee canopy
 * </pre>
 */
public final class VillageAnnex {
	private VillageAnnex() {}

	public static final int SX = 17;
	public static final int SY = 15;
	public static final int SZ = 17;
	public static final int CX = 8;
	static final int AX2 = 2 * CX;
	/** Floor row (first air row above the floor blocks). */
	public static final int Y = 1;
	/** Ceiling row of the hall. */
	public static final int CEIL = 7;
	/** Front wall plane. */
	public static final int FRONT = 14;
	/** Hall side walls. */
	public static final int WEST = 1;
	public static final int EAST = 15;
	/** Row of the hall's wall-top band; the cornice and the roof eaves sit one above. */
	static final int WALL_TOP = 8;

	public static String id(VillageStyle v) {
		return "casino/annex/" + v.id();
	}

	public static Piece build(VillageStyle v) {
		AnnexPalettes.Look look = AnnexPalettes.look(v);
		Blueprint b = Blueprint.of(id(v), CasinoKind.VILLAGE_CASINO, SX, SY, SZ, look.palette()).compactTables(false);
		ground(b);
		shell(b);
		sides(b);
		back(b);
		roof(b);
		front(b, look);
		interior(b);
		games(b);
		b.uplightGames();
		b.interior(WEST + 1, Y, 1, EAST - 1, CEIL - 1, FRONT - 1);
		b.keepClear(7, Y, 11, 9, Y + 2, SZ - 1); // door throat → cashier window
		b.entry(CX, Y, SZ - 1);
		b.anchor(CX, 5, 12);
		b.jigsaw(CX, 0, SZ - 1, Facing.SOUTH, "minecraft:street", "minecraft:street", "minecraft:empty", look.palette().block(Role.PATH));
		return b.build();
	}

	static final BlockSpec ROD_UP = BlockSpec.mc("end_rod", "facing", "up");

	static BlockSpec lantern(Blueprint b, boolean hanging) {
		return BlockSpec.of(b.palette().get(Role.LIGHT_HANGING).full(), "hanging", Boolean.toString(hanging), "waterlogged", "false");
	}

	// =====================================================================================================

	/** Clear the envelope above the floor; floor blocks; the porch paving. */
	static void ground(Blueprint b) {
		b.clear(0, 1, 0, SX - 1, SY - 1, SZ - 1);
		b.fill(0, 0, 0, SX - 1, 0, SZ - 1, Role.FOUNDATION);
		b.fill(WEST + 1, 0, 1, EAST - 1, 0, FRONT - 1, Role.FLOOR);
		b.fill(3, 0, FRONT + 1, 13, 0, SZ - 1, Role.PATH);
	}

	/** Hall walls (plinth rows 1..2, body above, a band on the wall top) and the coffered ceiling. */
	static void shell(Blueprint b) {
		BlockSpec base = b.palette().block(Role.WALL_BASE), wall = b.palette().block(Role.WALL);
		for (int y = Y; y <= WALL_TOP; y++) {
			BlockSpec s = y <= Y + 1 ? base : y == WALL_TOP ? b.palette().block(Role.BAND) : wall;
			for (int z = 0; z <= FRONT; z++) {
				b.set(WEST, y, z, s);
				b.set(EAST, y, z, s);
			}
			for (int x = WEST; x <= EAST; x++) {
				b.set(x, y, 0, s);
				b.set(x, y, FRONT, s);
			}
		}
		Ornament.coffers(b, WEST + 1, 1, EAST - 1, FRONT - 1, CEIL, 4, b.palette().block(Role.LIGHT));
		b.fill(WEST + 1, WALL_TOP, 1, EAST - 1, WALL_TOP, FRONT - 1, Role.CEILING); // attic floor over the coffers
	}

	/**
	 * Side elevations (x 1 / 15, outer strip x 0 / 16): pilasters at z 0, 4, 8, 12 with neon end-rod edges, an arched
	 * window in the first two bays (the third is a blind arch behind the wheel / Plinko), a lit sconce over every
	 * window, a cornice of upside-down stairs on the band.
	 */
	static void sides(Blueprint b) {
		b.mirrorX(AX2, m -> {
			for (int z : new int[] {0, 4, 8, 12}) {
				Ornament.pilaster(m, 0, z, Y, WALL_TOP - 1);
				m.set(0, 0, z, Role.PLINTH);
			}
			for (int bay : new int[] {1, 5, 9}) {
				boolean blind = bay == 9;
				GrandCasino.archWindow(m, false, bay, bay + 2, WEST, Y + 2, Y + 3, 1.5);
				if (blind) { // the wheel / Plinko stands behind it: a gilt blind panel instead of glass
					for (int z = bay; z <= bay + 2; z++) {
						for (int y = Y + 2; y <= Y + 4; y++) {
							BlockSpec c = m.at(WEST, y, z);
							if (c != null && c.name().endsWith("glass_pane")) {
								m.set(WEST, y, z, Role.WALL_ALT);
							}
						}
					}
				}
				m.set(0, Y + 5, bay + 1, trapdoorBracket(m));
				m.set(0, Y + 6, bay + 1, lantern(m, false));
				m.set(0, 0, bay + 1, Role.UPLIGHT); // a floor light washing the bay at night
			}
			Ornament.corniceRun(m, WEST, 0, WEST, FRONT - 1, WALL_TOP + 1, Facing.WEST);
		});
	}

	static BlockSpec trapdoorBracket(Blueprint b) {
		return BlockSpec.mc(Kits.wood(b, Role.FRAME) + "_trapdoor", "facing", "east", "half", "top", "open", "false", "powered", "false",
			"waterlogged", "false");
	}

	/** Back elevation (z 0, flush with the envelope): flush pilaster strips with neon slots, two blind arches. */
	static void back(Blueprint b) {
		b.mirrorX(AX2, m -> {
			for (int y = Y; y <= WALL_TOP - 1; y++) {
				m.set(4, y, 0, Ornament.upright(m, Role.COLUMN));
			}
			m.set(4, Y, 0, Role.CAPITAL);
			m.set(4, WALL_TOP - 1, 0, Role.CAPITAL);
			for (int y = Y + 2; y <= Y + 4; y++) {
				m.set(6, y, 0, Role.WALL_ALT);
			}
			m.set(6, Y + 5, 0, Role.GILT);
		});
		for (int y = Y + 2; y <= Y + 5; y++) {
			b.set(CX, y, 0, Role.NEON_B); // a glowing slot on the axis of the back wall
		}
	}

	/**
	 * A deco flat roof: a deck behind a parapet (capped by a rail slab) over the side cornices, and a glazed skylight
	 * lantern on the axis that glows at night.
	 */
	static void roof(Blueprint b) {
		int deck = WALL_TOP + 1;
		Material rail = b.palette().get(Role.RAIL);
		b.fill(WEST + 1, deck, 1, EAST - 1, deck, FRONT - 1, Role.ROOF_TRIM);
		for (int z = 0; z <= FRONT - 1; z++) {
			for (int x : new int[] {WEST, EAST}) {
				b.set(x, deck, z, Role.WALL);
				b.set(x, deck + 1, z, rail.slab("bottom"));
			}
		}
		for (int x = WEST; x <= EAST; x++) {
			b.set(x, deck, 0, Role.WALL);
			b.set(x, deck + 1, 0, rail.slab("bottom"));
		}
		// skylight lantern x 6..10, z 4..9: column corners, glass sides, a gilt cap; lit from inside
		int x0 = 6, x1 = 10, z0 = 4, z1 = 9;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				if (corner) {
					b.set(x, deck + 1, z, Ornament.upright(b, Role.COLUMN));
				} else if (edge) {
					b.set(x, deck + 1, z, Role.GLASS);
				} else {
					b.set(x, deck, z, Role.LIGHT);
				}
				b.set(x, deck + 2, z, edge ? b.palette().get(Role.GILT).slab("bottom") : b.palette().block(Role.GLASS));
			}
		}
	}

	// =====================================================================================================
	// front: pylons with the vertical CASINO blades, marquee canopy, stepped deco parapet, porch

	static void front(Blueprint b, AnnexPalettes.Look look) {
		Palette p = b.palette();
		Material gilt = p.get(Role.GILT);
		b.mirrorX(AX2, m -> {
			// ---- corner pylon x 0..2, z 14..15, rows 1..13 ----
			for (int y = Y; y <= 13; y++) {
				for (int x = 0; x <= 2; x++) {
					for (int z = FRONT; z <= FRONT + 1; z++) {
						m.set(x, y, z, y <= Y + 1 ? p.block(Role.PLINTH) : p.block(Role.WALL));
					}
				}
			}
			for (int x = 0; x <= 2; x++) {
				m.set(x, 0, FRONT + 1, Role.FOUNDATION);
				m.set(x, 0, SZ - 1, Role.FOUNDATION);
				m.set(x, Y, SZ - 1, p.get(Role.PLINTH).stairs(Facing.NORTH, false)); // the pylon's base step
			}
			for (int y = Y + 1; y <= 13; y++) {
				m.set(0, y, FRONT + 1, Ornament.upright(m, Role.COLUMN));
				m.set(2, y, FRONT + 1, Ornament.upright(m, Role.COLUMN));
				m.set(1, y, FRONT + 1, Role.LIGHT); // the glowing spine behind the letters
				m.set(0, y, SZ - 1, ROD_UP); // neon tubes framing the blade
				m.set(2, y, SZ - 1, ROD_UP);
			}
			// side face chaser (seen along the street)
			for (int y = Y + 2; y <= 12; y += 2) {
				m.set(0, y, FRONT, Role.BULB);
			}
			// stepped top: gilt cap, a bulb on each shoulder, rod finial on the spine
			m.set(0, 13, FRONT, gilt.slab("bottom"));
			m.set(2, 13, FRONT, gilt.slab("bottom"));
			m.set(0, 14, FRONT + 1, gilt.slab("bottom"));
			m.set(2, 14, FRONT + 1, gilt.slab("bottom"));
			m.set(1, 13, FRONT, Role.BULB);
			m.set(1, 14, FRONT + 1, ROD_UP);
			m.set(1, 14, FRONT, ROD_UP);
			// letters (not mirrored: banner patterns are NBT, the text reads the same on both pylons)
			BannerFont.column(m, "CASINO", 1, 13, SZ - 1, Facing.SOUTH, 2, look.bannerBase(), look.bannerInk());
		});

		// ---- facade wall between the pylons (x 3..13): door, poster windows, jambs ----
		b.clear(7, Y, FRONT, 9, Y + 2, FRONT);
		b.mirrorX(AX2, m -> {
			for (int y = Y; y <= Y + 2; y++) {
				m.set(6, y, FRONT, Ornament.upright(m, Role.COLUMN)); // door jambs
				m.set(3, y, FRONT, Ornament.upright(m, Role.COLUMN));
			}
			m.set(3, Y + 3, FRONT, Role.CAPITAL);
			for (int x = 4; x <= 5; x++) {
				m.set(x, Y + 1, FRONT, BlockSpec.mc("glass_pane")); // poster windows into the slot lounge
				m.set(x, Y + 2, FRONT, BlockSpec.mc("glass_pane"));
			}
			m.set(4, Y, FRONT + 1, p.get(Role.PLINTH).stairs(Facing.NORTH, false)); // a sill bench under the windows
			m.set(5, Y, FRONT + 1, p.get(Role.PLINTH).stairs(Facing.NORTH, false));
			for (int y = Y + 3; y <= 13; y++) {
				m.set(3, y, FRONT, Ornament.upright(m, Role.COLUMN)); // pilaster between pylon and marquee
			}
		});

		// ---- marquee canopy x 4..12, z 15..16, rows 4..9 ----
		int x0 = 4, x1 = 12;
		BlockSpec board = p.block(Role.SIGN_BACK), bulb = p.block(Role.BULB);
		for (int x = x0; x <= x1; x++) {
			b.set(x, 4, FRONT + 1, gilt.slab("top")); // soffit
			b.set(x, 4, SZ - 1, (x - x0) % 2 == 0 ? bulb : board); // lit fascia: the canopy edge
			for (int y = 5; y <= 8; y++) {
				b.set(x, y, FRONT + 1, board);
			}
			b.set(x, 5, SZ - 1, board);
			b.set(x, 8, SZ - 1, (x - x0) % 2 == 0 ? bulb : board);
			b.set(x, 9, FRONT + 1, gilt.slab("bottom"));
			b.set(x, 9, SZ - 1, gilt.slab("bottom"));
		}
		for (int x : new int[] {x0, x0 + 1, x1 - 1, x1}) {
			b.set(x, 6, SZ - 1, x == x0 || x == x1 ? bulb : board);
			b.set(x, 7, SZ - 1, x == x0 || x == x1 ? board : board);
		}
		b.set(x0, 7, SZ - 1, bulb);
		b.set(x1, 7, SZ - 1, bulb);
		for (int x = 6; x <= 10; x++) {
			b.set(x, 7, FRONT + 1, Role.LIGHT); // backlight of the letter board
		}
		for (int x : new int[] {5, 8, 11}) {
			b.set(x, 4, FRONT + 1, Role.LIGHT); // soffit lights over the porch
		}
		BannerFont.row(b, "*777*", CX, 7, SZ - 1, Facing.SOUTH, look.bannerBase(), look.bannerInk());

		// ---- stepped deco parapet x 3..13 above the marquee: dark panel, glowing flutes, gilt steps ----
		int[] top = {10, 11, 11, 12, 13, 13}; // top row for x = 3..8 (mirrored)
		for (int x = 3; x <= 13; x++) {
			int t = top[Math.min(x, AX2 - x) - 3];
			for (int y = WALL_TOP + 1; y <= t; y++) {
				boolean flute = (x == 6 || x == 10) && y >= 10 && y < t;
				b.set(x, y, FRONT, x == 3 || x == 13 ? Ornament.upright(b, Role.COLUMN) : flute ? p.block(Role.NEON_B) : p.block(Role.WALL));
				b.set(x, y, FRONT - 1, Role.WALL); // thickness: the board never shows its back
			}
			b.set(x, t + 1, FRONT, gilt.slab("bottom"));
			b.set(x, t + 1, FRONT - 1, gilt.slab("bottom"));
		}
		for (int x : new int[] {6, 10}) {
			int t = top[Math.min(x, AX2 - x) - 3];
			b.set(x, t + 1, FRONT, bulb); // a bulb on the flute tops
		}
		b.set(CX, 14, FRONT, ROD_UP);
		b.set(CX - 1, 14, FRONT, gilt.slab("bottom"));
		b.set(CX + 1, 14, FRONT, gilt.slab("bottom"));
		for (int y = WALL_TOP + 1; y <= 13; y++) { // the keystone fin on the axis
			b.set(CX, y, FRONT, y >= 10 ? p.block(Role.NEON_A) : p.block(Role.CAPITAL));
		}

		// ---- porch: runner, bollard lamps, uplights washing the pylons ----
		for (int z = FRONT + 1; z <= SZ - 1; z++) {
			for (int x = 7; x <= 9; x++) {
				b.set(x, Y, z, Role.CARPET);
			}
		}
		b.mirrorX(AX2, m -> {
			m.set(3, Y, SZ - 1, p.block(Role.BALUSTER));
			m.set(3, Y + 1, SZ - 1, lantern(m, false));
			m.set(3, 0, FRONT + 1, Role.UPLIGHT);
		});
	}

	// =====================================================================================================
	// interior: carpet, lobby floor, back bar, decor

	static void interior(Blueprint b) {
		Palette p = b.palette();
		// one deep carpet field over the gaming hall with a gilt border (walkways become dark by the table pods)
		for (int x = WEST + 1; x <= EAST - 1; x++) {
			for (int z = 1; z <= 9; z++) {
				boolean border = x == WEST + 1 || x == EAST - 1 || z == 1;
				b.set(x, Y, z, border ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
			}
		}
		// lobby: checker marble, a red runner with gilt edges from the door to the cashier
		Ornament.checker(b, WEST + 1, 10, EAST - 1, FRONT - 1, 0, p.block(Role.FLOOR), p.block(Role.FLOOR_ACCENT));
		for (int z = 11; z <= FRONT; z++) {
			for (int x = 7; x <= 9; x++) {
				b.set(x, Y, z, Role.CARPET);
			}
			if (z <= FRONT - 1) {
				b.set(6, Y, z, Role.CARPET_PATTERN);
				b.set(10, Y, z, Role.CARPET_PATTERN);
			}
		}
		b.set(CX, 0, FRONT, Role.FLOOR);
		// the dealers' row (z 1) is a free service corridor: house dealers (package DL) walk to their cells from the
		// channels between the tables, so only the corner keeps a counter with a planter
		Material counter = p.get(Role.COUNTER);
		b.set(WEST + 1, Y, 1, counter.slab("double"));
		b.set(WEST + 1, Y + 1, 1, Role.PLANTER);
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, EAST - 1, Y, 1, Facing.SOUTH);
		// lobby: a planter, the Loan Shark by the Plinko
		Kits.pottedPlant().apply(b, 4, Y, 10, Facing.SOUTH);
		b.npc(NpcRole.LOAN_SHARK, 12, Y, 10, Facing.WEST);
	}

	// =====================================================================================================
	// games

	static void games(Blueprint b) {
		pod(b, Game.BLACKJACK, 3, 3, null);
		pod(b, Game.BACCARAT, CX, 3, NpcRole.BACCARAT_DEALER);
		pod(b, Game.ROULETTE, 13, 3, null);
		pod(b, Game.CRAPS, 3, 7, null);
		pod(b, Game.POKER, CX, 7, null);
		pod(b, Game.UTH, 13, 7, null);
		Kits.cashierCage(Game.CASHIER).apply(b, CX, Y, 10, Facing.SOUTH);
		Kits.machineAlcove(Game.WHEEL).apply(b, WEST + 1, Y, 11, Facing.EAST);
		Kits.machineAlcove(Game.PLINKO).apply(b, EAST - 1, Y, 11, Facing.WEST);
		Kits.slotBank(Game.SLOTS_COPPER, 3).apply(b, 6, Y, FRONT - 1, Facing.NORTH);
		Kits.slotBank(Game.SLOTS_GOLD, 3).apply(b, 12, Y, FRONT - 1, Facing.NORTH);
		// a lantern chandelier over every table, high above its board volume (felt lit ≥ 12, nothing crate-like)
		for (var s : b.games()) {
			if (s.game().tableGame() != null) {
				Kits.chandelier(2).apply(b, s.pos().x(), CEIL, s.pos().z(), Facing.SOUTH);
			}
		}
	}

	/**
	 * A full 3 × 2 table pod facing south ({@link Kits#tablePod}); its stools never stand in the 2-wide channels
	 * between tables nor in the side walls, so every walkway stays open.
	 */
	static void pod(Blueprint b, Game g, int x, int z, NpcRole dealer) {
		BlockSpec[] keep = {b.at(x - 2, Y, z + 1), b.at(x + 2, Y, z + 1)};
		Kits.tablePod(g, dealer).apply(b, x, Y, z, Facing.SOUTH);
		b.set(x - 2, Y, z + 1, keep[0] == null ? Blueprint.AIR : keep[0]);
		b.set(x + 2, Y, z + 1, keep[1] == null ? Blueprint.AIR : keep[1]);
	}
}
