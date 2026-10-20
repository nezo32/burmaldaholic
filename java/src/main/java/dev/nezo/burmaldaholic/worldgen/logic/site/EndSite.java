package dev.nezo.burmaldaholic.worldgen.logic.site;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Site for the High Roller Lounge beside an End City (owner playtest W2, PURE): the Lounge no longer caps the highest
 * tower. It stands on the island surface ({@code WORLD_SURFACE_WG}) next to the city:
 * <ul>
 *   <li>every footprint column (and the front steps) is at least {@link #CLEAR} blocks from every city piece box,
 *       boxes taken over the full height (so nothing of the city is above or below it either);</li>
 *   <li>the island is under the whole footprint and its surface varies by at most {@link #MAX_SPREAD} blocks (the
 *       plate sits on the highest sample, a skirt fills under the lower part);</li>
 *   <li>the door faces the city; the ground in front is at most {@link #MAX_STEPS} below the plate (a flight of
 *       steps leads down) and never above it;</li>
 *   <li>the closest such site to the city wins (deterministic order).</li>
 * </ul>
 */
public final class EndSite {
	/** Surface model: y of the highest solid block of a column, or {@link #VOID}. */
	public interface Surface {
		int VOID = Integer.MIN_VALUE;

		int top(int x, int z);
	}

	/**
	 * @param placement where the template goes
	 * @param plateY    world y of the floor plate (template row {@code plateRow}) = the highest surface sample
	 * @param door      facing of the entrance
	 * @param steps     front steps down to the ground (0 = flush)
	 * @param stepsBox  footprint of the steps (3 wide, {@code steps} long; null when flush)
	 */
	public record Plan(Geometry.Placement placement, int plateY, Facing door, int steps, Geometry.Box stepsBox) {}

	public static final int CLEAR = 24;
	public static final int MAX_SPREAD = 4;
	public static final int MAX_STEPS = 6;
	static final int GRID = 8;
	static final int SAMPLE = 6;
	static final int MAX_TRIES = 60;
	static final int MAX_CHUNK_REACH = 7;

	private EndSite() {}

	/**
	 * @param city     bounding boxes of every city piece
	 * @param size     template size (unrotated)
	 * @param entry    template-local entrance cell (feet row) on the +z face
	 * @param plateRow template row of the floor plate (the feet row is {@code plateRow + 1})
	 * @param minLand  a column whose top is below this is void
	 */
	public static Optional<Plan> plan(List<Geometry.Box> city, Vec size, Vec entry, int plateRow, Surface s, int minLand, int chunkX,
			int chunkZ) {
		if (city.isEmpty()) {
			return Optional.empty();
		}
		Geometry.Box u = city.getFirst();
		for (Geometry.Box b : city) {
			u = new Geometry.Box(Math.min(u.minX(), b.minX()), Math.min(u.minY(), b.minY()), Math.min(u.minZ(), b.minZ()),
				Math.max(u.maxX(), b.maxX()), Math.max(u.maxY(), b.maxY()), Math.max(u.maxZ(), b.maxZ()));
		}
		int reach = CLEAR + Math.max(size.x(), size.z()) + MAX_STEPS + GRID;
		int ux = u.centerX();
		int uz = u.centerZ();
		record Cand(Geometry.Placement p, Facing door, int dist, long order) {}
		List<Cand> cands = new ArrayList<>();
		for (int cx = snap(u.minX() - reach); cx <= u.maxX() + reach; cx += GRID) {
			for (int cz = snap(u.minZ() - reach); cz <= u.maxZ() + reach; cz += GRID) {
				int dx = ux - cx;
				int dz = uz - cz;
				Facing door = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Facing.EAST : Facing.WEST) : (dz >= 0 ? Facing.SOUTH : Facing.NORTH);
				boolean swap = Geometry.turnsToFace(door) % 2 == 1;
				int fx = swap ? size.z() : size.x();
				int fz = swap ? size.x() : size.z();
				Geometry.Placement p = Geometry.atMin(new Vec(cx - fx / 2, 0, cz - fz / 2), size, door);
				Geometry.Box reachBox = withFront(p.box(), door, MAX_STEPS);
				if (Geometry.chunkReach(reachBox, chunkX, chunkZ) > MAX_CHUNK_REACH) {
					continue;
				}
				int nearest = Integer.MAX_VALUE;
				for (Geometry.Box b : city) {
					nearest = Math.min(nearest, Geometry.gapXZ(reachBox, b));
				}
				if (nearest < CLEAR) {
					continue;
				}
				cands.add(new Cand(p, door, nearest, ((long) cx << 32) ^ (cz & 0xFFFFFFFFL)));
			}
		}
		cands.sort(Comparator.comparingInt(Cand::dist).thenComparingLong(Cand::order));
		int tries = 0;
		for (Cand c : cands) {
			if (tries++ >= MAX_TRIES) {
				break;
			}
			Plan plan = fit(c.p(), c.door(), size, entry, plateRow, s, minLand);
			if (plan != null) {
				return Optional.of(plan);
			}
		}
		return Optional.empty();
	}

	private static int snap(int v) {
		return Math.floorDiv(v, GRID) * GRID;
	}

	/** The box grown by {@code n} blocks in front of a door facing {@code door}. */
	static Geometry.Box withFront(Geometry.Box b, Facing door, int n) {
		return new Geometry.Box(b.minX() + Math.min(0, door.dx() * n), b.minY(), b.minZ() + Math.min(0, door.dz() * n),
			b.maxX() + Math.max(0, door.dx() * n), b.maxY(), b.maxZ() + Math.max(0, door.dz() * n));
	}

	/** Terrain test of one candidate footprint; the placement's y is set from the surface. */
	static Plan fit(Geometry.Placement p0, Facing door, Vec size, Vec entry, int plateRow, Surface s, int minLand) {
		Geometry.Box b = p0.box();
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int x = b.minX(); x <= b.maxX() + SAMPLE - 1; x += SAMPLE) {
			for (int z = b.minZ(); z <= b.maxZ() + SAMPLE - 1; z += SAMPLE) {
				int top = s.top(Math.min(x, b.maxX()), Math.min(z, b.maxZ()));
				if (top == Surface.VOID || top < minLand) {
					return null;
				}
				lo = Math.min(lo, top);
				hi = Math.max(hi, top);
				if (hi - lo > MAX_SPREAD) {
					return null;
				}
			}
		}
		int plateY = hi;
		// the door's outside cell, then the run of steps down to the ground
		Vec out = Geometry.toWorld(p0.origin(), new Vec(entry.x(), 0, size.z()), p0.quarterTurns());
		Facing side = door.rotate(1);
		int steps = 0;
		for (int k = 0; k <= MAX_STEPS; k++) {
			int x = out.x() + door.dx() * k;
			int z = out.z() + door.dz() * k;
			int ground = Integer.MIN_VALUE;
			for (int l = -1; l <= 1; l++) {
				int top = s.top(x + side.dx() * l, z + side.dz() * l);
				if (top == Surface.VOID || top < minLand) {
					return null;
				}
				ground = Math.max(ground, top);
			}
			if (ground > plateY) {
				return null; // a bank in front of the door
			}
			// stairs stand on cells 0..steps-1, the one on cell k at plateY - k (high side towards the door); the
			// flight ends on the first cell whose ground is at most a half block below the last stair's low tread
			if (ground >= plateY - k) {
				steps = k;
				break;
			}
			if (k == MAX_STEPS) {
				return null;
			}
		}
		Vec origin = new Vec(p0.origin().x(), plateY - plateRow, p0.origin().z());
		Geometry.Placement p = new Geometry.Placement(origin, p0.quarterTurns(),
			Geometry.templateBox(origin, size, p0.quarterTurns()));
		Geometry.Box stepsBox = null;
		if (steps > 0) {
			Vec a = new Vec(out.x() - Math.abs(side.dx()), plateY - steps + 1, out.z() - Math.abs(side.dz()));
			Vec e = new Vec(out.x() + door.dx() * (steps - 1) + Math.abs(side.dx()), plateY,
				out.z() + door.dz() * (steps - 1) + Math.abs(side.dz()));
			stepsBox = Geometry.Box.corners(a, e);
		}
		return new Plan(p, plateY, door, steps, stepsBox);
	}
}
