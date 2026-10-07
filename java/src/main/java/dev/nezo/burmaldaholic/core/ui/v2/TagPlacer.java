package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Places text tags (roulette value tags, hand names, action tags, pot labels) so they never cover a "hard" obstacle
 * (cards, number glyphs; docs/architecture/ui-v2.md §2.2). The generalisation of {@code games.poker.present.LabelPlacer}
 * onto {@link Box}. Pure and deterministic.
 *
 * <p>Each request lists candidate top-left corners in preference order. The first candidate inside the bounds touching no
 * hard obstacle, no soft obstacle and no tag placed before it wins; then the same without the soft ones; then a ring
 * search (2 px steps, ≤ {@link #SEARCH} px, horizontal moves preferred) around the first candidate. When that fails too:
 * in {@link Mode#STRICT} the tag is {@link Box#EMPTY} (not drawn: the status line carries the text); in
 * {@link Mode#BEST_EFFORT} the candidate with the least hard overlap is used (LabelPlacer's behaviour).
 */
public final class TagPlacer {
	/** Maximum ring-search radius in px. */
	public static final int SEARCH = 72;

	public enum Mode {
		STRICT,
		BEST_EFFORT
	}

	/** A tag of {@code w × h} with candidate top-left corners {@code xy = [x0, y0, x1, y1, …]}. */
	public record Request(int w, int h, int... xy) {}

	private TagPlacer() {}

	public static List<Box> place(List<Box> hard, List<Box> soft, Box bounds, List<Request> requests, Mode mode) {
		List<Box> placed = new ArrayList<>(requests.size());
		List<Box> taken = new ArrayList<>();
		for (Request r : requests) {
			Box b = placeOne(hard, soft, bounds, taken, r, mode);
			placed.add(b);
			if (!b.isEmpty()) taken.add(b);
		}
		return placed;
	}

	/** {@link Mode#STRICT}. */
	public static List<Box> place(List<Box> hard, List<Box> soft, Box bounds, List<Request> requests) {
		return place(hard, soft, bounds, requests, Mode.STRICT);
	}

	private static Box placeOne(List<Box> hard, List<Box> soft, Box bounds, List<Box> placed, Request r, Mode mode) {
		int[] xy = r.xy();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i + 1 < xy.length; i += 2) {
				Box c = new Box(xy[i], xy[i + 1], r.w(), r.h());
				if (free(c, hard, pass == 0 ? soft : List.of(), bounds, placed)) return c;
			}
		}
		int ax = xy.length >= 2 ? xy[0] : bounds.x();
		int ay = xy.length >= 2 ? xy[1] : bounds.y();
		for (int rad = 2; rad <= SEARCH; rad += 2) {
			Box best = null;
			int bestD = Integer.MAX_VALUE;
			for (int dx = -rad; dx <= rad; dx += 2) {
				for (int dy = -rad; dy <= rad; dy += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != rad) continue;
					Box c = new Box(ax + dx, ay + dy, r.w(), r.h());
					if (!free(c, hard, List.of(), bounds, placed)) continue;
					int d = dx * dx + 2 * dy * dy;
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
			}
			if (best != null) return best;
		}
		if (mode == Mode.STRICT) return Box.EMPTY;
		Box best = new Box(ax, ay, r.w(), r.h());
		long least = Long.MAX_VALUE;
		for (int i = 0; i + 1 < xy.length; i += 2) {
			Box c = new Box(xy[i], xy[i + 1], r.w(), r.h());
			long o = 0;
			for (Box h : hard) o += overlap(c, h);
			if (o < least) {
				least = o;
				best = c;
			}
		}
		return best;
	}

	static long overlap(Box a, Box b) {
		int ix = Math.min(a.right(), b.right()) - Math.max(a.x(), b.x());
		int iy = Math.min(a.bottom(), b.bottom()) - Math.max(a.y(), b.y());
		return ix > 0 && iy > 0 ? (long) ix * iy : 0;
	}

	private static boolean free(Box c, List<Box> hard, List<Box> soft, Box bounds, List<Box> placed) {
		if (!c.inside(bounds)) return false;
		for (Box h : hard) if (c.intersects(h)) return false;
		for (Box h : soft) if (c.intersects(h)) return false;
		for (Box p : placed) if (c.intersects(p)) return false;
		return true;
	}
}
