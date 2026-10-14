package dev.nezo.burmaldaholic.client.board;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Debug counters of the in-world board kit (docs/architecture/ui-v2.md §7.1, §7.5): per table, what the last frame
 * submitted (quads, text lines, plates) and how long extract + submit + the geometry callbacks took on the render
 * thread. Read by the perf GameTest (counts asserted, times reported). Off by default ({@code -Dburmaldaholic.boardStats=true}
 * or {@link #setEnabled}); cleared on disconnect. Render thread only; a few HashMap writes per table per frame when on.
 */
public final class BoardStats {
	/** The last frame of one table. */
	public static final class Table {
		public int cardQuads, chipQuads, feltQuads, backings, lines, plates, leaders;
		public long nanos;
		public long frame;
		/** Screen-space rectangles of the plates drawn last frame (x0, y0, x1, y1 in plane blocks), for the overlap test. */
		public final float[] plateRects = new float[4 * 16];
		public int plateRectCount;
		/** LOD drawn last frame (null = not drawn). */
		public dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale.@org.jspecify.annotations.Nullable Lod lod;

		void reset(long frame) {
			lod = null;
			cardQuads = chipQuads = feltQuads = backings = lines = plates = leaders = 0;
			plateRectCount = 0;
			nanos = 0;
			this.frame = frame;
		}
	}

	private static final Map<Long, Table> TABLES = new HashMap<>();
	/** Where the counters go while stats are off (written, never read: no map entry per table, finding R-KW m12). */
	private static final Table SINK = new Table();
	/** Off unless {@code -Dburmaldaholic.boardStats=true} or a test turns it on ({@link #setEnabled}). */
	private static boolean enabled = Boolean.getBoolean("burmaldaholic.boardStats");

	private BoardStats() {}

	/** The stats of the table at {@code pos} (created on first use); a shared sink while stats are off. */
	public static Table of(BlockPos pos) {
		if (!enabled) return SINK;
		return TABLES.computeIfAbsent(pos.asLong(), k -> new Table());
	}

	/** Called once per extract of a table: resets it when a new frame started ({@code levelFrame} = any per-frame key). */
	static Table begin(BlockPos pos, long levelFrame) {
		Table t = of(pos);
		if (t.frame != levelFrame) t.reset(levelFrame);
		return t;
	}

	/** Every table seen since the last {@link #clear}. */
	public static Map<Long, Table> snapshot() {
		return Map.copyOf(TABLES);
	}

	public static void clear() {
		TABLES.clear();
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
		if (!on) TABLES.clear();
	}

}
