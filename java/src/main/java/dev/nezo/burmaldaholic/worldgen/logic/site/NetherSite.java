package dev.nezo.burmaldaholic.worldgen.logic.site;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Site probe for the Piglin Parlor beside a bastion (owner playtest W1, PURE): instead of "floor = bastion minY", every
 * side of the bastion × a few floor heights × a few shifts along the side is scored on the real terrain model:
 * <ul>
 *   <li>the floor row is at or above the lava sea ({@code lavaLevel}, y 32 in the Nether) and no lava is inside or
 *       above the envelope;</li>
 *   <li>the ground under the foundation is mostly solid;</li>
 *   <li>a <b>front tunnel</b> ({@link Tunnel}, 3 × 4, stairs) runs straight out of the front door to the nearest open
 *       air at least 3 high, and from there a walk on the terrain model reaches {@link #GOAL} blocks outside the
 *       envelope;</li>
 *   <li>a <b>back tunnel</b> runs from the back door into the bastion: to the nearest air of a bastion template
 *       (preferred), else to open air next to it.</li>
 * </ul>
 * A site without a front route is rejected; with none left the bastion gets no Parlor.
 */
public final class NetherSite {
	/** Terrain model: true where the generated rock is solid. Non-solid cells below {@code lavaLevel} are lava. */
	public interface Terrain {
		boolean solid(int x, int y, int z);
	}

	/** The host structure's blocks: {@link #OUTSIDE} every piece, template {@link #AIR}, or anything else. */
	public interface Host {
		int OUTSIDE = 0;
		int AIR = 1;
		int SOLID = 2;

		int cell(int x, int y, int z);
	}

	/**
	 * Template-local door cells (feet row). {@code front}: the front entrance on the +z face (z = size.z - 1); the
	 * front tunnel starts one block outside it. {@code back}: the first cell of the back passage (the wall cell of the
	 * back door, or the outermost cell of an open back door); the back tunnel walks -z from there. {@code back} may be
	 * null (no back door).
	 */
	public record Doors(Vec front, Vec back) {}

	/** A chosen site: where the template goes, its tunnels and the score (lower = better). */
	public record Plan(Geometry.Placement placement, Tunnel front, Tunnel back, boolean backIntoHost, int score) {}

	static final int[] DY = {0, 3, 6, 9, 12, 15, 18};
	static final int[] ALONG = {0, -8, 8};
	public static final int MAX_FRONT = 24;
	public static final int MAX_BACK = 24;
	static final int MAX_RAMP = 10;
	/** The walk from the front tunnel must reach this many blocks outside the envelope. */
	public static final int GOAL = 8;
	static final int BFS_BUDGET = 3000;
	static final int MAX_CHUNK_REACH = 7;
	static final double MIN_FLOOR = 0.6;

	private NetherSite() {}

	public static Optional<Plan> plan(Geometry.Box host, List<Geometry.Box> pieces, Vec size, Doors doors, Terrain t, Host h, int lavaLevel,
			int chunkX, int chunkZ, int firstSide) {
		Plan best = null;
		int base = Math.max(lavaLevel, host.minY());
		for (int dyi = 0; dyi < DY.length; dyi++) {
			for (int si = 0; si < 4; si++) {
				Facing side = Facing.values()[(firstSide + si) % 4];
				for (int along : ALONG) {
					Geometry.Placement p = Geometry.beside(host, size, side, 0, base + DY[dyi], along);
					int penalty = dyi * 3 + Math.abs(along) / 8 * 4;
					if (best != null && penalty >= best.score()) {
						continue;
					}
					Plan plan = evaluate(p, pieces, size, doors, t, h, lavaLevel, chunkX, chunkZ, penalty);
					if (plan != null && (best == null || plan.score() < best.score())) {
						best = plan;
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}

	static Plan evaluate(Geometry.Placement p, List<Geometry.Box> pieces, Vec size, Doors doors, Terrain t, Host h, int lavaLevel, int chunkX,
			int chunkZ, int penalty) {
		Geometry.Box env = p.box();
		if (env.minY() < lavaLevel || Geometry.chunkReach(env, chunkX, chunkZ) > MAX_CHUNK_REACH) {
			return null;
		}
		for (Geometry.Box b : pieces) {
			if (b.intersects(env)) {
				return null;
			}
		}
		// lava inside or above the envelope (only rows below the lava level can hold it)
		for (int y = env.minY(); y <= Math.min(env.maxY() + 4, lavaLevel - 1); y++) {
			for (int x = env.minX(); x <= env.maxX(); x += 4) {
				for (int z = env.minZ(); z <= env.maxZ(); z += 4) {
					if (!t.solid(x, y, z)) {
						return null;
					}
				}
			}
		}
		int solid = 0;
		int total = 0;
		for (int x = env.minX(); x <= env.maxX() + 3; x += 4) {
			for (int z = env.minZ(); z <= env.maxZ() + 3; z += 4) {
				total++;
				if (t.solid(Math.min(x, env.maxX()), env.minY() - 1, Math.min(z, env.maxZ()))) {
					solid++;
				}
			}
		}
		double floor = solid / (double) total;
		if (floor < MIN_FLOOR) {
			return null;
		}
		Tunnel front = frontRoute(p, size, doors.front(), t, lavaLevel, pieces, chunkX, chunkZ);
		if (front == null) {
			return null;
		}
		Tunnel back = null;
		boolean intoHost = false;
		if (doors.back() != null) {
			Tunnel b = backRoute(p, doors.back(), t, h, lavaLevel, true);
			intoHost = b != null;
			if (b == null) {
				b = backRoute(p, doors.back(), t, h, lavaLevel, false);
			}
			if (b != null && Geometry.chunkReach(b.box(), chunkX, chunkZ) <= MAX_CHUNK_REACH) {
				back = b;
			} else {
				intoHost = false;
			}
		}
		int score = penalty + front.length() * 2 + (back == null ? 60 : back.length() + (intoHost ? 0 : 15)) + (int) Math.round((1 - floor) * 20);
		return new Plan(p, front, back, intoHost, score);
	}

	/** Shortest front tunnel to open air from which the terrain model walks {@link #GOAL} blocks away, or null. */
	static Tunnel frontRoute(Geometry.Placement p, Vec size, Vec door, Terrain t, int lava, List<Geometry.Box> pieces, int chunkX, int chunkZ) {
		Facing d = Facing.SOUTH.rotate(p.quarterTurns());
		Facing s = d.rotate(1);
		Vec c0 = Geometry.toWorld(p.origin(), new Vec(door.x(), door.y(), size.z()), p.quarterTurns());
		int f0 = c0.y();
		Geometry.Box env = p.box();
		for (int k = 0; k <= MAX_FRONT; k++) {
			int cx = c0.x() + d.dx() * k;
			int cz = c0.z() + d.dz() * k;
			Geometry.Box column = new Geometry.Box(cx - 2 * Math.abs(s.dx()), f0 - MAX_RAMP - 2, cz - 2 * Math.abs(s.dz()),
				cx + 2 * Math.abs(s.dx()), f0 + MAX_RAMP + Tunnel.HEIGHT, cz + 2 * Math.abs(s.dz()));
			if (Geometry.chunkReach(column, chunkX, chunkZ) > MAX_CHUNK_REACH || hits(column, pieces)) {
				return null;
			}
			int rmax = Math.min(k, MAX_RAMP);
			for (int r = 0; r <= rmax; r++) {
				for (int sign = 1; sign >= -1; sign -= 2) {
					if (r == 0 && sign < 0) {
						continue;
					}
					int ft = f0 + sign * r;
					if (ft < lava + 1 || !open(t, lava, cx, cz, s, ft) || !openColumn(t, lava, cx + d.dx(), cz + d.dz(), ft)) {
						continue;
					}
					if (!walksAway(t, lava, cx, ft, cz, env, pieces)) {
						continue;
					}
					int[] feet = new int[k + 1];
					for (int i = 0; i <= k; i++) {
						feet[i] = f0 + sign * Math.min(i, r);
					}
					return new Tunnel(c0.x(), c0.z(), d, feet);
				}
			}
		}
		return null;
	}

	/**
	 * Back tunnel from the back door cell, walking -z (template-local) into the bastion: to the first step whose
	 * cross-section is template air of a bastion piece over a solid floor ({@code intoHost}), else to the first open
	 * terrain air outside every piece. The tunnel ends one step before that target.
	 */
	static Tunnel backRoute(Geometry.Placement p, Vec back, Terrain t, Host h, int lava, boolean intoHost) {
		Facing d = Facing.NORTH.rotate(p.quarterTurns());
		Facing s = d.rotate(1);
		Vec c0 = Geometry.toWorld(p.origin(), back, p.quarterTurns());
		int f0 = c0.y();
		int inside = back.z() + 1; // steps 0..back.z lie inside the envelope (wall, platform)
		for (int k = Math.max(1, inside); k <= MAX_BACK; k++) {
			int cx = c0.x() + d.dx() * k;
			int cz = c0.z() + d.dz() * k;
			int rmax = Math.min(k - inside, MAX_RAMP);
			for (int r = 0; r <= rmax; r++) {
				for (int sign = 1; sign >= -1; sign -= 2) {
					if (r == 0 && sign < 0) {
						continue;
					}
					int ft = f0 + sign * r;
					if (ft < lava + 1) {
						continue;
					}
					boolean ok = intoHost ? hostOpen(h, t, cx, cz, s, ft) : terrainOpen(h, t, lava, cx, cz, s, ft);
					if (!ok) {
						continue;
					}
					int[] feet = new int[k];
					for (int i = 0; i < k; i++) {
						feet[i] = i < inside ? f0 : f0 + sign * Math.min(i - inside + 1, r);
					}
					return new Tunnel(c0.x(), c0.z(), d, feet);
				}
			}
		}
		return null;
	}

	private static boolean hits(Geometry.Box box, List<Geometry.Box> pieces) {
		for (Geometry.Box b : pieces) {
			if (b.intersects(box)) {
				return true;
			}
		}
		return false;
	}

	/** The three walk columns of a step are air (not lava) for three rows from {@code feet}. */
	static boolean open(Terrain t, int lava, int cx, int cz, Facing s, int feet) {
		for (int l = -1; l <= 1; l++) {
			if (!openColumn(t, lava, cx + s.dx() * l, cz + s.dz() * l, feet)) {
				return false;
			}
		}
		return true;
	}

	static boolean openColumn(Terrain t, int lava, int x, int z, int feet) {
		for (int y = feet; y <= feet + 2; y++) {
			if (y < lava || t.solid(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	static boolean hostOpen(Host h, Terrain t, int cx, int cz, Facing s, int feet) {
		for (int l = -1; l <= 1; l++) {
			for (int y = feet; y <= feet + 2; y++) {
				if (h.cell(cx + s.dx() * l, y, cz + s.dz() * l) != Host.AIR) {
					return false;
				}
			}
		}
		int below = h.cell(cx, feet - 1, cz);
		return below == Host.SOLID || below == Host.OUTSIDE && t.solid(cx, feet - 1, cz);
	}

	static boolean terrainOpen(Host h, Terrain t, int lava, int cx, int cz, Facing s, int feet) {
		for (int l = -1; l <= 1; l++) {
			for (int y = feet; y <= feet + 2; y++) {
				int x = cx + s.dx() * l;
				int z = cz + s.dz() * l;
				if (h.cell(x, y, z) != Host.OUTSIDE || y < lava || t.solid(x, y, z)) {
					return false;
				}
			}
		}
		return h.cell(cx, feet - 1, cz) == Host.OUTSIDE && t.solid(cx, feet - 1, cz);
	}

	/**
	 * Walk on the terrain model from {@code (x, y, z)} (the tunnel's last floor): 4 directions, up one block or down
	 * at most three, never into lava, the envelope or a host piece. True once a cell {@link #GOAL} blocks outside the
	 * envelope is reached.
	 */
	public static boolean walksAway(Terrain t, int lava, int x, int y, int z, Geometry.Box env, List<Geometry.Box> pieces) {
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		queue.add(new int[] {x, y, z});
		seen.add(key(x, y, z));
		int budget = BFS_BUDGET;
		while (!queue.isEmpty() && budget-- > 0) {
			int[] c = queue.poll();
			if (outside(env, c[0], c[2]) >= GOAL && t.solid(c[0], c[1] - 1, c[2])) {
				return true;
			}
			for (Facing f : Facing.values()) {
				int nx = c[0] + f.dx();
				int nz = c[2] + f.dz();
				int ny = Integer.MIN_VALUE;
				int cy = c[1];
				if (t.solid(nx, cy, nz)) {
					if (!t.solid(nx, cy + 1, nz) && !t.solid(nx, cy + 2, nz) && !t.solid(c[0], cy + 2, c[2])) {
						ny = cy + 1;
					}
				} else if (!t.solid(nx, cy + 1, nz)) {
					int fy = cy;
					while (fy > cy - 4 && !t.solid(nx, fy - 1, nz)) {
						fy--;
					}
					if (fy > cy - 4) {
						ny = fy;
					}
				}
				if (ny == Integer.MIN_VALUE || ny < lava || inAny(env, pieces, nx, ny, nz) || !seen.add(key(nx, ny, nz))) {
					continue;
				}
				queue.add(new int[] {nx, ny, nz});
			}
		}
		return false;
	}

	/** Horizontal (Chebyshev) distance of a column outside a box; 0 inside. */
	public static int outside(Geometry.Box b, int x, int z) {
		return Math.max(0, Math.max(Math.max(b.minX() - x, x - b.maxX()), Math.max(b.minZ() - z, z - b.maxZ())));
	}

	private static boolean inAny(Geometry.Box env, List<Geometry.Box> pieces, int x, int y, int z) {
		if (env.contains(x, y, z)) {
			return true;
		}
		for (Geometry.Box b : pieces) {
			if (b.contains(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
