package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Spin;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Tumble;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The world FX of one published spin (slots.md §5.3), as timed events: the server plays them at
 * {@code startTick + ceil(atMs / 50)} so the particles and positional sounds match the cabinet reels every
 * spectator sees. PURE (the server scheduler lives in {@code games.slots.SlotsFx}).
 */
public final class CabinetFxPlan {
	public enum Kind {
		/** A spin starts: {@code slots.spin_loop} at the cabinet (arg = spin index). */
		SPIN,
		/** Free spins triggered: {@code sparkle} ring 20 + {@code slots.fs_intro}. */
		FREE_SPINS,
		/** Nether tumble step: {@code ember_burst} 8 at the reel face (arg = multiplier). */
		TUMBLE,
		/** End sticky reel: {@code void_motes} 6 at the reel (arg = reel). */
		STICKY,
		/** Tier celebration at the reveal gate (arg = {@link WinTier} ordinal; {@link #MAX_WIN_ARG} for max win). */
		TIER,
		/** Jackpot at the reveal gate (arg = 1 Mini … 4 Grand). */
		JACKPOT
	}

	/** {@link Kind#TIER} arg for a max win. */
	public static final int MAX_WIN_ARG = 100;

	public record Event(int atMs, Kind kind, int arg) {}

	private CabinetFxPlan() {}

	/** Every world-FX event of the sync, sorted by time (stable). Silent spins produce only {@link Kind#SPIN}. */
	public static List<Event> events(CabinetSync s) {
		List<Event> out = new ArrayList<>();
		List<Spin> spins = s.spins();
		for (int i = 0; i < spins.size(); i++) {
			Spin sp = spins.get(i);
			out.add(new Event(sp.startMs(), Kind.SPIN, i));
			for (Tumble t : sp.tumbles()) out.add(new Event(sp.startMs() + t.atMs(), Kind.TUMBLE, t.multiplier()));
			int fresh = sp.newSticky();
			for (int r = 0; r < CabinetSync.REELS; r++) {
				if ((fresh & 1 << r) != 0) {
					int at = sp.startMs() + sp.stopMs()[r] + CabinetFrames.EXPAND_START_MS * 100 / s.speedPct();
					out.add(new Event(at, Kind.STICKY, r));
				}
			}
		}
		if (spins.size() > 1) {
			Spin base = spins.getFirst();
			out.add(new Event(base.startMs() + base.lastStopMs() + CabinetSync.Builder.WIN_DELAY_MS * 100 / s.speedPct(),
				Kind.FREE_SPINS, spins.size() - 1));
		}
		if (s.maxWin()) {
			out.add(new Event(s.gateMs(), Kind.TIER, MAX_WIN_ARG));
		} else if (s.tier().ordinal() >= WinTier.NICE.ordinal() && s.tier() != WinTier.JACKPOT) {
			out.add(new Event(s.gateMs(), Kind.TIER, s.tier().ordinal()));
		}
		if (s.jackpotTier() > 0) out.add(new Event(s.gateMs(), Kind.JACKPOT, s.jackpotTier()));
		out.sort(Comparator.comparingInt(Event::atMs));
		return out;
	}

	/** Server tick (level game time) at which an event plays: {@code startTick + ceil(atMs / 50)}. */
	public static long tickOf(CabinetSync s, Event e) {
		return s.startTick() + (e.atMs() + 49) / 50;
	}
}
