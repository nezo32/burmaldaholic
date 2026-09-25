package dev.nezo.burmaldaholic.games.poker.present;

import java.util.ArrayList;
import java.util.List;

/**
 * Places text labels (hand names at the showdown, action tags, stamps, pot labels) so that they NEVER cover a card
 * (the approved mockup's "Three kings" / "Flush, ace high" bubbles sat on board cards; the redesign fixes that).
 * Pure and deterministic: every viewer's layout is the same function of the same rectangles.
 *
 * <p>Each request lists candidate positions in preference order (e.g. under the plate, above the cards, beside
 * them). The first candidate that stays inside the bounds and touches no obstacle ("hard": cards) and no label
 * placed before it wins. If none is free, a ring search around the first candidate (2 px steps, ≤ {@link #SEARCH}
 * px) finds the nearest free spot; only if that fails too the candidate with the least card overlap is used.
 */
public final class LabelPlacer {
	/** Maximum ring-search radius in px. */
	public static final int SEARCH = 72;

	public record Rect(int x, int y, int w, int h) {
		public boolean intersects(Rect o) {
			return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
		}

		public int overlap(Rect o) {
			int ix = Math.min(x + w, o.x + o.w) - Math.max(x, o.x);
			int iy = Math.min(y + h, o.y + o.h) - Math.max(y, o.y);
			return ix > 0 && iy > 0 ? ix * iy : 0;
		}

		public boolean inside(Rect b) {
			return x >= b.x && y >= b.y && x + w <= b.x + b.w && y + h <= b.y + b.h;
		}

		public int cx() {
			return x + w / 2;
		}

		public int cy() {
			return y + h / 2;
		}
	}

	/** A label of {@code w × h} with candidate top-left corners {@code xy = [x0, y0, x1, y1, …]}. */
	public record Request(int w, int h, int... xy) {}

	private LabelPlacer() {}

	/**
	 * Places the requests in order. {@code hard} are rectangles no label may cover (cards); {@code soft} are rectangles a
	 * label should avoid when it can (plates, chip stacks). Returns one rectangle per request.
	 */
	public static List<Rect> place(List<Rect> hard, List<Rect> soft, Rect bounds, List<Request> requests) {
		List<Rect> placed = new ArrayList<>(requests.size());
		for (Request r : requests) {
			placed.add(placeOne(hard, soft, bounds, placed, r));
		}
		return placed;
	}

	private static Rect placeOne(List<Rect> hard, List<Rect> soft, Rect bounds, List<Rect> placed, Request r) {
		int[] xy = r.xy();
		// 1) a candidate clear of cards, labels and soft obstacles; 2) clear of cards and labels
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i + 1 < xy.length; i += 2) {
				Rect c = new Rect(xy[i], xy[i + 1], r.w(), r.h());
				if (free(c, hard, pass == 0 ? soft : List.of(), bounds, placed)) return c;
			}
		}
		int ax = xy.length >= 2 ? xy[0] : bounds.x();
		int ay = xy.length >= 2 ? xy[1] : bounds.y();
		for (int rad = 2; rad <= SEARCH; rad += 2) {
			Rect best = null;
			int bestD = Integer.MAX_VALUE;
			for (int dx = -rad; dx <= rad; dx += 2) {
				for (int dy = -rad; dy <= rad; dy += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != rad) continue;
					Rect c = new Rect(ax + dx, ay + dy, r.w(), r.h());
					if (!free(c, hard, List.of(), bounds, placed)) continue;
					int d = dx * dx + 2 * dy * dy; // prefer horizontal moves: a label keeps its row
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
			}
			if (best != null) return best;
		}
		Rect best = new Rect(ax, ay, r.w(), r.h());
		int least = Integer.MAX_VALUE;
		for (int i = 0; i + 1 < xy.length; i += 2) {
			Rect c = new Rect(xy[i], xy[i + 1], r.w(), r.h());
			int o = 0;
			for (Rect h : hard) o += c.overlap(h);
			if (o < least) {
				least = o;
				best = c;
			}
		}
		return best;
	}

	private static boolean free(Rect c, List<Rect> hard, List<Rect> soft, Rect bounds, List<Rect> placed) {
		if (!c.inside(bounds)) return false;
		for (Rect h : hard) if (c.intersects(h)) return false;
		for (Rect h : soft) if (c.intersects(h)) return false;
		for (Rect p : placed) if (c.intersects(p)) return false;
		return true;
	}
}
