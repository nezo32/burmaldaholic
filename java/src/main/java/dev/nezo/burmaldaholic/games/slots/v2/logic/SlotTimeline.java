package dev.nezo.burmaldaholic.games.slots.v2.logic;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Builds the {@link Timeline} of one spin from its tape (slots.md §2.2–§2.3, SLOTS.md §10.2–§10.4). Consumed by the
 * server settle timer, the machine screen and the cabinet sync. The beat layout (lane, args) is the contract of
 * {@code games.slots.v2.present.SlotBeats}; every amount in the args is in CHIPS. Vectors: {@code slots_timeline.json}.
 *
 * <p>Layout (all times computed at normal speed in integer ms, then scaled: shared beats by the SPINNING player's
 * profile (turbo = 200 %), local beats by the viewer's profile):
 * <ul>
 *   <li>SHARED (clock S; the reveal gate is their end):
 *     <ul>
 *       <li>reel phase (base spin = spin 0, free spin i = spin i + 1): SPIN_UP 120; REEL_LAND(r) the 350 ms (600 when
 *           anticipated) before stop_r (stops from {@link Anticipation}, §10.3); ANTICIPATE(r) from the previous stop
 *           to stop_r; SYMBOL_LAND(r) 220 per scatter / coin / wild / bonus-reel bonus cell; End free spins: WILD_EXPAND
 *           stop + 270 (300), WILD_STICK stop + 570 (200); the win overview WIN_SHOW (last stop + 150, 900 ms) — or per
 *           tumble step WIN_SHOW 600 + TUMBLE_EXPLODE 250 / MULT_UP 200 + TUMBLE_FALL 300 + pause 200. Free spins run
 *           the layout × 0.8;</li>
 *       <li>bonus games (before free spins): Treasure Hunt BONUS_INTRO 600, then the timeline PAUSES at its end
 *           ({@link #huntPauseMs}) until the picks are done (the i-th pick reveals entry i, D6/F7 — the picks are sent
 *           per pick, not in the timeline), then HUNT_END (the dimmed rest reveal, a fixed 80 ms × board + 900 so the
 *           timeline never depends on hidden entries); Piglin's Hoard BONUS_INTRO 800, HOARD_RESPIN 900 each,
 *           HOARD_COLLECT 120 × coins + 300; Dragon Wheel BONUS_INTRO 700 + 800 ready, WHEEL_SPIN 4 500 / 4 000 /
 *           5 000 + 600 glow, WHEEL_UP 800 between rings, BONUS_END 1 000 (glow + exit);</li>
 *       <li>free spins: FS_INTRO 2 000; FS_SPIN spans each free spin (+ 250 between spins); FS_RETRIGGER 1 200; FS_OUTRO
 *           roll-up of the feature total + 1 500; MAX_WIN 500 when the cap ended the spin.</li>
 *     </ul></li>
 *   <li>LOCAL (clock L, after the gate): ROLLUP (roll-up + 800 hold; 0 roll-up for "Returned", ≤ 300 with reduce
 *       motion) with the WAY_CYCLE entries next to it (700 each; base game only, no feature), then the JACKPOT
 *       celebrations in tape order (Mini 2 000, Minor 2 500, Major 3 000, Grand 4 000; × 0.7 after the first; ≤ 300 with
 *       reduce motion) — the jackpot is the climax, AFTER the spin total (coordinator decision 2026-09-24) — then END.</li>
 * </ul>
 * Beat args never carry information that is not public at the beat's start (F7).
 */
public final class SlotTimeline {
	public static final String SPIN_UP = "slots.spin_up";
	public static final String REEL_LAND = "slots.reel_land";
	public static final String ANTICIPATE = "slots.anticipate";
	public static final String SYMBOL_LAND = "slots.symbol_land";
	public static final String WIN_SHOW = "slots.win_show";
	public static final String WAY_CYCLE = "slots.way_cycle";
	public static final String TUMBLE_EXPLODE = "slots.tumble_explode";
	public static final String TUMBLE_FALL = "slots.tumble_fall";
	public static final String MULT_UP = "slots.mult_up";
	public static final String WILD_EXPAND = "slots.wild_expand";
	public static final String WILD_STICK = "slots.wild_stick";
	public static final String FS_INTRO = "slots.fs_intro";
	public static final String FS_SPIN = "slots.fs_spin";
	public static final String FS_RETRIGGER = "slots.fs_retrigger";
	public static final String FS_OUTRO = "slots.fs_outro";
	public static final String BONUS_INTRO = "slots.bonus_intro";
	public static final String BONUS_END = "slots.bonus_end";
	public static final String HUNT_OPEN = "slots.hunt_open";
	public static final String HUNT_END = "slots.hunt_end";
	public static final String HOARD_RESPIN = "slots.hoard_respin";
	public static final String HOARD_COLLECT = "slots.hoard_collect";
	public static final String WHEEL_SPIN = "slots.wheel_spin";
	public static final String WHEEL_UP = "slots.wheel_up";
	public static final String JACKPOT = "slots.jackpot";
	public static final String ROLLUP = "slots.rollup";
	public static final String MAX_WIN = "slots.max_win";
	public static final String END = "slots.end";

	/** Timing tokens (normal speed, ms; slots.md §2.3). */
	public static final int T_SPIN_UP = 120;
	public static final int T_LAND = 350;
	public static final int T_LAND_ANTICIPATED = 600;
	public static final int T_SYMBOL_LAND = 220;
	public static final int T_WIN_DELAY = 150;
	public static final int T_WIN_ALL = 900;
	public static final int T_WAY_CYCLE = 700;
	public static final int T_TUMBLE_SHOW = 600;
	public static final int T_TUMBLE_EXPLODE = 250;
	public static final int T_MULT_UP = 200;
	public static final int T_TUMBLE_FALL = 300;
	public static final int T_TUMBLE_PAUSE = 200;
	public static final int T_WILD_EXPAND_AT = 270;
	public static final int T_WILD_EXPAND = 300;
	public static final int T_WILD_STICK = 200;
	public static final int T_FS_INTRO = 2000;
	public static final int T_FS_GAP = 250;
	public static final int T_FS_RETRIGGER = 1200;
	public static final int T_FS_OUTRO_HOLD = 1500;
	public static final int T_HUNT_INTRO = 600;
	/** Autoplay / "Open all" pick cadence (SLOTS.md §6.4: one chest every 600 ms). */
	public static final int T_HUNT_OPEN = 600;
	/** Rest reveal after the hunt: {@code T_HUNT_DIM × board + T_HUNT_DWELL}. */
	public static final int T_HUNT_DIM = 80;
	public static final int T_HUNT_DWELL = 900;
	public static final int T_HOARD_INTRO = 800;
	public static final int T_HOARD_RESPIN = 900;
	public static final int T_HOARD_COLLECT = 120;
	public static final int T_HOARD_COLLECT_END = 300;
	public static final int T_WHEEL_INTRO = 700;
	public static final int T_WHEEL_READY = 800;
	public static final int[] T_WHEEL_RINGS = {4500, 4000, 5000};
	public static final int T_WHEEL_UP = 800;
	public static final int T_WHEEL_GLOW = 600;
	public static final int T_WHEEL_EXIT = 400;
	public static final int T_MAX_WIN = 500;
	public static final int T_ROLLUP_HOLD = 800;
	public static final int T_REDUCED_ROLLUP = 300;
	public static final int[] T_JACKPOT = {0, 2000, 2500, 3000, 4000};

	/** Bonus kinds in BONUS_INTRO args[0] (= {@code SlotBeats.FEATURE_*}). */
	public static final int BONUS_HUNT = 2;
	public static final int BONUS_HOARD = 3;
	public static final int BONUS_WHEEL = 4;

	/** ANTICIPATE args[1]: why the reel anticipates. */
	public static final int REASON_SCATTER = 1;
	public static final int REASON_BONUS = 2;
	public static final int REASON_COINS = 3;

	private SlotTimeline() {}

	/** Unscaled beat of the plan. */
	private record Raw(int at, int dur, String kind, int lane, int group, int[] args) {}

	private static final class Plan {
		final List<Raw> beats = new ArrayList<>();
		final long bet;
		int group;

		Plan(long bet) {
			this.bet = bet;
		}

		int add(int at, int dur, String kind, int lane, int... args) {
			beats.add(new Raw(at, dur, kind, lane, group, args));
			return at + dur;
		}

		void next() {
			group++;
		}

		/** Fifths of the bet → chips (int, clamped). */
		int chips(long fifths) {
			return clampInt(fifths * bet / 5);
		}
	}

	/** Integer 0.8 × (free spins, SLOTS.md §10.2). */
	private static int fsScale(int ms) {
		return Math.floorDiv(ms * 4, 5);
	}

	/** Window with sticky reels (End free spins; bits 0–2 = reels 2–4) forced to Wild. */
	private static Window applySticky(MachineDef def, Window w, int sticky) {
		if (sticky == 0) return w;
		int[] c = w.cells();
		for (int b = 0; b < 3; b++) if ((sticky >> b & 1) != 0) for (int y = 0; y < 3; y++) c[(b + 1) * 3 + y] = def.wild();
		return new Window(c);
	}

	private static boolean sticky(int mask, int reel) {
		return reel >= 1 && reel <= 3 && (mask >> (reel - 1) & 1) != 0;
	}

	/** Cells of {@code window} whose symbol has {@code role} (BONUS: only on the bonus reels). */
	static int roleMask(MachineDef def, int[] cells, SymbolRole role) {
		int m = 0;
		for (int i = 0; i < 15; i++) {
			if (def.roles()[cells[i]] != role) continue;
			if (role == SymbolRole.BONUS && (def.bonusReelsMask() >> (i / 3) & 1) == 0) continue;
			m |= 1 << i;
		}
		return m;
	}

	/** Result of one reel phase. */
	private record Phase(int end, int[] finalCells) {}

	/**
	 * One reel phase (base spin {@code spin = 0} or free spin {@code spin = i + 1}) from {@code t0}.
	 *
	 * @param payFifths the phase's pay as the tape has it (free-spin multiplier applied; base: the evaluation's pay)
	 */
	private static Phase reelPhase(Plan p, MachineDef def, int spin, int[] stops, boolean free, int stickyBefore, long payFifths, int t0,
			IntUnaryOperator k, boolean anticipation) {
		SpinEval e = SpinEval.of(def, stops, free, stickyBefore);
		Window landed = applySticky(def, e.landed(), stickyBefore);
		int[] cells = landed.cells();
		int[] times = Anticipation.stopTimes(def, landed, anticipation, free, stickyBefore);
		boolean[] ant = Anticipation.anticipated(def, landed, anticipation, free, stickyBefore);
		p.add(t0, k.applyAsInt(T_SPIN_UP), SPIN_UP, -1, spin);
		int[] roleCount = new int[SymbolRole.values().length];
		int acc = stickyBefore;
		int lastStop = t0;
		int stickEnd = 0;
		for (int r = 0; r < 5; r++) {
			if (sticky(stickyBefore, r)) continue;
			int stop = t0 + k.applyAsInt(times[r]);
			lastStop = Math.max(lastStop, stop);
			int land = k.applyAsInt(ant[r] ? T_LAND_ANTICIPATED : T_LAND);
			if (ant[r]) {
				int a = t0 + k.applyAsInt(times[r - 1]);
				p.add(a, stop - a, ANTICIPATE, r, spin, Anticipation.reason(def, landed, r - 1, free, stickyBefore));
			}
			p.add(stop - land, land, REEL_LAND, r, spin, stops[r], ant[r] ? 1 : 0);
			boolean egg = false;
			for (int y = 0; y < 3; y++) {
				int s = cells[r * 3 + y];
				SymbolRole role = def.roles()[s];
				boolean counts = role == SymbolRole.SCATTER || role == SymbolRole.COIN || role == SymbolRole.WILD
					|| role == SymbolRole.BONUS && (def.bonusReelsMask() >> r & 1) != 0;
				if (!counts) continue;
				p.add(stop, k.applyAsInt(T_SYMBOL_LAND), SYMBOL_LAND, r, spin, y, s, roleCount[role.ordinal()]++);
				egg |= free && def.stickyWilds() && role == SymbolRole.WILD && r >= 1 && r <= 3;
			}
			if (egg && (e.stickyAfter() >> (r - 1) & 1) != 0) {
				acc |= 1 << (r - 1);
				p.add(stop + k.applyAsInt(T_WILD_EXPAND_AT), k.applyAsInt(T_WILD_EXPAND), WILD_EXPAND, r, spin);
				int st = stop + k.applyAsInt(T_WILD_EXPAND_AT + T_WILD_EXPAND);
				stickEnd = Math.max(stickEnd, p.add(st, k.applyAsInt(T_WILD_STICK), WILD_STICK, r, spin, acc));
			}
		}
		int t = Math.max(lastStop + k.applyAsInt(T_WIN_DELAY), stickEnd);
		int[] finalCells = e.finalWindow().cells();
		if (def.tumbles()) {
			int[] ladder = free ? def.ladderFree() : def.ladder();
			List<Tumble.Step> steps = e.chain().steps();
			for (int i = 0; i < steps.size(); i++) {
				Tumble.Step st = steps.get(i);
				if (st.result().payFifths() == 0) break;
				int mask = st.result().winMask();
				int pay = p.chips(st.payFifths());
				p.add(t, k.applyAsInt(T_TUMBLE_SHOW), WIN_SHOW, -1, spin, st.step(), mask, pay, st.multiplier());
				int x = t + k.applyAsInt(T_TUMBLE_SHOW);
				p.add(x, k.applyAsInt(T_TUMBLE_EXPLODE), TUMBLE_EXPLODE, -1, spin, st.step(), mask, pay, st.multiplier());
				int nextIndex = Math.min(st.step() + 1, ladder.length - 1);
				if (ladder.length > 0 && ladder[nextIndex] != st.multiplier()) {
					p.add(x, k.applyAsInt(T_MULT_UP), MULT_UP, -1, spin, st.step(), ladder[nextIndex], nextIndex);
				}
				int[] after = i + 1 < steps.size() ? steps.get(i + 1).window().cells() : finalCells;
				int[] args = new int[2 + 15];
				args[0] = spin;
				args[1] = st.step();
				System.arraycopy(after, 0, args, 2, 15);
				p.add(x + k.applyAsInt(T_TUMBLE_EXPLODE), k.applyAsInt(T_TUMBLE_FALL), TUMBLE_FALL, -1, args);
				t = x + k.applyAsInt(T_TUMBLE_EXPLODE + T_TUMBLE_FALL + T_TUMBLE_PAUSE);
			}
			if (e.scatterFifths() > 0) {
				t = p.add(t, k.applyAsInt(T_WIN_ALL), WIN_SHOW, -1, spin, steps.size() - 1, roleMask(def, finalCells, SymbolRole.SCATTER),
					p.chips(e.scatterFifths()), 1);
			}
		} else if (payFifths > 0) {
			int mult = free ? def.fsMultiplier() : 1;
			int mask = e.chain().steps().getFirst().result().winMask();
			if (e.scatterFifths() > 0) mask |= roleMask(def, finalCells, SymbolRole.SCATTER);
			t = p.add(t, k.applyAsInt(T_WIN_ALL), WIN_SHOW, -1, spin, 0, mask, p.chips(payFifths), mult);
		}
		return new Phase(t, finalCells);
	}

	/** Same as {@link #build(SpinTape, MachineDef, TimingProfile, TimingProfile, int, boolean, int[])} with defaults. */
	public static Timeline build(SpinTape tape, MachineDef def, TimingProfile shared, TimingProfile local, int seed) {
		return build(tape, def, shared, local, seed, true, null);
	}

	/**
	 * The spin's timeline.
	 *
	 * @param tape         the drawn spin, or the section a client may see (a running Treasure Hunt: the entries opened so
	 *                     far, total -1 — the shared part is the same, the local part appears once the hunt is complete)
	 * @param def          machine
	 * @param shared       the SPINNING player's profile (turbo = 200), published in {@code TimelineSeed.speedPct}
	 * @param local        the viewer's profile (roll-ups, celebrations)
	 * @param seed         cosmetic seed ({@code SeedMix.mix(posHash, spinSeq)})
	 * @param anticipation {@code slots.anticipation}
	 * @param bigWinTiers  {@code slots.bigWinTiers} (null = 5 / 15 / 40 / 100)
	 */
	public static Timeline build(SpinTape tape, MachineDef def, TimingProfile shared, TimingProfile local, int seed, boolean anticipation,
			int[] bigWinTiers) {
		Plan p = new Plan(tape.bet());
		int t = 0;
		int[] baseCells = null;
		List<Ways.WayWin> baseWins = List.of();
		long baseScatter = 0;
		if (!tape.bought()) {
			SpinEval e = SpinEval.of(def, tape.stops(), false, 0);
			Phase ph = reelPhase(p, def, 0, tape.stops(), false, 0, e.payFifths(), 0, ms -> ms, anticipation);
			t = ph.end();
			baseCells = ph.finalCells();
			if (!def.tumbles()) {
				baseWins = new ArrayList<>(e.chain().steps().getFirst().result().wins());
				baseWins.sort(Comparator.comparingLong(Ways.WayWin::payFifths).reversed().thenComparingInt(Ways.WayWin::symbol));
				baseScatter = e.scatterFifths();
			}
		}
		// ---- bonus game (SLOTS.md §3: before the free spins) ----
		if (tape.hunt() != null) {
			p.next();
			int trigger = baseCells == null ? 0 : roleMask(def, baseCells, SymbolRole.BONUS);
			t = p.add(t, T_HUNT_INTRO, BONUS_INTRO, -1, BONUS_HUNT, trigger); // interactive: pauses here (huntPauseMs)
			p.next();
			int board = def.features().pickBoard() > 0 ? def.features().pickBoard() : 15;
			t = p.add(t, T_HUNT_DIM * board + T_HUNT_DWELL, HUNT_END, -1);
		} else if (tape.hoard() != null) {
			p.next();
			SpinTape.Hoard h = tape.hoard();
			int respins = def.features().holdRespins() > 0 ? def.features().holdRespins() : 3;
			int mask = 0;
			for (int c : h.initialCells()) mask |= 1 << c;
			int[] vals = sortedValues(h.initialCells(), h.initialValues());
			int[] intro = new int[2 + vals.length];
			intro[0] = BONUS_HOARD;
			intro[1] = mask;
			System.arraycopy(vals, 0, intro, 2, vals.length);
			t = p.add(t, T_HOARD_INTRO, BONUS_INTRO, -1, intro);
			int left = respins;
			int coins = h.initialCells().length;
			long valueFifths = 0;
			for (int v : h.initialValues()) valueFifths += Math.max(0, v) * 5L;
			for (int i = 0; i < h.respinCells().size(); i++) {
				p.next();
				int[] cells = h.respinCells().get(i);
				int[] rv = h.respinValues().get(i);
				int nm = 0;
				for (int c : cells) nm |= 1 << c;
				left = cells.length > 0 ? respins : left - 1;
				coins += cells.length;
				for (int v : rv) valueFifths += Math.max(0, v) * 5L;
				int[] sorted = sortedValues(cells, rv);
				int[] args = new int[2 + sorted.length];
				args[0] = nm;
				args[1] = left;
				System.arraycopy(sorted, 0, args, 2, sorted.length);
				t = p.add(t, T_HOARD_RESPIN, HOARD_RESPIN, i, args);
			}
			p.next();
			t = p.add(t, T_HOARD_COLLECT * coins + T_HOARD_COLLECT_END, HOARD_COLLECT, -1, p.chips(valueFifths), coins >= 15 ? 1 : 0);
		} else if (tape.wheel() != null) {
			p.next();
			int trigger = baseCells == null ? 0 : roleMask(def, baseCells, SymbolRole.BONUS);
			t = p.add(t, T_WHEEL_INTRO, BONUS_INTRO, -1, BONUS_WHEEL, trigger) + T_WHEEL_READY;
			int[] segs = tape.wheel().segments();
			for (int ring = 0; ring < segs.length; ring++) {
				p.next();
				int dur = ring < T_WHEEL_RINGS.length ? T_WHEEL_RINGS[ring] : 4000;
				t = p.add(t, dur, WHEEL_SPIN, ring, segs[ring]) + T_WHEEL_GLOW;
				if (ring + 1 < segs.length) t = p.add(t, T_WHEEL_UP, WHEEL_UP, ring);
			}
			t = p.add(t - T_WHEEL_GLOW, T_WHEEL_GLOW + T_WHEEL_EXIT, BONUS_END, -1, BONUS_WHEEL);
		}
		// ---- free spins ----
		if (tape.freeSpins() != null) {
			SpinTape.FreeSpins fs = tape.freeSpins();
			p.next();
			int scatterMask = baseCells == null ? 0 : roleMask(def, baseCells, SymbolRole.SCATTER);
			t = p.add(t, T_FS_INTRO, FS_INTRO, -1, fs.awarded(), scatterMask);
			int sticky = 0;
			int total = fs.awarded();
			long before = 0;
			for (int i = 0; i < fs.spins().size(); i++) {
				SpinTape.FreeSpin s = fs.spins().get(i);
				p.next();
				int start = t;
				int idx = p.beats.size();
				t = reelPhase(p, def, i + 1, s.stops(), true, sticky, s.payFifths(), t, SlotTimeline::fsScale, anticipation).end();
				sticky = s.stickyMaskAfter();
				if (s.retrigger()) {
					int added = Math.max(0, Math.min(def.retrigger(), def.fsCap() - total));
					total += added;
					t = p.add(t, T_FS_RETRIGGER, FS_RETRIGGER, -1, added, total);
				}
				// FS_SPIN spans the whole free spin; it starts the phase (inserted before its beats)
				p.beats.add(idx, new Raw(start, t - start, FS_SPIN, i, p.group, new int[] {i, total, p.chips(before), def.fsMultiplier()}));
				before += s.payFifths();
				if (i + 1 < fs.spins().size()) t += T_FS_GAP;
			}
			p.next();
			long featureChips = fs.payFifths() * tape.bet() / 5;
			t = p.add(t, RollUp.durationMs(featureChips, tape.bet(), 600, 8000) + T_FS_OUTRO_HOLD, FS_OUTRO, -1, clampInt(featureChips));
		}
		if (tape.capHit()) {
			p.next();
			t = p.add(t, T_MAX_WIN, MAX_WIN, -1);
		}

		// ---- scale the shared part ----
		TimelineBuilder b = Timeline.builder("slots." + tape.machine().id, seed);
		int gate = shared.scale(t);
		for (Raw x : p.beats) {
			int at = shared.scale(x.at());
			int end = shared.scale(x.at() + x.dur());
			b.group(x.group()).clock(Clock.SHARED).add(at, end - at, x.kind(), x.lane(), x.args());
			gate = Math.max(gate, end); // a symbol pop may outlast the last stop: the gate is the end of every shared beat
		}
		// ---- local part ----
		Local lo = new Local(b, local, gate, p.group + 1);
		if (tape.totalFifths() > 0) {
			WinTier tier = SlotTiers.of(tape.totalFifths(), bigWinTiers);
			boolean returned = tier == WinTier.RETURN;
			int d = returned ? 0 : RollUp.durationMs(tape.totalChips(), tape.bet(), 600, 8000);
			if (local.reduceMotion()) d = Math.min(d, T_REDUCED_ROLLUP);
			int start = lo.off;
			lo.add(d + T_ROLLUP_HOLD, ROLLUP, -1, clampInt(tape.totalChips()), tier.ordinal(), clampInt(tape.bet()));
			int rollEnd = lo.off;
			// the way cycle runs next to the roll-up (decoration, skippable); only for a base game without a feature
			lo.off = start;
			if (!returned && !tape.featureTriggered()) {
				int i = 0;
				for (Ways.WayWin w : baseWins) {
					lo.add(T_WAY_CYCLE, WAY_CYCLE, i++, 0, 0, w.symbol(), w.k(), w.ways(), p.chips(w.payFifths()), w.cellMask());
				}
				if (baseScatter > 0 && baseCells != null) {
					int n = Integer.bitCount(roleMask(def, baseCells, SymbolRole.SCATTER));
					lo.add(T_WAY_CYCLE, WAY_CYCLE, i, 0, 0, def.scatter(), n, 0, p.chips(baseScatter), roleMask(def, baseCells, SymbolRole.SCATTER));
				}
			}
			lo.off = Math.max(lo.off, rollEnd);
			lo.g++;
		}
		for (int i = 0; i < tape.jackpots().size(); i++) {
			SpinTape.JackpotAward j = tape.jackpots().get(i);
			int full = T_JACKPOT[Math.max(1, Math.min(4, j.tier()))];
			int d = local.reduceMotion() ? T_REDUCED_ROLLUP : i == 0 ? full : Math.floorDiv(full * 7, 10);
			lo.add(d, JACKPOT, i, j.tier(), clampInt(j.chips()));
			lo.g++;
		}
		lo.add(0, END, -1);
		return b.build();
	}

	/** Values in cell-index order (Hoard coins). */
	private static int[] sortedValues(int[] cells, int[] values) {
		Integer[] idx = new Integer[cells.length];
		for (int i = 0; i < idx.length; i++) idx[i] = i;
		Arrays.sort(idx, Comparator.comparingInt(i -> cells[i]));
		int[] out = new int[cells.length];
		for (int i = 0; i < idx.length; i++) out[i] = values[idx[i]];
		return out;
	}

	/** Local beats laid out after the gate on the viewer's clock. */
	private static final class Local {
		final TimelineBuilder b;
		final TimingProfile profile;
		final int gate;
		int g;
		int off;

		Local(TimelineBuilder b, TimingProfile profile, int gate, int g) {
			this.b = b;
			this.profile = profile;
			this.gate = gate;
			this.g = g;
		}

		void add(int dur, String kind, int lane, int... args) {
			int at = gate + profile.scale(off);
			b.group(g).clock(Clock.LOCAL).add(at, gate + profile.scale(off + dur) - at, kind, lane, args);
			off += dur;
		}
	}

	static int clampInt(long v) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
	}

	/** Where the timeline pauses for the Treasure Hunt picks: end of the hunt BONUS_INTRO, or -1. */
	public static int huntPauseMs(Timeline tl) {
		for (Beat b : tl.beats()) if (b.kind().equals(BONUS_INTRO) && b.arg(0) == BONUS_HUNT) return b.end();
		return -1;
	}

	/** Shared-part length in ticks (the settle timer, SLOTS.md §10.2). */
	public static int gateTicks(Timeline tl) {
		return tl.sharedEndTicks();
	}

	/**
	 * Terminal display of a spin (fidelity: the last frame of every presentation equals this): the window the reels rest
	 * on (after tumbles; after free spins / for a bought feature: the last free spin's), or an empty array.
	 */
	public static int[] terminalWindow(SpinTape tape, MachineDef def) {
		int[] window = new int[0];
		if (!tape.bought()) window = SpinEval.of(def, tape.stops(), false, 0).finalWindow().cells();
		int sticky = 0;
		if (tape.freeSpins() != null) {
			for (SpinTape.FreeSpin s : tape.freeSpins().spins()) {
				window = SpinEval.of(def, s.stops(), true, sticky).finalWindow().cells();
				sticky = s.stickyMaskAfter();
			}
		}
		return window;
	}

	/** Stops the reels rest on after the spin (the last reel phase's; a bought feature without spins keeps {@code prev}). */
	public static int[] terminalStops(SpinTape tape, int[] prev) {
		if (tape.freeSpins() != null && !tape.freeSpins().spins().isEmpty()) {
			int[] out = prev.clone();
			if (!tape.bought()) out = tape.stops();
			int sticky = 0;
			for (SpinTape.FreeSpin s : tape.freeSpins().spins()) {
				int[] st = s.stops();
				for (int r = 0; r < 5; r++) if (!sticky(sticky, r)) out[r] = st[r];
				sticky = s.stickyMaskAfter();
			}
			return out;
		}
		return tape.bought() ? prev.clone() : tape.stops();
	}
}
