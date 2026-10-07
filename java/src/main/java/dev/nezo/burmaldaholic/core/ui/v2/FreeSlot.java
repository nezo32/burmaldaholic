package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.List;
import java.util.Optional;

/**
 * Finds a free place for a transient box (the ResultBanner, a toast on the felt) inside the regions a game layout
 * declares free (docs/architecture/ui-v2.md §2.1): the first region, in order, that can hold a {@code w × h} box clear of
 * every obstacle wins. Inside a region the centre is tried first, then positions moving outwards in 2 px steps (rows
 * nearest the centre first). Empty when nothing fits: the caller then skips the box (the status line carries it). Pure.
 */
public final class FreeSlot {
	private FreeSlot() {}

	public static Optional<Box> first(List<Box> regions, int w, int h, List<Box> obstacles) {
		for (Box r : regions) {
			Optional<Box> b = in(r, w, h, obstacles);
			if (b.isPresent()) return b;
		}
		return Optional.empty();
	}

	/** The free position nearest the region's centre, or empty. */
	public static Optional<Box> in(Box region, int w, int h, List<Box> obstacles) {
		if (w > region.w() || h > region.h() || w <= 0 || h <= 0) return Optional.empty();
		int cx = region.x() + (region.w() - w) / 2;
		int cy = region.y() + (region.h() - h) / 2;
		int spanX = (region.w() - w) / 2 + 1;
		int spanY = (region.h() - h) / 2 + 1;
		for (int dy = 0; dy <= spanY; dy += 2) {
			for (int sy : dy == 0 ? new int[] {1} : new int[] {-1, 1}) {
				int y = cy + sy * dy;
				if (y < region.y() || y + h > region.bottom()) continue;
				for (int dx = 0; dx <= spanX; dx += 2) {
					for (int sx : dx == 0 ? new int[] {1} : new int[] {-1, 1}) {
						int x = cx + sx * dx;
						if (x < region.x() || x + w > region.right()) continue;
						Box c = new Box(x, y, w, h);
						if (clear(c, obstacles)) return Optional.of(c);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean clear(Box c, List<Box> obstacles) {
		for (Box o : obstacles) if (c.intersects(o)) return false;
		return true;
	}
}
