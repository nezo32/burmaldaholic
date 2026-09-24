package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A typed, pre-indexed view of one spin's {@link Timeline} (beat contract {@link SlotBeats}). Built once per
 * spin (never per frame); every lookup afterwards is allocation-free. Holds the per-reel {@link ReelMotion}s of
 * every reel phase (base spin + each free spin) so the reel frames, the BER and the sounds agree.
 */
public final class SlotScript {
	private static final List<Beat> NONE = Collections.emptyList();

	private final Timeline timeline;
	private final MachineDef def;
	private final Map<String, List<Beat>> byKind = new HashMap<>();
	private final List<Phase> phases = new ArrayList<>();

	/** One reel phase: the base spin or a free spin. */
	public static final class Phase {
		public final int spin;
		public final int spinUpAt;
		public final int spinUpDur;
		/** Motion per reel, {@code null} when the reel does not spin in this phase (sticky reel). */
		public final ReelMotion[] reels = new ReelMotion[MachineDef.REELS];
		/** Landed window (strip window at the stops; before tumbles). */
		public final int[] landed = new int[MachineDef.REELS * MachineDef.ROWS];
		/** Cells at rest before this phase (continuity: what the screen showed). */
		public final int[] rest = new int[MachineDef.REELS * MachineDef.ROWS];
		/** Strip index at rest before this phase, per reel. */
		public final int[] restStops = new int[MachineDef.REELS];
		public final boolean[] anticipated = new boolean[MachineDef.REELS];
		public int lastStopMs;

		Phase(int spin, int spinUpAt, int spinUpDur) {
			this.spin = spin;
			this.spinUpAt = spinUpAt;
			this.spinUpDur = spinUpDur;
		}

		/** Stop time (ms since the timeline start) of reel r, or -1 when it does not spin. */
		public int stopAt(int r) {
			return reels[r] == null ? -1 : spinUpAt + reels[r].stopMs();
		}
	}

	/**
	 * @param timeline     the spin timeline
	 * @param def          machine
	 * @param restStops    stops of the window shown before the spin (previous spin; the rest window)
	 * @param restCells    cells shown before the spin (15; may differ from the strip window after tumbles), or
	 *                     {@code null} = the strip window of {@code restStops}
	 */
	public SlotScript(Timeline timeline, MachineDef def, int[] restStops, int[] restCells) {
		this.timeline = timeline;
		this.def = def;
		for (Beat b : timeline.beats()) byKind.computeIfAbsent(b.kind(), k -> new ArrayList<>()).add(b);
		int[] stops = restStops.clone();
		int[] cells = restCells != null ? restCells.clone() : window(def, stops);
		for (Beat up : kind(SlotTimeline.SPIN_UP)) {
			Phase p = new Phase(up.arg(SlotBeats.SPIN), up.at(), up.dur());
			System.arraycopy(stops, 0, p.restStops, 0, stops.length);
			System.arraycopy(cells, 0, p.rest, 0, cells.length);
			int[] landedStops = stops.clone();
			for (Beat land : kind(SlotTimeline.REEL_LAND)) {
				if (land.arg(SlotBeats.SPIN) != p.spin) continue;
				int r = land.lane();
				if (r < 0 || r >= MachineDef.REELS) continue;
				boolean ant = land.arg(SlotBeats.LAND_ANTICIPATED) != 0;
				p.anticipated[r] = ant;
				p.reels[r] = new ReelMotion(p.spinUpDur, land.at() - p.spinUpAt, land.dur(), ant, stops[r], land.arg(SlotBeats.LAND_STOP),
					def.stripLength(r));
				landedStops[r] = Math.floorMod(land.arg(SlotBeats.LAND_STOP), def.stripLength(r));
				p.lastStopMs = Math.max(p.lastStopMs, land.end());
			}
			int[] win = window(def, landedStops);
			for (int r = 0; r < MachineDef.REELS; r++) {
				for (int y = 0; y < MachineDef.ROWS; y++) {
					// a reel that does not spin keeps its cells (sticky reel in End free spins)
					p.landed[r * 3 + y] = p.reels[r] == null ? cells[r * 3 + y] : win[r * 3 + y];
				}
			}
			phases.add(p);
			stops = landedStops;
			cells = finalCells(p);
		}
	}

	private static int[] window(MachineDef def, int[] stops) {
		int[] c = new int[15];
		for (int r = 0; r < 5; r++) for (int y = 0; y < 3; y++) c[r * 3 + y] = def.symbolAt(r, stops[r], y);
		return c;
	}

	public Timeline timeline() {
		return timeline;
	}

	public MachineDef def() {
		return def;
	}

	public List<Beat> kind(String kind) {
		return byKind.getOrDefault(kind, NONE);
	}

	public List<Phase> phases() {
		return phases;
	}

	/** The reel phase running at {@code t}: the latest phase whose spin-up started, or -1 before the first. */
	public int phaseIndexAt(double t) {
		int idx = -1;
		for (int i = 0; i < phases.size(); i++) if (phases.get(i).spinUpAt <= t) idx = i;
		return idx;
	}

	public Phase phase(int index) {
		return phases.get(index);
	}

	/** Cells after the whole tumble chain of a phase (the landed window when there is no tumble). */
	public int[] finalCells(Phase p) {
		int[] cells = p.landed.clone();
		Beat last = null;
		for (Beat b : kind(SlotTimeline.TUMBLE_FALL)) if (b.arg(SlotBeats.SPIN) == p.spin) last = b;
		if (last != null) for (int i = 0; i < 15; i++) cells[i] = last.arg(SlotBeats.FALL_CELLS + i);
		return cells;
	}

	/** Latest beat of {@code kind} for phase {@code spin} that STARTED at or before {@code t}, or null. */
	public Beat latest(String kind, int spin, double t) {
		Beat found = null;
		for (Beat b : kind(kind)) {
			if (b.at() > t) break;
			if (spin < 0 || b.arg(SlotBeats.SPIN) == spin) found = b;
		}
		return found;
	}

	/** Latest beat of {@code kind} that is active at {@code t}, or null. */
	public Beat active(String kind, double t) {
		Beat found = null;
		for (Beat b : kind(kind)) {
			if (b.at() > t) break;
			if (b.activeAt(t)) found = b;
		}
		return found;
	}

	/** Sticky mask (bit r-1 for reels 2..4) at {@code t}: WILD_STICK beats that ended, cleared by the FS outro. */
	public int stickyMaskAt(double t) {
		int mask = 0;
		for (Beat b : kind(SlotTimeline.WILD_STICK)) if (b.end() <= t) mask = b.arg(SlotBeats.STICK_MASK);
		for (Beat b : kind(SlotTimeline.FS_OUTRO)) if (b.end() <= t) mask = 0;
		return mask;
	}

	/** Shared end of the reel part (the reveal gate), ms. */
	public int sharedEndMs() {
		return timeline.sharedEndMs();
	}
}
