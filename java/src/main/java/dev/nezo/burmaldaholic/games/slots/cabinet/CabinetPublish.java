package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTiers;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import java.util.ArrayList;
import java.util.List;

/**
 * The server's {@link CabinetSync} of one spin, with every time taken from the spin's {@link SlotTimeline} (the same
 * timeline its settle gate uses), so the cabinet, the screen and the world FX land on the same tick (F10). PURE.
 */
public final class CabinetPublish {
	private CabinetPublish() {}

	/**
	 * @param def        machine
	 * @param tape       the section of the tape the public may see (a running Treasure Hunt: the entries opened so far and
	 *                   total −1 — the result fields then stay empty until the hunt is over)
	 * @param tl         the spin's timeline (shared part; built from the same tape section or the full tape)
	 * @param seq        spin counter of the block
	 * @param startTick  server game time of ms 0
	 * @param seed       cosmetic seed
	 * @param speedPct   shared speed of the spinning player
	 * @param prevStops  stops the reels rested on before the spin
	 * @param huntCells  chests opened so far, in pick order (Treasure Hunt), or empty
	 * @param bigWinTiers {@code slots.bigWinTiers} (null = defaults)
	 */
	public static CabinetSync of(MachineDef def, SpinTape tape, Timeline tl, int seq, long startTick, int seed, int speedPct, int[] prevStops,
			int[] huntCells, int[] bigWinTiers) {
		List<CabinetSync.Spin> spins = new ArrayList<>();
		int[] rest = prevStops.clone();
		int stickyBefore = 0; // tape mask: bits 0–2 = reels 2–4
		for (Beat up : tl.beats()) {
			if (!up.kind().equals(SlotTimeline.SPIN_UP)) continue;
			int spin = up.arg(SlotBeats.SPIN);
			int[] stops;
			int stickyAfter;
			if (spin == 0) {
				stops = tape.stops();
				stickyAfter = 0;
			} else {
				SpinTape.FreeSpin fs = tape.freeSpins().spins().get(spin - 1);
				stops = fs.stops();
				stickyAfter = fs.stickyMaskAfter();
			}
			int held = reelMask(stickyBefore);
			int[] shown = rest.clone();
			int[] stopMs = new int[5];
			int ant = 0;
			for (Beat land : tl.beats()) {
				if (!land.kind().equals(SlotTimeline.REEL_LAND) || land.arg(SlotBeats.SPIN) != spin) continue;
				int r = land.lane();
				if (r < 0 || r >= 5) continue;
				shown[r] = stops[r];
				stopMs[r] = land.end() - up.at();
				if (land.arg(SlotBeats.LAND_ANTICIPATED) != 0) ant |= 1 << r;
			}
			for (int r = 0; r < 5; r++) if ((held >> r & 1) == 0) shown[r] = stops[r];
			List<CabinetSync.Tumble> tumbles = new ArrayList<>();
			int winMask = 0;
			int winShowMs = 0;
			for (Beat b : tl.beats()) {
				if (b.arg(SlotBeats.SPIN) != spin) continue;
				if (b.kind().equals(SlotTimeline.TUMBLE_EXPLODE)) {
					int[] after = fallAfter(tl, spin, b.arg(SlotBeats.TUMBLE_STEP));
					if (after != null) tumbles.add(new CabinetSync.Tumble(b.at() - up.at(), b.arg(SlotBeats.TUMBLE_MASK), b.arg(SlotBeats.TUMBLE_MULT), after));
				} else if (b.kind().equals(SlotTimeline.WIN_SHOW) && !def.tumbles()) {
					winMask = b.arg(SlotBeats.WIN_MASK);
					winShowMs = b.at() - up.at();
				}
			}
			spins.add(new CabinetSync.Spin(up.at(), shown, stopMs, ant, held, CabinetSync.windowOf(def.strips(), shown), tumbles, winMask, winShowMs,
				reelMask(stickyAfter) | held));
			rest = shown;
			stickyBefore = stickyAfter;
		}
		CabinetSync.Hoard hoard = null;
		if (tape.hoard() != null) hoard = hoard(tape.hoard(), tl);
		CabinetSync.Wheel wheel = null;
		if (tape.wheel() != null) wheel = wheel(def, tape.wheel(), tl);
		CabinetSync.Hunt hunt = null;
		if (tape.hunt() != null) hunt = hunt(tape, tl, huntCells);
		boolean known = tape.totalFifths() >= 0;
		WinTier tier = known ? SlotTiers.of(tape.totalFifths(), bigWinTiers) : WinTier.LOSS;
		int jackpot = 0;
		if (known) for (SpinTape.JackpotAward j : tape.jackpots()) jackpot = Math.max(jackpot, j.tier());
		int[][] strips = new int[5][];
		for (int r = 0; r < 5; r++) strips[r] = def.strips()[r].clone();
		return new CabinetSync(tape.machine(), seq, startTick, seed, speedPct, tl.sharedEndMs(), strips, prevStops, spins, hoard, wheel, hunt, tier,
			known ? tape.totalChips() : 0, jackpot, known && tape.capHit());
	}

	/** Tape sticky mask (bits 0–2 = reels 2–4) → reel mask (bit r = reel r). */
	private static int reelMask(int tapeMask) {
		return (tapeMask & 7) << 1;
	}

	private static int[] fallAfter(Timeline tl, int spin, int step) {
		for (Beat b : tl.beats()) {
			if (b.kind().equals(SlotTimeline.TUMBLE_FALL) && b.arg(SlotBeats.SPIN) == spin && b.arg(SlotBeats.TUMBLE_STEP) == step) {
				int[] c = new int[15];
				for (int i = 0; i < 15; i++) c[i] = b.arg(SlotBeats.FALL_CELLS + i);
				return c;
			}
		}
		return null;
	}

	private static CabinetSync.Hoard hoard(SpinTape.Hoard h, Timeline tl) {
		int start = 0;
		for (Beat b : tl.beats()) if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_HOARD) start = b.at();
		int[] values = new int[15];
		int mask = 0;
		for (int i = 0; i < h.initialCells().length; i++) {
			int c = h.initialCells()[i];
			mask |= 1 << c;
			values[c] = h.initialValues()[i];
		}
		List<Beat> respins = new ArrayList<>();
		for (Beat b : tl.beats()) if (b.kind().equals(SlotTimeline.HOARD_RESPIN)) respins.add(b);
		int n = Math.min(respins.size(), h.respinCells().size());
		int[] stepMs = new int[n];
		int[] stepMasks = new int[n];
		int m = mask;
		for (int i = 0; i < n; i++) {
			int[] cells = h.respinCells().get(i);
			int[] vals = h.respinValues().get(i);
			for (int k = 0; k < cells.length; k++) {
				m |= 1 << cells[k];
				values[cells[k]] = vals[k];
			}
			stepMs[i] = respins.get(i).at();
			stepMasks[i] = m;
		}
		int end = start;
		for (Beat b : tl.beats()) if (b.kind().equals(SlotTimeline.HOARD_COLLECT)) end = b.end();
		return new CabinetSync.Hoard(start, mask, values, stepMs, stepMasks, end);
	}

	private static CabinetSync.Wheel wheel(MachineDef def, SpinTape.Wheel w, Timeline tl) {
		int start = 0;
		int end = 0;
		for (Beat b : tl.beats()) {
			if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_WHEEL) start = b.at();
			if (b.kind().equals(SlotTimeline.BONUS_END)) end = b.at() + b.dur() * SlotTimeline.T_WHEEL_GLOW / (SlotTimeline.T_WHEEL_GLOW + SlotTimeline.T_WHEEL_EXIT);
		}
		int[] segs = w.segments();
		int n = Math.max(1, Math.min(3, segs.length));
		int[] sizes = new int[n];
		int[] at = new int[n];
		int[] dur = new int[n];
		int[][] rings = def.features().wheelRings();
		for (Beat b : tl.beats()) {
			if (!b.kind().equals(SlotTimeline.WHEEL_SPIN)) continue;
			int ring = b.lane();
			if (ring < 0 || ring >= n) continue;
			at[ring] = b.at();
			dur[ring] = b.dur();
		}
		for (int i = 0; i < n; i++) sizes[i] = i < rings.length ? rings[i].length : CabinetSync.Builder.WHEEL_RINGS[i];
		return new CabinetSync.Wheel(start, sizes, java.util.Arrays.copyOf(segs, n), at, dur, Math.max(end, start));
	}

	private static CabinetSync.Hunt hunt(SpinTape tape, Timeline tl, int[] huntCells) {
		int start = 0;
		int end = -1;
		for (Beat b : tl.beats()) {
			if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_HUNT) start = b.at();
			if (b.kind().equals(SlotTimeline.HUNT_END)) end = b.end();
		}
		SpinTape.Hunt h = tape.hunt();
		int n = Math.min(h.opened(), h.entries().length);
		int[] cells = new int[n];
		int[] entries = java.util.Arrays.copyOf(h.entries(), n);
		boolean[] used = new boolean[15];
		for (int i = 0; i < n; i++) {
			int c = i < huntCells.length ? huntCells[i] : -1;
			if (c < 0 || c >= 15 || used[c]) {
				c = 0;
				while (c < 14 && used[c]) c++;
			}
			used[c] = true;
			cells[i] = c;
		}
		boolean over = tape.totalFifths() >= 0;
		return new CabinetSync.Hunt(start, cells, entries, over ? Math.max(end, start) : -1);
	}
}
