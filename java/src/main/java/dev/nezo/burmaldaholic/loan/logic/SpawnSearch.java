package dev.nezo.burmaldaholic.loan.logic;

import java.util.function.DoubleSupplier;

/**
 * Safe collector spawn spots (GAME_DESIGN.md §5.5 "Spawn"): 24–40 blocks horizontally from the
 * debtor, solid top face, 2 free blocks above, not in water/lava, inside the world border and not
 * inside a protected area. PURE: the world side passes a block classifier and an area filter.
 */
public final class SpawnSearch {
	private SpawnSearch() {}

	/** Classification of one block for spawning. {@code null} from the classifier = unloaded / outside the world. */
	public enum Cell {
		SOLID, AIR, LIQUID, PASSABLE
	}

	@FunctionalInterface
	public interface CellReader {
		Cell at(int x, int y, int z);
	}

	/** Extra column filter: world border, claimed casinos, boss arenas. */
	@FunctionalInterface
	public interface ColumnFilter {
		boolean allowed(int x, int y, int z);
	}

	public record Rules(int minRadius, int maxRadius, int verticalRange, int attempts, int minY, int maxY) {
		public static Rules standard(int minY, int maxY) {
			return new Rules(24, 40, 12, 16, minY, maxY);
		}
	}

	/** Block position of a spawn (feet at y). */
	public record Spot(int x, int y, int z) {}

	/** Uniform-area random column on the ring [min, max] around the center. */
	public static int[] ringPoint(DoubleSupplier rng, double cx, double cz, int min, int max) {
		double a = rng.getAsDouble() * Math.PI * 2;
		double r = Math.sqrt(min * (double) min + rng.getAsDouble() * ((double) max * max - (double) min * min));
		return new int[] {(int) Math.floor(cx + Math.cos(a) * r), (int) Math.floor(cz + Math.sin(a) * r)};
	}

	/**
	 * Standing block y at column (x, z), searching outward from {@code nearY}: block y is solid,
	 * y+1 and y+2 are air. Returns {@code Integer.MIN_VALUE} when none in range.
	 */
	public static int standY(CellReader cells, int x, int z, int nearY, Rules rules) {
		int base = nearY;
		for (int d = 0; d <= rules.verticalRange(); d++) {
			int[] ys = d == 0 ? new int[] {base - 1} : new int[] {base - 1 - d, base - 1 + d};
			for (int y : ys) {
				if (y < rules.minY() || y + 2 >= rules.maxY()) {
					continue;
				}
				if (cells.at(x, y, z) != Cell.SOLID) {
					continue;
				}
				if (cells.at(x, y + 1, z) == Cell.AIR && cells.at(x, y + 2, z) == Cell.AIR) {
					return y;
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Up to {@code attempts} random ring columns; the first standable, allowed one wins. Null if none. */
	public static Spot find(DoubleSupplier rng, double cx, int cy, double cz, CellReader cells, ColumnFilter filter, Rules rules) {
		for (int i = 0; i < rules.attempts(); i++) {
			int[] p = ringPoint(rng, cx, cz, rules.minRadius(), rules.maxRadius());
			int y = standY(cells, p[0], p[1], cy, rules);
			if (y != Integer.MIN_VALUE && filter.allowed(p[0], y + 1, p[1])) {
				return new Spot(p[0], y + 1, p[1]);
			}
		}
		return null;
	}
}
