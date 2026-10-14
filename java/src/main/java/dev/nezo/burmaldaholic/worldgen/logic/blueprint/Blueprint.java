package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.List;

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

	private Blueprint(String id, CasinoKind kind, int sx, int sy, int sz, Palette palette) {
		this.id = id;
		this.kind = kind;
		this.palette = palette;
		this.grid = new Grid(sx, sy, sz);
	}

	/** {@code id} is the layout id, e.g. {@code casino/tudor/entrance} → template {@code burmaldaholic:worldgen/casino/tudor/entrance}. */
	public static Blueprint of(String id, CasinoKind kind, int sx, int sy, int sz, Palette palette) {
		return new Blueprint(id, kind, sx, sy, sz, palette);
	}

	public Palette palette() {
		return palette;
	}

	public Vec size() {
		return grid.size();
	}

	public BlockSpec at(int x, int y, int z) {
		return grid.get(x, y, z);
	}

	// ---- raw verbs ----------------------------------------------------------------------------------

	public Blueprint set(int x, int y, int z, BlockSpec b) {
		grid.set(x, y, z, b);
		return this;
	}

	public Blueprint set(int x, int y, int z, Role r) {
		return set(x, y, z, palette.block(r));
	}

	public Blueprint fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		grid.box(x0, y0, z0, x1, y1, z1, b);
		return this;
	}

	public Blueprint fill(int x0, int y0, int z0, int x1, int y1, int z1, Role r) {
		return fill(x0, y0, z0, x1, y1, z1, palette.block(r));
	}

	public Blueprint clear(int x0, int y0, int z0, int x1, int y1, int z1) {
		return fill(x0, y0, z0, x1, y1, z1, AIR);
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
		grid.walls(x0, y0 + 1, z0, x1, y0 + 1, z1, palette.block(Role.WALL_BASE));
		if (y1 - 1 > y0 + 1) {
			grid.walls(x0, y0 + 2, z0, x1, y1 - 1, z1, palette.block(Role.WALL));
		}
		fill(x0, y1, z0, x1, y1, z1, Role.CEILING);
		Geometry.Box inside = new Geometry.Box(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
		interior.add(inside);
		return new Room(this, new Geometry.Box(x0, y0, z0, x1, y1, z1));
	}

	/** Hip roof: {@code layers} rings of ROOF stairs stepping inwards from the eave box, flat ROOF cap. */
	public Blueprint hipRoof(int x0, int z0, int x1, int z1, int baseY, int layers) {
		Material m = palette.get(Role.ROOF);
		for (int i = 0; i < layers; i++) {
			int ax = x0 + i, az = z0 + i, bx = x1 - i, bz = z1 - i, y = baseY + i;
			if (ax > bx || az > bz) {
				break;
			}
			for (int x = ax; x <= bx; x++) {
				grid.set(x, y, az, m.stairs(Facing.SOUTH, false));
				grid.set(x, y, bz, m.stairs(Facing.NORTH, false));
			}
			for (int z = az + 1; z < bz; z++) {
				grid.set(ax, y, z, m.stairs(Facing.EAST, false));
				grid.set(bx, y, z, m.stairs(Facing.WEST, false));
			}
			if (i == layers - 1 && ax + 1 <= bx - 1 && az + 1 <= bz - 1) {
				fill(ax + 1, y, az + 1, bx - 1, y, bz - 1, Role.ROOF);
			}
		}
		return this;
	}

	/**
	 * Pixel-font sign on a wall plane facing {@code Facing.SOUTH} (z = {@code z}), letters alternating
	 * NEON_A / NEON_B on SIGN_BACK, BULB border every second cell. {@code topY} is the top text row.
	 * Only the front (+z) is supported in the skeleton; side signs come with lane CB-3 (Facades).
	 */
	public Blueprint marquee(String word, int centerX, int topY, int z) {
		Layouts.Sign sign = Layouts.signPixels(word);
		int left = centerX - sign.width() / 2;
		int x0 = left - 1, x1 = left + sign.width(), y0 = topY - 5, y1 = topY + 1;
		fill(x0, y0, z, x1, y1, z, Role.SIGN_BACK);
		for (Layouts.SignPixel px : sign.pixels()) {
			set(left + px.col(), topY - px.row(), z, px.letter() % 2 == 0 ? Role.NEON_A : Role.NEON_B);
		}
		for (int x = x0; x <= x1; x += 2) {
			set(x, y0, z, Role.BULB);
			set(x, y1, z, Role.BULB);
		}
		for (int y = y0; y <= y1; y += 2) {
			set(x0, y, z, Role.BULB);
			set(x1, y, z, Role.BULB);
		}
		return this;
	}

	// ---- game blocks, markers, jigsaws --------------------------------------------------------------

	/**
	 * A game block standing on the floor row {@code y}; {@code playerSide} is where players stand and is
	 * written as the block's {@code facing} (CasinoTableBlock faces the placer). Game blocks need no
	 * block-entity NBT: the block entity is created fresh on placement and resolves its preset from the
	 * casino record (WorldgenPresets).
	 */
	public Blueprint game(Game g, int x, int y, int z, Facing playerSide) {
		set(x, y, z, BlockSpec.of(g.blockId(), "facing", playerSide.id()));
		games.add(new Piece.GameSpot(g, new Vec(x, y, z), playerSide));
		return this;
	}

	public Blueprint keepClear(int x0, int y0, int z0, int x1, int y1, int z1) {
		keepClear.add(Geometry.Box.corners(new Vec(x0, y0, z0), new Vec(x1, y1, z1)));
		return this;
	}

	public Blueprint marker(int x, int y, int z, Markers.Marker m) {
		markers.add(new Layout.PlacedMarker(new Vec(x, y, z), m));
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
		jigsaws.add(new Layout.Jigsaw(new Vec(x, y, z), out.id() + "_up", name, target, pool, finalState.toString(), "rigid"));
		return this;
	}

	/** Places a kit at the anchor with the given front. */
	public Blueprint kit(Kit kit, int x, int y, int z, Facing front) {
		kit.apply(this, x, y, z, front);
		return this;
	}

	public Piece build() {
		Layout layout = new Layout(id, kind, grid, markers, jigsaws);
		return new Piece(layout, games, keepClear, interior);
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
			boolean alongX = side == Facing.NORTH || side == Facing.SOUTH;
			BlockSpec pane = BlockSpec.of(b.palette.get(Role.PANE).full(),
				"east", Boolean.toString(alongX), "west", Boolean.toString(alongX),
				"north", Boolean.toString(!alongX), "south", Boolean.toString(!alongX), "waterlogged", "false");
			int from = (alongX ? box.minX() : box.minZ()) + 2, to = (alongX ? box.maxX() : box.maxZ()) - 2;
			for (int t = from; t <= to; t += every) {
				for (int y = box.minY() + sill; y < box.minY() + sill + height; y++) {
					switch (side) {
						case SOUTH -> b.set(t, y, box.maxZ(), pane);
						case NORTH -> b.set(t, y, box.minZ(), pane);
						case WEST -> b.set(box.minX(), y, t, pane);
						case EAST -> b.set(box.maxX(), y, t, pane);
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

		public Blueprint done() {
			return b;
		}
	}
}
