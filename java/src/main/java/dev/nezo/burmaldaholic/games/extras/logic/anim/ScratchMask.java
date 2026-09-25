package dev.nezo.burmaldaholic.games.extras.logic.anim;

import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * Foil coverage of one scratch cell (extras-pvp.md §7.3, visual/extras.md §6.4). PURE and allocation-free after
 * construction: {@code nx × ny} sub-tiles of 4 × 4 px (solo 15 × 11, Showdown 6 × 6) kept as bits in a {@code long[]};
 * a set bit is foil still covering. The mask is cosmetic: what shows under the foil is the server's cell value and
 * the first erased sub-tile of a covered cell is what sends the scratch action.
 */
public final class ScratchMask {
	public static final int TILE = 4;
	/** A cell dissolves once this share of its sub-tiles is erased. */
	public static final double DISSOLVE_AT = 0.55;
	public static final int DISSOLVE_MS = 200;
	public static final int SWIPE_MS = 250;

	private final int nx;
	private final int ny;
	private final long[] bits;
	private int covered;

	public ScratchMask(int nx, int ny) {
		this.nx = nx;
		this.ny = ny;
		this.bits = new long[(nx * ny + 63) / 64];
		reset();
	}

	/** Solo cell 60 × 44. */
	public static ScratchMask solo() {
		return new ScratchMask(15, 11);
	}

	/** Showdown cell 24 × 24. */
	public static ScratchMask showdown() {
		return new ScratchMask(6, 6);
	}

	public int nx() {
		return nx;
	}

	public int ny() {
		return ny;
	}

	/** Full foil again. */
	public void reset() {
		for (int i = 0; i < nx * ny; i++) bits[i >> 6] |= 1L << (i & 63);
		covered = nx * ny;
	}

	/** Everything scratched. */
	public void clear() {
		java.util.Arrays.fill(bits, 0);
		covered = 0;
	}

	/** Covered at sub-tile (a, b); outside the cell counts as covered (no torn lip on the cell border). */
	public boolean covered(int a, int b) {
		if (a < 0 || b < 0 || a >= nx || b >= ny) return true;
		int i = b * nx + a;
		return (bits[i >> 6] >>> (i & 63) & 1L) != 0;
	}

	/** Erases one sub-tile; returns true when it was covered. */
	public boolean erase(int a, int b) {
		if (a < 0 || b < 0 || a >= nx || b >= ny || !covered(a, b)) return false;
		int i = b * nx + a;
		bits[i >> 6] &= ~(1L << (i & 63));
		covered--;
		return true;
	}

	/** Erases every sub-tile whose centre is within {@code r} px of (x, y) (cell-local px); returns how many. */
	public int eraseCircle(double x, double y, double r) {
		int n = 0;
		int a0 = (int) Math.floor((x - r) / TILE);
		int a1 = (int) Math.floor((x + r) / TILE);
		int b0 = (int) Math.floor((y - r) / TILE);
		int b1 = (int) Math.floor((y + r) / TILE);
		for (int b = b0; b <= b1; b++) {
			for (int a = a0; a <= a1; a++) {
				double cx = a * TILE + TILE / 2.0 - x;
				double cy = b * TILE + TILE / 2.0 - y;
				if (cx * cx + cy * cy <= r * r && erase(a, b)) n++;
			}
		}
		return n;
	}

	/** Erases along a line between two mouse samples (line-rasterised every 2 px). */
	public int eraseLine(double x0, double y0, double x1, double y1, double r) {
		double len = Math.hypot(x1 - x0, y1 - y0);
		int steps = Math.max(1, (int) Math.ceil(len / 2));
		int n = 0;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			n += eraseCircle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r);
		}
		return n;
	}

	public int coveredCount() {
		return covered;
	}

	public boolean untouched() {
		return covered == nx * ny;
	}

	public boolean isClear() {
		return covered == 0;
	}

	/** Share of the sub-tiles erased (0–1). */
	public double erased() {
		return 1 - covered / (double) (nx * ny);
	}

	/**
	 * Scratch-edge autotile variant of a covered sub-tile: the bitmask of its scratched neighbours (N 1, E 2, S 4,
	 * W 8), 0 when it has none.
	 */
	public int edge(int a, int b) {
		int m = 0;
		if (!covered(a, b - 1)) m |= 1;
		if (!covered(a + 1, b)) m |= 2;
		if (!covered(a, b + 1)) m |= 4;
		if (!covered(a - 1, b)) m |= 8;
		return m;
	}

	/**
	 * The order in which the remaining sub-tiles flake off when the cell dissolves: a permutation of all
	 * {@code nx·ny} indices from {@code seed} (Random(seq·9 + cell) in the spec; cosmetic, public).
	 */
	public static int[] dissolveOrder(int count, int seed) {
		int[] order = new int[count];
		for (int i = 0; i < count; i++) order[i] = i;
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		for (int i = count - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int t = order[i];
			order[i] = order[j];
			order[j] = t;
		}
		return order;
	}

	/**
	 * Auto-swipe (click without drag, keyboard, "Scratch all"): three zig-zag strokes over the cell. Returns the
	 * point at progress {@code p} (0–1) along the polyline (2, 4) → (w−2, h/3) → (2, 2h/3) → (w−2, h−4).
	 */
	public static double[] swipePoint(int w, int h, double p) {
		double[][] v = {{2, 4}, {w - 2, h / 3.0}, {2, 2 * h / 3.0}, {w - 2, h - 4}};
		double t = Math.max(0, Math.min(1, p)) * 3;
		int k = Math.min(2, (int) Math.floor(t));
		double q = t - k;
		return new double[] {v[k][0] + (v[k + 1][0] - v[k][0]) * q, v[k][1] + (v[k + 1][1] - v[k][1]) * q};
	}

	/** Swipe brush radius that clears a cell of height {@code h} in three strokes. */
	public static double swipeRadius(int h) {
		return Math.max(4, h / 4.0 + 2);
	}
}
