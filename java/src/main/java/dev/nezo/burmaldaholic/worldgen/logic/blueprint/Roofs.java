package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;

/**
 * Roof shapes (architecture doc §3.3, lane CB-3): static verbs on a {@link Blueprint}, palette-driven like the
 * rest of the DSL. Every verb writes only inside the box it is given ({@code x0..x1 × z0..z1}, rows
 * {@code baseY..maxY}) and never outside the template; the caller chooses the overhang by passing a box larger
 * than the walls.
 *
 * <p>{@link #roof} is the one-call entry used by the piece generators: it builds the style's
 * {@link RoofKind} over a footprint. The shape verbs below it stay public for pieces that compose their own
 * roofline (a dome over a flat wing, a spire on a corner, ...).
 */
public final class Roofs {
	private Roofs() {}

	/** Campfire used as chimney smoke (lit, no signal smoke column). */
	public static final BlockSpec CHIMNEY_FIRE = BlockSpec.mc("campfire", "facing", "south", "lit", "true", "signal_fire", "false",
		"waterlogged", "false");
	/** Upward end rod: finials, spire tips. */
	public static final BlockSpec FINIAL = BlockSpec.mc("end_rod", "facing", "up");

	/**
	 * The style's roof over the footprint {@code x0..x1 × z0..z1}: eaves at {@code baseY}, nothing above
	 * {@code maxY}. The footprint is usually the outer wall box (the eave ring sits on the wall top) or one
	 * block wider for an overhang.
	 */
	public static Blueprint roof(Blueprint b, RoofKind kind, int x0, int z0, int x1, int z1, int baseY, int maxY) {
		int w = x1 - x0 + 1, d = z1 - z0 + 1;
		switch (kind) {
			case HIP -> hip(b, x0, z0, x1, z1, baseY, maxY, Role.ROOF, Role.ROOF_TRIM, Role.ROOF_TRIM);
			case THATCH -> thatch(b, x0, z0, x1, z1, baseY, maxY);
			case STEEP_GABLE -> {
				gable(b, x0, z0, x1, z1, baseY, maxY, false, Role.ROOF, Role.ROOF_TRIM, Role.WALL);
				int cx = x0 + Math.max(2, w / 4);
				int cz = z0 + Math.max(2, d / 3);
				int top = Math.min(maxY, roofTopAt(x0, x1, cx, baseY) + 2);
				if (top - 1 > baseY) {
					chimney(b, cx, cz, baseY, top - 1, Role.ACCENT);
				}
			}
			case FLAT_DOME -> {
				flat(b, x0, z0, x1, z1, baseY, Role.ROOF);
				int ph = Math.min(3, maxY - baseY);
				steppedParapet(b, x0, z0, x1, z1, baseY + 1, ph, Role.ROOF_TRIM, Role.TRIM);
				int r = Math.max(2, Math.min(w, d) / 2 - 3);
				int h = Math.min(r + 1, maxY - baseY - 1);
				if (h >= 2) {
					dome(b, (x0 + x1) / 2.0, (z0 + z1) / 2.0, r, r, baseY + 1, h, Role.GLASS, Role.TRIM, true);
				}
			}
			case ICE_DOME -> {
				flat(b, x0, z0, x1, z1, baseY, Role.ROOF_TRIM);
				ring(b, x0, z0, x1, z1, baseY + 1, Role.ROOF_TRIM, true);
				int r = Math.max(2, Math.min(w, d) / 2 - 2);
				int h = Math.min(r + 2, maxY - baseY - 1);
				if (h >= 2) {
					dome(b, (x0 + x1) / 2.0, (z0 + z1) / 2.0, r, r, baseY + 1, h, Role.ROOF, Role.FRAME, true);
				}
				int sh = Math.min(maxY - baseY - 1, Math.max(2, h - 1));
				for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
					spire(b, c[0], c[1], baseY + 1, sh, Role.FRAME);
				}
			}
			case MANSARD -> mansard(b, x0, z0, x1, z1, baseY, Math.min(3, maxY - baseY), Role.ROOF, Role.ROOF_TRIM);
		}
		return b;
	}

	/** Height of a 45° gable/hip at column {@code x} for a roof spanning {@code x0..x1} that starts at {@code baseY}. */
	static int roofTopAt(int x0, int x1, int x, int baseY) {
		return baseY + Math.min(x - x0, x1 - x);
	}

	// ---- shapes ---------------------------------------------------------------------------------------

	/**
	 * Hip roof: rings of {@code roof} stairs stepping inwards one per row, the first ring in {@code eave}
	 * stairs. Where the rings meet (or {@code maxY} is reached) the remaining strip becomes a {@code ridge}
	 * slab line, so a long building gets a proper ridge instead of a single stair line.
	 */
	public static Blueprint hip(Blueprint b, int x0, int z0, int x1, int z1, int baseY, int maxY, Role roof, Role eave, Role ridge) {
		for (int i = 0; ; i++) {
			int ax = x0 + i, az = z0 + i, bx = x1 - i, bz = z1 - i, y = baseY + i;
			if (ax > bx || az > bz || y > maxY) {
				break;
			}
			Material m = b.palette().get(i == 0 ? eave : roof);
			if (bx - ax <= 1 || bz - az <= 1 || y == maxY) {
				// ridge strip: what is left of the footprint at this row
				Material r = b.palette().get(ridge);
				for (int x = ax; x <= bx; x++) {
					for (int z = az; z <= bz; z++) {
						boolean edge = x == ax || x == bx || z == az || z == bz;
						if (y == maxY && !(bx - ax <= 1 || bz - az <= 1) && edge) {
							b.set(x, y, z, stairFor(m, x, z, ax, az, bx, bz));
						} else {
							b.set(x, y, z, r.slab("bottom"));
						}
					}
				}
				break;
			}
			for (int x = ax; x <= bx; x++) {
				b.set(x, y, az, m.stairs(Facing.SOUTH, false));
				b.set(x, y, bz, m.stairs(Facing.NORTH, false));
			}
			for (int z = az + 1; z < bz; z++) {
				b.set(ax, y, z, m.stairs(Facing.EAST, false));
				b.set(bx, y, z, m.stairs(Facing.WEST, false));
			}
		}
		return b;
	}

	private static BlockSpec stairFor(Material m, int x, int z, int ax, int az, int bx, int bz) {
		if (z == az) {
			return m.stairs(Facing.SOUTH, false);
		}
		if (z == bz) {
			return m.stairs(Facing.NORTH, false);
		}
		return m.stairs(x == ax ? Facing.EAST : Facing.WEST, false);
	}

	/**
	 * Gable roof. {@code ridgeAlongX}: the ridge runs along x and the slopes face north / south (gable ends on
	 * the west / east walls); otherwise the ridge runs front-to-back and the gable ends face the front and the
	 * back. The first stair row is {@code eave}, the rest {@code roof}; the gable triangles are filled with
	 * {@code gableFill}; the ridge (or the flat strip left at {@code maxY}) is a {@code ROOF_TRIM} slab line.
	 */
	public static Blueprint gable(Blueprint b, int x0, int z0, int x1, int z1, int baseY, int maxY, boolean ridgeAlongX,
			Role roof, Role eave, Role gableFill) {
		int lo = ridgeAlongX ? z0 : x0, hi = ridgeAlongX ? z1 : x1;
		int from = ridgeAlongX ? x0 : z0, to = ridgeAlongX ? x1 : z1;
		Material ridge = b.palette().get(Role.ROOF_TRIM);
		for (int i = 0; ; i++) {
			int a = lo + i, c = hi - i, y = baseY + i;
			if (a > c || y > maxY) {
				break;
			}
			Material m = b.palette().get(i == 0 ? eave : roof);
			boolean last = c - a <= 1 || y == maxY;
			for (int t = from; t <= to; t++) {
				if (last) {
					for (int s = a; s <= c; s++) {
						boolean slope = (s == a || s == c) && c - a > 1;
						BlockSpec spec = slope ? m.stairs(slopeFacing(ridgeAlongX, s == a), false) : ridge.slab("bottom");
						if (c - a == 1) {
							spec = m.stairs(slopeFacing(ridgeAlongX, s == a), false);
						}
						put(b, ridgeAlongX, t, y, s, spec);
					}
				} else {
					put(b, ridgeAlongX, t, y, a, m.stairs(slopeFacing(ridgeAlongX, true), false));
					put(b, ridgeAlongX, t, y, c, m.stairs(slopeFacing(ridgeAlongX, false), false));
				}
			}
			if (c - a == 1 && !(y == maxY)) {
				// two stair rows meet: cap the ridge with slabs one row up when there is room
				if (y + 1 <= maxY) {
					for (int t = from; t <= to; t++) {
						for (int s = a; s <= c; s++) {
							put(b, ridgeAlongX, t, y + 1, s, ridge.slab("bottom"));
						}
					}
				}
			}
			// gable end triangles under this row
			for (int s = a + 1; s <= c - 1; s++) {
				if (!last) {
					put(b, ridgeAlongX, from, y, s, b.palette().block(gableFill));
					put(b, ridgeAlongX, to, y, s, b.palette().block(gableFill));
				}
			}
			if (last) {
				break;
			}
		}
		return b;
	}

	private static Facing slopeFacing(boolean ridgeAlongX, boolean lowSide) {
		if (ridgeAlongX) {
			return lowSide ? Facing.SOUTH : Facing.NORTH;
		}
		return lowSide ? Facing.EAST : Facing.WEST;
	}

	/** {@code t} runs along the ridge, {@code s} across it. */
	private static void put(Blueprint b, boolean ridgeAlongX, int t, int y, int s, BlockSpec spec) {
		if (ridgeAlongX) {
			b.set(t, y, s, spec);
		} else {
			b.set(s, y, t, spec);
		}
	}

	/**
	 * Thatch: a stepped hip of full {@code ROOF} blocks (hay bales have no stairs), an eave ring of
	 * {@code ROOF_TRIM} slabs one row under the first bale ring, and a {@code ROOF_TRIM} ridge with upturned
	 * end posts (fences) like a lodge ridge.
	 */
	public static Blueprint thatch(Blueprint b, int x0, int z0, int x1, int z1, int baseY, int maxY) {
		Material trim = b.palette().get(Role.ROOF_TRIM);
		ring(b, x0, z0, x1, z1, baseY, trim.slab("top"));
		int top = baseY;
		int ax = x0, az = z0, bx = x1, bz = z1;
		for (int i = 0; ; i++) {
			ax = x0 + 1 + i;
			az = z0 + 1 + i;
			bx = x1 - 1 - i;
			bz = z1 - 1 - i;
			int y = baseY + 1 + i;
			if (ax > bx || az > bz || y > maxY) {
				break;
			}
			top = y;
			if (bx - ax <= 1 || bz - az <= 1 || y == maxY) {
				b.fill(ax, y, az, bx, y, bz, Role.ROOF);
				if (y + 1 <= maxY) {
					boolean alongX = bx - ax >= bz - az;
					int cz = (az + bz) / 2, cx = (ax + bx) / 2;
					if (alongX) {
						b.fill(ax, y + 1, cz, bx, y + 1, cz, trim.slab("bottom"));
						if (bz - az >= 1) {
							b.fill(ax, y + 1, bz, bx, y + 1, bz, trim.slab("bottom"));
						}
					} else {
						b.fill(cx, y + 1, az, cx, y + 1, bz, trim.slab("bottom"));
					}
				}
				break;
			}
			ring(b, ax, az, bx, bz, y, b.palette().block(Role.ROOF));
		}
		return b;
	}

	/** Flat roof slab of {@code role} (full blocks) over the footprint at row {@code y}. */
	public static Blueprint flat(Blueprint b, int x0, int z0, int x1, int z1, int y, Role role) {
		return b.fill(x0, y, z0, x1, y, z1, role);
	}

	/** One ring (outline) of {@code spec} at row {@code y}. */
	public static Blueprint ring(Blueprint b, int x0, int z0, int x1, int z1, int y, BlockSpec spec) {
		for (int x = x0; x <= x1; x++) {
			b.set(x, y, z0, spec);
			b.set(x, y, z1, spec);
		}
		for (int z = z0; z <= z1; z++) {
			b.set(x0, y, z, spec);
			b.set(x1, y, z, spec);
		}
		return b;
	}

	/** Ring of {@code role}: full blocks, or bottom slabs when {@code slab}. */
	public static Blueprint ring(Blueprint b, int x0, int z0, int x1, int z1, int y, Role role, boolean slab) {
		Material m = b.palette().get(role);
		return ring(b, x0, z0, x1, z1, y, slab ? m.slab("bottom") : m.block());
	}

	/**
	 * Art-deco stepped parapet on the outline of a flat roof, rows {@code y..y+height-1}: one row everywhere,
	 * full {@code height} at the corners and a ziggurat (stepping down one row every two cells) centred on each
	 * side; the top block of every column is {@code cap}.
	 */
	public static Blueprint steppedParapet(Blueprint b, int x0, int z0, int x1, int z1, int y, int height, Role wall, Role cap) {
		if (height < 1) {
			return b;
		}
		int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) {
			int h = stepHeight(Math.abs(x - mx), height, x == x0 || x == x1);
			column(b, x, y, z0, h, wall, cap);
			column(b, x, y, z1, h, wall, cap);
		}
		for (int z = z0 + 1; z < z1; z++) {
			int h = stepHeight(Math.abs(z - mz), height, false);
			column(b, x0, y, z, h, wall, cap);
			column(b, x1, y, z, h, wall, cap);
		}
		return b;
	}

	private static int stepHeight(int distFromMid, int height, boolean corner) {
		if (corner) {
			return height;
		}
		return Math.max(1, height - distFromMid / 2);
	}

	private static void column(Blueprint b, int x, int y, int z, int h, Role wall, Role cap) {
		for (int i = 0; i < h; i++) {
			b.set(x, y + i, z, i == h - 1 ? cap : wall);
		}
	}

	/**
	 * Dome (half ellipsoid) centred on ({@code cx}, {@code cz}) — use {@code .5} centres for even widths —
	 * radii {@code rx}, {@code rz}, rising {@code h} rows above {@code baseY}. Shell cells get {@code shell};
	 * when {@code hollow} the cells inside are cleared (a glass dome lights the room under it). A {@code ring}
	 * band marks the springing row and a {@link #FINIAL} crowns the top when it fits.
	 */
	public static Blueprint dome(Blueprint b, double cx, double cz, int rx, int rz, int baseY, int h, Role shell, Role ring,
			boolean hollow) {
		Vec size = b.size();
		int minX = (int) Math.floor(cx - rx), maxX = (int) Math.ceil(cx + rx);
		int minZ = (int) Math.floor(cz - rz), maxZ = (int) Math.ceil(cz + rz);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = 0; y < h; y++) {
					if (x < 0 || z < 0 || x >= size.x() || z >= size.z() || baseY + y >= size.y()) {
						continue;
					}
					if (!inDome(x, y, z, cx, cz, rx, rz, h)) {
						continue;
					}
					boolean surface = !inDome(x + 1, y, z, cx, cz, rx, rz, h) || !inDome(x - 1, y, z, cx, cz, rx, rz, h)
						|| !inDome(x, y, z + 1, cx, cz, rx, rz, h) || !inDome(x, y, z - 1, cx, cz, rx, rz, h)
						|| !inDome(x, y + 1, z, cx, cz, rx, rz, h);
					if (surface) {
						b.set(x, baseY + y, z, y == 0 && ring != null ? ring : shell);
					} else if (hollow) {
						b.set(x, baseY + y, z, Blueprint.AIR);
					}
				}
			}
		}
		int tx = (int) Math.round(cx), tz = (int) Math.round(cz);
		int top = baseY + h;
		if (top < size.y() && Math.abs(cx - tx) < 0.01 && Math.abs(cz - tz) < 0.01) {
			b.set(tx, top, tz, FINIAL);
		}
		return b;
	}

	static boolean inDome(int x, int y, int z, double cx, double cz, int rx, int rz, int h) {
		if (y < 0) {
			return true;
		}
		double ex = (x - cx) / (rx + 0.5), ez = (z - cz) / (rz + 0.5), ey = y / (h - 0.5);
		return ex * ex + ez * ez + ey * ey <= 1.0;
	}

	/**
	 * Steep mansard: {@code height} rows of {@code roof} stepping in one cell every row (full blocks for the
	 * near-vertical lower slope), then a flat {@code cap} slab top.
	 */
	public static Blueprint mansard(Blueprint b, int x0, int z0, int x1, int z1, int baseY, int height, Role roof, Role cap) {
		for (int i = 0; i < height; i++) {
			int ax = x0 + i, az = z0 + i, bx = x1 - i, bz = z1 - i;
			if (ax > bx || az > bz) {
				return b;
			}
			ring(b, ax, az, bx, bz, baseY + i, roof, false);
		}
		int ax = x0 + height, az = z0 + height, bx = x1 - height, bz = z1 - height;
		if (ax <= bx && az <= bz) {
			b.fill(ax, baseY + height - 1, az, bx, baseY + height - 1, bz, b.palette().get(cap).slab("top"));
		}
		return b;
	}

	/**
	 * Spire: a {@code body} column of {@code height} rows from {@code baseY} with a {@link #FINIAL} on top
	 * (skipped at the template ceiling).
	 */
	public static Blueprint spire(Blueprint b, int x, int z, int baseY, int height, Role body) {
		for (int i = 0; i < height; i++) {
			b.set(x, baseY + i, z, body);
		}
		if (baseY + height < b.size().y()) {
			b.set(x, baseY + height, z, FINIAL);
		}
		return b;
	}

	/** Chimney: a {@code body} column from {@code y0} to {@code y1} with a lit campfire (smoke) on top when it fits. */
	public static Blueprint chimney(Blueprint b, int x, int z, int y0, int y1, Role body) {
		for (int y = y0; y <= y1; y++) {
			b.set(x, y, z, body);
		}
		if (y1 + 1 < b.size().y()) {
			b.set(x, y1 + 1, z, CHIMNEY_FIRE);
		}
		return b;
	}
}
