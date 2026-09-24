package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.FrameModel;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import java.util.Arrays;

/**
 * The PURE slot frame sampler (docs/architecture/animation.md §3.6, lane J-L9): what the reel window shows at time
 * {@code t} of a spin timeline, as data. The Java screen and the BER draw from {@link Sampler}; the fidelity tests
 * assert {@code frame(o, tl, tl.endMs()).equals(terminal(o))} for every tape (slots.md §2.1 F1–F10, global §6).
 *
 * <p>Rules: the last three cells of every reel are the paid window (F2); a reel lands exactly on its drawn stop at
 * its REEL_LAND end (F3/F10); after the tumble chain the window is the chain's final window; a skip, reduce motion
 * or a late join only change how the frames between look, never the terminal frame (F8).
 */
public final class SlotFrames implements FrameModel<SlotFrames.Outcome, SlotFrames.Frame> {
	public static final SlotFrames INSTANCE = new SlotFrames();

	/**
	 * What the frames animate to (the server truth, never predicted by the client).
	 *
	 * @param def           machine
	 * @param tape          the drawn spin
	 * @param restStops     stops of the window shown before the spin
	 * @param restCells     cells shown before the spin (after the previous tumbles) or {@code null}
	 * @param terminalCells the window the player must end up seeing: the last reel phase's final window (tumble
	 *                      chain applied; a sticky reel keeps the cells it had when it became sticky). Computed by
	 *                      the engine ({@code Tumble.run(…).finalWindow()}), independent of the timeline.
	 */
	public record Outcome(MachineDef def, SpinTape tape, int[] restStops, int[] restCells, int[] terminalCells) {}

	/**
	 * One frame. {@code cells}: 15 symbols of the rows at rest positions ({@code floor(top)} for a moving reel);
	 * {@code offsetMilli}: per reel the downward offset in thousandths of a cell (0 at rest); {@code stickyMask}:
	 * sticky reels shown as tall wilds.
	 */
	public record Frame(int[] cells, int[] offsetMilli, int stickyMask) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Frame f && Arrays.equals(f.cells, cells) && Arrays.equals(f.offsetMilli, offsetMilli) && f.stickyMask == stickyMask;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(cells) * 31 + Arrays.hashCode(offsetMilli) * 7 + stickyMask;
		}

		@Override
		public String toString() {
			return "Frame" + Arrays.toString(cells) + Arrays.toString(offsetMilli) + "/" + stickyMask;
		}
	}

	@Override
	public Frame frame(Outcome o, Timeline timeline, double tMs) {
		Sampler s = new Sampler(new SlotScript(timeline, o.def(), o.restStops(), o.restCells()));
		s.sample(tMs);
		return s.snapshot();
	}

	@Override
	public Frame terminal(Outcome o) {
		return new Frame(o.terminalCells().clone(), new int[MachineDef.REELS], 0);
	}

	/**
	 * Allocation-free sampler over one {@link SlotScript}: call {@link #sample} each frame, then read the public
	 * arrays.
	 */
	public static final class Sampler {
		private final SlotScript script;
		/** Real top strip index per reel (for drawing moving reels). */
		public final double[] top = new double[MachineDef.REELS];
		/** Blur frames per reel. */
		public final boolean[] blur = new boolean[MachineDef.REELS];
		/** ms since the reel stopped in the current phase (−1 while it spins or when it did not spin). */
		public final double[] sinceStop = new double[MachineDef.REELS];
		/** ms since the reel's landing started, and whether the reel is moving at all. */
		public final boolean[] moving = new boolean[MachineDef.REELS];
		/** Cells at rest positions (the grid drawn when {@code moving[r]} is false). */
		public final int[] cells = new int[15];
		/** Rest cells before the running phase (drawn for unwrapped rows of the rest window while spinning up). */
		public final int[] rest = new int[15];
		public int phase = -1;
		public int spin = -1;
		public int stickyMask;
		/** Running tumble fall (or null) and the window before it. */
		public Beat fall;
		public final int[] beforeFall = new int[15];
		private double t;

		public Sampler(SlotScript script) {
			this.script = script;
		}

		public SlotScript script() {
			return script;
		}

		public double t() {
			return t;
		}

		public void sample(double tMs) {
			this.t = tMs;
			phase = script.phaseIndexAt(tMs);
			stickyMask = script.stickyMaskAt(tMs);
			fall = null;
			if (phase < 0) {
				SlotScript.Phase first = script.phases().isEmpty() ? null : script.phases().get(0);
				MachineDef def = script.def();
				for (int r = 0; r < 5; r++) {
					top[r] = first == null ? 0 : first.restStops[r];
					blur[r] = false;
					moving[r] = false;
					sinceStop[r] = -1;
				}
				if (first != null) {
					System.arraycopy(first.rest, 0, cells, 0, 15);
					System.arraycopy(first.rest, 0, rest, 0, 15);
				} else {
					Arrays.fill(cells, 0);
					Arrays.fill(rest, 0);
				}
				spin = -1;
				return;
			}
			SlotScript.Phase p = script.phase(phase);
			spin = p.spin;
			System.arraycopy(p.rest, 0, rest, 0, 15);
			double local = tMs - p.spinUpAt;
			for (int r = 0; r < 5; r++) {
				ReelMotion m = p.reels[r];
				if (m == null) {
					top[r] = p.restStops[r];
					blur[r] = false;
					moving[r] = false;
					sinceStop[r] = -1;
					for (int y = 0; y < 3; y++) cells[r * 3 + y] = p.landed[r * 3 + y];
					continue;
				}
				top[r] = m.top(local);
				blur[r] = m.blurred(local);
				moving[r] = !m.landed(local);
				sinceStop[r] = moving[r] ? -1 : local - m.stopMs();
				if (moving[r]) {
					int base = (int) Math.floor(top[r]);
					for (int y = 0; y < 3; y++) cells[r * 3 + y] = cellAt(p, r, base + y, local < m.landAt());
				} else {
					for (int y = 0; y < 3; y++) cells[r * 3 + y] = p.landed[r * 3 + y];
				}
			}
			// tumble chain of this phase: cells follow the last completed fall; a running fall exposes its window
			Beat lastDone = null;
			for (Beat b : script.kind(SlotTimeline.TUMBLE_FALL)) {
				if (b.arg(SlotBeats.SPIN) != p.spin || b.at() > tMs) continue;
				if (b.end() <= tMs) {
					lastDone = b;
				} else {
					fall = b;
				}
			}
			if (fall != null) {
				if (lastDone != null) {
					for (int i = 0; i < 15; i++) beforeFall[i] = lastDone.arg(SlotBeats.FALL_CELLS + i);
				} else {
					System.arraycopy(p.landed, 0, beforeFall, 0, 15);
				}
				for (int i = 0; i < 15; i++) cells[i] = fall.arg(SlotBeats.FALL_CELLS + i);
			} else if (lastDone != null) {
				for (int i = 0; i < 15; i++) cells[i] = lastDone.arg(SlotBeats.FALL_CELLS + i);
			}
		}

		/**
		 * Symbol at unwrapped strip index {@code idx} of reel r while spinning: the rest cells for the rows of the
		 * rest window before the splice (what the screen showed, even after tumbles), else the strip.
		 */
		public int cellAt(SlotScript.Phase p, int r, int idx, boolean beforeSplice) {
			MachineDef def = script.def();
			if (beforeSplice) {
				int d = idx - p.restStops[r];
				if (d >= 0 && d < 3) return p.rest[r * 3 + d];
			}
			return def.symbolAt(r, Math.floorMod(idx, def.stripLength(r)), 0);
		}

		/** Symbol drawn at visible row k (−1 … 3) of a moving reel r at the sampled time. */
		public int movingCell(int r, int k) {
			SlotScript.Phase p = script.phase(phase);
			ReelMotion m = p.reels[r];
			double local = t - p.spinUpAt;
			return cellAt(p, r, (int) Math.floor(top[r]) + k, m != null && local < m.landAt());
		}

		public Frame snapshot() {
			int[] off = new int[5];
			for (int r = 0; r < 5; r++) off[r] = moving[r] ? (int) Math.round(ReelMotion.offset(top[r]) * 1000) : 0;
			return new Frame(cells.clone(), off, stickyMask);
		}
	}
}
