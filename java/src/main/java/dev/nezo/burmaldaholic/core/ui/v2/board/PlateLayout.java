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
		int n = order.length;
		int[] placed = new int[n];
		int count = 0;
		for (int oi = 0; oi < n; oi++) {
			int i = order[oi];
			float baseV = Math.max(av[i], contentTop + gap);
			float bestU = au[i];
			float bestV = Float.MAX_VALUE;
			for (float s : SHIFTS) {
				float u = au[i] + s * w[i];
				float v = baseV;
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
				// prefer the lowest result; among equals the smaller shift (SHIFTS is ordered by |s|)
				if (v < bestV - 1e-4f) {
					bestV = v;
					bestU = u;
				}
				if (v <= baseV + 1e-4f) break;
			}
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
}
