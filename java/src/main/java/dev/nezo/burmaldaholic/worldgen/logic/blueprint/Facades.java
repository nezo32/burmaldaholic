package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.ArrayList;
import java.util.List;

/**
 * Facade verbs (architecture doc §3.3 / §7, lane CB-3): pixel lettering on any side, the marquee with its
 * bulb border, vertical blade signs, porticos, awnings, lamp posts and {@link #front}, which dresses a whole
 * front elevation in one of the {@link FacadeKind}s.
 *
 * <p>Text always reads left-to-right <b>for a viewer standing outside that side</b>: on the front (+z) x
 * increases, on the back (-z) x decreases, on the east (+x) side z decreases, on the west side z increases
 * ({@code side.rotate(-1)} is the reading direction).
 */
public final class Facades {
	private Facades() {}

	// ---- lettering ---------------------------------------------------------------------------------------

	/** Single-line SMALL layout of {@code word} (letters one column apart). See {@link SignFont} for the planner. */
	public static Layouts.Sign text(String word) {
		SignFont.Plan p = SignFont.SMALL.line(word);
		return new Layouts.Sign(p.pixels(), p.width());
	}

	/** Cell of a wall plane: {@code along} runs along the wall, {@code plane} is the wall's z (front/back) or x (sides). */
	static int[] cell(Facing side, int plane, int along, int y) {
		return side == Facing.NORTH || side == Facing.SOUTH ? new int[] {along, y, plane} : new int[] {plane, y, along};
	}

	static void put(Blueprint b, Facing side, int plane, int along, int y, BlockSpec spec) {
		int[] c = cell(side, plane, along, y);
		b.set(c[0], c[1], c[2], spec);
	}

	static void put(Blueprint b, Facing side, int plane, int along, int y, Role role) {
		put(b, side, plane, along, y, b.palette().block(role));
	}

	/** Reading step along the wall for a viewer outside {@code side}: +1 or -1. */
	static int step(Facing side) {
		Facing dir = side.rotate(-1);
		return dir.dx() != 0 ? dir.dx() : dir.dz();
	}

	/** The cells a marquee occupies on its wall plane (inclusive). */
	public record SignBox(int alongFrom, int alongTo, int y0, int y1) {
		public int width() {
			return Math.abs(alongTo - alongFrom) + 1;
		}
	}

	/**
	 * Marquee on the wall plane {@code plane} of {@code side}: a SIGN_BACK board {@code pad} cells wider than the
	 * text on each side, {@code plan.height() + 2} rows from {@code bottomY} (bulb row, text, bulb row), letters
	 * alternating NEON_A / NEON_B, a BULB every second cell on the top and bottom rows (and on the end columns
	 * when {@code sideBulbs}). Centred on {@code center} (along the wall).
	 */
	public static SignBox marquee(Blueprint b, SignFont.Plan plan, Facing side, int center, int bottomY, int plane, int pad,
			boolean sideBulbs) {
		int w = plan.width() + 2 * pad, top = bottomY + plan.height() + 1;
		int s = step(side);
		int start = center - s * ((w - 1) / 2);
		for (int i = 0; i < w; i++) {
			for (int y = bottomY; y <= top; y++) {
				put(b, side, plane, start + s * i, y, Role.SIGN_BACK);
			}
		}
		for (Layouts.SignPixel px : plan.pixels()) {
			put(b, side, plane, start + s * (pad + px.col()), top - 1 - px.row(), px.letter() % 2 == 0 ? Role.NEON_A : Role.NEON_B);
		}
		for (int i = 0; i < w; i += 2) {
			put(b, side, plane, start + s * i, bottomY, Role.BULB);
			put(b, side, plane, start + s * i, top, Role.BULB);
		}
		if (w % 2 == 0) {
			put(b, side, plane, start + s * (w - 1), bottomY, Role.BULB);
			put(b, side, plane, start + s * (w - 1), top, Role.BULB);
		}
		if (sideBulbs) {
			for (int y = bottomY; y <= top; y += 2) {
				put(b, side, plane, start, y, Role.BULB);
				put(b, side, plane, start + s * (w - 1), y, Role.BULB);
			}
		}
		return new SignBox(Math.min(start, start + s * (w - 1)), Math.max(start, start + s * (w - 1)), bottomY, top);
	}

	/** Stained glass of the same colour as a {@code *_concrete} block (other blocks are returned unchanged). */
	public static BlockSpec glassOf(BlockSpec b) {
		return b.name().endsWith("_concrete") ? BlockSpec.of(b.name().replace("_concrete", "_stained_glass")) : b;
	}

	/**
	 * Illuminated sign (glows day and night): letters of stained glass in the NEON_A / NEON_B colours, each backed
	 * by a LIGHT block in the cell behind it (towards the wall), on a SIGN_BACK board {@code boardWidth} wide
	 * (at least {@code plan.width() + 2 * pad}; extra columns re-centre the text). Rows from {@code bottomY}: a
	 * bulb strip (BULB every second cell between ROOF_TRIM top slabs), the {@code plan.height()} text rows, a
	 * ROOF_TRIM slab cap. With {@code pad > 0} the end columns are a TRIM frame. The cell behind the board must be
	 * inside the template.
	 */
	public static SignBox litSign(Blueprint b, SignFont.Plan plan, Facing side, int center, int bottomY, int plane, int pad,
			int boardWidth) {
		int w = Math.max(boardWidth, plan.width() + 2 * pad);
		SignFont.Plan p = plan.widen(w - 2 * pad);
		int top = bottomY + p.height() + 1;
		int s = step(side);
		int start = center - s * ((w - 1) / 2);
		int behind = plane - (side == Facing.SOUTH || side == Facing.EAST ? 1 : -1);
		Material trim = b.palette().get(Role.ROOF_TRIM);
		for (int i = 0; i < w; i++) {
			int a = start + s * i;
			put(b, side, plane, a, bottomY, i % 2 == 0 ? b.palette().block(Role.BULB) : trim.slab("top"));
			put(b, side, plane, a, top, trim.slab("bottom"));
			for (int y = bottomY + 1; y < top; y++) {
				put(b, side, plane, a, y, pad > 0 && (i == 0 || i == w - 1) ? Role.ROOF_TRIM : Role.SIGN_BACK);
			}
		}
		for (Layouts.SignPixel px : p.pixels()) {
			int a = start + s * (pad + px.col());
			int y = top - 1 - px.row();
			put(b, side, plane, a, y, glassOf(b.palette().block(px.letter() % 2 == 0 ? Role.NEON_A : Role.NEON_B)));
			put(b, side, behind, a, y, Role.LIGHT);
		}
		// hide the letter lights from the other side: plain WALL behind the board where nothing stands there
		int back = behind - (plane - behind);
		for (int i = 0; i < w; i++) {
			for (int y = bottomY + 1; y < top; y++) {
				int[] c = cell(side, back, start + s * i, y);
				if (b.size().x() > c[0] && c[0] >= 0 && b.size().z() > c[2] && c[2] >= 0) {
					BlockSpec there = b.at(c[0], c[1], c[2]);
					if (there == null || there.is("minecraft:air")) {
						put(b, side, back, start + s * i, y, Role.WALL);
					}
				}
			}
		}
		return new SignBox(Math.min(start, start + s * (w - 1)), Math.max(start, start + s * (w - 1)), bottomY, top);
	}

	/** Marquee with the default look: SMALL letters on one line, one cell of padding, bulbs all round. */
	public static SignBox marquee(Blueprint b, String word, Facing side, int center, int bottomY, int plane) {
		return marquee(b, SignFont.SMALL.line(word), side, center, bottomY, plane, 1, true);
	}

	/**
	 * Vertical blade sign: a two-cell-thick SIGN_BACK board sticking out of the wall at ({@code x}, {@code z})
	 * in direction {@code out}, letters stacked top to bottom from {@code topY} (5 rows each, one row apart),
	 * readable from both sides; BULB every second row on the outer edge. Footprint: 5 cells along
	 * {@code out} × 2 across, {@code 6 × letters + 1} rows.
	 */
	public static Blueprint bladeSign(Blueprint b, String word, int x, int z, int topY, Facing out) {
		Facing p = out.rotate(1);
		int rows = word.length() * 6 + 1;
		int y0 = topY + 1 - rows + 1;
		for (int layer = 0; layer < 2; layer++) {
			int bx = x + layer * p.dx(), bz = z + layer * p.dz();
			for (int t = 0; t < 5; t++) {
				for (int y = y0; y <= topY + 1; y++) {
					b.set(bx + t * out.dx(), y, bz + t * out.dz(), Role.SIGN_BACK);
				}
			}
			for (int y = y0; y <= topY + 1; y += 2) {
				b.set(bx + 4 * out.dx(), y, bz + 4 * out.dz(), Role.BULB);
			}
			for (int i = 0; i < word.length(); i++) {
				String[] glyph = SignFont.SMALL.glyph(word.charAt(i));
				int gw = glyph[0].length(), left = Math.max(0, (5 - gw) / 2);
				for (int row = 0; row < 5; row++) {
					for (int c = 0; c < gw; c++) {
						if (glyph[row].charAt(c) != '#') {
							continue;
						}
						// layer 1 (on the +p side) reads outwards, layer 0 is seen mirrored from the other side
						int t = layer == 1 ? left + c : left + (gw - 1 - c);
						b.set(bx + t * out.dx(), topY - i * 6 - row, bz + t * out.dz(), i % 2 == 0 ? Role.NEON_A : Role.NEON_B);
					}
				}
			}
		}
		return b;
	}

	// ---- small elements -----------------------------------------------------------------------------------

	/** Fence post (no connections) of {@code fenceId}. */
	public static BlockSpec post(String fenceId) {
		return BlockSpec.of(fenceId, "east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false");
	}

	/** Fence running along x ({@code alongX}) or z, connected on both sides. */
	public static BlockSpec rail(String fenceId, boolean alongX) {
		return BlockSpec.of(fenceId, "east", Boolean.toString(alongX), "west", Boolean.toString(alongX),
			"north", Boolean.toString(!alongX), "south", Boolean.toString(!alongX), "waterlogged", "false");
	}

	public static final BlockSpec LANTERN = BlockSpec.mc("lantern", "hanging", "false", "waterlogged", "false");

	/** Lamp post on the floor row {@code y}: {@code height} fence posts and a standing lantern on top. */
	public static Blueprint lampPost(Blueprint b, int x, int y, int z, String fenceId, int height) {
		for (int i = 0; i < height; i++) {
			b.set(x, y + i, z, post(fenceId));
		}
		return b.set(x, y + height, z, LANTERN);
	}

	/** Leaves that never decay (templates must not place decaying leaves). */
	public static BlockSpec leaves(String id) {
		return BlockSpec.of(id, "distance", "1", "persistent", "true", "waterlogged", "false");
	}

	/** Little palm: a jungle-log trunk of {@code height} and a cross of persistent jungle leaves on top. */
	public static Blueprint palm(Blueprint b, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++) {
			b.set(x, y + i, z, BlockSpec.mc("jungle_log", "axis", "y"));
		}
		BlockSpec leaf = leaves("minecraft:jungle_leaves");
		int top = y + height;
		b.set(x, top, z, leaf);
		for (Facing f : Facing.values()) {
			int lx = x + f.dx(), lz = z + f.dz();
			if (b.size().x() > lx && lx >= 0 && b.size().z() > lz && lz >= 0) {
				b.set(lx, top - 1, lz, leaf);
			}
		}
		return b;
	}

	/**
	 * Striped awning over {@code x0..x1} on a front (+z) wall at plane {@code z}: {@code depth} rows sloping one
	 * row down per cell outwards, wool stripes alternating {@code woolA} / {@code woolB} along x, starting at
	 * row {@code y} next to the wall.
	 */
	public static Blueprint awning(Blueprint b, int x0, int x1, int y, int z, int depth, String woolA, String woolB) {
		for (int d = 0; d < depth; d++) {
			for (int x = x0; x <= x1; x++) {
				b.set(x, y - d, z + 1 + d, BlockSpec.of((x - x0) % 2 == 0 ? woolA : woolB));
			}
		}
		return b;
	}

	/**
	 * Portico in front of a +z wall: {@code column} pillars at both front corners ({@code x0}, {@code x1} at
	 * {@code zFront}) rising {@code height} rows above the floor block {@code floorY}, an entablature of TRIM
	 * on the front edge and ROOF_TRIM top slabs back to the wall ({@code zWall + 1 .. zFront}), and a one-row
	 * ROOF_TRIM pediment on top.
	 */
	public static Blueprint portico(Blueprint b, int x0, int x1, int zWall, int zFront, int floorY, int height, Role column) {
		for (int y = floorY + 1; y <= floorY + height; y++) {
			b.set(x0, y, zFront, column);
			b.set(x1, y, zFront, column);
		}
		int top = floorY + height + 1;
		Material trim = b.palette().get(Role.ROOF_TRIM);
		for (int z = zWall + 1; z <= zFront; z++) {
			for (int x = x0; x <= x1; x++) {
				b.set(x, top, z, z == zFront ? b.palette().block(Role.ROOF_TRIM) : trim.slab("top"));
			}
		}
		if (top + 1 < b.size().y()) {
			b.set(x0, top + 1, zFront, trim.stairs(Facing.EAST, false));
			b.set(x1, top + 1, zFront, trim.stairs(Facing.WEST, false));
			for (int x = x0 + 1; x < x1; x++) {
				b.set(x, top + 1, zFront, trim.slab("bottom"));
			}
		}
		return b;
	}

	// ---- whole front -----------------------------------------------------------------------------------

	/**
	 * A front (+z) elevation. The wall is the plane {@code z} from {@code x0} to {@code x1}; {@code floorY} is
	 * the floor block row (the door's sill); the door is {@code doorWidth} × {@code doorHeight} centred on
	 * {@code doorCenter}; the marquee's bottom row is {@code signBottomY} (7 rows tall, on the porch line);
	 * the crown above it ends at {@code topY} with its apex over {@link #centerX()}; {@code porch} cells in front
	 * of the wall ({@code z+1 .. z+porch}) belong to the facade (canopy, columns, lamp posts) and must be inside
	 * the template. Nothing is written behind the wall plane.
	 */
	public record Front(int x0, int x1, int z, int floorY, int doorCenter, int doorWidth, int doorHeight, int signBottomY, int topY,
			int porch, String word) {
		public int doorLeft() {
			return doorCenter - doorWidth / 2;
		}

		public int doorRight() {
			return doorLeft() + doorWidth - 1;
		}

		public int centerX() {
			return (x0 + x1) / 2;
		}

		/** Top row of the marquee (its height depends on the lettering plan that fits this front). */
		public int signTopY() {
			return signBottomY + frontPlan(this).plan().height() + 1;
		}

		public int canopyY() {
			return floorY + doorHeight + 1;
		}

		/** Plane of the marquee: the porch line when there is a porch. */
		public int signPlane() {
			return porch > 0 ? z + 1 : z;
		}

		public int porchFront() {
			return z + porch;
		}
	}

	/** Dresses the front elevation {@code f} in the style {@code kind} (wall, door, porch, marquee, crown). */
	public static Blueprint front(Blueprint b, FacadeKind kind, Front f) {
		baseWall(b, f);
		switch (kind) {
			case PORTICO -> portico(b, f);
			case DECO_FINS -> decoFins(b, f);
			case VERANDAH -> verandah(b, f);
			case CHALET_BALCONY -> chalet(b, f);
			case ICE_COLONNADE -> colonnade(b, f);
		}
		openDoor(b, f);
		return b;
	}

	private static void baseWall(Blueprint b, Front f) {
		b.fill(f.x0(), f.floorY() + 1, f.z(), f.x1(), f.signTopY(), f.z(), Role.WALL);
		b.fill(f.x0(), f.floorY() + 1, f.z(), f.x1(), f.floorY() + 1, f.z(), Role.WALL_BASE);
		if (f.porch() > 0) {
			b.fill(f.x0(), f.floorY(), f.z() + 1, f.x1(), f.floorY(), f.porchFront(), Role.PATH);
			b.clear(f.x0(), f.floorY() + 1, f.z() + 1, f.x1(), f.topY(), f.porchFront());
		}
	}

	/** Keeps the door opening and the porch in front of it clear (whatever the style put there). */
	private static void openDoor(Blueprint b, Front f) {
		for (int z = f.z(); z <= f.porchFront(); z++) {
			b.clear(f.doorLeft(), f.floorY() + 1, z, f.doorRight(), f.floorY() + f.doorHeight(), z);
		}
	}

	/**
	 * Post positions along a front: both ends, both sides of the door, and every {@code every} cells in between
	 * where that keeps a free cell next to the door posts and the ends.
	 */
	static List<Integer> bays(Front f, int every) {
		List<Integer> out = new ArrayList<>(List.of(f.x0(), f.doorLeft() - 1, f.doorRight() + 1, f.x1()));
		for (int x = f.x0() + every; x < f.x1() - 1; x += every) {
			if (x < f.doorLeft() - 2 || x > f.doorRight() + 2) {
				out.add(x);
			}
		}
		out.sort(null);
		return out;
	}

	private static BlockSpec pane(Blueprint b) {
		return BlockSpec.of(b.palette().get(Role.PANE).full(), "east", "true", "west", "true", "north", "false", "south", "false",
			"waterlogged", "false");
	}

	/** Pane windows between the outer ends and the door, {@code inset} cells in, rows sill..top (inclusive). */
	private static void frontWindows(Blueprint b, Front f, int inset, int sill, int top) {
		int l0 = f.x0() + inset, l1 = f.doorLeft() - inset - 1, r0 = f.doorRight() + inset + 1, r1 = f.x1() - inset;
		for (int y = sill; y <= top; y++) {
			for (int x = l0; x <= l1; x++) {
				b.set(x, y, f.z(), pane(b));
			}
			for (int x = r0; x <= r1; x++) {
				b.set(x, y, f.z(), pane(b));
			}
		}
	}

	private static void pilaster(Blueprint b, int x, int y0, int y1, int z, Role role) {
		b.fill(x, y0, z, x, y1, z, role);
	}

	private static final BlockSpec HANGING_LANTERN = BlockSpec.mc("lantern", "hanging", "true", "waterlogged", "false");

	/** Lettering plan + padding + board width of a front's marquee. */
	public record FrontPlan(SignFont.Plan plan, int pad, int boardWidth) {}

	/**
	 * The marquee layout for a front: the largest font that fits with a 2-cell (then 1, then 0) margin, falling
	 * back to two SMALL lines; the board is widened by one when that keeps it centred on the front.
	 */
	public static FrontPlan frontPlan(Front f) {
		int avail = f.x1() - f.x0() + 1;
		SignFont.Plan best = null;
		int bestPad = 0;
		for (int pad = 2; pad >= 0 && best == null; pad--) {
			SignFont.Plan p = SignFont.plan(f.word(), avail - 2 * pad).orElse(null);
			if (p != null && p.font() == SignFont.LARGE) {
				best = p;
				bestPad = pad;
			}
		}
		for (int pad = 2; pad >= 0 && best == null; pad--) {
			SignFont.Plan p = SignFont.plan(f.word(), avail - 2 * pad).orElse(null);
			if (p != null) {
				best = p;
				bestPad = pad;
			}
		}
		if (best == null) {
			throw new IllegalArgumentException("'" + f.word() + "' does not fit a " + avail + "-wide front");
		}
		int w = best.width() + 2 * bestPad;
		if ((avail - w) % 2 == 1) {
			w++;
		}
		return new FrontPlan(best, bestPad, w);
	}

	/** The illuminated marquee on the porch line. */
	private static SignBox frontSign(Blueprint b, Front f, boolean sideFrame) {
		FrontPlan fp = frontPlan(f);
		return litSign(b, fp.plan(), Facing.SOUTH, f.centerX(), f.signBottomY(), f.signPlane(), fp.pad(), fp.boardWidth());
	}

	/** Half-width of a 45° crown triangle with its apex at ({@code centerX}, {@code topY}) at row {@code y}. */
	private static int crownHalf(Front f, int y) {
		return f.topY() - y;
	}

	/**
	 * A 45° gable end on the wall plane from the row above the marquee to the apex at {@code topY}: edges in
	 * {@code edge} stairs, inside {@code fill}; returns the base row.
	 */
	private static int gableEnd(Blueprint b, Front f, Role edge, Role fill) {
		Material m = b.palette().get(edge);
		int y0 = f.signTopY() + 1;
		for (int y = y0; y <= f.topY(); y++) {
			int h = crownHalf(f, y);
			int a = Math.max(f.x0(), f.centerX() - h), c = Math.min(f.x1(), f.centerX() + h);
			if (a + 1 <= c - 1) {
				b.fill(a + 1, y, f.z(), c - 1, y, f.z(), fill);
			}
			if (a == c) {
				b.set(a, y, f.z(), m.slab("bottom"));
			} else {
				b.set(a, y, f.z(), m.stairs(Facing.EAST, false));
				b.set(c, y, f.z(), m.stairs(Facing.WEST, false));
			}
		}
		return y0;
	}

	// Tudor: timber-framed wall, quartz portico, framed marquee, half-timbered front gable with a window.
	private static void portico(Blueprint b, Front f) {
		frontWindows(b, f, 2, f.floorY() + 2, f.floorY() + f.doorHeight());
		for (int x : bays(f, 4)) {
			pilaster(b, x, f.floorY() + 1, f.signTopY(), f.z(), Role.FRAME);
		}
		b.fill(f.x0(), f.canopyY(), f.z(), f.x1(), f.canopyY(), f.z(), Role.BEAM);
		if (f.porch() > 0) {
			int zf = f.porchFront(), l = f.doorLeft() - 1, r = f.doorRight() + 1;
			for (int y = f.floorY() + 1; y < f.canopyY(); y++) {
				b.set(l, y, zf, Role.ACCENT);
				b.set(r, y, zf, Role.ACCENT);
			}
			Material trim = b.palette().get(Role.ROOF_TRIM);
			for (int z = f.z() + 1; z <= zf; z++) {
				for (int x = l - 1; x <= r + 1; x++) {
					b.set(x, f.canopyY(), z, z == zf ? b.palette().block(Role.ROOF_TRIM) : trim.slab("top"));
				}
			}
			b.set(l - 1, f.canopyY(), zf, trim.stairs(Facing.EAST, true));
			b.set(r + 1, f.canopyY(), zf, trim.stairs(Facing.WEST, true));
			b.set(f.doorCenter(), f.canopyY() - 1, f.z() + 1, HANGING_LANTERN);
			lampPost(b, f.x0() + 1, f.floorY() + 1, zf, "minecraft:dark_oak_fence", 2);
			lampPost(b, f.x1() - 1, f.floorY() + 1, zf, "minecraft:dark_oak_fence", 2);
			b.set(f.x0() + 2, f.floorY() + 1, zf, Role.PLANTER);
			b.set(f.x1() - 2, f.floorY() + 1, zf, Role.PLANTER);
		}
		frontSign(b, f, true);
		int y0 = gableEnd(b, f, Role.ROOF_TRIM, Role.WALL);
		// half-timbering in the gable: king post, collar beam, a small window
		for (int y = y0; y < f.topY(); y++) {
			b.set(f.centerX(), y, f.z(), Role.BEAM);
		}
		int h = crownHalf(f, y0);
		if (h >= 3) {
			b.fill(f.centerX() - h + 1, y0, f.z(), f.centerX() + h - 1, y0, f.z(), Role.BEAM);
			b.set(f.centerX() - 1, y0 + 1, f.z(), pane(b));
			b.set(f.centerX() + 1, y0 + 1, f.z(), pane(b));
		}
		if (f.topY() + 1 < b.size().y()) {
			b.set(f.centerX(), f.topY() + 1, f.z(), Roofs.FINIAL);
		}
	}

	// Oasis: art-deco fins rising above the sign into a stepped crown, glass transom, palms.
	private static void decoFins(Blueprint b, Front f) {
		frontWindows(b, f, 3, f.floorY() + 2, f.floorY() + f.doorHeight());
		b.fill(f.x0(), f.canopyY(), f.z(), f.x1(), f.canopyY(), f.z(), Role.BEAM);
		b.fill(f.doorLeft(), f.canopyY(), f.z(), f.doorRight(), f.canopyY(), f.z(), Role.GLASS); // transom
		int cx = f.centerX();
		int rows = f.topY() - f.signTopY();
		for (int x = f.x0(); x <= f.x1(); x++) {
			int d = Math.abs(x - cx);
			boolean fin = x == f.x0() || x == f.x1() || x == f.doorLeft() - 1 || x == f.doorRight() + 1 || d % 3 == 0;
			if (fin) {
				pilaster(b, x, f.floorY() + 2, f.canopyY() - 1, f.z(), Role.FRAME);
			}
			int crown = rows - d / 2;
			if (!fin) {
				crown = Math.min(crown, rows - 1 - d / 2);
			}
			for (int y = f.signTopY() + 1; y <= f.signTopY() + crown; y++) {
				b.set(x, y, f.z(), fin ? Role.FRAME : Role.WALL);
			}
			if (crown >= 1 && fin) {
				b.set(x, f.signTopY() + crown, f.z(), Role.TRIM);
			}
		}
		if (f.signTopY() + rows + 1 < b.size().y()) {
			b.set(cx, f.signTopY() + rows + 1, f.z(), Roofs.FINIAL);
		}
		if (f.porch() > 0) {
			int zf = f.porchFront();
			Material trim = b.palette().get(Role.TRIM);
			for (int z = f.z() + 1; z <= zf; z++) {
				for (int x = f.doorLeft() - 2; x <= f.doorRight() + 2; x++) {
					b.set(x, f.canopyY(), z, trim.slab("top"));
				}
			}
			for (int x : new int[] {f.doorLeft() - 2, f.doorRight() + 2}) {
				pilaster(b, x, f.floorY() + 1, f.canopyY() - 2, zf, Role.FRAME);
				b.set(x, f.canopyY() - 1, zf, Role.LIGHT);
			}
			int ph = Math.min(4, f.signBottomY() - f.floorY() - 1);
			palm(b, f.x0() + 1, f.floorY() + 1, zf, ph);
			palm(b, f.x1() - 1, f.floorY() + 1, zf, ph);
			b.set(f.x0() + 3, f.floorY() + 1, zf, Role.PLANTER);
			b.set(f.x1() - 3, f.floorY() + 1, zf, Role.PLANTER);
		}
		frontSign(b, f, true);
	}

	// Safari: verandah on log posts with a thatch lip and railing, wooden board between bone tusks, thatch hood.
	private static void verandah(Blueprint b, Front f) {
		List<Integer> posts = bays(f, 4);
		frontWindows(b, f, 2, f.floorY() + 2, f.floorY() + f.doorHeight());
		for (int x : posts) {
			pilaster(b, x, f.floorY() + 1, f.signTopY(), f.z(), Role.FRAME);
		}
		b.fill(f.x0(), f.canopyY(), f.z(), f.x1(), f.canopyY(), f.z(), Role.BEAM);
		if (f.porch() > 0) {
			int zf = f.porchFront();
			Material trim = b.palette().get(Role.ROOF_TRIM);
			for (int x = f.x0(); x <= f.x1(); x++) {
				for (int z = f.z() + 1; z < zf; z++) {
					b.set(x, f.canopyY(), z, trim.slab("bottom"));
				}
				b.set(x, f.canopyY(), zf, b.palette().block(Role.ROOF)); // thatch lip
			}
			for (int x : posts) {
				pilaster(b, x, f.floorY() + 1, f.canopyY() - 1, zf, Role.FRAME);
			}
			for (int x = f.x0() + 1; x < f.x1(); x++) {
				if (!posts.contains(x) && (x < f.doorLeft() - 1 || x > f.doorRight() + 1)) {
					b.set(x, f.floorY() + 1, zf, rail("minecraft:acacia_fence", true));
				}
			}
			b.set(f.doorLeft() - 2, f.canopyY() - 1, zf, HANGING_LANTERN);
			b.set(f.doorRight() + 2, f.canopyY() - 1, zf, HANGING_LANTERN);
		}
		SignBox s = frontSign(b, f, false);
		// thatch hood over the board, stepping in two cells a row
		for (int y = s.y1() + 1, i = 0; y <= f.topY(); y++, i++) {
			int a = f.x0() + 1 + 2 * i, c = f.x1() - 1 - 2 * i;
			if (a > c) {
				break;
			}
			b.fill(a, y, f.z(), c, y, f.z(), Role.ROOF);
			if (i == 0 && f.porch() > 0) {
				b.fill(f.x0(), y, f.signPlane(), f.x1(), y, f.signPlane(), b.palette().get(Role.ROOF_TRIM).slab("bottom"));
			}
		}
		// bone tusks curling up at the board's ends
		int plane = f.signPlane();
		int y = s.y1() + 1;
		for (int[] t : new int[][] {{s.alongFrom(), 1}, {s.alongTo(), -1}}) {
			if (y + 1 <= f.topY()) {
				b.set(t[0], y, plane, Role.ACCENT);
				b.set(t[0], y + 1, plane, Role.ACCENT);
				b.set(t[0] + t[1], y + 1, plane, Role.ACCENT);
			}
		}
	}

	// Alpine: mossy stone base, chalet balcony with flower pots over the door, carved gable with a round window.
	private static void chalet(Blueprint b, Front f) {
		b.fill(f.x0(), f.floorY() + 1, f.z(), f.x1(), f.floorY() + 2, f.z(), Role.WALL_BASE);
		frontWindows(b, f, 2, f.floorY() + 2, f.floorY() + f.doorHeight());
		List<Integer> posts = bays(f, 4);
		for (int x : posts) {
			pilaster(b, x, f.floorY() + 1, f.signTopY(), f.z(), Role.FRAME);
		}
		b.fill(f.x0(), f.canopyY(), f.z(), f.x1(), f.canopyY(), f.z(), Role.BEAM);
		Material ceil = b.palette().get(Role.CEILING);
		if (f.porch() > 0) {
			int zf = f.porchFront();
			int bal = f.canopyY();
			for (int x = f.x0(); x <= f.x1(); x++) {
				for (int z = f.z() + 1; z <= zf; z++) {
					b.set(x, bal, z, ceil.slab("top"));
				}
				if (zf > f.signPlane()) {
					b.set(x, bal + 1, zf, rail("minecraft:spruce_fence", true));
				}
			}
			if (zf > f.signPlane()) {
				b.set(f.x0(), bal + 1, zf, post("minecraft:spruce_fence"));
				b.set(f.x1(), bal + 1, zf, post("minecraft:spruce_fence"));
			}
			for (int x : posts) {
				b.set(x, bal - 1, zf, ceil.stairs(x <= f.centerX() ? Facing.WEST : Facing.EAST, true));
				pilaster(b, x, f.floorY() + 1, bal - 2, zf, Role.FRAME);
			}
			for (int x = f.x0(); x <= f.x1(); x++) {
				if (!posts.contains(x) && (x < f.doorLeft() - 1 || x > f.doorRight() + 1)) {
					b.set(x, f.floorY() + 1, zf, BlockSpec.mc("potted_red_tulip"));
				}
			}
			b.set(f.doorCenter(), bal - 1, f.z() + 1, HANGING_LANTERN);
		}
		frontSign(b, f, true);
		int y0 = gableEnd(b, f, Role.ROOF, Role.WALL);
		// carved trim: upside-down stairs under the gable edges, a round (pane) window framed in logs
		for (int y = y0; y < f.topY(); y++) {
			int h = crownHalf(f, y);
			int a = Math.max(f.x0(), f.centerX() - h), c = Math.min(f.x1(), f.centerX() + h);
			if (c - a >= 4) {
				b.set(a + 1, y, f.z(), ceil.stairs(Facing.WEST, true));
				b.set(c - 1, y, f.z(), ceil.stairs(Facing.EAST, true));
			}
		}
		if (crownHalf(f, y0) >= 3) {
			b.set(f.centerX(), y0 + 1, f.z(), pane(b));
			b.set(f.centerX() - 1, y0 + 1, f.z(), Role.FRAME);
			b.set(f.centerX() + 1, y0 + 1, f.z(), Role.FRAME);
			b.set(f.centerX(), y0, f.z(), Role.FRAME);
			b.set(f.centerX(), y0 + 2, f.z(), Role.FRAME);
		}
		b.set(f.centerX(), f.topY(), f.z(), b.palette().get(Role.ROOF_TRIM).slab("bottom"));
	}

	// Ice palace: quartz colonnade, snow on the ledges, blue-ice battlement crown with spires.
	private static void colonnade(Blueprint b, Front f) {
		frontWindows(b, f, 2, f.floorY() + 2, f.floorY() + f.doorHeight());
		for (int x : bays(f, 4)) {
			pilaster(b, x, f.floorY() + 1, f.signTopY(), f.z(), Role.FRAME);
		}
		b.fill(f.x0(), f.canopyY(), f.z(), f.x1(), f.canopyY(), f.z(), Role.TRIM);
		BlockSpec snow = BlockSpec.mc("snow", "layers", "2");
		if (f.porch() > 0) {
			int zf = f.porchFront();
			Material trim = b.palette().get(Role.ROOF_TRIM);
			for (int x = f.x0(); x <= f.x1(); x++) {
				for (int z = f.z() + 1; z <= zf; z++) {
					b.set(x, f.canopyY(), z, z == zf ? b.palette().block(Role.TRIM) : trim.slab("top"));
				}
			}
			for (int x = f.x0() + 1; x <= f.x1() - 1; x += 2) {
				if (x < f.doorLeft() - 1 || x > f.doorRight() + 1) {
					pilaster(b, x, f.floorY() + 1, f.canopyY() - 1, zf, Role.FRAME);
				}
			}
			if (f.canopyY() + 1 < f.signBottomY() || f.signPlane() != zf) {
				for (int x = f.x0(); x <= f.x1(); x++) {
					b.set(x, f.canopyY() + 1, zf, snow);
				}
			}
			b.set(f.doorLeft() - 1, f.canopyY() - 1, zf, HANGING_LANTERN);
			b.set(f.doorRight() + 1, f.canopyY() - 1, zf, HANGING_LANTERN);
		}
		frontSign(b, f, true);
		// crown: blue-ice band, merlons, three spires (centre one tallest) with a froglight core
		int y = f.signTopY() + 1;
		int top = f.topY();
		for (int x = f.x0(); x <= f.x1(); x++) {
			if (y <= top) {
				b.set(x, y, f.z(), Role.ROOF);
			}
			if (y + 1 <= top && (x - f.x0()) % 2 == 0) {
				b.set(x, y + 1, f.z(), Role.ROOF);
				if (y + 2 <= top) {
					b.set(x, y + 2, f.z(), snow);
				}
			}
		}
		for (int x : new int[] {f.x0(), f.x1()}) {
			Roofs.spire(b, x, f.z(), y, Math.max(1, top - y - 1), Role.FRAME);
		}
		Roofs.spire(b, f.centerX(), f.z(), y, Math.max(1, Math.min(top - y + 1, b.size().y() - 1 - y)), Role.FRAME);
		b.set(f.centerX(), y + 1, f.z(), Role.BULB);
	}
}
