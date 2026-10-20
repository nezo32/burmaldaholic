package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.ArrayList;
import java.util.List;

/**
 * Architectural verbs of the blueprint DSL (casino redesign CR-1, findings/CASINO-REDESIGN.md §2.2 / §5): columns with
 * bases and capitals, arches, cornices and entablatures, balustrades, straight and twin staircases, discs and rings,
 * ribbed domes on drums, deterministic hashing, floor patterns, coffered ceilings and neon signage.
 *
 * <p>Every verb draws only through public {@link Blueprint} verbs, so it resolves {@link Role}s through the palette (a
 * biome swap is free) and works inside {@link Blueprint#mirrorX} (symmetry). Coordinates are template-local and
 * inclusive, rows are y. PURE and deterministic (randomness only from {@link #hash}).
 */
public final class Ornament {
	private Ornament() {}

	public static final BlockSpec AIR = Blueprint.AIR;

	// ---- small helpers -------------------------------------------------------------------------------------------

	/** The role's full block with {@code axis=y} when it has an axis (pillars, logs, froglights). */
	public static BlockSpec upright(Blueprint b, Role role) {
		BlockSpec s = b.palette().block(role);
		return Shapes.hasAxis(s.name()) ? BlockSpec.of(s.name(), "axis", "y") : s;
	}

	private static boolean free(Blueprint b, int x, int y, int z) {
		if (!b.inside(x, y, z)) {
			return false;
		}
		BlockSpec c = b.at(x, y, z);
		return c == null || c.is("minecraft:air");
	}

	/** Deterministic hash of a cell in [0, 1) (SplitMix64 finaliser over the packed coordinates and the seed). */
	public static double hash(int x, int y, int z, long seed) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}

	// ---- columns -------------------------------------------------------------------------------------------------

	/** Which ends of a {@link #column} flare out with stairs. */
	public enum Flare {
		NONE, BASE, CAPITAL, BOTH;

		boolean base() {
			return this == BASE || this == BOTH;
		}

		boolean capital() {
			return this == CAPITAL || this == BOTH;
		}
	}

	/**
	 * A column at ({@code x}, {@code z}) from {@code y0} to {@code y1}: a CAPITAL block as base at {@code y0}, a COLUMN shaft
	 * ({@code axis=y}) in between and a CAPITAL block at {@code y1}. A flared base adds PLINTH stairs (high side toward the
	 * shaft) in the four free cells around the base; a flared capital adds CORNICE upside-down stairs around the
	 * capital. Flares only go into free (air / void) cells, so a column against a wall flares on its open sides only.
	 */
	public static Blueprint column(Blueprint b, int x, int z, int y0, int y1, Flare flare) {
		b.set(x, y0, z, Role.CAPITAL);
		for (int y = y0 + 1; y < y1; y++) {
			b.set(x, y, z, upright(b, Role.COLUMN));
		}
		b.set(x, y1, z, Role.CAPITAL);
		for (Facing f : Facing.values()) {
			int nx = x + f.dx(), nz = z + f.dz();
			if (flare.base() && free(b, nx, y0, nz)) {
				b.set(nx, y0, nz, b.palette().get(Role.PLINTH).stairs(f.opposite(), false));
			}
			if (flare.capital() && free(b, nx, y1, nz)) {
				b.set(nx, y1, nz, b.palette().get(Role.CORNICE).stairs(f.opposite(), true));
			}
		}
		return b;
	}

	/** A pilaster: a column standing against a wall (no flares) — a shaft one cell proud of the wall plane. */
	public static Blueprint pilaster(Blueprint b, int x, int z, int y0, int y1) {
		return column(b, x, z, y0, y1, Flare.NONE);
	}

	// ---- arches --------------------------------------------------------------------------------------------------

	/**
	 * Opening top above the spring line, in half blocks, of column {@code i} (0-based) of an arch {@code w} wide rising
	 * {@code rise} blocks: an ellipse through the jamb faces and the crown, sampled at the cell centre.
	 */
	static int archHalfBlocks(int i, int w, double rise) {
		double half = w / 2.0;
		double t = (i + 0.5 - half) / half;
		return (int) Math.round(2 * rise * Math.sqrt(Math.max(0, 1 - t * t)));
	}

	/**
	 * Arch in a wall: the wall slab is {@code p0..p1} on the axis across the wall (z for {@code alongX}, else x), the
	 * opening spans {@code a0..a1} along the wall and rows {@code floorY} up to an elliptical intrados that springs at
	 * {@code springY} and rises {@code rise} blocks (≤ 0 = semicircular, rise = width / 2). The opening is cleared;
	 * the intrados is lined with ARCH voussoirs — upside-down stairs where the curve steps (high side toward the
	 * jamb), top slabs where it is half a block, full blocks elsewhere — every wall cell next to the curve is an ARCH
	 * block (the archivolt ring), the crown gets a CAPITAL keystone, and the spandrels up to the extrados are filled
	 * with {@code fill} (null = leave whatever the wall has).
	 */
	public static Blueprint arch(Blueprint b, boolean alongX, int a0, int a1, int p0, int p1, int floorY, int springY, double rise,
			Role fill) {
		int w = a1 - a0 + 1;
		double r = rise > 0 ? rise : w / 2.0;
		int[] k = new int[w];
		for (int i = 0; i < w; i++) {
			k[i] = archHalfBlocks(i, w, r);
		}
		int crownY = springY + (int) Math.ceil(r);
		Material arch = b.palette().get(Role.ARCH);
		for (int p = Math.min(p0, p1); p <= Math.max(p0, p1); p++) {
			// spandrel fill first, then the opening, then the voussoir ring
			for (int i = -1; i <= w; i++) {
				for (int y = springY; y <= crownY; y++) {
					if (fill != null) {
						cell(b, alongX, a0 + i, y, p, b.palette().block(fill));
					}
				}
			}
			for (int i = 0; i < w; i++) {
				int open = springY + k[i] / 2; // first row that is not fully open
				for (int y = floorY; y < open; y++) {
					cell(b, alongX, a0 + i, y, p, AIR);
				}
				int jamb = i < w / 2 ? -1 : 1; // the nearer jamb
				if (w % 2 == 1 && i == w / 2) {
					jamb = 0;
				}
				BlockSpec v;
				if (k[i] % 2 == 1) {
					int nk = jamb == 0 ? Integer.MAX_VALUE : i + jamb < 0 || i + jamb >= w ? -1 : k[i + jamb];
					boolean solidTowardJamb = nk < k[i];
					Facing toJamb = facingAlong(alongX, jamb);
					v = solidTowardJamb && toJamb != null ? arch.stairs(toJamb, true) : arch.slab("top");
				} else {
					v = arch.block();
				}
				cell(b, alongX, a0 + i, open, p, v);
				// ring: one more ARCH block above the voussoir row where the curve is flat, along the jamb-side where it is steep
				if (open + 1 <= crownY + 1) {
					cell(b, alongX, a0 + i, open + 1, p, arch.block());
				}
			}
			for (int side : new int[] {-1, w}) {
				for (int y = springY; y <= springY + Math.max(0, k[side < 0 ? 0 : w - 1] / 2); y++) {
					cell(b, alongX, a0 + side, y, p, arch.block());
				}
			}
			if (w % 2 == 1) {
				int top = springY + k[w / 2] / 2;
				cell(b, alongX, a0 + w / 2, top + (k[w / 2] % 2 == 1 ? 1 : 1), p, b.palette().block(Role.CAPITAL));
			}
		}
		return b;
	}

	private static Facing facingAlong(boolean alongX, int sign) {
		if (sign == 0) {
			return null;
		}
		return alongX ? sign < 0 ? Facing.WEST : Facing.EAST : sign < 0 ? Facing.NORTH : Facing.SOUTH;
	}

	private static void cell(Blueprint b, boolean alongX, int a, int y, int p, BlockSpec s) {
		int x = alongX ? a : p, z = alongX ? p : a;
		if (b.inside(x, y, z)) {
			b.set(x, y, z, s);
		}
	}

	/**
	 * Heights (opening top, in half blocks above the spring line) of an arch {@code w} wide. Kept public as the oracle
	 * {@code OrnamentTest} checks {@link #arch} against (not called by the pieces).
	 */
	public static int[] archProfile(int w, double rise) {
		double r = rise > 0 ? rise : w / 2.0;
		int[] k = new int[w];
		for (int i = 0; i < w; i++) {
			k[i] = archHalfBlocks(i, w, r);
		}
		return k;
	}

	// ---- cornices, entablatures, bands ---------------------------------------------------------------------------

	/**
	 * A straight cornice run along one facade: upside-down CORNICE stairs one cell outside the wall line, their high
	 * side against the wall ({@code out} = the side the facade faces). The wall line is {@code (x0, z0)..(x1, z1)};
	 * the overhang lands at {@code wall + out}.
	 */
	public static Blueprint corniceRun(Blueprint b, int x0, int z0, int x1, int z1, int y, Facing out) {
		Material m = b.palette().get(Role.CORNICE);
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				int cx = x + out.dx(), cz = z + out.dz();
				if (b.inside(cx, y, cz)) {
					b.set(cx, y, cz, m.stairs(out.opposite(), true));
				}
			}
		}
		return b;
	}

	/**
	 * A cornice ring around the walls {@code x0..x1 × z0..z1} at row {@code y}: upside-down CORNICE stairs one cell
	 * outside every side (the finishing pass turns the corners into outer-corner stairs).
	 */
	public static Blueprint cornice(Blueprint b, int x0, int z0, int x1, int z1, int y) {
		corniceRun(b, x0, z0, x1, z0, y, Facing.NORTH);
		corniceRun(b, x0, z1, x1, z1, y, Facing.SOUTH);
		corniceRun(b, x0, z0, x0, z1, y, Facing.WEST);
		corniceRun(b, x1, z0, x1, z1, y, Facing.EAST);
		Material m = b.palette().get(Role.CORNICE);
		int[][] corners = {{x0 - 1, z0 - 1}, {x1 + 1, z0 - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}};
		Facing[] f = {Facing.SOUTH, Facing.SOUTH, Facing.NORTH, Facing.NORTH};
		for (int i = 0; i < 4; i++) {
			if (b.inside(corners[i][0], y, corners[i][1])) {
				b.set(corners[i][0], y, corners[i][1], m.stairs(f[i], true));
			}
		}
		return b;
	}

	/**
	 * Entablature over one facade wall line (x0, z0)..(x1, z1) facing {@code out}: an architrave of BAND blocks at
	 * {@code y}, a frieze at {@code y + 1} with GILT dentils every second cell (WALL between), and the CORNICE overhang at
	 * {@code y + 2} (upside-down stairs) capped by a CORNICE slab row on the wall line at {@code y + 2}.
	 */
	public static Blueprint entablature(Blueprint b, int x0, int z0, int x1, int z1, int y, Facing out) {
		int i = 0;
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++, i++) {
				b.set(x, y, z, Role.BAND);
				b.set(x, y + 1, z, i % 2 == 0 ? Role.GILT : Role.WALL);
				b.set(x, y + 2, z, Role.CORNICE);
			}
		}
		return corniceRun(b, x0, z0, x1, z1, y + 2, out);
	}

	/** A horizontal BAND course (string course) of {@code role} stairs projecting from a wall line, high side on the wall. */
	public static Blueprint stringCourse(Blueprint b, int x0, int z0, int x1, int z1, int y, Facing out, Role role, boolean top) {
		Material m = b.palette().get(role);
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				int cx = x + out.dx(), cz = z + out.dz();
				if (b.inside(cx, y, cz)) {
					b.set(cx, y, cz, m.stairs(out.opposite(), top));
				}
			}
		}
		return b;
	}

	// ---- balustrades ---------------------------------------------------------------------------------------------

	/**
	 * Balustrade on the straight line (x0, z0)..(x1, z1) at row {@code y}: BALUSTER wall posts with a RAIL bottom-slab
	 * hand rail on top (y + 1); every {@code pierEvery} cells (and at both ends) a pier — a CAPITAL block capped with a
	 * RAIL slab — when {@code pierEvery > 0}. 1.5 blocks high: nobody falls off a gallery or balcony.
	 */
	public static Blueprint balustrade(Blueprint b, int x0, int z0, int x1, int z1, int y, int pierEvery) {
		Material rail = b.palette().get(Role.RAIL);
		BlockSpec baluster = b.palette().block(Role.BALUSTER);
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		int dx = Integer.signum(x1 - x0), dz = Integer.signum(z1 - z0);
		for (int i = 0; i <= n; i++) {
			int x = x0 + i * dx, z = z0 + i * dz;
			boolean pier = pierEvery > 0 && (i == 0 || i == n || i % pierEvery == 0);
			b.set(x, y, z, pier ? b.palette().block(Role.CAPITAL) : baluster);
			b.set(x, y + 1, z, rail.slab("bottom"));
		}
		return b;
	}

	// ---- staircases ----------------------------------------------------------------------------------------------

	/**
	 * Straight stair flight: {@code steps} stairs climbing toward {@code up}, the first one at ({@code x}, {@code y},
	 * {@code z}) (its top is the next floor row), {@code width} cells wide across toward {@code across}. Each step is a
	 * {@code role} stair with its high side toward {@code up}; the cells under it down to {@code baseY} are the role's
	 * full block (a closed stringer) and the three rows above each step are cleared (head room). Returns the cell
	 * after the last step on the top floor row (where the landing starts).
	 */
	public static int[] flight(Blueprint b, int x, int y, int z, Facing up, Facing across, int width, int steps, Role role, int baseY) {
		Material m = b.palette().get(role);
		for (int s = 0; s < steps; s++) {
			for (int w = 0; w < width; w++) {
				int cx = x + s * up.dx() + w * across.dx(), cz = z + s * up.dz() + w * across.dz();
				int sy = y + s;
				for (int yy = baseY; yy < sy; yy++) {
					b.set(cx, yy, cz, m.block());
				}
				b.set(cx, sy, cz, m.stairs(up, false));
				for (int h = 1; h <= 3; h++) {
					if (b.inside(cx, sy + h, cz)) {
						b.set(cx, sy + h, cz, AIR);
					}
				}
			}
		}
		return new int[] {x + steps * up.dx(), y + steps, z + steps * up.dz()};
	}

	// ---- circles -------------------------------------------------------------------------------------------------

	/** Cells (x, z) of a disc of radius {@code r} around ({@code cx}, {@code cz}) (half-integer centres for even widths). */
	public static List<int[]> discCells(double cx, double cz, double r) {
		List<int[]> out = new ArrayList<>();
		int x0 = (int) Math.floor(cx - r - 1), x1 = (int) Math.ceil(cx + r + 1);
		int z0 = (int) Math.floor(cz - r - 1), z1 = (int) Math.ceil(cz + r + 1);
		double rr = (r + 0.5) * (r + 0.5);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double dx = x - cx, dz = z - cz;
				if (dx * dx + dz * dz <= rr - 0.25) {
					out.add(new int[] {x, z});
				}
			}
		}
		return out;
	}

	/** Boundary cells of {@link #discCells}: in the disc with a 4-neighbour outside it (a closed 1-wide ring). */
	public static List<int[]> ringCells(double cx, double cz, double r) {
		List<int[]> disc = discCells(cx, cz, r);
		java.util.Set<Long> in = new java.util.HashSet<>();
		disc.forEach(c -> in.add(key(c[0], c[1])));
		List<int[]> out = new ArrayList<>();
		for (int[] c : disc) {
			if (!in.contains(key(c[0] + 1, c[1])) || !in.contains(key(c[0] - 1, c[1])) || !in.contains(key(c[0], c[1] + 1))
				|| !in.contains(key(c[0], c[1] - 1))) {
				out.add(c);
			}
		}
		return out;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	public static Blueprint disc(Blueprint b, double cx, double cz, double r, int y, BlockSpec s) {
		for (int[] c : discCells(cx, cz, r)) {
			if (b.inside(c[0], y, c[1])) {
				b.set(c[0], y, c[1], s);
			}
		}
		return b;
	}

	public static Blueprint ring(Blueprint b, double cx, double cz, double r, int y, BlockSpec s) {
		for (int[] c : ringCells(cx, cz, r)) {
			if (b.inside(c[0], y, c[1])) {
				b.set(c[0], y, c[1], s);
			}
		}
		return b;
	}

	// ---- domes ---------------------------------------------------------------------------------------------------

	/**
	 * Top of a dome column above its base row, in half blocks: a half ellipsoid of radius {@code r} (to the outer cell
	 * edge) and {@code rise} blocks, sampled at the cell centre {@code d} away from the axis; -1 outside.
	 */
	static int domeHalfBlocks(double d, double r, double rise) {
		double t = d / (r + 0.5);
		if (t >= 1) {
			return -1;
		}
		return Math.max(1, (int) Math.round(2 * rise * Math.sqrt(1 - t * t)));
	}

	/**
	 * Drum: a ring of radius {@code r} for rows {@code y0..y1}, GLASS with COLUMN pilasters on the eight compass lines
	 * (a lit glass ring on the skyline at night) and a BAND course at {@code y0 - 1}.
	 */
	public static Blueprint drum(Blueprint b, double cx, double cz, double r, int y0, int y1) {
		for (int[] c : ringCells(cx, cz, r)) {
			double dx = c[0] - cx, dz = c[1] - cz;
			boolean pier = Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6;
			for (int y = y0; y <= y1; y++) {
				b.set(c[0], y, c[1], pier ? upright(b, Role.COLUMN) : b.palette().block(Role.GLASS));
			}
			b.set(c[0], y0 - 1, c[1], Role.BAND);
		}
		return b;
	}

	/**
	 * Ribbed dome: a half-ellipsoid shell of radius {@code r} rising {@code rise} rows from {@code baseY}, in DOME
	 * material — full blocks, a bottom slab where a column tops out half a block higher, and stairs (high side toward
	 * the axis) on the terrace edges so the curve reads smooth — with RIB material on the eight compass lines, the inside
	 * cleared (and registered as interior), and a lantern on the crown: a CAPITAL drum, a LIGHT block, a lightning-rod
	 * finial. Returns the crown row.
	 */
	public static int dome(Blueprint b, double cx, double cz, double r, int baseY, double rise) {
		List<int[]> cells = discCells(cx, cz, r);
		java.util.Map<Long, Integer> top = new java.util.HashMap<>();
		for (int[] c : cells) {
			top.put(key(c[0], c[1]), domeHalfBlocks(Math.hypot(c[0] - cx, c[1] - cz), r, rise));
		}
		int crown = baseY;
		for (int[] c : cells) {
			int x = c[0], z = c[1];
			int k = top.get(key(x, z));
			int full = k / 2; // rows baseY .. baseY + full - 1 are below the surface
			int lowest = full;
			for (Facing f : Facing.values()) {
				Integer nk = top.get(key(x + f.dx(), z + f.dz()));
				lowest = Math.min(lowest, nk == null ? 0 : nk / 2);
			}
			double dx = x - cx, dz = z - cz;
			boolean rib = Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6;
			Material m = b.palette().get(rib ? Role.RIB : Role.DOME);
			for (int y = baseY; y < baseY + Math.max(lowest - 1, 0); y++) {
				b.set(x, y, z, AIR);
			}
			for (int y = baseY + Math.max(lowest - 1, 0); y < baseY + full; y++) {
				b.set(x, y, z, m.block());
			}
			int ty = baseY + full;
			if (k % 2 == 1) {
				b.set(x, ty, z, m.slab("bottom"));
				crown = Math.max(crown, ty);
			} else if (full > 0) {
				// the top full row: a stair on a terrace edge (a lower neighbour on the outward side)
				Facing in = inward(dx, dz);
				Integer outK = in == null ? null : top.get(key(x - in.dx(), z - in.dz()));
				int y = baseY + full - 1;
				if (in != null && (outK == null || outK / 2 < full) && m.hasStairs()) {
					b.set(x, y, z, m.stairs(in, false));
				}
				crown = Math.max(crown, y);
			}
		}
		b.interior((int) Math.floor(cx - r), baseY, (int) Math.floor(cz - r), (int) Math.ceil(cx + r), crown - 1, (int) Math.ceil(cz + r));
		return crown;
	}

	/** Dominant direction from a cell toward the dome axis (null on the axis). */
	private static Facing inward(double dx, double dz) {
		if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) {
			return null;
		}
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Facing.WEST : Facing.EAST;
		}
		return dz > 0 ? Facing.NORTH : Facing.SOUTH;
	}

	/**
	 * Crown lantern on top of a dome or roof at ({@code x}, {@code y}, {@code z}): a CAPITAL base, a glass lantern
	 * (GLASS with a LIGHT core) and a lightning-rod finial on a CAPITAL cap — 5 rows; returns the finial row.
	 */
	public static int lantern(Blueprint b, int x, int y, int z) {
		b.set(x, y, z, Role.CAPITAL);
		b.set(x, y + 1, z, Role.LIGHT);
		b.set(x, y + 2, z, b.palette().get(Role.RAIL).slab("bottom"));
		b.set(x, y + 3, z, BlockSpec.mc("lightning_rod", "facing", "up", "powered", "false", "waterlogged", "false"));
		return y + 3;
	}

	// ---- floor patterns and ceilings -----------------------------------------------------------------------------

	/** Checkerboard of {@code a} / {@code c} on row {@code y} over {@code x0..x1 × z0..z1} ((x + z) even = a). */
	public static Blueprint checker(Blueprint b, int x0, int z0, int x1, int z1, int y, BlockSpec a, BlockSpec c) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				b.set(x, y, z, Math.floorMod(x + z, 2) == 0 ? a : c);
			}
		}
		return b;
	}

	/** Diamond (♦, mirror-symmetric on both axes) of Manhattan radius {@code r} on row {@code y}; {@code outline} draws the edge only. */
	public static Blueprint diamond(Blueprint b, int cx, int cz, int r, int y, BlockSpec s, boolean outline) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m <= r && (!outline || m == r)) {
					b.set(cx + dx, y, cz + dz, s);
				}
			}
		}
		return b;
	}

	/**
	 * Sunburst fan on row {@code y}: {@code rays} rays from ({@code cx}, {@code cz}) between {@code fromDeg} and {@code toDeg}
	 * (0° = +z / south, 90° = +x), from radius {@code r0} to {@code r1}; a cell is on a ray when its angle is within a
	 * half cell of it. Symmetric when the fan is.
	 */
	public static Blueprint sunburst(Blueprint b, double cx, double cz, int y, int rays, double r0, double r1, double fromDeg, double toDeg,
			BlockSpec s) {
		for (int x = (int) Math.floor(cx - r1); x <= Math.ceil(cx + r1); x++) {
			for (int z = (int) Math.floor(cz - r1); z <= Math.ceil(cz + r1); z++) {
				double dx = x - cx, dz = z - cz, d = Math.hypot(dx, dz);
				if (d < r0 || d > r1 + 0.25 || !b.inside(x, y, z)) {
					continue;
				}
				double ang = Math.toDegrees(Math.atan2(dx, dz));
				for (int i = 0; i < rays; i++) {
					double a = rays == 1 ? fromDeg : fromDeg + (toDeg - fromDeg) * i / (rays - 1);
					double diff = Math.abs(((ang - a) % 360 + 540) % 360 - 180);
					if (Math.toRadians(diff) * d <= 0.5) {
						b.set(x, y, z, s);
						break;
					}
				}
			}
		}
		return b;
	}

	/**
	 * Coffered ceiling on row {@code y} over {@code x0..x1 × z0..z1}: a grid of CEILING beams every {@code pitch} cells
	 * (lines through {@code x0}, {@code z0}) with COFFER panels between them; the centre of every second coffer is a
	 * {@code light} block (pass null for none).
	 */
	public static Blueprint coffers(Blueprint b, int x0, int z0, int x1, int z1, int y, int pitch, BlockSpec light) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean beam = (x - x0) % pitch == 0 || (z - z0) % pitch == 0;
				b.set(x, y, z, beam ? Role.CEILING : Role.COFFER);
				int cxi = (x - x0) / pitch, czi = (z - z0) / pitch;
				boolean centre = (x - x0) % pitch == pitch / 2 && (z - z0) % pitch == pitch / 2;
				if (light != null && centre && Math.floorMod(cxi + czi, 2) == 0) {
					b.set(x, y, z, light);
				}
			}
		}
		return b;
	}

	// ---- neon signage --------------------------------------------------------------------------------------------

	/**
	 * Kept for CR-2 (HighRollerLounge, PiglinParlor draw their signs with it); the Grand Casino draws its own marquee.
	 * Neon marquee: a SIGN_BACK board on the wall plane {@code plane} of {@code side}, {@code plan.height() + 2} rows from
	 * {@code bottomY}, {@code pad} cells wider than the text each side; the letters are made of the light-emitting
	 * NEON_A / NEON_B roles (alternating per letter) standing one cell proud of the board when {@code raised}; a
	 * chaser border of BULBs every second cell with SIGN_BACK sockets between (raised too). Text reads left-to-right
	 * for a viewer outside {@code side}.
	 */
	public static Facades.SignBox neon(Blueprint b, SignFont.Plan plan, Facing side, int center, int bottomY, int plane, int pad, boolean raised) {
		int w = plan.width() + 2 * pad, top = bottomY + plan.height() + 1;
		int s = Facades.step(side);
		int start = center - s * ((w - 1) / 2);
		int front = raised ? plane + (side == Facing.SOUTH || side == Facing.EAST ? 1 : -1) : plane;
		for (int i = 0; i < w; i++) {
			for (int y = bottomY; y <= top; y++) {
				Facades.put(b, side, plane, start + s * i, y, Role.SIGN_BACK);
			}
		}
		for (Layouts.SignPixel px : plan.pixels()) {
			Facades.put(b, side, front, start + s * (pad + px.col()), top - 1 - px.row(), px.letter() % 2 == 0 ? Role.NEON_A : Role.NEON_B);
		}
		for (int i = 0; i < w; i++) {
			BlockSpec bulbOrSocket = i % 2 == 0 ? b.palette().block(Role.BULB) : b.palette().block(Role.SIGN_BACK);
			Facades.put(b, side, front, start + s * i, bottomY, bulbOrSocket);
			Facades.put(b, side, front, start + s * i, top, bulbOrSocket);
		}
		for (int y = bottomY + 1; y < top; y++) {
			BlockSpec bulbOrSocket = (y - bottomY) % 2 == 0 ? b.palette().block(Role.BULB) : b.palette().block(Role.SIGN_BACK);
			Facades.put(b, side, front, start, y, bulbOrSocket);
			Facades.put(b, side, front, start + s * (w - 1), y, bulbOrSocket);
		}
		return new Facades.SignBox(Math.min(start, start + s * (w - 1)), Math.max(start, start + s * (w - 1)), bottomY, top);
	}
}
