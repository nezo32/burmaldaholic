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
 *   <li>the whole template box is at least {@link #CLEAR} blocks from every city piece box, boxes taken over the
 *       full height (nothing of the city is above or below it either);</li>
 *   <li>the island is under the whole floor plate and its surface varies by at most {@link #MAX_SPREAD} blocks: the
 *       plate row goes on the highest sample, the piece's own skirt covers the slope (CR-2 contract);</li>
 *   <li>the door faces the city; the ground at the foot of the piece's front stair is between one block below the
 *       stair foot and the plate (higher ground buries the lower steps);</li>
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
	 * Template shape (unrotated, front = +z).
	 *
	 * @param size       template size
	 * @param plateDepth last z row of the floor plate (the stair lies beyond it)
	 * @param plateRow   template row of the floor plate (put on the highest ground)
	 * @param stairFoot  walk cell at the foot of the front stair
	 */
	public record Shape(Vec size, int plateDepth, int plateRow, Vec stairFoot) {}

	/**
	 * @param placement where the template goes
	 * @param plateY    world y of the floor plate (= the highest surface sample under it)
	 * @param door      facing of the entrance
	 */
	public record Plan(Geometry.Placement placement, int plateY, Facing door) {}

	public static final int CLEAR = 24;
	public static final int MAX_SPREAD = 4;
	static final int GRID = 8;
	static final int SAMPLE = 6;
	static final int MAX_TRIES = 60;
	static final int MAX_CHUNK_REACH = 7;

	private EndSite() {}

	/**
	 * @param city    bounding boxes of every city piece
	 * @param minLand a column whose top is below this is void
	 */
	public static Optional<Plan> plan(List<Geometry.Box> city, Shape shape, Surface s, int minLand, int chunkX, int chunkZ) {
		if (city.isEmpty()) {
			return Optional.empty();
		}
		Geometry.Box u = city.getFirst();
		for (Geometry.Box b : city) {
			u = new Geometry.Box(Math.min(u.minX(), b.minX()), Math.min(u.minY(), b.minY()), Math.min(u.minZ(), b.minZ()),
				Math.max(u.maxX(), b.maxX()), Math.max(u.maxY(), b.maxY()), Math.max(u.maxZ(), b.maxZ()));
		}
		Vec size = shape.size();
		int reach = CLEAR + Math.max(size.x(), size.z()) + GRID;
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
				if (Geometry.chunkReach(p.box(), chunkX, chunkZ) > MAX_CHUNK_REACH) {
					continue;
				}
				int nearest = Integer.MAX_VALUE;
				for (Geometry.Box b : city) {
					nearest = Math.min(nearest, Geometry.gapXZ(p.box(), b));
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
			Plan plan = fit(c.p(), c.door(), shape, s, minLand);
			if (plan != null) {
				return Optional.of(plan);
			}
		}
		return Optional.empty();
	}

	private static int snap(int v) {
		return Math.floorDiv(v, GRID) * GRID;
	}

	/** Terrain test of one candidate; the placement's y is set from the surface. Null when it does not fit. */
	static Plan fit(Geometry.Placement p0, Facing door, Shape shape, Surface s, int minLand) {
		Vec size = shape.size();
		int turns = p0.quarterTurns();
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int x = 0; x <= size.x() - 1 + SAMPLE - 1; x += SAMPLE) {
			for (int z = 0; z <= shape.plateDepth() + SAMPLE - 1; z += SAMPLE) {
				Vec w = Geometry.toWorld(p0.origin(), new Vec(Math.min(x, size.x() - 1), 0, Math.min(z, shape.plateDepth())), turns);
				int top = s.top(w.x(), w.z());
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
		int y0 = plateY - shape.plateRow();
		Vec foot = shape.stairFoot();
		for (int dz = 0; dz <= 1; dz++) {
			for (int l = -1; l <= 1; l++) {
				Vec w = Geometry.toWorld(p0.origin(), new Vec(foot.x() + l, 0, foot.z() + dz), turns);
				int top = s.top(w.x(), w.z());
				// the stair foot stands on template row foot.y - 1; one block lower is a step down, the plate is the top
				if (top == Surface.VOID || top < y0 + foot.y() - 2 || top > plateY) {
					return null;
				}
			}
		}
		Vec origin = new Vec(p0.origin().x(), y0, p0.origin().z());
		return new Plan(new Geometry.Placement(origin, turns, Geometry.templateBox(origin, size, turns)), plateY, door);
	}
}
