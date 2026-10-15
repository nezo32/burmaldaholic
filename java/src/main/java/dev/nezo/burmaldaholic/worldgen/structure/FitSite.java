package dev.nezo.burmaldaholic.worldgen.structure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the casino terrain fit samples the ground (PURE; R-CB M6b/M7, casino-buildings.md §6.1): the columns of a
 * footprint made of piece boxes, in check order so a bad site fails early —
 * <ol>
 *   <li>every corner of every piece box,</li>
 *   <li>the 3×3 door throat in front of the start piece (contract §3.1: the entrance faces template +z),</li>
 *   <li>the outline of the union at ≤ {@link #SPACING} blocks (world-aligned, so neighbouring candidates and the
 *       noise cells share columns; edges shared between pieces are skipped),</li>
 *   <li>every piece centre.</li>
 * </ol>
 * Boxes are inclusive horizontal extents. Rotations are vanilla's clockwise quarter turns of a template about its
 * origin ({@code StructureTemplate.transform}, pivot 0): {@code (x, z) → (−z, x)}.
 */
public final class FitSite {
	/** Outline probe spacing (≤ 4, the overworld noise cell width). */
	public static final int SPACING = 4;
	/** The door throat: this many columns deep in front of the door, 3 wide. */
	public static final int THROAT_DEPTH = 3;

	private FitSite() {}

	/** Inclusive horizontal box. */
	public record Box(int minX, int minZ, int maxX, int maxZ) {
		public Box {
			if (maxX < minX || maxZ < minZ) {
				throw new IllegalArgumentException("empty box " + minX + "," + minZ + ".." + maxX + "," + maxZ);
			}
		}

		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		boolean strictlyInside(int x, int z) {
			return x > minX && x < maxX && z > minZ && z < maxZ;
		}

		public Box shift(int dx, int dz) {
			return new Box(minX + dx, minZ + dz, maxX + dx, maxZ + dz);
		}

		public int centerX() {
			return Math.floorDiv(minX + maxX, 2);
		}

		public int centerZ() {
			return Math.floorDiv(minZ + maxZ, 2);
		}
	}

	/** {@code local} (template x, z) rotated by {@code turns} clockwise quarter turns about the template origin. */
	public static int[] rotate(int x, int z, int turns) {
		return switch (Math.floorMod(turns, 4)) {
			case 0 -> new int[] {x, z};
			case 1 -> new int[] {-z, x};
			case 2 -> new int[] {-x, -z};
			default -> new int[] {z, -x};
		};
	}

	/** World box of a template of size {@code sx × sz} placed at origin {@code ox, oz} with {@code turns}. */
	public static Box templateBox(int ox, int oz, int sx, int sz, int turns) {
		int[] a = rotate(0, 0, turns);
		int[] b = rotate(sx - 1, sz - 1, turns);
		return new Box(ox + Math.min(a[0], b[0]), oz + Math.min(a[1], b[1]), ox + Math.max(a[0], b[0]), oz + Math.max(a[1], b[1]));
	}

	/**
	 * The door throat of a start piece (template size {@code sx × sz}, origin {@code ox, oz}, {@code turns}): the
	 * 3-wide strip {@link #THROAT_DEPTH} deep in front of the door, which sits on the template's +z face at
	 * {@code x = (sx − 1) / 2}.
	 */
	public static List<int[]> throat(int ox, int oz, int sx, int sz, int turns) {
		List<int[]> out = new ArrayList<>();
		int cx = (sx - 1) / 2;
		for (int dz = 0; dz < THROAT_DEPTH; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int[] r = rotate(cx + dx, sz + dz, turns);
				out.add(new int[] {ox + r[0], oz + r[1]});
			}
		}
		return out;
	}

	/** Probe columns of the footprint {@code boxes} plus {@code throat}, in check order, without duplicates. */
	public static List<int[]> points(List<Box> boxes, List<int[]> throat) {
		return points(boxes, throat, SPACING);
	}

	/**
	 * Same with the outline every {@code spacing} blocks (a multiple of {@link #SPACING}, world-aligned: a coarser
	 * set is a subset of the finer one).
	 */
	public static List<int[]> points(List<Box> boxes, List<int[]> throat, int spacing) {
		Set<Long> seen = new LinkedHashSet<>();
		for (Box b : boxes) {
			add(seen, b.minX(), b.minZ());
			add(seen, b.maxX(), b.maxZ());
			add(seen, b.maxX(), b.minZ());
			add(seen, b.minX(), b.maxZ());
		}
		for (int[] t : throat) {
			add(seen, t[0], t[1]);
		}
		for (Box b : boxes) {
			for (int x = alignUp(b.minX(), spacing); x <= b.maxX(); x += spacing) {
				outline(seen, boxes, x, b.minZ());
				outline(seen, boxes, x, b.maxZ());
			}
			for (int z = alignUp(b.minZ(), spacing); z <= b.maxZ(); z += spacing) {
				outline(seen, boxes, b.minX(), z);
				outline(seen, boxes, b.maxX(), z);
			}
		}
		for (Box b : boxes) {
			add(seen, b.centerX(), b.centerZ());
		}
		List<int[]> out = new ArrayList<>(seen.size());
		for (long k : seen) {
			out.add(new int[] {(int) (k >> 32), (int) k});
		}
		return out;
	}

	/** A straight row of outline columns: {@code (x0 + i·spacing, z0)} when {@code alongX}, else {@code (x0, z0 + i·spacing)}. */
	public record Line(int x0, int z0, boolean alongX, int spacing, int n) {}

	/**
	 * The outline columns of {@link #points(List, List, int)} as straight rows (two per box side direction), so a
	 * sampler can fetch a whole side in one call. Columns hidden inside another box are included (harmless extra).
	 */
	public static List<Line> lines(List<Box> boxes, int spacing) {
		List<Line> out = new ArrayList<>();
		for (Box b : boxes) {
			int x0 = alignUp(b.minX(), spacing);
			int nx = x0 > b.maxX() ? 0 : (b.maxX() - x0) / spacing + 1;
			int z0 = alignUp(b.minZ(), spacing);
			int nz = z0 > b.maxZ() ? 0 : (b.maxZ() - z0) / spacing + 1;
			if (nx > 0) {
				out.add(new Line(x0, b.minZ(), true, spacing, nx));
				out.add(new Line(x0, b.maxZ(), true, spacing, nx));
			}
			if (nz > 0) {
				out.add(new Line(b.minX(), z0, false, spacing, nz));
				out.add(new Line(b.maxX(), z0, false, spacing, nz));
			}
		}
		return out;
	}

	/** Outline column unless it lies strictly inside another box (a wall shared between pieces). */
	private static void outline(Set<Long> seen, List<Box> boxes, int x, int z) {
		for (Box o : boxes) {
			if (o.strictlyInside(x, z)) {
				return;
			}
		}
		add(seen, x, z);
	}

	private static int alignUp(int v, int spacing) {
		return Math.floorDiv(v + spacing - 1, spacing) * spacing;
	}

	private static void add(Set<Long> seen, int x, int z) {
		seen.add(((long) x << 32) | (z & 0xFFFFFFFFL));
	}
}
