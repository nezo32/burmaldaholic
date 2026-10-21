package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Ornament;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.SignFont;

/**
 * High Roller Lounge "Void Penthouse" (casino redesign CR-2, findings/CASINO-REDESIGN.md §4.3): a glass-curtain
 * pavilion standing on the End island surface beside an End City (placement: package WG), crowned like the Chrysler
 * Building. Envelope {@value #SX} × {@value #SY} × {@value #SZ}, axis x = {@value #CX}, front = +z.
 *
 * <p>Rows: 0..{@value #SKIRT_TOP} the skirt — a solid purpur plinth under the whole plate (x 0..{@value #SX}-1,
 * z 0..{@value #PZ}) that fills the ground down to {@value #SKIRT} rows below the plate, so terrain with up to
 * {@value #SKIRT} blocks of spread under the footprint never leaves a gap; {@value #FLOOR} the floor plate, floor row
 * {@value #FR}, hall ceiling {@value #CEIL} (seven free rows), the roof deck above it. In front of the terrace
 * (z {@value #PZ}+1..{@value #SZ}-1) a 3-wide flight of {@value #SKIRT} steps runs down to the lowest ground the skirt
 * allows; the cells above the steps are structure void, so where the ground is higher the steps simply sink into it.
 * On the deck: the neon "CASINO" attic over the front, and behind it the crown — a glazed shaft and five receding
 * scalloped tiers, each face an arch outlined in glowing froglight over magenta sunburst windows, topped by an end-rod
 * needle.
 *
 * <p>Hall (x 1..23, z 1..19): the bar on the back wall; two rows of three full multi-block tables (high-roller
 * blackjack / roulette / baccarat; high-roller UTH, craps, poker), every dealer cell two blocks of air (house dealers:
 * package DL); the cashier booth with the Shulker Croupier on the west wall; Plinko, the Wheel of Fortune and four
 * netherite slots along the front wall either side of the door. Terrace (z 21..{@value #PZ}): two lit amethyst
 * fountains, a balustrade, the stair on the axis.
 *
 * <p>Site contract for placement (template coordinates, before rotation; front = +z): put row {@link #FLOOR} on the
 * HIGHEST ground block under the plate footprint (x 0..{@value #SX}-1, z 0..{@value #PZ}); the ground under it may be
 * up to {@value #SKIRT} rows lower. Everything over the plate from row {@link #FR} up is carved to air. Walk-in cells:
 * {@link #DOOR} (the hall door), {@link #TERRACE_EDGE} (top of the stair) and {@link #STAIR_FOOT} (the lowest step,
 * standing row 1).
 */
public final class HighRollerLounge {
	public static final String ID = "casino/lounge";
	public static final int SX = 25;
	/** Rows of skirt under the plate (the terrain spread the base tolerates). */
	public static final int SKIRT = 4;
	static final int SKIRT_TOP = SKIRT - 1;
	/** Floor plate row: goes on the highest ground block of the footprint. */
	public static final int FLOOR = SKIRT;
	/** Floor row (first air row over the plate). */
	public static final int FR = FLOOR + 1;
	static final int CEIL = FR + 7;
	public static final int SY = CEIL + 31;
	/** Last z row of the plate (terrace edge); the stair is z PZ+1..SZ-1. */
	public static final int PZ = 24;
	public static final int SZ = PZ + 1 + SKIRT;
	/** Front wall of the hall; the terrace is z 21..PZ. */
	static final int FZ = 20;
	public static final int CX = 12;
	static final int AX2 = 2 * CX;
	/** Crown tower centre (z) and the shaft's half width. */
	static final int TZ = 9;
	static final int SHAFT = 6;
	/** Standing cell in the hall door. */
	public static final Vec DOOR = new Vec(CX, FR, FZ);
	/** Standing cell on the terrace at the top of the stair. */
	public static final Vec TERRACE_EDGE = new Vec(CX, FR, PZ);
	/** Standing cell on the lowest step (row 0 holds the step). */
	public static final Vec STAIR_FOOT = new Vec(CX, 1, SZ - 1);

	private HighRollerLounge() {}

	public static Piece build() {
		return build(OtherworldPalettes.LOUNGE);
	}

	static Piece build(Palette p) {
		Blueprint b = Blueprint.of(ID, CasinoKind.HIGH_ROLLER, SX, SY, SZ, p).compactTables(false);
		skirt(b, p);
		hall(b, p);
		terrace(b, p);
		stair(b, p);
		games(b);
		interior(b, p);
		b.uplightGames();
		roof(b, p);
		sign(b, p);
		crown(b, p);
		b.interior(1, FR, 1, SX - 2, CEIL - 1, FZ - 1);
		b.entry(TERRACE_EDGE.x(), TERRACE_EDGE.y(), TERRACE_EDGE.z());
		b.anchor(CX, FR + 4, 16);
		return b.build();
	}

	static final BlockSpec ROD_UP = BlockSpec.mc("end_rod", "facing", "up");

	static BlockSpec lantern(Blueprint b, boolean hanging) {
		return BlockSpec.of(b.palette().get(Role.LIGHT_HANGING).full(), "hanging", Boolean.toString(hanging), "waterlogged", "false");
	}

	// ---- skirt and plate --------------------------------------------------------------------------------------------

	/**
	 * The ground-standing base: a solid plinth {@value #SKIRT} rows deep under the whole plate (no air anywhere under
	 * the floor), dressed where it shows above low ground — end-stone-brick courses between purpur pilasters every
	 * six cells and a purpur band under the plate's edge.
	 */
	private static void skirt(Blueprint b, Palette p) {
		b.fill(0, 0, 0, SX - 1, SKIRT_TOP, PZ, Role.FOUNDATION);
		for (int y = 0; y <= SKIRT_TOP; y++) {
			for (int t = 0; t < SX; t++) {
				face(b, p, t, y, 0, t);
				face(b, p, t, y, PZ, t);
			}
			for (int t = 0; t <= PZ; t++) {
				face(b, p, 0, y, t, t);
				face(b, p, SX - 1, y, t, t);
			}
		}
		b.fill(0, FLOOR, 0, SX - 1, FLOOR, PZ, Role.FOUNDATION);
		b.fill(1, FLOOR, 1, SX - 2, FLOOR, FZ - 1, Role.FLOOR);
	}

	/** One outer cell of the skirt, {@code t} along its face. */
	private static void face(Blueprint b, Palette p, int x, int y, int z, int t) {
		boolean corner = (x == 0 || x == SX - 1) && (z == 0 || z == PZ);
		if (corner || Math.floorMod(t, 6) == 0) {
			b.set(x, y, z, Ornament.upright(b, Role.COLUMN));
		} else if (y == SKIRT_TOP) {
			b.set(x, y, z, Role.WALL_BASE);
		} else {
			b.set(x, y, z, Role.COFFER);
		}
	}

	/**
	 * The front stair: {@value #SKIRT} steps of end-stone-brick stairs (3 wide on the axis) from the terrace down to
	 * standing row 1, solid underneath, between purpur cheek walls that step down with it. Cells above the steps are
	 * left as structure void: on low ground the flight stands free, on higher ground its lower steps sink into it.
	 */
	private static void stair(Blueprint b, Palette p) {
		Material path = p.get(Role.PATH);
		for (int i = 0; i < SKIRT; i++) {
			int z = PZ + 1 + i, top = SKIRT_TOP - i;
			for (int x = CX - 1; x <= CX + 1; x++) {
				for (int y = 0; y < top; y++) {
					b.set(x, y, z, Role.FOUNDATION);
				}
				b.set(x, top, z, path.stairs(Facing.NORTH, false));
			}
			for (int x : new int[] {CX - 2, CX + 2}) {
				for (int y = 0; y <= top; y++) {
					b.set(x, y, z, Ornament.upright(b, Role.COLUMN));
				}
				b.set(x, top + 1, z, path.slab("bottom"));
			}
		}
	}

	// ---- hall: glass curtain walls ----------------------------------------------------------------------------------

	/** One curtain-wall cell of row {@code y}, {@code t} along the wall: a pillar every 6, an end-rod mullion every 3. */
	private static BlockSpec curtain(Blueprint b, int t, int y) {
		if (Math.floorMod(t, 6) == 0) {
			return Ornament.upright(b, Role.FRAME);
		}
		if (y == FR) {
			return b.palette().block(Role.WALL_BASE);
		}
		return Math.floorMod(t, 3) == 0 ? ROD_UP : b.palette().block(Role.GLASS);
	}

	private static void hall(Blueprint b, Palette p) {
		b.clear(0, FR, 0, SX - 1, SY - 1, PZ);
		for (int y = FR; y < CEIL; y++) {
			for (int x = 0; x < SX; x++) {
				b.set(x, y, 0, curtain(b, x, y));
				b.set(x, y, FZ, curtain(b, x, y));
			}
			for (int z = 0; z <= FZ; z++) {
				b.set(0, y, z, curtain(b, z, y));
				b.set(SX - 1, y, z, curtain(b, z, y));
			}
		}
		// door: a 3-wide opening under a purpur lintel, purpur jambs
		b.clear(CX - 1, FR, FZ, CX + 1, FR + 2, FZ);
		for (int y = FR; y <= FR + 2; y++) {
			b.set(CX - 2, y, FZ, Ornament.upright(b, Role.COLUMN));
			b.set(CX + 2, y, FZ, Ornament.upright(b, Role.COLUMN));
		}
		for (int x = CX - 2; x <= CX + 2; x++) {
			b.set(x, FR + 3, FZ, Role.TRIM);
		}
		// the ceiling band, the roof plate with a cornice of stairs all round
		Material roof = p.get(Role.ROOF);
		for (int x = 0; x < SX; x++) {
			for (int z = 0; z <= FZ; z++) {
				b.set(x, CEIL, z, Role.ROOF);
			}
		}
		for (int x = 0; x < SX; x++) {
			b.set(x, CEIL, FZ + 1, roof.stairs(Facing.NORTH, true));
		}
		Ornament.coffers(b, 1, 1, SX - 2, FZ - 1, CEIL, 4, p.block(Role.LIGHT));
	}

	// ---- terrace --------------------------------------------------------------------------------------------------

	private static void terrace(Blueprint b, Palette p) {
		for (int x = 0; x < SX; x++) {
			for (int z = FZ + 1; z <= PZ; z++) {
				b.set(x, FLOOR, z, Math.floorMod(x + z, 2) == 0 ? p.block(Role.PATH) : p.block(Role.FLOOR));
			}
		}
		b.mirrorX(AX2, m -> {
			Ornament.balustrade(m, 0, PZ, CX - 2, PZ, FR, 4);
			Ornament.balustrade(m, 0, FZ + 1, 0, PZ, FR, 0);
			m.set(CX - 2, FR + 2, PZ, ROD_UP);
			m.set(0, FR + 2, PZ, ROD_UP);
			// a lit amethyst fountain either side of the runner
			int fx = 6, fz = 22;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						m.set(fx + dx, FR, fz + dz, Role.ACCENT);
					}
				}
			}
			m.set(fx, FR, fz, BlockSpec.mc("water", "level", "0"));
			m.set(fx, FLOOR, fz, BlockSpec.mc("sea_lantern"));
			for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
				m.set(fx + c[0], FR + 1, fz + c[1], BlockSpec.mc("amethyst_cluster", "facing", "up", "waterlogged", "false"));
			}
		});
		for (int z = FZ + 1; z <= PZ; z++) {
			for (int x = CX - 1; x <= CX + 1; x++) {
				b.set(x, FR, z, x == CX ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
			}
		}
	}

	// ---- games ------------------------------------------------------------------------------------------------------

	static void games(Blueprint b) {
		Kits.tablePod(Game.BLACKJACK_HIGH_ROLLER, null).apply(b, 6, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.ROULETTE_HIGH_ROLLER, null).apply(b, CX, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.BACCARAT_HIGH_ROLLER, NpcRole.BACCARAT_DEALER).apply(b, 18, FR, 7, Facing.SOUTH);
		Kits.tablePod(Game.UTH_HIGH_ROLLER, null).apply(b, 6, FR, 13, Facing.SOUTH);
		Kits.tablePod(Game.CRAPS, null).apply(b, CX, FR, 13, Facing.SOUTH);
		Kits.tablePod(Game.POKER, null).apply(b, 18, FR, 13, Facing.SOUTH);
		// cashier booth on the west wall: the Shulker Croupier behind the window between two counters with grilles
		b.game(Game.CASHIER, 2, FR, 10, Facing.EAST);
		Material counter = b.palette().get(Role.COUNTER);
		for (int z : new int[] {8, 12}) {
			b.set(2, FR, z, counter.slab("double"));
			b.set(1, FR, z, counter.slab("double"));
			b.set(2, FR + 1, z, BlockSpec.mc("iron_bars"));
		}
		b.set(1, FR, 9, counter.slab("double"));
		b.set(1, FR, 11, counter.slab("double"));
		b.npc(NpcRole.ENDER_CROUPIER, 1, FR, 10, Facing.EAST);
		b.npc(NpcRole.MERCHANT, 3, FR, 12, Facing.EAST); // DL/EC: the merchant beside the cashier
		for (int z = 8; z <= 12; z++) {
			b.set(2, FR + 2, z, counter.slab("top"));
			b.set(1, FR + 2, z, counter.slab("top"));
			b.set(1, FR + 3, z, z == 10 ? b.palette().block(Role.NEON_A) : b.palette().block(Role.TRIM));
		}
		b.keepClear(3, FR, 9, 3, FR + 1, 11);
		// front wall: Plinko and the Wheel in lit alcoves, netherite slots either side of the door
		Kits.machineAlcove(Game.PLINKO).apply(b, 4, FR, FZ - 1, Facing.NORTH);
		Kits.machineAlcove(Game.WHEEL).apply(b, SX - 5, FR, FZ - 1, Facing.NORTH);
		Kits.slotBank(Game.SLOTS_NETHERITE, 2).apply(b, 8, FR, FZ - 1, Facing.NORTH);
		Kits.slotBank(Game.SLOTS_NETHERITE, 2).apply(b, 17, FR, FZ - 1, Facing.NORTH);
		// a starburst chandelier over every table: chain, a lantern one row over the board volume, four end-rod rays
		for (var s : b.games()) {
			if (s.game().tableGame() == null) {
				continue;
			}
			int x = s.pos().x(), z = s.pos().z();
			for (int y = CEIL - 1; y > FR + 4; y--) {
				b.set(x, y, z, BlockSpec.mc("iron_chain", "axis", "y", "waterlogged", "false"));
			}
			b.set(x, FR + 4, z, lantern(b, true));
			for (Facing f : Facing.values()) {
				b.set(x + f.dx(), FR + 5, z + f.dz(), BlockSpec.mc("end_rod", "facing", f.id()));
			}
		}
	}

	// ---- interior ---------------------------------------------------------------------------------------------------

	static void interior(Blueprint b, Palette p) {
		// one deep field over the gaming floor with a gilt border (no pattern next to the felt)
		for (int x = 1; x <= SX - 2; x++) {
			for (int z = 5; z <= 17; z++) {
				BlockSpec at = b.at(x, FR, z);
				if (at != null && !at.is("minecraft:air") || dealerCell(b, x, z)) {
					continue;
				}
				boolean border = x == 4 || x == SX - 5 || z == 5 || z == 17;
				boolean inField = x >= 4 && x <= SX - 5;
				if (inField) {
					b.set(x, FR, z, border ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
				}
			}
		}
		// runner from the door to the bar
		for (int z = 15; z <= FZ; z++) {
			for (int x = CX - 1; x <= CX + 1; x++) {
				b.set(x, FR, z, x == CX ? p.block(Role.CARPET_PATTERN) : p.block(Role.CARPET));
			}
		}
		// bar on the back wall, VIP sofas in the corners, the chest by the bar
		Kits.bar(9).apply(b, 8, FR, 3, Facing.SOUTH);
		b.mirrorX(AX2, m -> {
			Kits.vipSofa(3).apply(m, 2, FR, 1, Facing.SOUTH);
			Kits.pottedPlant().apply(m, 1, FR, 4, Facing.SOUTH);
		});
		b.chest(CasinoLoot.Table.HIGH_ROLLER, 18, FR, 2, Facing.SOUTH);
		// a purpur side table with glazed top by the cashier and the wheel
		b.set(SX - 2, FR, 10, Ornament.upright(b, Role.COLUMN));
		b.set(SX - 2, FR + 1, 10, BlockSpec.mc("magenta_glazed_terracotta", "facing", "west"));
		b.set(SX - 2, FR, 14, Ornament.upright(b, Role.COLUMN));
		b.set(SX - 2, FR + 1, 14, BlockSpec.mc("magenta_glazed_terracotta", "facing", "west"));
	}

	private static boolean dealerCell(Blueprint b, int x, int z) {
		for (var s : b.games()) {
			int[] d = dev.nezo.burmaldaholic.worldgen.logic.blueprint.TableClearance.dealer(s);
			if (d != null && d[0] == x && d[1] == z) {
				return true;
			}
		}
		return false;
	}

	// ---- roof, sign, crown ------------------------------------------------------------------------------------------

	private static void roof(Blueprint b, Palette p) {
		int deck = CEIL + 1;
		Material rail = p.get(Role.RAIL);
		for (int x = 0; x < SX; x++) {
			b.set(x, deck, 0, Role.WALL);
			b.set(x, deck + 1, 0, rail.slab("bottom"));
		}
		for (int z = 0; z <= FZ; z++) {
			b.set(0, deck, z, Role.WALL);
			b.set(0, deck + 1, z, rail.slab("bottom"));
			b.set(SX - 1, deck, z, Role.WALL);
			b.set(SX - 1, deck + 1, z, rail.slab("bottom"));
		}
		for (int[] c : new int[][] {{0, 0}, {SX - 1, 0}}) {
			b.set(c[0], deck + 1, c[1], ROD_UP);
		}
	}

	/** The neon attic over the front: pearlescent froglight letters on obsidian with a froglight chaser. */
	private static void sign(Blueprint b, Palette p) {
		int bottom = CEIL + 1;
		SignFont.Plan plan = SignFont.SMALL.line("CASINO");
		var box = Ornament.neon(b, plan, Facing.SOUTH, CX, bottom, FZ, (SX - plan.width()) / 2, false);
		for (int x = box.alongFrom(); x <= box.alongTo(); x++) {
			for (int y = box.y0(); y <= box.y1(); y++) {
				b.set(x, y, FZ - 1, Role.WALL); // thickness: the board never shows its back
			}
			b.set(x, box.y1() + 1, FZ, p.get(Role.TRIM).slab("bottom"));
			b.set(x, box.y1() + 1, FZ - 1, p.get(Role.TRIM).slab("bottom"));
		}
		b.set(CX, box.y1() + 2, FZ, ROD_UP);
	}

	/** Height of a scallop (semicircular arch) over column {@code d} cells from the centre of a face {@code half} wide. */
	static int arc(int d, int half) {
		return (int) Math.floor(Math.sqrt(Math.max(0, half * half - d * d)));
	}

	/**
	 * Chrysler crown: a glazed shaft, then receding scalloped tiers — on each face an arch (a semicircle the full width of
	 * the tier) outlined in glowing froglight, magenta sunburst windows under it, the corners stepping down — each lit
	 * inside so the windows glow at night, then a needle of end rods.
	 */
	private static void crown(Blueprint b, Palette p) {
		int base = CEIL + 1;
		// shaft: 13 × 13, rows 12..19 (it clears the sign attic), purpur pillars at corners, black glass with end-rod mullions
		int x0 = CX - SHAFT, x1 = CX + SHAFT, z0 = TZ - SHAFT, z1 = TZ + SHAFT, top = base + 7;
		for (int y = base; y <= top; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
					if (!edgeX && !edgeZ) {
						continue;
					}
					int t = edgeZ ? x - CX : z - TZ;
					BlockSpec s = edgeX && edgeZ ? Ornament.upright(b, Role.FRAME) : y == top ? p.block(Role.TRIM)
						: Math.floorMod(t, 3) == 0 ? ROD_UP : p.block(Role.GLASS);
					b.set(x, y, z, s);
				}
			}
		}
		b.set(CX, base + 1, TZ, Role.LIGHT);
		int[][] tiers = {{6, top + 1}, {5, top + 5}, {4, top + 9}, {3, top + 12}, {2, top + 15}};
		int crownTop = 0;
		for (int[] t : tiers) {
			crownTop = Math.max(crownTop, tier(b, p, t[0], t[1]));
		}
		// the needle
		int y = crownTop + 1;
		b.set(CX, y, TZ, Role.CAPITAL);
		for (int i = 1; y + i < SY; i++) {
			b.set(CX, y + i, TZ, ROD_UP);
		}
	}

	/**
	 * Sunburst window cell of an arch face: on one of the seven rays fanning from the springing point at the face centre
	 * (the Chrysler crown's triangular windows); the rays alternate glass / purpur by angle.
	 */
	static boolean sunray(int along, int up, int half) {
		double ang = Math.atan2(up - 0.5, along); // 0 .. pi across the face
		int sector = (int) Math.floor(ang / (Math.PI / 9));
		return sector % 2 == 1 && Math.hypot(along, up) >= 1.5;
	}

	/** One scalloped tier {@code 2 * half + 1} wide from row {@code y0}; returns its apex row. */
	private static int tier(Blueprint b, Palette p, int half, int y0) {
		int apex = y0;
		for (int x = CX - half; x <= CX + half; x++) {
			for (int z = TZ - half; z <= TZ + half; z++) {
				int dx = x - CX, dz = z - TZ;
				int h = Math.min(arc(dx, half), arc(dz, half));
				boolean edgeX = Math.abs(dx) == half, edgeZ = Math.abs(dz) == half;
				for (int y = y0; y <= y0 + h; y++) {
					BlockSpec s;
					if (edgeX || edgeZ) {
						int along = edgeZ ? dx : dz; // position along the face this cell belongs to
						boolean rim = y == y0 + h;
						boolean corner = edgeX && edgeZ;
						if (corner) {
							s = Ornament.upright(b, Role.FRAME);
						} else if (rim) {
							s = p.block(Role.NEON_A);
						} else if (y > y0 && sunray(along, y - y0, half)) {
							s = BlockSpec.mc("magenta_stained_glass");
						} else {
							s = p.block(Role.WALL);
						}
					} else if (y == y0 + h) {
						s = p.block(Role.DOME);
					} else if (y == y0 && dx == 0 && dz == 0) {
						s = p.block(Role.LIGHT);
					} else {
						s = Blueprint.AIR;
					}
					b.set(x, y, z, s);
				}
				apex = Math.max(apex, y0 + h);
			}
		}
		return apex;
	}
}
