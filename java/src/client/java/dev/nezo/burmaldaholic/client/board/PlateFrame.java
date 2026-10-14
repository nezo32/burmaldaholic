package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Frame-level seat plate arbitration across tables (docs/architecture/ui-v2.md §7.4, finding R-KW M7, design audit W P0
 * "plates of neighbouring tables merge"). Each table's renderer, while drawing frame N, registers its plates as screen
 * rectangles ({@link #table}) and reads the answer {@link PlateLayout#resolveGlobal} gave for frame N − 1's rectangles
 * (one frame of latency, no deferred drawing): a plate that would overlap a plate of a better-ranked table (the seated
 * or looked-at one first, then the nearer) fades out over {@link #FADE_NS}, and fades back in once it is free with a
 * margin (hysteresis). Render thread only; the per-table entries are reused, so a frame allocates nothing per table.
 *
 * <p>Screen space = camera-space tangents ({@code x / depth}, {@code y / depth}), so rectangles of tables at different
 * distances compare exactly as the player sees them.
 */
public final class PlateFrame {
	/** Fade time of a plate whose visibility changed. */
	public static final long FADE_NS = 150_000_000L;
	/** Hysteresis margin (camera tangent units: ≈ 8 px at 1080p, FOV 70) a hidden plate needs before it returns. */
	public static final float MARGIN = 0.01f;
	/** Rank bonuses: the table the viewer sits at, then the one under the crosshair, then by distance. */
	public static final double SEATED = -2000, LOOKED_AT = -1000;

	private static final class Entry {
		final PlateLayout.TablePlates plates = new PlateLayout.TablePlates();
		/** The resolve's answer for this table's plates of the previous frame (ids → shown). */
		final PlateLayout.TablePlates answer = new PlateLayout.TablePlates();
		long frame = Long.MIN_VALUE;
	}

	private static final Map<Long, Entry> TABLES = new HashMap<>();
	private static final List<PlateLayout.TablePlates> SCRATCH = new ArrayList<>();
	private static long frame = Long.MIN_VALUE, previous = Long.MIN_VALUE;
	private static long dtNs;
	/** Shown plate rectangles (alpha ≥ ½) of the last complete frame, all tables: x0, y0, x1, y1 (tests). */
	private static float[] lastRects = new float[64];
	private static int lastRectCount;
	private static float[] curRects = new float[64];
	private static int curRectCount;
	private static int lastHidden, curHidden;

	private PlateFrame() {}

	/** Rolls to frame {@code now} (any per-frame timestamp, ns) when it changed: resolves the frame just finished. */
	static void begin(long now) {
		if (now == frame) return;
		dtNs = frame == Long.MIN_VALUE ? 0 : Math.max(0, Math.min(100_000_000L, now - frame));
		SCRATCH.clear();
		for (Iterator<Entry> it = TABLES.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			if (e.frame == frame) SCRATCH.add(e.plates);
			else if (frame - e.frame > 2_000_000_000L) it.remove();
		}
		if (!SCRATCH.isEmpty()) PlateLayout.resolveGlobal(SCRATCH, MARGIN);
		for (Entry e : TABLES.values()) {
			if (e.frame != frame) continue;
			PlateLayout.TablePlates a = e.answer;
			a.clear();
			PlateLayout.TablePlates p = e.plates;
			for (int i = 0; i < p.count; i++) a.add(p.ids[i], 0, 0, 0, 0, p.shown[i]);
		}
		float[] swap = lastRects;
		lastRects = curRects;
		lastRectCount = curRectCount;
		curRects = swap;
		curRectCount = 0;
		lastHidden = curHidden;
		curHidden = 0;
		previous = frame;
		frame = now;
	}

	/** The current frame's key, and the one before it. */
	static long current() {
		return frame;
	}

	static long previous() {
		return previous;
	}

	/** Rank of a table this frame: the seated table, then the looked-at one, then by distance. */
	static double rank(BoardState s) {
		return s.dist + (s.seated ? SEATED : 0) + (s.lookedAt ? LOOKED_AT : 0);
	}

	/** Nanoseconds since the previous frame (0 on the first; ≤ 100 ms). */
	static long dtNs() {
		return dtNs;
	}

	/** Starts this frame's registration of the table at {@code key}; returns the entry to {@link #add} plates to. */
	static PlateLayout.TablePlates table(long key, double rank) {
		Entry e = TABLES.computeIfAbsent(key, k -> new Entry());
		if (e.frame != frame) {
			e.frame = frame;
			e.plates.clear();
			e.plates.key = key;
		}
		e.plates.rank = rank;
		return e.plates;
	}

	/** Whether plate {@code id} of the table may be shown (last resolve; true for a plate it has not seen). */
	static boolean shown(long key, int id) {
		Entry e = TABLES.get(key);
		return e == null || e.answer.shown(id, true);
	}

	/** Records a plate drawn this frame at alpha ≥ ½ (or a hidden one) for {@link #lastRects}. */
	static void drawn(float x0, float y0, float x1, float y1, boolean visible) {
		if (!visible) {
			curHidden++;
			return;
		}
		if (curRectCount * 4 + 4 > curRects.length) curRects = java.util.Arrays.copyOf(curRects, curRects.length * 2);
		int k = curRectCount++ * 4;
		curRects[k] = x0;
		curRects[k + 1] = y0;
		curRects[k + 2] = x1;
		curRects[k + 3] = y1;
	}

	/** The plates shown in the last complete frame across all tables (copy: x0, y0, x1, y1 per plate). */
	public static float[] lastRects() {
		return java.util.Arrays.copyOf(lastRects, lastRectCount * 4);
	}

	/** Plates faded out (hidden by a better-ranked table) in the last complete frame. */
	public static int lastHidden() {
		return lastHidden;
	}

	public static void clear() {
		TABLES.clear();
		SCRATCH.clear();
		frame = previous = Long.MIN_VALUE;
		lastRectCount = curRectCount = 0;
		lastHidden = curHidden = 0;
	}
}
