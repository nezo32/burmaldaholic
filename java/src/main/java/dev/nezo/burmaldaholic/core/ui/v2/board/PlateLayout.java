package dev.nezo.burmaldaholic.core.ui.v2.board;

/**
 * Collision avoidance of the in-world seat plates (docs/architecture/ui-v2.md §7.4). Works in the camera-facing plane
 * through the table centre: {@code u} to the camera's right, {@code v} up, in blocks (the renderer projects each
 * anchor with the camera's left/up vectors — two dot products, no matrices). Plates are pushed ABOVE the felt
 * content ({@code contentTop}: the highest projected card / chip / wheel point plus the gap) so a plate never covers a
 * card (review of the v2 concept: plates overlapped cards), then placed in priority order (viewer first, then the
 * nearest seat): each tries its own column, then small sideways shifts, then rises above whatever it hits. A plate
 * that ends further than {@link #LEADER} from its anchor gets a leader line. Pure, allocation-light (≤ 12 plates,
 * O(n²)), deterministic.
 */
public final class PlateLayout {
	/** Distance (blocks) from the anchor above which a 1-px leader line is drawn to the seat. */
	public static final float LEADER = 0.18f;
	private static final float[] SHIFTS = {0f, 0.25f, -0.25f, 0.5f, -0.5f};
	/** Two candidates within this height (blocks) count as equal. */
	private static final float TIE = 0.02f;

	private PlateLayout() {}

	/**
	 * @param au, av     anchors (plate bottom-centre wanted), plane coordinates
	 * @param w, h       plate sizes in blocks
	 * @param order      placement priority (indices into the arrays)
	 * @param contentTop v of the top of the felt content (cards, stacks) — no plate bottom below it
	 * @param gap        minimum gap between plates and above the content
	 * @param outU, outV result bottom-centres
	 * @param leader     result: true when a leader line should be drawn
	 */
	public static void place(float[] au, float[] av, float[] w, float[] h, int[] order, float contentTop, float gap, float[] outU,
			float[] outV, boolean[] leader) {
		place(au, av, w, h, order, new float[] {Float.NEGATIVE_INFINITY}, new float[] {Float.POSITIVE_INFINITY}, new float[] {contentTop}, 1, gap,
			null, outU, outV, leader);
	}

	/**
	 * Obstacle-aware placement (the renderer's call): a plate's floor is the top of the felt content (card, stack,
	 * wheel) BELOW IT — {@code obsTop[k] + gap} for every obstacle whose u-range {@code [obsU0, obsU1]} overlaps the
	 * plate's — so a plate hovers just above its own seat's cards instead of above the whole table.
	 *
	 * @param shift in/out (nullable): the candidate index used last frame per plate; on a tie (within 0.02 block) the
	 *              previous candidate wins, so plates do not flip sides while the camera moves (1-frame memory)
	 */
	public static void place(float[] au, float[] av, float[] w, float[] h, int[] order, float[] obsU0, float[] obsU1, float[] obsTop, int obsCount,
			float gap, int @org.jspecify.annotations.Nullable [] shift, float[] outU, float[] outV, boolean[] leader) {
		place(au, av, w, h, order, order.length, obsU0, obsU1, obsTop, obsCount, gap, shift, outU, outV, leader, new Scratch(order.length));
	}

	/** Reusable work arrays of {@link #place} (the renderer keeps one; finding R-KW m11: no per-frame allocation). */
	public static final class Scratch {
		final int[] placed;
		final float[] cv = new float[SHIFTS.length];
		final boolean[] cfree = new boolean[SHIFTS.length];

		public Scratch(int maxPlates) {
			placed = new int[maxPlates];
		}
	}

	/** {@link #place} on the first {@code n} entries of {@code order}, with caller-owned scratch (≥ n plates). */
	public static void place(float[] au, float[] av, float[] w, float[] h, int[] order, int n, float[] obsU0, float[] obsU1, float[] obsTop,
			int obsCount, float gap, int @org.jspecify.annotations.Nullable [] shift, float[] outU, float[] outV, boolean[] leader, Scratch sc) {
		int[] placed = sc.placed;
		float[] cv = sc.cv;
		boolean[] cfree = sc.cfree;
		int count = 0;
		for (int oi = 0; oi < n; oi++) {
			int i = order[oi];
			int prev = shift == null ? -1 : shift[i];
			for (int si = 0; si < SHIFTS.length; si++) {
				float u = au[i] + SHIFTS[si] * w[i];
				float floor = av[i];
				for (int k = 0; k < obsCount; k++) {
					if (obsU1[k] > u - w[i] / 2 - gap && obsU0[k] < u + w[i] / 2 + gap) floor = Math.max(floor, obsTop[k] + gap);
				}
				float v = floor;
				// rise above every placed plate this candidate hits (repeat until free; bounded by n)
				for (int pass = 0; pass <= count; pass++) {
					float hitTop = Float.NEGATIVE_INFINITY;
					for (int k = 0; k < count; k++) {
						int j = placed[k];
						if (overlaps(u, v, w[i], h[i], outU[j], outV[j], w[j], h[j], gap)) hitTop = Math.max(hitTop, outV[j] + h[j]);
					}
					if (hitTop == Float.NEGATIVE_INFINITY) break;
					v = hitTop + gap;
				}
				cv[si] = v;
				cfree[si] = v <= floor + 1e-4f;
			}
			// a candidate that needs no rise is "free": last frame's if still free, else the first free one (SHIFTS is
			// ordered by |s|); when none is free, the lowest (ties within TIE: last frame's, else the smaller shift)
			int best = -1;
			if (prev >= 0 && prev < SHIFTS.length && cfree[prev]) best = prev;
			for (int si = 0; best < 0 && si < SHIFTS.length; si++) if (cfree[si]) best = si;
			if (best < 0) {
				best = 0;
				for (int si = 1; si < SHIFTS.length; si++) if (cv[si] < cv[best] - TIE) best = si;
				if (prev >= 0 && prev < SHIFTS.length && Math.abs(cv[prev] - cv[best]) <= TIE) best = prev;
			}
			float bestU = au[i] + SHIFTS[best] * w[i];
			float bestV = cv[best];
			if (shift != null) shift[i] = best;
			outU[i] = bestU;
			outV[i] = bestV;
			float du = bestU - au[i];
			float dv = bestV - av[i];
			leader[i] = du * du + dv * dv > LEADER * LEADER;
			placed[count++] = i;
		}
	}

	static boolean overlaps(float u1, float v1, float w1, float h1, float u2, float v2, float w2, float h2, float gap) {
		return Math.abs(u1 - u2) < (w1 + w2) / 2 + gap && v1 < v2 + h2 + gap && v2 < v1 + h1 + gap;
	}

	// ---- across tables (finding R-KW M7, design audit W P0 "merged plates") ------------------------------------------

	/**
	 * One table's plates for the frame-level resolve, as SCREEN rectangles (any common unit, x right, y up — the renderer
	 * uses camera-space tangents, so rectangles of tables at different depths compare as the player sees them), plus
	 * fixed occupants that are always drawn (its board panel). Reused across frames: {@link #clear} then {@link #add}.
	 */
	public static final class TablePlates {
		public static final int MAX = 16, MAX_FIXED = 2;
		/** Any stable key of the table (its block position). */
		public long key;
		/** Lower is placed first: the seated / looked-at table, then by distance. */
		public double rank;
		public int count;
		public final int[] ids = new int[MAX];
		/** x0, y0, x1, y1 per plate. */
		public final float[] rects = new float[4 * MAX];
		/** In: the plate was shown by the previous resolve (hysteresis). Out: shown now. */
		public final boolean[] shown = new boolean[MAX];
		public final float[] fixed = new float[4 * MAX_FIXED];
		public int fixedCount;

		public void clear() {
			count = 0;
			fixedCount = 0;
		}

		/** Adds a plate; {@code wasShown} = the previous resolve's answer for it (true for a plate seen the first time). */
		public void add(int id, float x0, float y0, float x1, float y1, boolean wasShown) {
			if (count >= MAX) return;
			ids[count] = id;
			int k = count * 4;
			rects[k] = x0;
			rects[k + 1] = y0;
			rects[k + 2] = x1;
			rects[k + 3] = y1;
			shown[count++] = wasShown;
		}

		public void addFixed(float x0, float y0, float x1, float y1) {
			if (fixedCount >= MAX_FIXED) return;
			int k = fixedCount++ * 4;
			fixed[k] = x0;
			fixed[k + 1] = y0;
			fixed[k + 2] = x1;
			fixed[k + 3] = y1;
		}

		/** The resolve's answer for plate {@code id}; {@code dflt} when the table had no such plate. */
		public boolean shown(int id, boolean dflt) {
			for (int i = 0; i < count; i++) if (ids[i] == id) return shown[i];
			return dflt;
		}
	}

	/**
	 * Frame-level plate visibility across tables: tables in {@link TablePlates#rank} order (ties by key), each plate shown
	 * iff its rectangle is free of every plate already shown and every fixed occupant of a better-ranked table; a shown
	 * plate is added to the occupied set. Hysteresis: a plate hidden last time must be free by {@code margin} on every
	 * side before it comes back, so a plate on the edge of another does not blink. Result: the shown plates of all tables
	 * are pairwise disjoint (touching edges allowed), and the best-ranked table keeps all its (already disjoint) plates.
	 * The renderer feeds last frame's rectangles and fades a plate whose answer changed (150 ms).
	 */
	public static void resolveGlobal(java.util.List<TablePlates> tables, float margin) {
		tables.sort((a, b) -> a.rank != b.rank ? Double.compare(a.rank, b.rank) : Long.compare(a.key, b.key));
		int total = 0;
		for (TablePlates t : tables) total += t.count + t.fixedCount;
		float[] occ = new float[4 * Math.max(1, total)];
		int[] owner = new int[Math.max(1, total)];
		int n = 0;
		for (int ti = 0; ti < tables.size(); ti++) {
			TablePlates t = tables.get(ti);
			for (int i = 0; i < t.count; i++) {
				int k = i * 4;
				boolean free = true;
				for (int j = 0; j < n && free; j++) {
					int o = j * 4;
					// the margin applies against OTHER tables only (a table's own plates are laid out apart already)
					float m = t.shown[i] || owner[j] == ti ? 0 : margin;
					if (t.rects[k] - m < occ[o + 2] && occ[o] < t.rects[k + 2] + m && t.rects[k + 1] - m < occ[o + 3] && occ[o + 1] < t.rects[k + 3] + m) free = false;
				}
				t.shown[i] = free;
				if (free) {
					System.arraycopy(t.rects, k, occ, n * 4, 4);
					owner[n++] = ti;
				}
			}
			for (int f = 0; f < t.fixedCount; f++) {
				System.arraycopy(t.fixed, f * 4, occ, n * 4, 4);
				owner[n++] = ti;
			}
		}
	}
}
