package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint.Suit;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * Generators of the standalone Overworld casino (architecture doc §4.1, §7.1): six jigsaw pieces per
 * {@link Style} — {@code entrance} (start), {@code floor_a} / {@code floor_b}, {@code bar}, {@code office},
 * {@code vip}. PURE and deterministic.
 *
 * <p>Shared frame: every piece has the floor block on row {@link #FLOOR_Y} (= ground level after
 * placement: start height −3 over a 2-row foundation) and the floor row (first air row) at
 * {@link #ROW}. Non-start pieces are authored with their entry connector on their own +z face.
 * Doorways between pieces are 3 wide × {@link #DOOR_H} high, opened by both pieces.
 *
 * <p>Pieces and pools (CB-1 reads {@link Part} for the pool JSON):
 * <pre>
 *   entrance  31×29×17  pool entrance (1)   CASHIER + Croupier, WHEEL, PLINKO; back → floor
 *   floor_a   31×27×17  pool floor (1)      6 tables, copper ×6 + gold ×4 slots; front ← entrance, east → bar, back → rear
 *   floor_b   31×27×17  pool floor (1)      same games around a dealers' pit
 *   bar       17×20×13  pool bar (1)        bar, Loan Shark booth, duel pit, gold slots ×3; entry ← floor
 *   office    15×16×9   pool rear (2)       manager's office, loot chest; entry ← floor
 *   vip       17×18×13  pool rear (1)       poker #2, player-banked baccarat + UTH, netherite slots ×2, chest
 * </pre>
 * Authored sizes are X×Y×Z with the entry on +z (the bar's 17-wide entry face is its long side, so in an
 * assembly it reaches 13 blocks east of the floor: footprint 44 × 47).
 */
public final class OverworldCasino {
	private OverworldCasino() {}

	/** Floor block row of every piece. */
	public static final int FLOOR_Y = 2;
	/** Floor row (first air row) of every piece: kits, games and NPCs stand here. */
	public static final int ROW = FLOOR_Y + 1;
	/** Height of the doorways between pieces. */
	public static final int DOOR_H = 4;

	/** Jigsaw names ({@code burmaldaholic:casino/<connector>}). */
	public static final String ENTRANCE_BACK = "burmaldaholic:casino/entrance_back";
	public static final String FLOOR_FRONT = "burmaldaholic:casino/floor_front";
	public static final String FLOOR_SIDE = "burmaldaholic:casino/floor_side";
	public static final String BAR_SIDE = "burmaldaholic:casino/bar_side";
	public static final String FLOOR_BACK = "burmaldaholic:casino/floor_back";
	public static final String REAR_FRONT = "burmaldaholic:casino/rear_front";
	public static final String EMPTY_POOL = "minecraft:empty";

	/** The six pieces: layout-id suffix, the pool they belong to and their weight in it. */
	public enum Part {
		ENTRANCE("entrance", "entrance", 1),
		FLOOR_A("floor_a", "floor", 1),
		FLOOR_B("floor_b", "floor", 1),
		BAR("bar", "bar", 1),
		OFFICE("office", "rear", 2),
		VIP("vip", "rear", 1);

		private final String suffix;
		private final String pool;
		private final int weight;

		Part(String suffix, String pool, int weight) {
			this.suffix = suffix;
			this.pool = pool;
			this.weight = weight;
		}

		public String suffix() {
			return suffix;
		}

		/** Pool name inside the style ({@code entrance}, {@code floor}, {@code bar}, {@code rear}). */
		public String pool() {
			return pool;
		}

		public int weight() {
			return weight;
		}

		public boolean start() {
			return this == ENTRANCE;
		}
	}

	/** Layout id of a part, e.g. {@code casino/tudor/floor_a}. */
	public static String layoutId(Style s, Part part) {
		return "casino/" + s.id() + "/" + part.suffix();
	}

	/** Pool id of a pool name, e.g. {@code burmaldaholic:worldgen/casino/tudor/floor}. */
	public static String poolId(Style s, String pool) {
		return "burmaldaholic:worldgen/casino/" + s.id() + "/" + pool;
	}

	/**
	 * The casino kind of the standalone structure. {@code CasinoKind.CASINO} is added by lane CB-1; until
	 * it exists the pieces fall back to {@code VILLAGE_CASINO} (resolved by name so this compiles either way).
	 */
	public static CasinoKind kind() {
		for (CasinoKind k : CasinoKind.values()) {
			if (k.name().equals("CASINO")) {
				return k;
			}
		}
		return CasinoKind.VILLAGE_CASINO;
	}

	/** All six pieces of a style, in {@link Part} order. */
	public static List<Piece> pieces(Style s) {
		List<Piece> out = new ArrayList<>();
		for (Part part : Part.values()) {
			out.add(piece(s, part));
		}
		return out;
	}

	public static Piece piece(Style s, Part part) {
		return switch (part) {
			case ENTRANCE -> entrance(s);
			case FLOOR_A -> floorA(s);
			case FLOOR_B -> floorB(s);
			case BAR -> bar(s);
			case OFFICE -> office(s);
			case VIP -> vip(s);
		};
	}

	// =====================================================================================================
	// shared Tudor vocabulary
	// =====================================================================================================

	private static Blueprint start(Style s, Part part, int sx, int sy, int sz) {
		return Blueprint.of(layoutId(s, part), kind(), sx, sy, sz, s.palette());
	}

	/**
	 * Half-timbered wall plane between {@code a0..a1} (x for walls along x, z otherwise) at the fixed
	 * coordinate {@code c}, rows {@code y0..y1}: FRAME posts every {@code every}, BEAM rails at {@code railY}
	 * and {@code y1}, and in every plain panel above the rail a diagonal FRAME brace (alternating / and \).
	 * Only overwrites WALL cells (openings, panes and decor stay).
	 */
	public static void halfTimber(Blueprint b, boolean alongX, int c, int a0, int a1, int y0, int y1, int every, int railY) {
		BlockSpec wall = b.palette().block(Role.WALL);
		BlockSpec beam = axis(b.palette().block(Role.BEAM), alongX ? "x" : "z");
		BlockSpec post = axis(b.palette().block(Role.FRAME), "y");
		for (int a = a0; a <= a1; a++) {
			boolean isPost = (a - a0) % every == 0 || a == a1;
			for (int y = y0; y <= y1; y++) {
				int x = alongX ? a : c, z = alongX ? c : a;
				if (!wall.equals(b.at(x, y, z))) {
					continue;
				}
				if (isPost) {
					b.set(x, y, z, post);
				} else if (y == railY || y == y1) {
					b.set(x, y, z, beam);
				}
			}
		}
		int panel = 0;
		for (int p0 = a0; p0 + every <= a1; p0 += every, panel++) {
			int inner0 = p0 + 1, inner1 = p0 + every - 1, w = inner1 - inner0 + 1, rows = y1 - 1 - railY;
			if (w < 2 || rows < 2) {
				continue;
			}
			boolean plain = true;
			for (int a = inner0; a <= inner1 && plain; a++) {
				for (int y = railY + 1; y < y1; y++) {
					int x = alongX ? a : c, z = alongX ? c : a;
					if (!wall.equals(b.at(x, y, z))) {
						plain = false;
						break;
					}
				}
			}
			if (!plain) {
				continue;
			}
			int n = Math.min(w, rows);
			int base = railY + 1 + (rows - n) / 2;
			for (int i = 0; i < n; i++) {
				int a = panel % 2 == 0 ? inner0 + i : inner1 - i;
				b.set(alongX ? a : c, base + i, alongX ? c : a, post);
			}
		}
	}

	public static BlockSpec axis(BlockSpec b, String axis) {
		return b.properties().containsKey("axis") || dev.nezo.burmaldaholic.worldgen.logic.blueprint.Shapes.hasAxis(b.name())
			? BlockSpec.of(b.name(), "axis", axis) : b;
	}

	/** Leaded window: PANE cells {@code w} wide, {@code h} high, with a BEAM sill under it. */
	public static void window(Blueprint b, boolean alongX, int c, int a, int y, int w, int h) {
		BlockSpec pane = b.palette().block(Role.PANE);
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				int x = alongX ? a + i : c, z = alongX ? c : a + i;
				b.set(x, y + j, z, pane);
			}
		}
	}

	/** Tudor chimney stack of WALL_BASE with a stone cap and two pots, rows y0..top. */
	public static void chimney(Blueprint b, int x, int z, int y0, int top) {
		b.fill(x, y0, z, x + 1, top - 1, z + 1, Role.WALL_BASE);
		b.fill(x, top - 3, z, x + 1, top - 3, z + 1, Role.FOUNDATION);
		b.fill(x, top, z, x + 1, top, z + 1, b.palette().get(Role.FOUNDATION).slab("double"));
		b.set(x, top + 1, z, BlockSpec.mc("flower_pot"));
		b.set(x + 1, top + 1, z + 1, BlockSpec.mc("flower_pot"));
	}

	/** Ridge finial: a brass spike (waxed lightning rod, never oxidises) on the ridge cell. */
	public static void finial(Blueprint b, int x, int y, int z) {
		b.set(x, y, z, BlockSpec.mc("waxed_lightning_rod", "facing", "up", "powered", "false", "waterlogged", "false"));
	}

	/** Gilded tower finial: a TRIM block with a brass spike on top. */
	public static void grandFinial(Blueprint b, int x, int y, int z) {
		b.set(x, y, z, Role.TRIM);
		finial(b, x, y + 1, z);
	}

	/**
	 * Gabled dormer on a WEST (x = 0) or NORTH (z = 0) roof slope whose eave sits on row {@code eaveY}: a
	 * 3-wide timbered face with a window, cheeks and a little gable roof running 4 cells into the roof.
	 */
	public static void dormer(Blueprint b, boolean west, int a0, int eaveY) {
		Material roof = b.palette().get(Role.ROOF);
		for (int i = 0; i < 3; i++) {
			for (int y = eaveY; y <= eaveY + 1; y++) {
				set(b, west, 0, y, a0 + i, i == 1 ? b.palette().block(Role.PANE) : axis(b.palette().block(Role.FRAME), "y"));
				for (int d = 1; d <= 2; d++) {
					set(b, west, d, y, a0 + i, i == 1 ? Blueprint.AIR : b.palette().block(Role.WALL));
				}
			}
		}
		for (int d = 0; d <= 3; d++) {
			set(b, west, d, eaveY + 2, a0, roof.stairs(west ? Facing.SOUTH : Facing.EAST, false));
			set(b, west, d, eaveY + 2, a0 + 2, roof.stairs(west ? Facing.NORTH : Facing.WEST, false));
			set(b, west, d, eaveY + 3, a0 + 1, roof.slab("bottom"));
		}
		set(b, west, 0, eaveY + 2, a0 + 1, b.palette().block(Role.WALL));
		set(b, west, 1, eaveY + 2, a0 + 1, b.palette().block(Role.WALL));
		set(b, west, 0, eaveY - 1, a0 + 1, b.palette().get(Role.ROOF_TRIM).stairs(west ? Facing.EAST : Facing.SOUTH, true));
	}

	private static void set(Blueprint b, boolean west, int depth, int y, int along, BlockSpec s) {
		if (west) {
			b.set(depth, y, along, s);
		} else {
			b.set(along, y, depth, s);
		}
	}

	/** Two wall banners in the style's colours flanking a point on a wall (suit-like patterns). */
	public static void bannerPair(Blueprint b, int xLeft, int xRight, int y, int z, Facing facing, Suit left, Suit right) {
		b.banner(xLeft, y, z, facing, "red", suitLayers(left));
		b.banner(xRight, y, z, facing, "red", suitLayers(right));
	}

	public static Blueprint.BannerLayer[] suitLayers(Suit suit) {
		String sym = switch (suit) {
			case DIAMOND -> "rhombus";
			case CLUB -> "flower";
			case HEART -> "circle";
			case SPADE -> "triangle_top";
		};
		return new Blueprint.BannerLayer[] {
			Blueprint.layer("border", "yellow"),
			Blueprint.layer(sym, suit.red() ? "white" : "black"),
			Blueprint.layer("stripe_bottom", "yellow"),
			Blueprint.layer("curly_border", "black")};
	}

	/** Rug: CARPET field with a CARPET_ACCENT border on the floor row (needs the floor below). */
	public static void rug(Blueprint b, int x0, int z0, int x1, int z1) {
		b.fill(x0, ROW, z0, x1, ROW, z1, Role.CARPET_ACCENT);
		if (x1 - x0 >= 2 && z1 - z0 >= 2) {
			b.fill(x0 + 1, ROW, z0 + 1, x1 - 1, ROW, z1 - 1, Role.CARPET);
		}
	}

	/**
	 * Casino carpet: CARPET field with a diagonal CARPET_ACCENT lattice (diamonds every {@code period}) on the
	 * floor row of the box.
	 */
	public static void casinoCarpet(Blueprint b, int x0, int z0, int x1, int z1, int period) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean line = Math.floorMod(x + z, period) == 0 || Math.floorMod(x - z, period) == 0;
				b.set(x, ROW, z, line ? b.palette().block(Role.CARPET_ACCENT) : b.palette().block(Role.CARPET));
			}
		}
	}

	/** Suit medallion: a 9 × 9 carpet with a CARPET_ACCENT ring, a cream field and the 7 × 7 suit. */
	public static void medallion(Blueprint b, Suit suit, int cx, int cz) {
		int h = Suit.SIZE / 2 + 1;
		b.fill(cx - h, ROW, cz - h, cx + h, ROW, cz + h, Role.CARPET_ACCENT);
		b.fill(cx - h + 1, ROW, cz - h + 1, cx + h - 1, ROW, cz + h - 1, BlockSpec.mc("white_carpet"));
		b.inlay(suit, cx - h + 1, ROW, cz - h + 1, BlockSpec.mc(suit.red() ? "red_carpet" : "black_carpet"));
	}

	/**
	 * Door throat: the 3-wide doorway at the wall cell ({@code x}, {@code z}) facing {@code out} and the two
	 * cells inside it stay walkable for {@link #DOOR_H} − 1 rows (nothing may stand in a door, not even a sofa).
	 */
	public static void throat(Blueprint b, int x, int z, Facing out) {
		Facing along = out.rotate(1);
		int ix = x - 2 * out.dx(), iz = z - 2 * out.dz();
		b.keepClear(x - along.dx(), ROW, z - along.dz(), ix + along.dx(), ROW + DOOR_H - 2, iz + along.dz());
	}

	/** Roof hook: the style's own roof, else the given default. */
	public static void roof(Style s, Blueprint b, Style.Hall hall, Runnable fallback) {
		if (!s.signature().roof(b, hall)) {
			fallback.run();
		}
	}

	/** Coffered ceiling: BEAM grid every {@code every} cells on row {@code y}. */
	public static void coffers(Blueprint b, int x0, int z0, int x1, int z1, int y, int every) {
		for (int x = x0; x <= x1; x += every) {
			b.beam(x, y, z0, x, y, z1, Role.BEAM);
		}
		for (int z = z0; z <= z1; z += every) {
			b.beam(x0, y, z, x1, y, z, Role.BEAM);
		}
	}

	/**
	 * Corner tower 3 × 3 (x0..x0+2, z0..z0+2) from the floor block row to {@code topY}: WALL_BASE with
	 * FOUNDATION quoins, TRIM bands, window slits glowing with a LIGHT core behind the pane, a crenellated
	 * parapet and a pyramid cap with a finial.
	 */
	public static void tower(Blueprint b, int x0, int z0, int topY) {
		Material roof = b.palette().get(Role.ROOF);
		b.fill(x0, FLOOR_Y, z0, x0 + 2, topY, z0 + 2, Role.WALL_BASE);
		for (int y = ROW; y <= topY; y++) {
			if (y % 2 == 1) {
				for (int[] c : new int[][] {{x0, z0}, {x0 + 2, z0}, {x0, z0 + 2}, {x0 + 2, z0 + 2}}) {
					b.set(c[0], y, c[1], Role.FOUNDATION);
				}
			}
		}
		for (int y : new int[] {ROW, ROW + 7, topY - 4}) {
			b.fill(x0, y, z0, x0 + 2, y, z0 + 2, Role.FOUNDATION);
		}
		b.fill(x0, topY, z0, x0 + 2, topY, z0 + 2, Role.TRIM);
		int cx = x0 + 1, cz = z0 + 1;
		for (int y : new int[] {ROW + 2, ROW + 3, ROW + 9, ROW + 10, topY - 2}) {
			b.set(cx, y, cz, Role.LIGHT);
			b.set(cx, y, z0 + 2, Role.PANE);
			b.set(x0, y, cz, Role.PANE);
			b.set(x0 + 2, y, cz, Role.PANE);
			b.set(cx, y, z0, Role.PANE);
		}
		for (int[] c : new int[][] {{x0, z0}, {x0 + 2, z0}, {x0, z0 + 2}, {x0 + 2, z0 + 2}}) {
			b.set(c[0], topY + 1, c[1], Role.WALL_BASE);
		}
		b.set(cx, topY + 1, z0, roof.stairs(Facing.SOUTH, false));
		b.set(cx, topY + 1, z0 + 2, roof.stairs(Facing.NORTH, false));
		b.set(x0, topY + 1, cz, roof.stairs(Facing.EAST, false));
		b.set(x0 + 2, topY + 1, cz, roof.stairs(Facing.WEST, false));
		b.set(cx, topY + 1, cz, Role.ROOF);
		b.set(cx, topY + 2, cz, roof.slab("bottom"));
		b.set(cx, topY + 2, cz, Role.ROOF);
		grandFinial(b, cx, topY + 3, cz);
	}

	// =====================================================================================================
	// ENTRANCE (start piece) — 31 × 29 × 17: lobby z 0..10, forecourt z 11..16
	// =====================================================================================================

	static Piece entrance(Style s) {
		Blueprint b = start(s, Part.ENTRANCE, 31, 29, 17);
		int top = 28;
		b.foundation(0, 0, 30, 16, 1);
		// forecourt: clear ground cover and trees in front of the facade, lay the path and a red carpet
		b.clear(0, ROW, 11, 30, 20, 16);
		b.fill(0, FLOOR_Y, 11, 30, FLOOR_Y, 16, Role.PATH);
		b.fill(12, FLOOR_Y, 11, 18, FLOOR_Y, 16, Role.FLOOR_ACCENT);

		// lobby: interior x 1..29, y 3..11, z 1..9
		Blueprint.Room lobby = b.room(0, FLOOR_Y, 0, 30, 12, 10);
		lobby.door(Facing.SOUTH, 15, 5, 5).door(Facing.NORTH, 15, 3, DOOR_H);
		for (int x : new int[] {5, 8, 21, 24}) {
			window(b, true, 10, x, ROW + 1, 2, 3);
		}
		for (int z : new int[] {2, 5}) {
			window(b, false, 0, z, ROW + 1, 2, 4);
			window(b, false, 30, z, ROW + 1, 2, 4);
		}
		halfTimber(b, true, 10, 0, 30, ROW + 1, 9, 3, ROW + 5);
		halfTimber(b, false, 0, 0, 10, ROW + 1, 11, 3, ROW + 6);
		halfTimber(b, false, 30, 0, 10, ROW + 1, 11, 3, ROW + 6);
		halfTimber(b, true, 0, 0, 30, ROW + 1, 11, 3, ROW + 6);

		// roofs: main gable along x (eaves y 13, ridge y 18), a tall cross gable crowning the marquee
		Style.Hall hall = new Style.Hall(Part.ENTRANCE, "lobby", 0, 0, 30, 10, 13, top);
		roof(s, b, hall, () -> {
			b.gableRoof(0, 0, 30, 10, 13, true, Role.ROOF, Role.WALL);
			for (int z = 0; z <= 10; z++) {
				b.stairs(0, 13 + Math.min(z, 10 - z), z, Role.ROOF_TRIM, z <= 5 ? Facing.SOUTH : Facing.NORTH, false);
				b.stairs(30, 13 + Math.min(z, 10 - z), z, Role.ROOF_TRIM, z <= 5 ? Facing.SOUTH : Facing.NORTH, false);
			}
			halfTimber(b, false, 0, 1, 9, 13, 17, 2, 13);
			halfTimber(b, false, 30, 1, 9, 13, 17, 2, 13);
			// crown: front cross gable, ridge along z, x 5..25 (rows 17..27)
			for (int i = 0; i <= 10; i++) {
				int y = 17 + i, xa = 5 + i, xb = 25 - i;
				for (int z = 2; z <= 11; z++) {
					if (xa == xb) {
						b.slab(xa, y, z, Role.ROOF, "bottom");
					} else {
						b.stairs(xa, y, z, z == 11 ? Role.ROOF_TRIM : Role.ROOF, Facing.EAST, false);
						b.stairs(xb, y, z, z == 11 ? Role.ROOF_TRIM : Role.ROOF, Facing.WEST, false);
					}
				}
				for (int x = xa + 1; x < xb; x++) {
					b.set(x, y, 10, Role.WALL);
					b.set(x, y, 2, Role.WALL);
				}
				if (xa == xb) {
					break;
				}
			}
			b.fill(4, 13, 10, 26, 16, 10, Role.WALL);
			// timber the crown's gable end: posts, collar beams, a rose window
			BlockSpec wallBlock = s.palette().block(Role.WALL);
			for (int gz : new int[] {10, 2}) {
				for (int x : new int[] {8, 12, 18, 22}) {
					for (int y = 17; y <= 27; y++) {
						if (wallBlock.equals(b.at(x, y, gz))) {
							b.set(x, y, gz, axis(s.palette().block(Role.FRAME), "y"));
						}
					}
				}
				for (int x = 7; x <= 23; x++) {
					for (int y : new int[] {19, 23}) {
						if (wallBlock.equals(b.at(x, y, gz))) {
							b.set(x, y, gz, axis(s.palette().block(Role.BEAM), "x"));
						}
					}
				}
				b.fill(14, 20, gz, 16, 22, gz, Role.PANE);
			}
			b.fill(15, 21, 3, 15, 21, 9, Role.LIGHT);
			b.fill(14, 20, 3, 16, 22, 9, Role.LIGHT);
			b.fill(4, 14, 9, 26, 16, 9, Role.WALL);
			grandFinial(b, 15, 27, 11);
			b.fill(0, 12, 0, 30, 12, 10, Role.CEILING);
		});
		chimney(b, 6, 2, 13, 22);
		chimney(b, 23, 2, 13, 22);
		// corner towers frame the facade
		tower(b, 0, 9, 21);
		tower(b, 28, 9, 21);

		// MARQUEE: "CASINO" 23 wide × 7 tall, raised glowing letters on a black board (y 10..16)
		b.marquee("CASINO", 15, 15, 10, true, 1);
		// portico, lamp posts, hedges, red carpet
		b.kit(Kits.portico(11, 4, 5), 10, ROW, 14, Facing.SOUTH);
		b.fill(14, ROW, 11, 16, ROW, 16, Role.CARPET);
		b.kit(Kits.lampPost(5), 6, ROW, 15, Facing.SOUTH);
		b.kit(Kits.lampPost(5), 24, ROW, 15, Facing.SOUTH);
		BlockSpec hedge = BlockSpec.mc("azalea_leaves", "distance", "7", "persistent", "true", "waterlogged", "false");
		BlockSpec bloom = BlockSpec.mc("flowering_azalea_leaves", "distance", "7", "persistent", "true", "waterlogged", "false");
		for (int x = 0; x <= 30; x++) {
			if (x <= 4 || x >= 26) {
				b.set(x, ROW, 16, x % 2 == 0 ? bloom : hedge);
			}
		}
		for (int x : new int[] {0, 2, 28, 30}) {
			b.set(x, ROW, 13, hedge);
			b.set(x, ROW, 14, bloom);
		}
		b.banner(1, ROW + 6, 12, Facing.SOUTH, "red", suitLayers(Suit.DIAMOND));
		b.banner(29, ROW + 6, 12, Facing.SOUTH, "red", suitLayers(Suit.CLUB));
		bannerPair(b, 11, 19, ROW + 3, 11, Facing.SOUTH, Suit.HEART, Suit.SPADE);

		// --- lobby interior -------------------------------------------------------------------------
		for (int x = 1; x <= 29; x++) {
			for (int z = 1; z <= 9; z++) {
				b.set(x, FLOOR_Y, z, (x + z) % 2 == 0 ? s.palette().block(Role.FLOOR_ACCENT) : s.palette().block(Role.FLOOR));
			}
		}
		medallion(b, Suit.CLUB, 15, 5);
		b.fill(14, ROW, 10, 16, ROW, 10, Role.CARPET);
		b.fill(14, ROW, 0, 16, ROW, 0, Role.CARPET);
		coffers(b, 1, 1, 29, 9, 11, 4);
		// cashier cage (left of the arch, the first stop) and the Croupier
		b.kit(Kits.cashierCage(Game.CASHIER), 7, ROW, 2, Facing.SOUTH);
		// lounge corner (right of the arch)
		b.kit(Kits.vipSofa(3), 22, ROW, 1, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 26, ROW, 1, Facing.SOUTH);
		b.kit(Kits.pottedPlant(), 20, ROW, 1, Facing.SOUTH);
		// wheel and plinko alcoves on the end walls
		b.kit(Kits.machineAlcove(Game.WHEEL), 1, ROW, 5, Facing.EAST);
		b.kit(Kits.machineAlcove(Game.PLINKO), 29, ROW, 5, Facing.WEST);
		// planters by the door
		b.kit(Kits.planter(), 3, ROW, 8, Facing.SOUTH);
		b.kit(Kits.planter(), 27, ROW, 8, Facing.SOUTH);
		// banners flanking the arch, sconces by the door
		bannerPair(b, 12, 18, ROW + 3, 1, Facing.SOUTH, Suit.SPADE, Suit.HEART);
		for (int x : new int[] {11, 19}) {
			b.kit(Kits.wallSconce(), x, ROW + 3, 9, Facing.NORTH);
		}
		b.kit(Kits.grandChandelier(4), 15, 11, 5, Facing.SOUTH);
		b.kit(Kits.chandelier(2), 7, 11, 6, Facing.SOUTH);
		b.kit(Kits.chandelier(2), 23, 11, 6, Facing.SOUTH);
		b.keepClear(13, ROW, 9, 17, ROW + 3, 11);
		throat(b, 15, 0, Facing.NORTH);
		b.aisle(14, ROW, 1, 16, 9);

		s.signature().decorate(b, Part.ENTRANCE);
		b.anchor(15, 10, 8);
		b.entry(15, ROW, 16);
		b.entry(15, ROW, 0);
		b.jigsaw(15, FLOOR_Y, 0, Facing.NORTH, ENTRANCE_BACK, FLOOR_FRONT, poolId(s, "floor"), s.palette().block(Role.FLOOR));
		return b.build();
	}

	// =====================================================================================================
	// GAMING FLOOR — 31 × 27 × 17, two variants
	// =====================================================================================================

	/**
	 * Hall shell shared by both floors: walls, doors, windows, timber, a hip roof with dormers and a
	 * clerestory lantern over the ring {@code cx0..cx1 × cz0..cz1} (glass rows 18..20) lighting the floor.
	 */
	private static Blueprint floorShell(Style s, Part part, int cx0, int cz0, int cx1, int cz1) {
		Blueprint b = start(s, part, 31, 27, 17);
		int top = 26;
		b.foundation(0, 0, 30, 16, 1);
		Blueprint.Room hall = b.room(0, FLOOR_Y, 0, 30, 14, 16);
		hall.door(Facing.SOUTH, 15, 3, DOOR_H).door(Facing.NORTH, 15, 3, DOOR_H).door(Facing.EAST, 8, 3, DOOR_H);
		for (int z : new int[] {1, 5, 9, 13}) {
			window(b, false, 0, z, ROW + 2, 2, 6);
		}
		for (int x : new int[] {1, 5, 24, 28}) {
			window(b, true, 0, x, ROW + 5, 2, 4);
		}
		halfTimber(b, false, 0, 0, 16, ROW + 1, 13, 4, ROW + 1);
		halfTimber(b, true, 0, 0, 30, ROW + 1, 13, 4, ROW + 1);
		halfTimber(b, false, 30, 0, 16, ROW + 1, 13, 4, ROW + 1);
		halfTimber(b, true, 16, 0, 30, ROW + 1, 13, 4, ROW + 1);
		Style.Hall roofHall = new Style.Hall(part, "hall", 0, 0, 30, 16, 14, top);
		roof(s, b, roofHall, () -> {
			b.hipRoof(0, 0, 30, 16, 14, 1, Role.ROOF_TRIM, false);
			b.hipRoof(1, 1, 29, 15, 15, 4, Role.ROOF, false);
			b.fill(5, 18, 5, 25, 18, 11, Role.ROOF); // roof deck around the lantern
			// light well up to the clerestory lantern
			b.fill(cx0, 14, cz0, cx1, 17, cz1, Role.WALL);
			b.clear(cx0 + 1, 14, cz0 + 1, cx1 - 1, 17, cz1 - 1);
			b.interior(cx0 + 1, 14, cz0 + 1, cx1 - 1, 17, cz1 - 1);
			b.clerestory(cx0, cz0, cx1, cz1, 18, 20);
			int layers = Math.min(cz1 - cz0, cx1 - cx0) / 2 + 1;
			b.hipRoof(cx0, cz0, cx1, cz1, 21, layers, Role.ROOF, true);
			int ridgeY = 21 + layers - 1;
			int rz = (cz0 + cz1) / 2;
			finial(b, cx0 + layers - 1, ridgeY + 1, rz);
			finial(b, cx1 - layers + 1, ridgeY + 1, rz);
			dormer(b, true, 2, 14);
			dormer(b, true, 12, 14);
		});
		chimney(b, 2, 7, 15, 23);
		return b;
	}

	/** Floor A: two rows of pods either side of a central runner with a suit medallion; slots on the back wall. */
	static Piece floorA(Style s) {
		Blueprint b = floorShell(s, Part.FLOOR_A, 5, 5, 25, 11);
		// floor finish: casino carpet, runner, medallion, carpet to the bar door
		casinoCarpet(b, 1, 2, 29, 15, 6);
		rug(b, 13, 0, 17, 16);
		b.fill(14, ROW, 0, 16, ROW, 16, Role.CARPET);
		b.fill(18, ROW, 7, 29, ROW, 9, Role.CARPET);
		medallion(b, Suit.SPADE, 15, 10);
		// slot banks on the back wall
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 6), 2, ROW, 1, Facing.SOUTH);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 4), 23, ROW, 1, Facing.SOUTH);
		// one plant in the corner: the gold bank's end cabinet (26, 1) keeps its side cell free
		b.kit(Kits.pottedPlant(), 28, ROW, 1, Facing.SOUTH);
		// table pods: row 1 (z 4) and row 2 (z 11), players stand south of the tables
		b.kit(Kits.tablePod(Game.CRAPS, null), 4, ROW, 4, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.ROULETTE, null), 10, ROW, 4, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.BACCARAT, NpcRole.BACCARAT_DEALER), 20, ROW, 4, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.POKER, null), 26, ROW, 4, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 4, ROW, 11, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.UTH, null), 26, ROW, 11, Facing.SOUTH);
		for (int[] t : new int[][] {{4, 4}, {10, 4}, {20, 4}, {26, 4}, {4, 11}, {26, 11}}) {
			b.kit(Kits.pendantLamp(6), t[0], 14, t[1], Facing.SOUTH);
		}
		// velvet ropes and planters frame the medallion, chip stacks by the pods
		b.kit(Kits.ropeBarrier(4), 9, ROW, 11, Facing.WEST);
		b.kit(Kits.ropeBarrier(4), 21, ROW, 14, Facing.EAST);
		b.kit(Kits.planter(), 2, ROW, 14, Facing.SOUTH);
		b.kit(Kits.planter(), 28, ROW, 14, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 8, ROW, 14, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 22, ROW, 14, Facing.SOUTH);
		b.kit(Kits.grandChandelier(10), 15, 22, 8, Facing.SOUTH);
		b.kit(Kits.chandelier(3), 15, 14, 2, Facing.SOUTH);
		bannerPair(b, 12, 18, ROW + 4, 1, Facing.SOUTH, Suit.CLUB, Suit.HEART);
		for (int z : new int[] {4, 8, 12}) {
			b.banner(1, ROW + 4, z, Facing.EAST, "red", suitLayers(z == 8 ? Suit.DIAMOND : Suit.SPADE));
		}
		b.aisle(14, ROW, 1, 16, 15);
		b.aisle(18, ROW, 7, 29, 9);
		return finishFloor(s, b, Part.FLOOR_A);
	}

	/** Floor B: the tables ring a dealers' pit in the middle; slots on the west and back walls. */
	static Piece floorB(Style s) {
		Blueprint b = floorShell(s, Part.FLOOR_B, 9, 5, 21, 11);
		// carpet loop around the pit, runner from the door, carpet to the bar door
		rug(b, 3, 2, 27, 14);
		b.fill(14, ROW, 0, 16, ROW, 16, Role.CARPET);
		b.fill(24, ROW, 7, 29, ROW, 9, Role.CARPET);
		// the pit: tables face outwards, dealers inside on a parquet floor
		b.fill(9, ROW, 6, 21, ROW, 10, Blueprint.AIR);
		b.fill(9, FLOOR_Y, 6, 21, FLOOR_Y, 10, Role.FLOOR_ACCENT);
		b.kit(Kits.tablePod(Game.CRAPS, null), 11, ROW, 5, Facing.NORTH);
		b.kit(Kits.tablePod(Game.ROULETTE, null), 19, ROW, 5, Facing.NORTH);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 11, ROW, 11, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.UTH, null), 19, ROW, 11, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.BACCARAT, NpcRole.BACCARAT_DEALER), 8, ROW, 8, Facing.WEST);
		b.kit(Kits.tablePod(Game.POKER, null), 22, ROW, 8, Facing.EAST);
		b.kit(Kits.ropeBarrier(3), 16, ROW, 5, Facing.NORTH); // x 14..16: clear of the craps / roulette felts
		b.kit(Kits.ropeBarrier(3), 14, ROW, 11, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 15, ROW, 8, Facing.SOUTH);
		b.kit(Kits.pottedPlant(), 13, ROW, 8, Facing.SOUTH);
		b.kit(Kits.pottedPlant(), 17, ROW, 8, Facing.SOUTH);
		for (int[] t : new int[][] {{11, 5}, {19, 5}, {11, 11}, {19, 11}, {8, 8}, {22, 8}}) {
			b.kit(Kits.pendantLamp(6), t[0], 14, t[1], Facing.SOUTH);
		}
		// slots: copper on the west wall, gold on the back wall right of the arch
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 6), 1, ROW, 11, Facing.EAST);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 4), 18, ROW, 1, Facing.SOUTH);
		b.kit(Kits.planter(), 28, ROW, 2, Facing.SOUTH);
		b.kit(Kits.planter(), 28, ROW, 14, Facing.SOUTH);
		b.kit(Kits.planter(), 2, ROW, 14, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 9, ROW, 1, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 11, ROW, 1, Facing.SOUTH);
		b.kit(Kits.grandChandelier(10), 15, 22, 8, Facing.SOUTH);
		b.kit(Kits.chandelier(3), 26, 14, 13, Facing.SOUTH);
		b.kit(Kits.chandelier(3), 4, 14, 4, Facing.SOUTH);
		b.kit(Kits.chandelier(3), 27, 14, 4, Facing.SOUTH);
		bannerPair(b, 4, 8, ROW + 4, 1, Facing.SOUTH, Suit.DIAMOND, Suit.CLUB);
		b.aisle(14, ROW, 12, 16, 15);
		b.aisle(24, ROW, 7, 29, 9);
		return finishFloor(s, b, Part.FLOOR_B);
	}

	private static Piece finishFloor(Style s, Blueprint b, Part part) {
		throat(b, 15, 16, Facing.SOUTH);
		throat(b, 15, 0, Facing.NORTH);
		throat(b, 30, 8, Facing.EAST);
		s.signature().decorate(b, part);
		b.entry(15, ROW, 16);
		b.entry(15, ROW, 0);
		b.entry(30, ROW, 8);
		BlockSpec floor = s.palette().block(Role.FLOOR);
		b.jigsaw(15, FLOOR_Y, 16, Facing.SOUTH, FLOOR_FRONT, ENTRANCE_BACK, EMPTY_POOL, floor);
		b.jigsaw(30, FLOOR_Y, 8, Facing.EAST, FLOOR_SIDE, BAR_SIDE, poolId(s, "bar"), floor);
		b.jigsaw(15, FLOOR_Y, 0, Facing.NORTH, FLOOR_BACK, REAR_FRONT, poolId(s, "rear"), floor);
		return b.build();
	}

	// =====================================================================================================
	// BAR — 17 × 20 × 13 (entry on the 17-wide +z face)
	// =====================================================================================================

	static Piece bar(Style s) {
		Blueprint b = start(s, Part.BAR, 17, 20, 13);
		int top = 19;
		b.foundation(0, 0, 16, 12, 1);
		Blueprint.Room room = b.room(0, FLOOR_Y, 0, 16, 11, 12);
		room.door(Facing.SOUTH, 8, 3, DOOR_H);
		for (int x : new int[] {5, 10}) {
			window(b, true, 0, x, ROW + 5, 2, 2);
		}
		for (int z : new int[] {1, 5, 9}) {
			window(b, false, 0, z, ROW + 2, 2, 4);
			window(b, false, 16, z, ROW + 2, 2, 4);
		}
		halfTimber(b, false, 0, 0, 12, ROW + 1, 10, 4, ROW + 1);
		halfTimber(b, false, 16, 0, 12, ROW + 1, 10, 4, ROW + 1);
		halfTimber(b, true, 0, 0, 16, ROW + 1, 10, 4, ROW + 1);
		Style.Hall hall = new Style.Hall(Part.BAR, "bar", 0, 0, 16, 12, 11, top);
		roof(s, b, hall, () -> {
			int ridge = b.gableRoof(0, 0, 16, 12, 11, true, Role.ROOF, Role.WALL);
			for (int z = 0; z <= 12; z++) {
				int y = 11 + Math.min(z, 12 - z);
				b.stairs(0, y, z, Role.ROOF_TRIM, z <= 6 ? Facing.SOUTH : Facing.NORTH, false);
				b.stairs(16, y, z, Role.ROOF_TRIM, z <= 6 ? Facing.SOUTH : Facing.NORTH, false);
			}
			halfTimber(b, false, 0, 1, 11, 11, 16, 2, 11);
			halfTimber(b, false, 16, 1, 11, 11, 16, 2, 11);
			b.set(0, 14, 6, Role.PANE);
			b.set(16, 14, 6, Role.PANE);
			finial(b, 0, ridge + 1, 6);
			finial(b, 16, ridge + 1, 6);
			b.fill(1, 11, 1, 15, 11, 11, Role.CEILING);
			dormer(b, false, 7, 11);
		});
		chimney(b, 12, 1, 11, 19 - 2);
		b.fill(1, FLOOR_Y, 1, 15, FLOOR_Y, 11, Role.FLOOR);
		rug(b, 6, 5, 11, 11);
		// the bar along the back wall
		b.kit(Kits.bar(8), 2, ROW, 3, Facing.SOUTH);
		for (int x = 3; x <= 9; x += 3) {
			b.kit(Kits.chandelier(2), x, 11, 4, Facing.SOUTH);
		}
		// Loan Shark booth (back right): desk, money-green banners
		b.fill(12, ROW, 3, 15, ROW, 3, s.palette().get(Role.COUNTER).slab("top"));
		b.set(12, ROW + 1, 3, BlockSpec.mc("potted_red_tulip"));
		b.set(15, ROW + 1, 3, BlockSpec.mc("green_candle", "candles", "3", "lit", "true", "waterlogged", "false"));
		b.set(13, ROW + 1, 3, BlockSpec.mc("yellow_candle", "candles", "4", "lit", "true", "waterlogged", "false"));
		b.npc(NpcRole.LOAN_SHARK, 14, ROW, 2, Facing.SOUTH);
		b.banner(12, ROW + 3, 1, Facing.SOUTH, "green", Blueprint.layer("border", "yellow"), Blueprint.layer("circle", "yellow"));
		b.banner(15, ROW + 3, 1, Facing.SOUTH, "green", Blueprint.layer("border", "yellow"), Blueprint.layer("rhombus", "yellow"));
		b.kit(Kits.chandelier(2), 14, 11, 4, Facing.SOUTH);
		// duel pit (west), gold slots (east wall)
		b.kit(Kits.duelPit(), 3, ROW, 8, Facing.SOUTH);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 3), 15, ROW, 6, Facing.WEST);
		b.kit(Kits.grandChandelier(3), 8, 11, 7, Facing.SOUTH);
		b.kit(Kits.chandelier(2), 3, 11, 8, Facing.SOUTH);
		b.kit(Kits.planter(), 14, ROW, 10, Facing.SOUTH);
		b.kit(Kits.chipStacks(), 11, ROW, 11, Facing.SOUTH);
		throat(b, 8, 12, Facing.SOUTH);
		s.signature().decorate(b, Part.BAR);
		b.entry(8, ROW, 12);
		b.jigsaw(8, FLOOR_Y, 12, Facing.SOUTH, BAR_SIDE, FLOOR_SIDE, EMPTY_POOL, s.palette().block(Role.FLOOR));
		return b.build();
	}

	// =====================================================================================================
	// OFFICE — 15 × 16 × 9 (rear, weight 2)
	// =====================================================================================================

	static Piece office(Style s) {
		Blueprint b = start(s, Part.OFFICE, 15, 16, 9);
		int top = 15;
		b.foundation(0, 0, 14, 8, 1);
		Blueprint.Room room = b.room(0, FLOOR_Y, 0, 14, 9, 8);
		room.door(Facing.SOUTH, 7, 3, DOOR_H);
		window(b, false, 0, 3, ROW + 1, 2, 3);
		window(b, false, 14, 3, ROW + 1, 2, 3);
		window(b, true, 0, 2, ROW + 2, 2, 2);
		window(b, true, 0, 11, ROW + 2, 2, 2);
		halfTimber(b, true, 0, 0, 14, ROW + 1, 8, 7, ROW + 1);
		halfTimber(b, false, 0, 0, 8, ROW + 1, 8, 4, ROW + 1);
		halfTimber(b, false, 14, 0, 8, ROW + 1, 8, 4, ROW + 1);
		Style.Hall hall = new Style.Hall(Part.OFFICE, "office", 0, 0, 14, 8, 9, top);
		roof(s, b, hall, () -> {
			b.hipRoof(0, 0, 14, 8, 9, 1, Role.ROOF_TRIM, false);
			b.hipRoof(1, 1, 13, 7, 10, 4, Role.ROOF);
			finial(b, 4, 14, 4);
			finial(b, 10, 14, 4);
		});
		b.fill(1, FLOOR_Y, 1, 13, FLOOR_Y, 7, Role.FLOOR);
		rug(b, 4, 3, 10, 7);
		// bookshelves along the back wall around a fireplace, desk, safe, chest
		b.fill(1, ROW, 1, 13, ROW + 2, 1, BlockSpec.mc("bookshelf"));
		b.kit(Kits.fireplace(6), 7, ROW, 2, Facing.SOUTH);
		Material desk = s.palette().get(Role.STOOL);
		b.fill(5, ROW, 4, 9, ROW, 4, desk.slab("top"));
		b.set(5, ROW + 1, 4, BlockSpec.mc("lantern", "hanging", "false", "waterlogged", "false"));
		b.set(9, ROW + 1, 4, BlockSpec.mc("potted_fern"));
		b.set(7, ROW + 1, 4, BlockSpec.mc("red_candle", "candles", "2", "lit", "true", "waterlogged", "false"));
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, 2, ROW, 4, Facing.EAST);
		b.fill(1, ROW, 6, 1, ROW + 1, 7, BlockSpec.mc("chiseled_deepslate"));
		b.set(13, ROW, 6, BlockSpec.mc("barrel", "facing", "west", "open", "false"));
		b.set(13, ROW + 1, 6, BlockSpec.mc("potted_fern"));
		b.kit(Kits.chandelier(2), 7, 9, 5, Facing.SOUTH);
		b.banner(4, ROW + 4, 1, Facing.SOUTH, "red", suitLayers(Suit.HEART));
		b.banner(10, ROW + 4, 1, Facing.SOUTH, "red", suitLayers(Suit.SPADE));
		s.signature().decorate(b, Part.OFFICE);
		throat(b, 7, 8, Facing.SOUTH);
		b.entry(7, ROW, 8);
		b.jigsaw(7, FLOOR_Y, 8, Facing.SOUTH, REAR_FRONT, FLOOR_BACK, EMPTY_POOL, s.palette().block(Role.FLOOR));
		return b.build();
	}

	// =====================================================================================================
	// VIP — 17 × 18 × 13 (rear, weight 1)
	// =====================================================================================================

	static Piece vip(Style s) {
		Blueprint b = start(s, Part.VIP, 17, 18, 13);
		int top = 17;
		b.foundation(0, 0, 16, 12, 1);
		Blueprint.Room room = b.room(0, FLOOR_Y, 0, 16, 11, 12);
		room.door(Facing.SOUTH, 8, 3, DOOR_H);
		for (int z : new int[] {4, 8}) {
			window(b, false, 0, z, ROW + 2, 1, 5);
			window(b, false, 16, z, ROW + 2, 1, 5);
		}
		window(b, true, 0, 7, ROW + 4, 3, 3);
		halfTimber(b, false, 0, 0, 12, ROW + 1, 10, 4, ROW + 1);
		halfTimber(b, false, 16, 0, 12, ROW + 1, 10, 4, ROW + 1);
		halfTimber(b, true, 0, 0, 16, ROW + 1, 10, 4, ROW + 1);
		Style.Hall hall = new Style.Hall(Part.VIP, "vip", 0, 0, 16, 12, 11, top);
		roof(s, b, hall, () -> {
			b.hipRoof(0, 0, 16, 12, 11, 1, Role.ROOF_TRIM, false);
			b.hipRoof(1, 1, 15, 11, 12, 5, Role.ROOF);
			finial(b, 6, 17 - 1, 6);
			finial(b, 10, 17 - 1, 6);
		});
		b.fill(1, FLOOR_Y, 1, 15, FLOOR_Y, 11, Role.FLOOR);
		rug(b, 1, 1, 15, 11);
		coffers(b, 1, 1, 15, 11, 10, 3);
		// games: poker #2 at the back, player-banked baccarat and UTH, netherite slots
		b.kit(Kits.tablePod(Game.POKER, null), 8, ROW, 3, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.BACCARAT_PLAYER_BANKED, null), 4, ROW, 7, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.UTH_PLAYER_BANKED, null), 12, ROW, 7, Facing.SOUTH);
		b.kit(Kits.slotBank(Game.SLOTS_NETHERITE, 2), 13, ROW, 1, Facing.SOUTH);
		b.kit(Kits.fireplace(8), 3, ROW, 2, Facing.SOUTH);
		for (int[] t : new int[][] {{8, 3}, {4, 7}, {12, 7}}) {
			b.kit(Kits.pendantLamp(4), t[0], 11, t[1], Facing.SOUTH);
		}
		b.kit(Kits.vipSofa(2), 4, ROW, 11, Facing.NORTH);
		b.kit(Kits.vipSofa(2), 14, ROW, 11, Facing.NORTH);
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, 15, ROW, 4, Facing.WEST);
		// velvet-rope arch at the door
		b.fill(6, ROW, 11, 6, ROW + 3, 11, Role.ACCENT);
		b.fill(10, ROW, 11, 10, ROW + 3, 11, Role.ACCENT);
		b.fill(6, ROW + 4, 11, 10, ROW + 4, 11, Role.TRIM);
		b.set(8, ROW + 4, 11, Role.BULB);
		bannerPair(b, 6, 10, ROW + 3, 12, Facing.SOUTH, Suit.DIAMOND, Suit.HEART);
		throat(b, 8, 12, Facing.SOUTH);
		s.signature().decorate(b, Part.VIP);
		b.entry(8, ROW, 12);
		b.jigsaw(8, FLOOR_Y, 12, Facing.SOUTH, REAR_FRONT, FLOOR_BACK, EMPTY_POOL, s.palette().block(Role.FLOOR));
		return b.build();
	}
}
