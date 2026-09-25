package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Hoard;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Spin;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Tumble;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Wheel;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;

/**
 * The cabinet's view of a spin as a shared presentation timeline (docs/architecture/animation.md §3), using the
 * {@link SlotTimeline} beat kinds. It is rebuilt from the sync only (so a spectator needs no machine config) and
 * drives the BER's positional cues (reel stops, tumbles, sticky wilds, wheel). Every beat is on the SHARED clock;
 * {@code END} sits at the reveal gate, so {@code sharedEndMs() == gateMs} whenever the gate is the last beat.
 * PURE.
 */
public final class CabinetBeats {
	private CabinetBeats() {}

	public static Timeline timeline(CabinetSync s) {
		TimelineBuilder b = Timeline.builder("slots." + s.machine().id, s.seed()).clock(Clock.SHARED);
		int pct = s.speedPct();
		for (int i = 0; i < s.spins().size(); i++) {
			Spin sp = s.spins().get(i);
			int st = sp.startMs();
			b.group(i);
			if (i > 0) b.add(st, 0, SlotTimeline.FS_SPIN, -1, i);
			b.add(st, (int) Math.round(ReelMotion.spinUp(pct)), SlotTimeline.SPIN_UP, -1, i);
			for (int r = 0; r < CabinetSync.REELS; r++) {
				if ((sp.heldMask() & 1 << r) != 0) continue;
				boolean ant = (sp.anticipationMask() & 1 << r) != 0;
				int stop = sp.stopMs()[r];
				int land = (int) Math.round(ReelMotion.land(pct, ant));
				int from = Math.max(0, stop - land);
				if (ant) b.add(st + from, stop - from, SlotTimeline.ANTICIPATE, r);
				b.add(st + from, stop - from, SlotTimeline.REEL_LAND, r, sp.stops()[r]);
			}
			for (Tumble t : sp.tumbles()) {
				double k = 100.0 / pct * (i > 0 ? 0.8 : 1.0);
				int ex = (int) Math.round(CabinetFrames.EXPLODE_MS * k);
				int fall = (int) Math.round(CabinetFrames.FALL_MS * k);
				b.add(st + t.atMs(), ex, SlotTimeline.TUMBLE_EXPLODE, -1, t.explodeMask());
				b.add(st + t.atMs(), 0, SlotTimeline.MULT_UP, -1, t.multiplier());
				b.add(st + t.atMs() + ex, fall, SlotTimeline.TUMBLE_FALL, -1);
			}
			int fresh = sp.newSticky();
			for (int r = 0; r < CabinetSync.REELS; r++) {
				if ((fresh & 1 << r) == 0) continue;
				int at = st + sp.stopMs()[r] + CabinetFrames.EXPAND_START_MS * 100 / pct;
				b.add(at, CabinetFrames.EXPAND_MS * 100 / pct, SlotTimeline.WILD_EXPAND, r);
				b.add(at + CabinetFrames.EXPAND_MS * 100 / pct, CabinetFrames.STICK_MS * 100 / pct, SlotTimeline.WILD_STICK, r);
			}
			if (sp.winMask() != 0) b.add(st + sp.winShowMs(), 0, SlotTimeline.WIN_SHOW, -1, sp.winMask());
		}
		int g = s.spins().size();
		Hoard h = s.hoard();
		if (h != null) {
			b.group(g++);
			b.add(h.startMs(), 0, SlotTimeline.BONUS_INTRO, -1, 1);
			for (int i = 0; i < h.stepMs().length; i++) b.add(h.stepMs()[i], CabinetFrames.HOARD_LAND_MS * 100 / pct, SlotTimeline.HOARD_RESPIN, i, h.stepMasks()[i]);
			b.add(Math.max(h.startMs(), h.endMs()), 0, SlotTimeline.HOARD_COLLECT, -1);
		}
		Wheel w = s.wheel();
		if (w != null) {
			b.group(g++);
			b.add(w.startMs(), CabinetFrames.WHEEL_RISE_MS * 100 / pct, SlotTimeline.WHEEL_UP, 0);
			for (int i = 0; i < w.segments().length; i++) {
				if (i > 0) b.add(w.spinMs()[i] - CabinetFrames.WHEEL_UP_MS * 100 / pct, CabinetFrames.WHEEL_UP_MS * 100 / pct, SlotTimeline.WHEEL_UP, i);
				b.add(w.spinMs()[i], w.spinDurMs()[i], SlotTimeline.WHEEL_SPIN, i, w.segments()[i]);
			}
		}
		b.group(g);
		b.add(s.gateMs(), 0, SlotTimeline.END, -1, s.tier().ordinal(), s.jackpotTier());
		return b.build();
	}
}
