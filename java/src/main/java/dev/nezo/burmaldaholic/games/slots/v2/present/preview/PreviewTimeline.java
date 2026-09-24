package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTiers;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Tumble;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Ways;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Window;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PREVIEW FIXTURE (lane J-L9): a reference {@code SlotTimeline} builder that emits exactly the {@link SlotBeats}
 * contract with the slots.md §2.3 timing tokens and the SLOTS.md §10.3 honest anticipation rule. The screen uses
 * {@link #build} only while {@link SlotTimeline#build} still throws (lane J-L8, S-M3); lane J-L8 may lift this code.
 * Shared beats are scaled by the spinning player's profile (turbo = 200 %), local beats by the viewer's profile.
 */
public final class PreviewTimeline {
	private PreviewTimeline() {}

	/** The real builder when it exists, else this preview builder. */
	public static Timeline buildOrPreview(SpinTape tape, MachineDef def, int[] restStops, TimingProfile shared, TimingProfile local, int seed,
			boolean anticipation) {
		try {
			return SlotTimeline.build(tape, def, shared, local, seed);
		} catch (UnsupportedOperationException notYet) {
			return build(tape, def, restStops, shared, local, seed, anticipation);
		}
	}

	private static final class Ctx {
		final TimelineBuilder b;
		final MachineDef def;
		final TimingProfile shared;
		final long bet;
		int group;

		Ctx(TimelineBuilder b, MachineDef def, TimingProfile shared, long bet) {
			this.b = b;
			this.def = def;
			this.shared = shared;
			this.bet = bet;
		}

		int ms(int base, boolean fs) {
			return shared.scale(fs ? base * 4 / 5 : base);
		}

		int chips(long fifths) {
			return (int) Math.min(Integer.MAX_VALUE, fifths * bet / 5);
		}
	}

	private record PhaseEnd(int t, int[] finalCells, Ways.Result finalEval, long payFifths, int stickyAfter, int[] stopsAfter, int winShowAt) {}

	public static Timeline build(SpinTape tape, MachineDef def, int[] restStops, TimingProfile shared, TimingProfile local, int seed,
			boolean anticipation) {
		TimelineBuilder b = Timeline.builder("slots." + def.machine().id, seed);
		Ctx c = new Ctx(b, def, shared, tape.bet());
		int t = 0;
		int firstWinShow = -1;
		int[] stops = restStops.clone();
		PhaseEnd base = null;
		boolean features = tape.freeSpins() != null || tape.hunt() != null || tape.hoard() != null || tape.wheel() != null;
		if (!tape.bought()) {
			base = reelPhase(c, 0, tape.stops(), stops, 0, false, 0, anticipation, !features);
			t = base.t;
			stops = base.stopsAfter;
			firstWinShow = base.winShowAt;
		}
		// bonus games first (SLOTS.md §3.2: Hoard, then free spins)
		if (tape.hunt() != null) t = hunt(c, tape.hunt(), t, base);
		if (tape.hoard() != null) t = hoard(c, tape.hoard(), t);
		if (tape.wheel() != null) t = wheel(c, tape.wheel(), t, base);
		if (tape.freeSpins() != null) t = freeSpins(c, tape, t, stops, anticipation, base);
		if (tape.capHit()) {
			b.group(++c.group).clock(Clock.SHARED);
			b.add(t, c.ms(500, false), SlotTimeline.MAX_WIN, -1);
			t += c.ms(500, false);
		}
		// LOCAL part after the reveal gate: jackpots (tape order, 70 % after the first), then the spin total
		b.clock(Clock.LOCAL);
		int lt = t;
		for (int i = 0; i < tape.jackpots().size(); i++) {
			SpinTape.JackpotAward a = tape.jackpots().get(i);
			int len = local.scale(jackpotMs(a.tier()) * (i == 0 ? 10 : 7) / 10);
			b.group(++c.group).add(lt, len, SlotTimeline.JACKPOT, i, a.tier(), (int) Math.min(Integer.MAX_VALUE, a.chips()));
			lt += len;
		}
		long total = tape.totalFifths();
		if (total > 0) {
			WinTier tier = SlotTiers.of(total, null);
			int roll = rollUpMs(total, local);
			int at = !features && tape.jackpots().isEmpty() && firstWinShow >= 0 ? firstWinShow : lt;
			b.group(++c.group).add(at, roll + local.scale(800), SlotTimeline.ROLLUP, -1, c.chips(total), tier.ordinal(), (int) tape.bet());
			lt = Math.max(lt, at + roll + local.scale(800));
		}
		b.clock(Clock.LOCAL).group(c.group).add(Math.max(t, lt), 0, SlotTimeline.END, -1);
		return b.build();
	}

	/** Slot roll-up: {@code clamp(600 + 900·log10(1 + win/bet), 600, 8000)}; reduce motion ≤ 300 ms; × local speed. */
	public static int rollUpMs(long totalFifths, TimingProfile local) {
		int d = RollUp.durationMs(totalFifths, 5, 600, 8000);
		if (local.reduceMotion()) d = Math.min(d, 300);
		return local.scale(d);
	}

	/** Jackpot celebration length by sub-tier (slots.md §4.11). */
	public static int jackpotMs(int tier) {
		return switch (tier) {
			case 1 -> 2000;
			case 2 -> 2500;
			case 3 -> 3000;
			default -> 4000;
		};
	}

	// ---- one reel phase (base spin or a free spin) ---------------------------------------------------------------

	private static PhaseEnd reelPhase(Ctx c, int spin, int[] stops, int[] restStops, int stickyBefore, boolean fs, int at, boolean anticipation,
			boolean cycle) {
		MachineDef def = c.def;
		TimelineBuilder b = c.b;
		int winShowAt = -1;
		b.clock(Clock.SHARED).group(c.group);
		b.add(at, c.ms(120, fs), SlotTimeline.SPIN_UP, -1, spin);
		int[] landed = PreviewEngine.window(def, stops);
		int[] stopAt = new int[5];
		boolean[] ant = new boolean[5];
		int[] reason = new int[5];
		boolean anticipating = false;
		int why = 0;
		for (int r = 0; r < 5; r++) {
			if (r == 0) {
				stopAt[r] = c.ms(600, fs);
			} else {
				stopAt[r] = stopAt[r - 1] + (anticipating ? c.ms(1000, fs) : c.ms(150, fs));
				ant[r] = anticipating && !sticky(stickyBefore, r);
				reason[r] = why;
			}
			if (anticipation && !anticipating && r < 4) {
				why = anticipationReason(def, landed, r, stickyBefore);
				anticipating = why != 0;
			}
		}
		int[] roleCount = new int[SymbolRole.values().length];
		int stickyAfter = stickyBefore;
		int wild = PreviewEngine.wildIndex(def);
		int lastStop = 0;
		int stickEnd = 0;
		for (int r = 0; r < 5; r++) {
			if (sticky(stickyBefore, r)) continue;
			int land = ant[r] ? c.ms(600, fs) : c.ms(350, fs);
			int stopMs = at + stopAt[r];
			b.add(stopMs - land, land, SlotTimeline.REEL_LAND, r, spin, stops[r], ant[r] ? 1 : 0);
			if (ant[r]) b.add(at + stopAt[r - 1], stopAt[r] - stopAt[r - 1], SlotTimeline.ANTICIPATE, r, spin, reason[r]);
			lastStop = Math.max(lastStop, stopMs);
			boolean egg = false;
			for (int y = 0; y < 3; y++) {
				int s = landed[r * 3 + y];
				SymbolRole role = def.roles()[s];
				boolean counts = role == SymbolRole.SCATTER || role == SymbolRole.COIN || role == SymbolRole.WILD
					|| role == SymbolRole.BONUS && (def.bonusReelsMask() & (1 << r)) != 0;
				if (!counts) continue;
				b.add(stopMs, c.ms(220, fs), SlotTimeline.SYMBOL_LAND, r, spin, y, s, roleCount[role.ordinal()]++);
				if (fs && def.machine() == Machine.END && s == wild && r >= 1 && r <= 3) egg = true;
			}
			if (egg) {
				stickyAfter |= 1 << (r - 1);
				b.add(stopMs + c.ms(270, fs), c.ms(300, fs), SlotTimeline.WILD_EXPAND, r, spin);
				b.add(stopMs + c.ms(570, fs), c.ms(200, fs), SlotTimeline.WILD_STICK, r, spin, stickyAfter);
				stickEnd = Math.max(stickEnd, stopMs + c.ms(770, fs));
			}
		}
		int t = Math.max(lastStop + c.ms(150, fs), stickEnd);
		int[] finalCells;
		Ways.Result finalEval;
		long pay = 0;
		int[] ladder = fs ? def.ladderFree() : def.ladder();
		if (ladder.length > 0) {
			Tumble.Chain chain = PreviewEngine.tumble(def, stops, ladder);
			for (Tumble.Step s : chain.steps()) {
				b.group(c.group);
				if (winShowAt < 0) winShowAt = t;
				int showMs = c.ms(600, fs);
				b.add(t, showMs, SlotTimeline.WIN_SHOW, -1, spin, s.step(), s.result().winMask(), c.chips(s.payFifths()), s.multiplier());
				t += showMs;
				int mask = s.result().winMask();
				b.add(t, c.ms(250, fs), SlotTimeline.TUMBLE_EXPLODE, -1, spin, s.step(), mask, c.chips(s.payFifths()), s.multiplier());
				int nextIndex = Math.min(s.step() + 1, ladder.length - 1);
				if (ladder[nextIndex] != s.multiplier()) {
					b.add(t, c.ms(200, fs), SlotTimeline.MULT_UP, -1, spin, s.step(), ladder[nextIndex], nextIndex);
				}
				t += c.ms(250, fs);
				int[] after = afterStep(chain, s);
				int[] args = new int[2 + 15];
				args[0] = spin;
				args[1] = s.step();
				System.arraycopy(after, 0, args, 2, 15);
				b.add(t, c.ms(300, fs), SlotTimeline.TUMBLE_FALL, -1, args);
				t += c.ms(300, fs) + c.ms(200, fs);
				pay += s.payFifths();
			}
			finalCells = chain.finalWindow().cells();
			finalEval = PreviewEngine.evaluate(def, finalCells, 0);
		} else {
			finalCells = landed.clone();
			finalEval = PreviewEngine.evaluate(def, landed, stickyAfter);
			long scat = PreviewEngine.scatterPay(def, finalEval.scatters());
			int mult = fs ? def.fsMultiplier() : 1;
			pay = (finalEval.payFifths() + scat) * mult;
			if (pay > 0) {
				int scatterMask = scat > 0 ? roleMask(def, PreviewEngine.applySticky(def, landed, stickyAfter), SymbolRole.SCATTER) : 0;
				winShowAt = t;
				int show = c.ms(900, fs);
				b.add(t, show, SlotTimeline.WIN_SHOW, -1, spin, 0, finalEval.winMask() | scatterMask, c.chips(pay), mult);
				t += show;
				if (cycle && !fs) {
					List<Ways.WayWin> wins = new ArrayList<>(finalEval.wins());
					wins.sort(Comparator.comparingLong(Ways.WayWin::payFifths).reversed());
					int i = 0;
					for (Ways.WayWin w : wins) {
						b.add(t, c.ms(700, fs), SlotTimeline.WAY_CYCLE, i++, spin, 0, w.symbol(), w.k(), w.ways(), c.chips(w.payFifths() * mult),
							w.cellMask());
						t += c.ms(700, fs);
					}
					if (scat > 0) {
						b.add(t, c.ms(700, fs), SlotTimeline.WAY_CYCLE, i, spin, 0, SymbolStyle.SCATTER, finalEval.scatters(), 0,
							c.chips(scat * mult), scatterMask);
						t += c.ms(700, fs);
					}
				}
			}
		}
		// sticky reels keep the cells they had when they became sticky (the reel does not spin)
		int[] stopsAfter = restStops.clone();
		for (int r = 0; r < 5; r++) if (!sticky(stickyBefore, r)) stopsAfter[r] = stops[r];
		return new PhaseEnd(t, finalCells, finalEval, pay, stickyAfter, stopsAfter, winShowAt);
	}

	private static int[] afterStep(Tumble.Chain chain, Tumble.Step s) {
		int i = chain.steps().indexOf(s);
		if (i + 1 < chain.steps().size()) return chain.steps().get(i + 1).window().cells();
		return chain.finalWindow().cells();
	}

	private static boolean sticky(int mask, int reel) {
		return reel >= 1 && reel <= 3 && (mask & (1 << (reel - 1))) != 0;
	}

	/**
	 * SLOTS.md §10.3 after reel {@code k} stopped, on the cells ALREADY VISIBLE on reels 0..k: 1 scatter, 2 bonus, 3
	 * coins, 0 none. Sticky reels count as visible wilds (they cover scatters).
	 */
	public static int anticipationReason(MachineDef def, int[] landed, int k, int stickyMask) {
		int[] cells = PreviewEngine.applySticky(def, landed, stickyMask);
		int scatters = 0;
		int coins = 0;
		boolean[] bonusOn = new boolean[5];
		for (int r = 0; r <= k; r++) {
			for (int y = 0; y < 3; y++) {
				SymbolRole role = def.roles()[cells[r * 3 + y]];
				if (role == SymbolRole.SCATTER) scatters++;
				if (role == SymbolRole.COIN) coins++;
				if (role == SymbolRole.BONUS && (def.bonusReelsMask() & (1 << r)) != 0) bonusOn[r] = true;
			}
		}
		if (scatters >= 2 && k < 4) return 1;
		if (def.machine() == Machine.OVERWORLD && bonusOn[0] && bonusOn[2] && k < 4) return 2;
		if (def.machine() == Machine.END && bonusOn[1] && bonusOn[2] && k < 3) return 2;
		if (def.machine() == Machine.NETHER && coins >= 4 && coins + 3 * (4 - k) >= 6) return 3;
		return 0;
	}

	private static int roleMask(MachineDef def, int[] cells, SymbolRole role) {
		int m = 0;
		for (int i = 0; i < 15; i++) if (def.roles()[cells[i]] == role) m |= 1 << i;
		return m;
	}

	// ---- features ---------------------------------------------------------------------------------------------

	private static int hunt(Ctx c, SpinTape.Hunt hunt, int t, PhaseEnd base) {
		TimelineBuilder b = c.b;
		b.clock(Clock.SHARED).group(++c.group);
		int trigger = base == null ? 0 : roleMask(c.def, base.finalCells, SymbolRole.BONUS);
		b.add(t, c.ms(600, false), SlotTimeline.BONUS_INTRO, -1, 2, trigger);
		t += c.ms(600, false);
		for (int i = 0; i < hunt.opened(); i++) {
			b.group(++c.group);
			b.add(t, c.ms(700, false), SlotTimeline.HUNT_OPEN, i, hunt.entries()[i]);
			t += c.ms(700, false);
		}
		int remaining = hunt.entries().length - hunt.opened();
		return t + c.ms(80 * remaining + 900, false);
	}

	private static int hoard(Ctx c, SpinTape.Hoard hoard, int t) {
		TimelineBuilder b = c.b;
		b.clock(Clock.SHARED).group(++c.group);
		int mask = 0;
		for (int cell : hoard.initialCells()) mask |= 1 << cell;
		// values in cell-index order of the mask bits
		int[] order = sortedValues(hoard.initialCells(), hoard.initialValues());
		int[] intro = new int[2 + order.length];
		intro[0] = 3;
		intro[1] = mask;
		System.arraycopy(order, 0, intro, 2, order.length);
		b.add(t, c.ms(800, false), SlotTimeline.BONUS_INTRO, -1, intro);
		t += c.ms(800, false);
		int respins = 3;
		int coins = hoard.initialCells().length;
		long totalFifths = 0;
		for (int v : hoard.initialValues()) totalFifths += Math.max(0, v) * 5L;
		for (int i = 0; i < hoard.respinCells().size(); i++) {
			int[] cells = hoard.respinCells().get(i);
			int[] vals = hoard.respinValues().get(i);
			int nm = 0;
			for (int cell : cells) nm |= 1 << cell;
			respins = cells.length > 0 ? 3 : respins - 1;
			int[] sorted = sortedValues(cells, vals);
			int[] args = new int[2 + sorted.length];
			args[0] = nm;
			args[1] = respins;
			System.arraycopy(sorted, 0, args, 2, sorted.length);
			b.group(++c.group).add(t, c.ms(900, false), SlotTimeline.HOARD_RESPIN, i, args);
			t += c.ms(900, false);
			coins += cells.length;
			for (int v : vals) totalFifths += Math.max(0, v) * 5L;
		}
		int collect = c.ms(120 * coins + 300, false);
		b.group(++c.group).add(t, collect, SlotTimeline.HOARD_COLLECT, -1, c.chips(totalFifths), coins >= 15 ? 1 : 0);
		return t + collect;
	}

	private static int[] sortedValues(int[] cells, int[] values) {
		Integer[] idx = new Integer[cells.length];
		for (int i = 0; i < idx.length; i++) idx[i] = i;
		java.util.Arrays.sort(idx, Comparator.comparingInt(i -> cells[i]));
		int[] out = new int[cells.length];
		for (int i = 0; i < idx.length; i++) out[i] = values[idx[i]];
		return out;
	}

	private static int wheel(Ctx c, SpinTape.Wheel wheel, int t, PhaseEnd base) {
		TimelineBuilder b = c.b;
		b.clock(Clock.SHARED).group(++c.group);
		int trigger = base == null ? 0 : roleMask(c.def, base.finalCells, SymbolRole.BONUS);
		b.add(t, c.ms(700, false), SlotTimeline.BONUS_INTRO, -1, 4, trigger);
		t += c.ms(700, false) + c.ms(800, false); // intro + ready
		int[] spinMs = {4500, 4000, 5000};
		for (int ring = 0; ring < wheel.segments().length; ring++) {
			b.group(++c.group);
			int d = c.ms(spinMs[ring], false);
			b.add(t, d, SlotTimeline.WHEEL_SPIN, ring, wheel.segments()[ring]);
			t += d + c.ms(600, false);
			if (ring + 1 < wheel.segments().length) {
				b.add(t, c.ms(800, false), SlotTimeline.WHEEL_UP, ring);
				t += c.ms(800, false);
			}
		}
		return t + c.ms(400, false);
	}

	private static int freeSpins(Ctx c, SpinTape tape, int t, int[] stops, boolean anticipation, PhaseEnd base) {
		TimelineBuilder b = c.b;
		SpinTape.FreeSpins fs = tape.freeSpins();
		b.clock(Clock.SHARED).group(++c.group);
		int scatterMask = base == null ? 0 : roleMask(c.def, base.finalCells, SymbolRole.SCATTER);
		b.add(t, c.ms(2000, false), SlotTimeline.FS_INTRO, -1, fs.awarded(), scatterMask);
		t += c.ms(2000, false);
		int total = fs.awarded();
		int sticky = 0;
		long before = 0;
		int[] rest = stops;
		int mult = c.def.fsMultiplier();
		for (int i = 0; i < fs.spins().size(); i++) {
			SpinTape.FreeSpin spin = fs.spins().get(i);
			b.clock(Clock.SHARED).group(++c.group);
			int startT = t;
			PhaseEnd p = reelPhase(c, i + 1, spin.stops(), rest, sticky, true, t, anticipation, false);
			t = p.t;
			sticky = p.stickyAfter;
			rest = p.stopsAfter;
			if (spin.retrigger()) {
				int added = Math.min(c.def.retrigger(), c.def.fsCap() - total);
				total += added;
				b.add(t, c.ms(1200, false), SlotTimeline.FS_RETRIGGER, -1, added, total);
				t += c.ms(1200, false);
			}
			b.group(c.group).add(startT, t - startT, SlotTimeline.FS_SPIN, i, i, total, c.chips(before), mult);
			before += spin.payFifths();
			t += c.ms(250, false);
		}
		b.clock(Clock.SHARED).group(++c.group);
		int outro = rollUpMs(fs.payFifths(), TimingProfile.SHARED) + 1500;
		outro = c.shared.scale(outro);
		b.add(t, outro, SlotTimeline.FS_OUTRO, -1, c.chips(fs.payFifths()));
		return t + outro;
	}

	/** Cell mask helper for tests. */
	public static int bit(int reel, int row) {
		return Window.bit(reel, row);
	}
}
