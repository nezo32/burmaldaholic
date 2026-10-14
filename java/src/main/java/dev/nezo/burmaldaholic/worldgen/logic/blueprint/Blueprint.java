package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blueprint DSL (PURE; architecture doc docs/architecture/casino-buildings.md §3). A thin, palette-aware
 * layer over {@link Grid} that produces a {@link Piece} = the existing {@link Layout} (exported unchanged by
 * {@code TemplateExporter}) + metadata for {@link Checks}.
 *
 * <p>Coordinates are template-local and inclusive. The piece's FRONT is +z (south), like every v0.1
 * layout: a viewer outside the front looks north, their left is -x (west). Text reads with x increasing.
 * Rows: y = 0 is the lowest template row. Cells never written stay {@code null} = structure void (the
 * world block is kept), so terrain around a piece is untouched unless the piece clears it explicitly.
 *
 * <p>Jigsaw pieces are placed with vanilla's "known shape" setting, i.e. the game does NOT recompute
 * fence / pane / stair connections after placement. {@link #build()} therefore runs a finishing pass that
 * writes those connection properties exactly like the game would ({@link #finishShapes()}).
 *
 * <p>Determinism: the blueprint has no randomness of its own; a variant that wants variety takes a
 * {@code long variantSeed} and draws from {@code new java.util.SplittableRandom(seed)} in a fixed order.
 */
public final class Blueprint {
	public static final BlockSpec AIR = BlockSpec.mc("air");

	private final String id;
	private final CasinoKind kind;
	private final Palette palette;
	private final Grid grid;
	private final List<Layout.PlacedMarker> markers = new ArrayList<>();
	private final List<Layout.Jigsaw> jigsaws = new ArrayList<>();
	private final List<Piece.GameSpot> games = new ArrayList<>();
	private final List<Geometry.Box> keepClear = new ArrayList<>();
	private final List<Geometry.Box> interior = new ArrayList<>();
	private final List<Geometry.Box> aisles = new ArrayList<>();
	private final List<Vec> entries = new ArrayList<>();
	private final Map<Vec, Map<String, Object>> blockNbt = new LinkedHashMap<>();

	private Blueprint(String id, CasinoKind kind, int sx, int sy, int sz, Palette palette) {
		this.id = id;
		this.kind = kind;
		this.palette = palette;
		this.grid = new Grid(sx, sy, sz);
	}

	/** {@code id} is the layout id, e.g. {@code casino/tudor/entrance} → template {@code burmaldaholic:worldgen/casino/tudor/entrance}. */
	public static Blueprint of(String id, CasinoKind kind, int sx, int sy, int sz, Palette palette) {
		return new Blueprint(id, kind, sx, sy, sz, palette).compactTables(Checks.PENDING_CLEARANCE.contains(id));
	}

	public String id() {
		return id;
	}

	private boolean compactTables;

	/**
	 * Legacy pieces ({@link Checks#PENDING_CLEARANCE}, built before multi-block tables) place compact 1 × 1 tables
	 * (no {@code part} property → the block's default SINGLE state) and the legacy table pod, so their layouts stay
	 * byte-identical until the casino redesign replaces them. Every other piece gets full 3 × 2 tables.
	 */
	public boolean compactTables() {
		return compactTables;
	}

	/** Overrides the {@link #compactTables()} default (tests). */
	public Blueprint compactTables(boolean compact) {
		this.compactTables = compact;
		return this;
	}

	public Palette palette() {
		return palette;
	}

	public Vec size() {
		return grid.size();
	}

	public boolean inside(int x, int y, int z) {
		return grid.inside(tx(x), y, z);
	}

	public BlockSpec at(int x, int y, int z) {
		BlockSpec b = grid.get(tx(x), y, z);
		return mirrored() ? Mirror.x(b) : b;
	}

	// ---- mirror symmetry -----------------------------------------------------------------------------

	/** Twice the mirror axis x while {@link #mirrorX} runs its second pass; {@link Integer#MIN_VALUE} = no mirror. */
	private int mirrorAxis2 = Integer.MIN_VALUE;

	private boolean mirrored() {
		return mirrorAxis2 != Integer.MIN_VALUE;
	}

	/** Template x of a drawing x (identity unless a mirrored pass runs). */
	private int tx(int x) {
		return mirrored() ? mirrorAxis2 - x : x;
	}

	private Facing tf(Facing f) {
		return mirrored() && (f == Facing.EAST || f == Facing.WEST) ? f.opposite() : f;
	}

	private Geometry.Box tbox(Geometry.Box b) {
		if (!mirrored()) {
			return b;
		}
		return new Geometry.Box(Math.min(tx(b.minX()), tx(b.maxX())), b.minY(), b.minZ(), Math.max(tx(b.minX()), tx(b.maxX())), b.maxY(),
			b.maxZ());
	}

	/**
	 * Mirror-symmetry helper: runs {@code draw} once as written, then once more mirrored about the plane x =
	 * {@code axis2} / 2 ({@code axis2} = twice the axis, so an odd-width building of width w uses {@code w - 1}). Every
	 * verb drawn in the second pass lands mirrored: cells, block states (facing / shape / hinge / east-west arms /
	 * rotation, see {@link Mirror}), games and their player sides, NPC and chest markers, keep-clear, interior and aisle
	 * boxes, entries and jigsaws. Reads ({@link #at}) are mirrored too, so verbs that inspect the grid behave the same.
	 */
	public Blueprint mirrorX(int axis2, java.util.function.Consumer<Blueprint> draw) {
		if (mirrored()) {
			throw new IllegalStateException("mirrorX does not nest");
		}
		draw.accept(this);
		mirrorAxis2 = axis2;
		try {
			draw.accept(this);
		} finally {
			mirrorAxis2 = Integer.MIN_VALUE;
		}
		return this;
	}

	// ---- raw verbs ----------------------------------------------------------------------------------

	public Blueprint set(int x, int y, int z, BlockSpec b) {
		int mx = tx(x);
		grid.set(mx, y, z, mirrored() ? Mirror.x(b) : b);
		blockNbt.remove(new Vec(mx, y, z));
		return this;
	}

	public Blueprint set(int x, int y, int z, Role r) {
		return set(x, y, z, palette.block(r));
	}

	/** Sets the cell only when it is inside the piece (decor that may hang over an edge). */
	public Blueprint setIfInside(int x, int y, int z, BlockSpec b) {
		return inside(x, y, z) ? set(x, y, z, b) : this;
	}

	public Blueprint fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, b);
				}
			}
		}
		return this;
	}

	public Blueprint fill(int x0, int y0, int z0, int x1, int y1, int z1, Role r) {
		return fill(x0, y0, z0, x1, y1, z1, palette.block(r));
	}

	public Blueprint clear(int x0, int y0, int z0, int x1, int y1, int z1) {
		return fill(x0, y0, z0, x1, y1, z1, AIR);
	}

	/** Stairs of the role's material; {@code facing} is the high (back) side, {@code top} = upside-down. */
	public Blueprint stairs(int x, int y, int z, Role r, Facing facing, boolean top) {
		return set(x, y, z, palette.get(r).stairs(facing, top));
	}

	/** Slab of the role's material ({@code bottom}, {@code top} or {@code double}). */
	public Blueprint slab(int x, int y, int z, Role r, String type) {
		return set(x, y, z, palette.get(r).slab(type));
	}

	/** Replaces every cell of the box that currently holds {@code from} (exact state) with {@code to}. */
	public Blueprint replace(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec from, BlockSpec to) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					if (from.equals(at(x, y, z))) {
						set(x, y, z, to);
					}
				}
			}
		}
		return this;
	}

	/** Sets a block together with its block-entity NBT (written into the template's {@code blocks[].nbt}). */
	public Blueprint setNbt(int x, int y, int z, BlockSpec block, Map<String, Object> nbt) {
		set(x, y, z, block);
		blockNbt.put(new Vec(tx(x), y, z), nbt);
		return this;
	}

	// ---- structure verbs ----------------------------------------------------------------------------

	/** Solid FOUNDATION from y = 0 up to {@code topY} (the skirt that hides terrain under a slope). */
	public Blueprint foundation(int x0, int z0, int x1, int z1, int topY) {
		return fill(x0, 0, z0, x1, topY, z1, Role.FOUNDATION);
	}

	/**
	 * A room: FLOOR at {@code y0}, walls (WALL_BASE on the first row, WALL above) up to {@code y1 - 1},
	 * CEILING at {@code y1}, air inside. The inside counts as interior for the light check.
	 */
	public Room room(int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(x0, y0, z0, x1, y0, z1, Role.FLOOR);
		clear(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
		wallRing(x0, y0 + 1, z0, x1, y0 + 1, z1, palette.block(Role.WALL_BASE));
		if (y1 - 1 > y0 + 1) {
			wallRing(x0, y0 + 2, z0, x1, y1 - 1, z1, palette.block(Role.WALL));
		}
		fill(x0, y1, z0, x1, y1, z1, Role.CEILING);
		Geometry.Box inside = new Geometry.Box(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
		interior.add(tbox(inside));
		return new Room(this, new Geometry.Box(x0, y0, z0, x1, y1, z1));
	}

	private void wallRing(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				set(x, y, z0, b);
				set(x, y, z1, b);
			}
			for (int z = z0; z <= z1; z++) {
				set(x0, y, z, b);
				set(x1, y, z, b);
			}
		}
	}

	/** Registers an extra interior volume (a mezzanine, a clerestory) for the light check. */
	public Blueprint interior(int x0, int y0, int z0, int x1, int y1, int z1) {
		interior.add(tbox(Geometry.Box.corners(new Vec(x0, y0, z0), new Vec(x1, y1, z1))));
		return this;
	}

	/** Hip roof: {@code layers} rings of ROOF stairs stepping inwards from the eave box, flat ROOF cap. */
	public Blueprint hipRoof(int x0, int z0, int x1, int z1, int baseY, int layers) {
		return hipRoof(x0, z0, x1, z1, baseY, layers, Role.ROOF);
	}

	/**
	 * Horizontal or vertical beam of a role along the box's long axis: logs, pillars and other axis blocks
	 * get {@code axis=x|y|z} so a beam lies the right way; other blocks are written as they are.
	 */
	public Blueprint beam(int x0, int y0, int z0, int x1, int y1, int z1, Role role) {
		String axis = Math.abs(x1 - x0) > 0 ? "x" : Math.abs(z1 - z0) > 0 ? "z" : "y";
		BlockSpec b = palette.block(role);
		return fill(x0, y0, z0, x1, y1, z1, Shapes.hasAxis(b.name()) ? BlockSpec.of(b.name(), "axis", axis) : b);
	}

	/** Hip roof of any stair-shaped role (ROOF, ROOF_TRIM …); the last ring is capped flat. */
	public Blueprint hipRoof(int x0, int z0, int x1, int z1, int baseY, int layers, Role role) {
		return hipRoof(x0, z0, x1, z1, baseY, layers, role, true);
	}

	/** Hip roof; {@code cap = false} leaves the inside of the last ring open (a lantern or tower sits there). */
	public Blueprint hipRoof(int x0, int z0, int x1, int z1, int baseY, int layers, Role role, boolean cap) {
		Material m = palette.get(role);
		for (int i = 0; i < layers; i++) {
			int ax = x0 + i, az = z0 + i, bx = x1 - i, bz = z1 - i, y = baseY + i;
			if (ax > bx || az > bz) {
				break;
			}
			if (ax == bx || az == bz) { // a ridge line: slabs
				fill(ax, y, az, bx, y, bz, m.slab("bottom"));
				break;
			}
			for (int x = ax; x <= bx; x++) {
				set(x, y, az, m.stairs(Facing.SOUTH, false));
				set(x, y, bz, m.stairs(Facing.NORTH, false));
			}
			for (int z = az + 1; z < bz; z++) {
				set(ax, y, z, m.stairs(Facing.EAST, false));
				set(bx, y, z, m.stairs(Facing.WEST, false));
			}
			if (cap && i == layers - 1 && ax + 1 <= bx - 1 && az + 1 <= bz - 1) {
				fill(ax + 1, y, az + 1, bx - 1, y, bz - 1, role);
			}
		}
		return this;
	}

	/**
	 * Gable roof whose ridge runs along x ({@code ridgeAlongX}) or z: stairs climb from both long eaves to a
	 * slab ridge; the two gable ends are filled with {@code gableFill} (null = leave open). Returns the
	 * ridge row y. Used for dormers, the porch and the bar wing; lane CB-3 adds more shapes in {@code Roofs}.
	 */
	public int gableRoof(int x0, int z0, int x1, int z1, int baseY, boolean ridgeAlongX, Role role, Role gableFill) {
		Material m = palette.get(role);
		int lo = ridgeAlongX ? z0 : x0, hi = ridgeAlongX ? z1 : x1;
		int y = baseY;
		for (int i = 0; lo + i <= hi - i; i++, y++) {
			int a = lo + i, c = hi - i;
			for (int t = ridgeAlongX ? x0 : z0; t <= (ridgeAlongX ? x1 : z1); t++) {
				if (a == c) {
					setXZ(ridgeAlongX, t, y, a, m.slab("bottom"));
				} else {
					setXZ(ridgeAlongX, t, y, a, m.stairs(ridgeAlongX ? Facing.SOUTH : Facing.EAST, false));
					setXZ(ridgeAlongX, t, y, c, m.stairs(ridgeAlongX ? Facing.NORTH : Facing.WEST, false));
				}
			}
			if (gableFill != null && c - a >= 2) {
				for (int u = a + 1; u < c; u++) {
					int e0 = ridgeAlongX ? x0 : z0, e1 = ridgeAlongX ? x1 : z1;
					setXZ(ridgeAlongX, e0, y, u, palette.block(gableFill));
					setXZ(ridgeAlongX, e1, y, u, palette.block(gableFill));
				}
			}
			if (a == c || a + 1 == c) {
				break;
			}
		}
		return y;
	}

	private void setXZ(boolean alongX, int along, int y, int across, BlockSpec b) {
		if (alongX) {
			set(along, y, across, b);
		} else {
			set(across, y, along, b);
		}
	}

	/**
	 * Clerestory lantern: a raised band of GLASS between FRAME posts (every 3 cells) on the ring
	 * {@code x0..x1 × z0..z1} for rows {@code y0..y1}, air inside (registered as interior), the ceiling under
	 * it opened. The caller roofs it (e.g. {@link #hipRoof}) at {@code y1 + 1}.
	 */
	public Blueprint clerestory(Geometry.Box box) {
		return clerestory(box.minX(), box.minZ(), box.maxX(), box.maxZ(), box.minY(), box.maxY());
	}

	/** {@link #clerestory(Geometry.Box)} with explicit ring {@code x0..x1 × z0..z1} and glass rows {@code y0..y1}. */
	public Blueprint clerestory(int x0, int z0, int x1, int z1, int y0, int y1) {
		clear(x0 + 1, y0 - 1, z0 + 1, x1 - 1, y1, z1 - 1);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				set(x, y, z0, (x - x0) % 3 == 0 ? palette.block(Role.FRAME) : palette.block(Role.GLASS));
				set(x, y, z1, (x - x0) % 3 == 0 ? palette.block(Role.FRAME) : palette.block(Role.GLASS));
			}
			for (int z = z0; z <= z1; z++) {
				set(x0, y, z, (z - z0) % 3 == 0 ? palette.block(Role.FRAME) : palette.block(Role.GLASS));
				set(x1, y, z, (z - z0) % 3 == 0 ? palette.block(Role.FRAME) : palette.block(Role.GLASS));
			}
		}
		for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			fill(c[0], y0, c[1], c[0], y1, c[1], Role.FRAME);
		}
		fill(x0, y0 - 1, z0, x1, y0 - 1, z0, Role.BEAM);
		fill(x0, y0 - 1, z1, x1, y0 - 1, z1, Role.BEAM);
		fill(x0, y0 - 1, z0, x0, y0 - 1, z1, Role.BEAM);
		fill(x1, y0 - 1, z0, x1, y0 - 1, z1, Role.BEAM);
		interior.add(tbox(new Geometry.Box(x0 + 1, y0 - 1, z0 + 1, x1 - 1, y1, z1 - 1)));
		return this;
	}

	/**
	 * Pixel-font sign on a wall plane facing {@code Facing.SOUTH} (z = {@code z}), letters alternating
	 * NEON_A / NEON_B on SIGN_BACK, BULB border every second cell. {@code topY} is the top text row.
	 * Only the front (+z) is supported here; side signs come with lane CB-3 (Facades).
	 */
	public Blueprint marquee(String word, int centerX, int topY, int z) {
		return marquee(word, centerX, topY, z, false);
	}

	/**
	 * Marquee with {@code raised} letters: the SIGN_BACK board stays at {@code z} and the letters stand one
	 * block proud of it at {@code z + 1} (depth reads from far away and at grazing angles); the bulb
	 * border is raised too. Returns the board box via the blueprint for chaining.
	 */
	public Blueprint marquee(String word, int centerX, int topY, int z, boolean raised) {
		return marquee(word, centerX, topY, z, raised, 0);
	}

	/**
	 * Marquee with {@code spacing} empty columns between letters (an odd total width centres exactly on an
	 * odd facade: "CASINO" with spacing 1 is 21 wide + border = 23).
	 */
	public Blueprint marquee(String word, int centerX, int topY, int z, boolean raised, int spacing) {
		Layouts.Sign sign = Layouts.signPixels(word);
		int width = sign.width() + spacing * (word.length() - 1);
		int left = centerX - width / 2;
		int x0 = left - 1, x1 = left + width, y0 = topY - 5, y1 = topY + 1;
		int lz = raised ? z + 1 : z;
		fill(x0, y0, z, x1, y1, z, Role.SIGN_BACK);
		for (Layouts.SignPixel px : sign.pixels()) {
			set(left + px.col() + px.letter() * spacing, topY - px.row(), lz, px.letter() % 2 == 0 ? Role.NEON_A : Role.NEON_B);
		}
		for (int x = x0; x <= x1; x += 2) {
			set(x, y0, lz, Role.BULB);
			set(x, y1, lz, Role.BULB);
		}
		for (int y = y0; y <= y1; y += 2) {
			set(x0, y, lz, Role.BULB);
			set(x1, y, lz, Role.BULB);
		}
		if (raised) { // bulbs alternate with dark sockets so the border reads as a chaser
			for (int x = x0 + 1; x < x1; x += 2) {
				set(x, y0, lz, Role.SIGN_BACK);
				set(x, y1, lz, Role.SIGN_BACK);
			}
			for (int y = y0 + 1; y < y1; y += 2) {
				set(x0, y, lz, Role.SIGN_BACK);
				set(x1, y, lz, Role.SIGN_BACK);
			}
		}
		return this;
	}

	// ---- signage and floor art -----------------------------------------------------------------------

	/** Dye colours valid for banners, carpets, concrete … (vanilla ids). */
	public static final List<String> DYES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
		"gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/** One banner layer: vanilla pattern id path ({@code rhombus}, {@code flower}, {@code circle} …) + dye. */
	public record BannerLayer(String pattern, String color) {
		public BannerLayer {
			if (!DYES.contains(color)) {
				throw new IllegalArgumentException("not a dye colour: " + color);
			}
			if (pattern.contains(":")) {
				throw new IllegalArgumentException("pattern without namespace please: " + pattern);
			}
		}
	}

	public static BannerLayer layer(String pattern, String color) {
		return new BannerLayer(pattern, color);
	}

	/**
	 * Wall banner hanging on the wall block behind it ({@code facing} = the side the banner shows to;
	 * the supporting wall is at {@code -facing}). Patterns are written as block-entity NBT in the 26.2
	 * component format ({@code patterns: [{pattern: "minecraft:rhombus", color: "red"}]}); newer versions
	 * upgrade it through DFU.
	 */
	public Blueprint banner(int x, int y, int z, Facing facing, String baseColor, BannerLayer... layers) {
		if (!DYES.contains(baseColor)) {
			throw new IllegalArgumentException("not a dye colour: " + baseColor);
		}
		BlockSpec block = BlockSpec.mc(baseColor + "_wall_banner", "facing", facing.id());
		Map<String, Object> nbt = Nbt.compound();
		nbt.put("id", "minecraft:banner");
		List<Map<String, Object>> list = new ArrayList<>();
		for (BannerLayer l : layers) {
			Map<String, Object> e = Nbt.compound();
			e.put("color", l.color());
			e.put("pattern", "minecraft:" + l.pattern());
			list.add(e);
		}
		nbt.put("patterns", Nbt.TagList.compounds(list));
		return setNbt(x, y, z, block, nbt);
	}

	/** Card suits for {@link #inlay}: 7 × 7 pixel glyphs (5 × 5 reads as a cross from eye height). */
	public enum Suit {
		SPADE("...#...", "..###..", ".#####.", "#######", "##.#.##", "...#...", "..###.."),
		HEART(".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#..."),
		DIAMOND("...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."),
		CLUB("..###..", "..###..", "##.#.##", "#######", "##.#.##", "...#...", "..###..");

		/** Glyph edge length in cells. */
		public static final int SIZE = 7;

		private final String[] rows;

		Suit(String... rows) {
			this.rows = rows;
		}

		public boolean pixel(int col, int row) {
			return rows[row].charAt(col) == '#';
		}

		public boolean red() {
			return this == HEART || this == DIAMOND;
		}
	}

	/**
	 * {@link Suit#SIZE}² pixel suit laid flat in the plane y = {@code y}, top-left at ({@code x}, {@code z}); it
	 * reads upright for someone standing at the front (+z) looking north. Only the suit's pixels are written.
	 */
	public Blueprint inlay(Suit suit, int x, int y, int z, BlockSpec pixel) {
		for (int row = 0; row < Suit.SIZE; row++) {
			for (int col = 0; col < Suit.SIZE; col++) {
				if (suit.pixel(col, row)) {
					set(x + col, y, z + row, pixel);
				}
			}
		}
		return this;
	}

	public Blueprint inlay(Suit suit, int x, int y, int z, Role pixel) {
		return inlay(suit, x, y, z, palette.block(pixel));
	}

	// ---- game blocks, markers, jigsaws --------------------------------------------------------------

	/**
	 * A game block standing on the floor row {@code y}; {@code playerSide} is where players stand and is
	 * written as the block's {@code facing} (CasinoTableBlock faces the placer). Game blocks need no
	 * block-entity NBT: the block entity is created fresh on placement and resolves its preset from the
	 * casino record (WorldgenPresets). Tables ({@link Game#tableGame()} non-null) stamp their whole 3 × 2 footprint
	 * ({@code TableFootprint.cells}) with {@code part} + {@code facing}; the anchor is the MAIN (front-centre) cell.
	 * Upright machines ({@link Game#machineKind()}) stamp every cell of {@code MachineKind.localCells} (width × height,
	 * one deep); the anchor is the bottom-centre MAIN cell.
	 */
	public Blueprint game(Game g, int x, int y, int z, Facing playerSide) {
		TableGame tg = g.tableGame();
		// resolved in template space: a mirrored pass stamps the mirrored table, not a mirror image of its cells
		int mx = tx(x);
		Facing f = tf(playerSide);
		dev.nezo.burmaldaholic.core.table.parts.MachineKind mk = g.machineKind();
		if (mk != null && !compactTables) {
			// an upright multi-block machine (MT-3): one row deep, main = bottom centre, players in front
			for (dev.nezo.burmaldaholic.core.table.parts.LocalCell c : mk.localCells()) {
				int[] o = c.offset(f.dx(), f.dz());
				grid.set(mx + o[0], y + o[1], z + o[2], BlockSpec.of(g.blockId(), "facing", f.id(), "part", c.part().getSerializedName()));
				blockNbt.remove(new Vec(mx + o[0], y + o[1], z + o[2]));
			}
		} else if (tg == null || compactTables) {
			grid.set(mx, y, z, BlockSpec.of(g.blockId(), "facing", f.id()));
			blockNbt.remove(new Vec(mx, y, z));
		} else {
			// a full multi-block table (MT-DESIGN §6): every footprint cell with its part; the spot keeps the main pos
			for (TableFootprint.Cell c : TableFootprint.cells(tg, f.dx(), f.dz())) {
				grid.set(mx + c.dx(), y, z + c.dz(), BlockSpec.of(g.blockId(), "facing", f.id(), "part", c.part().getSerializedName()));
				blockNbt.remove(new Vec(mx + c.dx(), y, z + c.dz()));
			}
		}
		games.add(new Piece.GameSpot(g, new Vec(mx, y, z), f));
		return this;
	}

	/** Every game placed so far (template space). */
	public List<Piece.GameSpot> games() {
		return List.copyOf(games);
	}

	/**
	 * Floor cells (template space, {x, y, z}) of a game's glowing corner tiles: under the players' corner cells r ±1 at
	 * f +1 (a table's walkway ends, a machine's front diagonals), on the floor-block row. The light path tile → the cell
	 * above it → one row up → over to the centre of the walkway → over the main block reaches the cell above the game
	 * at 15 − 4 = 11 without entering the board / headroom volume (rows y + 1..y + 3 of the zone stay air).
	 */
	public static List<Vec> uplightCells(Piece.GameSpot s) {
		List<Vec> out = new ArrayList<>();
		Facing f = s.playerSide();
		for (int r : new int[] {-1, 1}) {
			int[] o = TableFootprint.cellOffset(r, 1, f.dx(), f.dz());
			out.add(new Vec(s.pos().x() + o[0], s.pos().y() - 1, s.pos().z() + o[1]));
		}
		return out;
	}

	/**
	 * Auto table lighting (CASINO-REDESIGN §3.2): every game placed so far gets {@link #uplightCells} set to UPLIGHT —
	 * a light block flush with the floor, uncovered (a carpet on the cell above is removed) — where the floor there is
	 * a full block. Light ≥ 10 over every game then never depends on lamps inside the board clearance. Call it after
	 * the games and their kits (carpets) are placed.
	 */
	public Blueprint uplightGames() {
		BlockSpec tile = palette.block(Role.UPLIGHT);
		for (Piece.GameSpot s : games) {
			for (Vec v : uplightCells(s)) {
				if (!grid.inside(v.x(), v.y(), v.z()) || !Shapes.fullCube(grid.get(v.x(), v.y(), v.z()))) {
					continue;
				}
				BlockSpec above = grid.inside(v.x(), v.y() + 1, v.z()) ? grid.get(v.x(), v.y() + 1, v.z()) : null;
				if (above != null && Shapes.isCarpet(above.name())) {
					grid.set(v.x(), v.y() + 1, v.z(), AIR);
				} else if (above != null && !above.is("minecraft:air")) {
					continue; // something stands on that cell (a stool, a planter): leave the floor alone
				}
				grid.set(v.x(), v.y(), v.z(), tile);
			}
		}
		return this;
	}

	public Blueprint keepClear(int x0, int y0, int z0, int x1, int y1, int z1) {
		keepClear.add(tbox(Geometry.Box.corners(new Vec(x0, y0, z0), new Vec(x1, y1, z1))));
		return this;
	}

	/**
	 * A walking aisle on the floor row {@code y}: every cell of the box (2 rows tall) must stay walkable
	 * (air or carpet) and its narrow side must be ≥ 2 wide ({@link Checks}).
	 */
	public Blueprint aisle(int x0, int y, int z0, int x1, int z1) {
		Geometry.Box box = Geometry.Box.corners(new Vec(x0, y, z0), new Vec(x1, y + 1, z1));
		aisles.add(tbox(box));
		return this;
	}

	/**
	 * A cell (on the floor row) where people walk into this piece: the front door or a jigsaw doorway.
	 * {@link Checks} proves every game's player cell is reachable on foot from an entry.
	 */
	public Blueprint entry(int x, int y, int z) {
		entries.add(new Vec(tx(x), y, z));
		return this;
	}

	public Blueprint marker(int x, int y, int z, Markers.Marker m) {
		Markers.Marker mm = switch (m) {
			case Markers.Npc n -> new Markers.Npc(n.role(), tf(n.facing()));
			case Markers.Chest c -> new Markers.Chest(c.table(), tf(c.facing()));
			case Markers.Anchor a -> a;
		};
		markers.add(new Layout.PlacedMarker(new Vec(tx(x), y, z), mm));
		return this;
	}

	/** NPC standing on the floor row {@code y} (the marker sits one row above its feet, v0.1 convention). */
	public Blueprint npc(NpcRole role, int x, int y, int z, Facing facing) {
		return marker(x, y + 1, z, new Markers.Npc(role, facing));
	}

	public Blueprint chest(CasinoLoot.Table table, int x, int y, int z, Facing facing) {
		return marker(x, y, z, new Markers.Chest(table, facing));
	}

	/** Registers the casino (one per assembled casino: put it in the start piece only). */
	public Blueprint anchor(int x, int y, int z) {
		return marker(x, y, z, new Markers.Anchor(id));
	}

	/**
	 * Horizontal jigsaw connector in the outer wall/floor, pointing {@code out}. {@code finalState} is the
	 * block that replaces the jigsaw after assembly (usually the floor block at that cell).
	 */
	public Blueprint jigsaw(int x, int y, int z, Facing out, String name, String target, String pool, BlockSpec finalState) {
		jigsaws.add(new Layout.Jigsaw(new Vec(tx(x), y, z), tf(out).id() + "_up", name, target, pool,
			(mirrored() ? Mirror.x(finalState) : finalState).toString(), "aligned"));
		return this;
	}

	/** Places a kit at the anchor with the given front. */
	public Blueprint kit(Kit kit, int x, int y, int z, Facing front) {
		kit.apply(this, x, y, z, front);
		return this;
	}

	public Piece build() {
		finish();
		Layout layout = new Layout(id, kind, grid, markers, jigsaws, blockNbt);
		return new Piece(layout, games, keepClear, interior, aisles, entries);
	}

	private boolean finished;

	/** Runs the finishing pass ({@link #finishShapes()}) once; a later {@link #build()} / {@link #slice} reuses it. */
	public Blueprint finish() {
		if (!finished) {
			finishShapes();
			finished = true;
		}
		return this;
	}

	/**
	 * A z-slab {@code z0..z1} of this (finished) blueprint as its own blueprint {@code newId} (z shifted by -z0): the
	 * cells, block NBT, markers, games (by their main block; a footprint must not cross the cut), entries and the
	 * keep-clear / interior / aisle boxes clipped to the slab. A big building is designed in one piece and cut into
	 * jigsaw pieces this way; connection shapes are finished on the whole building first, so the cut is seamless.
	 * Add the connectors with {@link #jigsaw} on the slice.
	 */
	public Blueprint slice(String newId, int z0, int z1) {
		finish();
		Vec s = grid.size();
		Blueprint out = new Blueprint(newId, kind, s.x(), s.y(), z1 - z0 + 1, palette).compactTables(compactTables);
		out.finished = true;
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = z0; z <= z1; z++) {
					out.grid.set(x, y, z - z0, grid.get(x, y, z));
				}
			}
		}
		blockNbt.forEach((v, nbt) -> {
			if (v.z() >= z0 && v.z() <= z1) {
				out.blockNbt.put(new Vec(v.x(), v.y(), v.z() - z0), nbt);
			}
		});
		for (Layout.PlacedMarker m : markers) {
			if (m.pos().z() >= z0 && m.pos().z() <= z1) {
				out.markers.add(new Layout.PlacedMarker(new Vec(m.pos().x(), m.pos().y(), m.pos().z() - z0), m.marker()));
			}
		}
		for (Piece.GameSpot g : games) {
			if (g.pos().z() < z0 || g.pos().z() > z1) {
				continue;
			}
			for (int[] c : TableClearance.footprint(g)) {
				if (c[1] < z0 || c[1] > z1) {
					throw new IllegalStateException(g.game() + " at " + g.pos() + " crosses the cut " + z0 + ".." + z1);
				}
			}
			out.games.add(new Piece.GameSpot(g.game(), new Vec(g.pos().x(), g.pos().y(), g.pos().z() - z0), g.playerSide()));
		}
		clip(keepClear, out.keepClear, z0, z1);
		clip(interior, out.interior, z0, z1);
		clip(aisles, out.aisles, z0, z1);
		for (Vec e : entries) {
			if (e.z() >= z0 && e.z() <= z1) {
				out.entries.add(new Vec(e.x(), e.y(), e.z() - z0));
			}
		}
		return out;
	}

	private static void clip(List<Geometry.Box> from, List<Geometry.Box> to, int z0, int z1) {
		for (Geometry.Box b : from) {
			int a = Math.max(b.minZ(), z0), c = Math.min(b.maxZ(), z1);
			if (a <= c) {
				to.add(new Geometry.Box(b.minX(), b.minY(), a - z0, b.maxX(), b.maxY(), c - z0));
			}
		}
	}

	// ---- finishing pass: connection properties ------------------------------------------------------

	/**
	 * Writes the neighbour-dependent properties the game would compute (jigsaw pieces are placed with a
	 * "known shape" and never updated): fence / pane / iron-bar arms and stair corner shapes.
	 */
	void finishShapes() {
		Vec s = grid.size();
		for (int y = 0; y < s.y(); y++) {
			for (int z = 0; z < s.z(); z++) {
				for (int x = 0; x < s.x(); x++) {
					BlockSpec b = grid.get(x, y, z);
					if (b == null) {
						continue;
					}
					String n = b.name();
					if (Shapes.isFence(n) || Shapes.isPane(n)) {
						grid.set(x, y, z, connect(b, x, y, z));
					} else if (Shapes.isWall(n)) {
						grid.set(x, y, z, connectWall(b, x, y, z));
					}
				}
			}
		}
		for (int y = 0; y < s.y(); y++) {
			for (int z = 0; z < s.z(); z++) {
				for (int x = 0; x < s.x(); x++) {
					BlockSpec b = grid.get(x, y, z);
					if (b != null && Shapes.isStairs(b.name())) {
						grid.set(x, y, z, with(b, "shape", stairShape(b, x, y, z)));
					}
				}
			}
		}
	}

	private BlockSpec connect(BlockSpec b, int x, int y, int z) {
		Map<String, String> p = new LinkedHashMap<>(b.properties());
		for (Facing f : Facing.values()) {
			int nx = x + f.dx(), nz = z + f.dz();
			BlockSpec n = grid.inside(nx, y, nz) ? grid.get(nx, y, nz) : null;
			p.put(f.id(), Boolean.toString(Shapes.connects(b.name(), n, f.opposite())));
		}
		p.putIfAbsent("waterlogged", "false");
		return new BlockSpec(b.name(), p);
	}

	/**
	 * Wall arms like vanilla's WallBlock: a side connects to walls, panes, bars, fence gates and full blocks; it is
	 * {@code tall} when the block above covers its top (a slab rail, a full block), else {@code low}; the post
	 * ({@code up}) stays at ends, corners, crossings and under lanterns / rods, and drops on straight runs.
	 */
	private BlockSpec connectWall(BlockSpec b, int x, int y, int z) {
		Map<String, String> p = new LinkedHashMap<>(b.properties());
		BlockSpec above = grid.inside(x, y + 1, z) ? grid.get(x, y + 1, z) : null;
		boolean covered = above != null && (Shapes.fullCube(above) || above.name().endsWith("_slab") && !"top".equals(
			above.properties().get("type")) || Shapes.isStairs(above.name()) && "bottom".equals(above.properties().get("half")));
		boolean[] con = new boolean[4];
		for (Facing f : Facing.values()) {
			int nx = x + f.dx(), nz = z + f.dz();
			BlockSpec n = grid.inside(nx, y, nz) ? grid.get(nx, y, nz) : null;
			con[f.ordinal()] = Shapes.connects(b.name(), n, f.opposite());
			p.put(f.id(), con[f.ordinal()] ? covered ? "tall" : "low" : "none");
		}
		boolean n = con[Facing.NORTH.ordinal()], e = con[Facing.EAST.ordinal()], so = con[Facing.SOUTH.ordinal()], w = con[Facing.WEST.ordinal()];
		boolean straight = n && so && !e && !w || e && w && !n && !so;
		boolean postOverride = above != null && (above.name().endsWith("lantern") || above.name().endsWith("torch")
			|| above.name().endsWith("end_rod") || Shapes.isWall(above.name()));
		p.put("up", Boolean.toString(postOverride || !straight));
		p.putIfAbsent("waterlogged", "false");
		return new BlockSpec(b.name(), p);
	}

	private String stairShape(BlockSpec b, int x, int y, int z) {
		Facing facing = Facing.byId(b.properties().getOrDefault("facing", "north"));
		String half = b.properties().getOrDefault("half", "bottom");
		BlockSpec behind = stairAt(x + facing.dx(), y, z + facing.dz(), half);
		if (behind != null) {
			Facing f2 = Facing.byId(behind.properties().get("facing"));
			if (axis(f2) != axis(facing) && canTakeShape(b, x, y, z, f2.opposite())) {
				return f2 == facing.rotate(-1) ? "outer_left" : "outer_right";
			}
		}
		BlockSpec front = stairAt(x - facing.dx(), y, z - facing.dz(), half);
		if (front != null) {
			Facing f3 = Facing.byId(front.properties().get("facing"));
			if (axis(f3) != axis(facing) && canTakeShape(b, x, y, z, f3)) {
				return f3 == facing.rotate(-1) ? "inner_left" : "inner_right";
			}
		}
		return "straight";
	}

	private boolean canTakeShape(BlockSpec b, int x, int y, int z, Facing face) {
		int nx = x + face.dx(), nz = z + face.dz();
		BlockSpec n = grid.inside(nx, y, nz) ? grid.get(nx, y, nz) : null;
		return n == null || !Shapes.isStairs(n.name())
			|| !n.properties().get("facing").equals(b.properties().get("facing"))
			|| !n.properties().get("half").equals(b.properties().get("half"));
	}

	private BlockSpec stairAt(int x, int y, int z, String half) {
		if (!grid.inside(x, y, z)) {
			return null;
		}
		BlockSpec n = grid.get(x, y, z);
		return n != null && Shapes.isStairs(n.name()) && half.equals(n.properties().get("half")) ? n : null;
	}

	private static boolean axis(Facing f) {
		return f == Facing.EAST || f == Facing.WEST;
	}

	static BlockSpec with(BlockSpec b, String key, String value) {
		Map<String, String> p = new LinkedHashMap<>(b.properties());
		p.put(key, value);
		return new BlockSpec(b.name(), p);
	}

	// ---- rooms --------------------------------------------------------------------------------------

	/** Fluent helpers for one room's walls. Sides: FRONT = +z, BACK = -z, LEFT = -x, RIGHT = +x. */
	public static final class Room {
		private final Blueprint b;
		private final Geometry.Box box;

		Room(Blueprint b, Geometry.Box box) {
			this.b = b;
			this.box = box;
		}

		public Geometry.Box box() {
			return box;
		}

		/** Opening of {@code width} × {@code height} centred on {@code center} along the wall of {@code side}. */
		public Room door(Facing side, int center, int width, int height) {
			int lo = center - width / 2, hi = lo + width - 1, y0 = box.minY() + 1, y1 = box.minY() + height;
			switch (side) {
				case SOUTH -> b.clear(lo, y0, box.maxZ(), hi, y1, box.maxZ());
				case NORTH -> b.clear(lo, y0, box.minZ(), hi, y1, box.minZ());
				case WEST -> b.clear(box.minX(), y0, lo, box.minX(), y1, hi);
				case EAST -> b.clear(box.maxX(), y0, lo, box.maxX(), y1, hi);
			}
			return this;
		}

		/** Pane windows every {@code every} cells along {@code side}, rows sill..sill+height-1 above the floor. */
		public Room windows(Facing side, int every, int sill, int height) {
			return windows(side, every, sill, height, 1);
		}

		/** Like {@link #windows(Facing, int, int, int)} with {@code width} panes side by side per window. */
		public Room windows(Facing side, int every, int sill, int height, int width) {
			boolean alongX = side == Facing.NORTH || side == Facing.SOUTH;
			BlockSpec pane = BlockSpec.of(b.palette.get(Role.PANE).full(),
				"east", Boolean.toString(alongX), "west", Boolean.toString(alongX),
				"north", Boolean.toString(!alongX), "south", Boolean.toString(!alongX), "waterlogged", "false");
			int from = (alongX ? box.minX() : box.minZ()) + 2, to = (alongX ? box.maxX() : box.maxZ()) - 2;
			for (int t0 = from; t0 + width - 1 <= to; t0 += every) {
				for (int t = t0; t < t0 + width; t++) {
					for (int y = box.minY() + sill; y < box.minY() + sill + height; y++) {
						switch (side) {
							case SOUTH -> b.set(t, y, box.maxZ(), pane);
							case NORTH -> b.set(t, y, box.minZ(), pane);
							case WEST -> b.set(box.minX(), y, t, pane);
							case EAST -> b.set(box.maxX(), y, t, pane);
						}
					}
				}
			}
			return this;
		}

		/** FRAME pilasters at the corners and every {@code every} cells, BEAM lintel under the ceiling. */
		public Room frame(int every) {
			int y0 = box.minY() + 1, y1 = box.maxY() - 1;
			for (int x = box.minX(); x <= box.maxX(); x += every) {
				b.fill(x, y0, box.minZ(), x, y1, box.minZ(), Role.FRAME);
				b.fill(x, y0, box.maxZ(), x, y1, box.maxZ(), Role.FRAME);
			}
			for (int z = box.minZ(); z <= box.maxZ(); z += every) {
				b.fill(box.minX(), y0, z, box.minX(), y1, z, Role.FRAME);
				b.fill(box.maxX(), y0, z, box.maxX(), y1, z, Role.FRAME);
			}
			for (int[] c : new int[][] {{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()}}) {
				b.fill(c[0], y0, c[1], c[0], y1, c[1], Role.FRAME);
			}
			return this;
		}

		/** Horizontal BEAM band around the room at {@code dy} rows above the floor (Tudor rails, sill lines). */
		public Room band(int dy, Role role) {
			int y = box.minY() + dy;
			for (int x = box.minX(); x <= box.maxX(); x++) {
				bandCell(x, y, box.minZ(), role);
				bandCell(x, y, box.maxZ(), role);
			}
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				bandCell(box.minX(), y, z, role);
				bandCell(box.maxX(), y, z, role);
			}
			return this;
		}

		private void bandCell(int x, int y, int z, Role role) {
			BlockSpec cur = b.at(x, y, z);
			if (cur != null && !Checks.air(cur) && !Shapes.isPane(cur.name())) {
				b.set(x, y, z, role);
			}
		}

		public Blueprint done() {
			return b;
		}
	}
}
