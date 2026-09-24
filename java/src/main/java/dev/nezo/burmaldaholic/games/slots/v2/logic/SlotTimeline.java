package dev.nezo.burmaldaholic.games.slots.v2.logic;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineBuilder;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Builds the {@link Timeline} of one spin from its tape (slots.md §2.2–§2.3, SLOTS.md §10.2–§10.4). Consumed by the
 * server settle timer, the screen, the BER and — through the identical Bedrock port
 * ({@code bedrock/src/games/slots/v2/logic/timeline.ts}) — the DDUI form and the {@code slot_reels} entity. Vectors:
 * {@code slots_timeline.json} (byte-identical canonical JSON in both editions).
 *
 * <p>Layout (all times computed at normal speed in integer ms, then scaled: shared beats by the SPINNING player's
 * profile (turbo = 200 %), local beats by the viewer's profile):
 * <ul>
 *   <li>SHARED (clock S; the reveal gate is their end):
 *     <ul>
 *       <li>base spin: SPIN_UP 0–120; REEL_LAND(r) the 350 ms before stop_r (stops from {@link Anticipation}, §10.3);
 *           ANTICIPATE(r) from the previous stop to stop_r; SYMBOL_LAND(r) cues for scatter/bonus/coin/wild cells; win
 *           overview WIN_SHOW (stop_5 + 150, 900 ms) — or per tumble step WIN_SHOW 600 + TUMBLE_EXPLODE 250 / MULT_UP 200
 *           + TUMBLE_FALL 300 + pause 200;</li>
 *       <li>bonus: BONUS_INTRO args[0] = 1 hunt / 2 hoard / 3 wheel (600 / 800 / 700 ms). The Treasure Hunt is
 *           INTERACTIVE: the timeline PAUSES at the end of the hunt intro ({@link #huntPauseMs}) until the picks are done
 *           (the i-th pick reveals entry i, D6); the picks are local presentation, so no HUNT_OPEN beat is in the spin
 *           timeline. HOARD_RESPIN(i) 900 each, HOARD_COLLECT 120 per coin; WHEEL_SPIN (lane ring, args[0] segment)
 *           4 500 / 4 000 / 5 000 + 600 result glow, WHEEL_UP 800 zoom before the next ring;</li>
 *       <li>free spins: FS_INTRO 2 000; per spin FS_SPIN(args [i, spins awarded so far]) cue + the base layout × 0.8
 *           (sticky reels do not spin; WILD_EXPAND stop + 270 / 300 ms, WILD_STICK + 570 / 200 ms); FS_RETRIGGER 1 200;
 *           FS_OUTRO roll-up of the feature total + 1 500 hold; MAX_WIN cue when the cap ended the spin.</li>
 *     </ul></li>
 *   <li>LOCAL (clock L, after the gate): MAX_WIN plate 200; ROLLUP (args [tier ordinal, totalFifths]; 0 for "Returned";
 *       ≤ 300 with reduce motion); WAY_CYCLE 700 per winning symbol (args [symbol, pay]) next to the roll-up, only when the
 *       base spin is the last reel segment; then JACKPOT celebrations in tape order (args [tier, i]; Mini 2 000, Minor
 *       2 500, Major 3 000, Grand 4 000; × 0.7 after the first; ≤ 300 with reduce motion) — the jackpot is the climax, AFTER
 *       the spin total (coordinator decision 2026-09-24, SLOTS.md / animation slots.md §2.5; the Bedrock builder of
 *       1564aa5 still plays them before the roll-up and will be aligned at integration); END cue.</li>
 * </ul>
 * Ordinal contract with the frames: a reel segment starts at SPIN_UP (base) or FS_SPIN (free spin i); REEL_LAND lane r
 * ENDS at reel r's stop; the k-th TUMBLE_EXPLODE / TUMBLE_FALL of a segment removes the wins of evaluation k−1 and drops
 * evaluation k; the j-th WIN_SHOW highlights evaluation j; the i-th JACKPOT beat is {@code tape.jackpots()[i]}; MULT_UP
 * args[0] = the multiplier. Beat args never carry information that is not public at the beat's start (F7).
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
	public static final String HUNT_OPEN = "slots.hunt_open";
	public static final String HOARD_RESPIN = "slots.hoard_respin";
	public static final String HOARD_COLLECT = "slots.hoard_collect";
	public static final String WHEEL_SPIN = "slots.wheel_spin";
	public static final String WHEEL_UP = "slots.wheel_up";
	public static final String JACKPOT = "slots.jackpot";
	public static final String ROLLUP = "slots.rollup";
	public static final String MAX_WIN = "slots.max_win";
	public static final String END = "slots.end";

	/** Timing tokens (normal speed, ms; slots.md §2.3) — Bedrock {@code SLOT_MS}. */
	public static final int T_SPIN_UP = 120;
	public static final int T_LAND = 350;
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
	public static final int T_FS_RETRIGGER = 1200;
	public static final int T_FS_OUTRO_HOLD = 1500;
	public static final int T_HUNT_INTRO = 600;
	/** Autoplay / "Open all" pick cadence (SLOTS.md §6.4: one chest every 600 ms). */
	public static final int T_HUNT_OPEN = 600;
	public static final int T_HOARD_INTRO = 800;
	public static final int T_HOARD_RESPIN = 900;
	public static final int T_HOARD_COLLECT = 120;
	public static final int T_WHEEL_INTRO = 700;
	public static final int[] T_WHEEL_RINGS = {4500, 4000, 5000};
	public static final int T_WHEEL_UP = 800;
	public static final int T_WHEEL_GLOW = 600;
	public static final int T_MAX_WIN = 200;
	public static final int T_REDUCED_ROLLUP = 300;
	public static final int[] T_JACKPOT = {0, 2000, 2500, 3000, 4000};

	/** Bonus kinds in BONUS_INTRO args[0] (the hunt pauses after the intro with args[0] = 1). */
	public static final int BONUS_HUNT = 1;
	public static final int BONUS_HOARD = 2;
	public static final int BONUS_WHEEL = 3;

	private SlotTimeline() {}

	/** Unscaled beat of the plan. */
	private record Raw(int at, int dur, String kind, int lane, int group, int[] args) {}

	private static final class Plan {
		final List<Raw> beats = new ArrayList<>();
		int group;

		int add(int at, int dur, String kind, int lane, int... args) {
			beats.add(new Raw(at, dur, kind, lane, group, args));
			return at + dur;
		}

		void next() {
			group++;
		}
	}

	/** Integer 0.8 × (free spins, SLOTS.md §10.2). */
	private static int fsScale(int ms) {
		return Math.floorDiv(ms * 4, 5);
	}

	private static boolean special(MachineDef def, int s) {
		return s == def.scatter() || s == def.bonus() || s == def.coin() || s == def.wild();
	}

	/** Window with sticky reels (End free spins) forced to WWW. */
	private static Window applySticky(MachineDef def, Window w, int sticky) {
		if (sticky == 0) return w;
		int[] c = w.cells();
		for (int b = 0; b < 3; b++) if ((sticky >> b & 1) != 0) for (int y = 0; y < 3; y++) c[(b + 1) * 3 + y] = def.wild();
		return new Window(c);
	}

	/** One reel phase (base or free spin) from {@code t0}; returns the time the phase ends (after its win show / tumbles). */
	private static int reelPhase(Plan p, MachineDef def, int[] stops, boolean free, int stickyBefore, int t0, IntUnaryOperator k,
			boolean anticipation) {
		SpinEval e = SpinEval.of(def, stops, free, stickyBefore);
		int mult = free ? def.fsMultiplier() : 1;
		Window landed = def.tumbles() ? e.chain().steps().getFirst().window() : applySticky(def, e.landed(), stickyBefore);
		int[] times = Anticipation.stopTimes(def, landed, anticipation, free, stickyBefore);
		p.add(t0, k.applyAsInt(T_SPIN_UP), SPIN_UP, -1);
		for (int r = 0; r < 5; r++) {
			int stickyBit = r >= 1 && r <= 3 ? 1 << (r - 1) : 0;
			int stop = t0 + k.applyAsInt(times[r]);
			if (r > 0 && times[r] - times[r - 1] > Anticipation.STAGGER_MS) {
				int a = t0 + k.applyAsInt(times[r - 1]);
				p.add(a, stop - a, ANTICIPATE, r);
			}
			if ((stickyBefore & stickyBit) == 0) {
				p.add(stop - k.applyAsInt(T_LAND), k.applyAsInt(T_LAND), REEL_LAND, r, stops[r]);
				for (int y = 0; y < 3; y++) {
					int s = landed.at(r, y);
					if (special(def, s)) p.add(stop, 0, SYMBOL_LAND, r, y, s);
				}
			}
			if (free && stickyBit != 0 && (e.stickyAfter() & stickyBit) != 0 && (stickyBefore & stickyBit) == 0) {
				p.add(stop + k.applyAsInt(T_WILD_EXPAND_AT), k.applyAsInt(T_WILD_EXPAND), WILD_EXPAND, r);
				p.add(stop + k.applyAsInt(T_WILD_EXPAND_AT + T_WILD_EXPAND), k.applyAsInt(T_WILD_STICK), WILD_STICK, r, r);
			}
		}
		int t = t0 + k.applyAsInt(times[4]);
		for (Raw b : p.beats) if (b.kind().equals(WILD_STICK) && b.at() + b.dur() > t) t = b.at() + b.dur();
		t += k.applyAsInt(T_WIN_DELAY);
		if (def.tumbles()) {
			int[] ladder = free ? def.ladderFree() : def.ladder();
			List<Tumble.Step> steps = e.chain().steps();
			for (Tumble.Step st : steps) {
				if (st.result().payFifths() == 0) break;
				p.add(t, k.applyAsInt(T_TUMBLE_SHOW), WIN_SHOW, -1, st.step(), clampInt(st.payFifths()));
				int x = t + k.applyAsInt(T_TUMBLE_SHOW);
				p.add(x, k.applyAsInt(T_TUMBLE_EXPLODE), TUMBLE_EXPLODE, -1, st.step(), st.result().winMask());
				p.add(x, k.applyAsInt(T_MULT_UP), MULT_UP, -1, Tumble.multiplier(ladder, st.step() + 1), st.step() + 1);
				p.add(x + k.applyAsInt(T_TUMBLE_EXPLODE), k.applyAsInt(T_TUMBLE_FALL), TUMBLE_FALL, -1, st.step() + 1);
				t = x + k.applyAsInt(T_TUMBLE_EXPLODE + T_TUMBLE_FALL + T_TUMBLE_PAUSE);
			}
			long scat = e.scatterFifths() * mult;
			if (scat > 0) t = p.add(t, k.applyAsInt(T_WIN_ALL), WIN_SHOW, -1, steps.size() - 1, clampInt(scat));
		} else {
			long pay = e.payFifths() * mult;
			if (pay > 0) t = p.add(t, k.applyAsInt(T_WIN_ALL), WIN_SHOW, -1, 0, clampInt(pay));
		}
		return t;
	}

	/** Same as {@link #build(SpinTape, MachineDef, TimingProfile, TimingProfile, int, boolean, int[])} with defaults. */
	public static Timeline build(SpinTape tape, MachineDef def, TimingProfile shared, TimingProfile local, int seed) {
		return build(tape, def, shared, local, seed, true, null);
	}

	/**
	 * The spin's timeline.
	 *
	 * @param tape         the drawn spin (a Treasure Hunt's entries are not needed: the hunt pauses the timeline)
	 * @param def          machine
	 * @param shared       the SPINNING player's profile (turbo = 200), published in {@code TimelineSeed.speedPct}
	 * @param local        the viewer's profile (roll-ups, celebrations)
	 * @param seed         cosmetic seed ({@code SeedMix.mix(posHash, spinSeq)})
	 * @param anticipation {@code slots.anticipation}
	 * @param bigWinTiers  {@code slots.bigWinTiers} (null = 5 / 15 / 40 / 100)
	 */
	public static Timeline build(SpinTape tape, MachineDef def, TimingProfile shared, TimingProfile local, int seed, boolean anticipation,
			int[] bigWinTiers) {
		Plan p = new Plan();
		int t = 0;
		List<long[]> baseWins = new ArrayList<>();
		if (!tape.bought()) {
			t = reelPhase(p, def, tape.stops(), false, 0, 0, ms -> ms, anticipation);
			if (!def.tumbles()) {
				SpinEval e = SpinEval.of(def, tape.stops(), false, 0);
				for (Ways.WayWin w : e.chain().steps().getFirst().result().wins()) baseWins.add(new long[] {w.symbol(), w.payFifths()});
				baseWins.sort((a, b) -> a[1] != b[1] ? Long.compare(b[1], a[1]) : Long.compare(a[0], b[0]));
			}
		}
		// ---- bonus ----
		if (tape.hunt() != null) {
			p.next();
			t = p.add(t, T_HUNT_INTRO, BONUS_INTRO, -1, BONUS_HUNT); // interactive: pauses here (huntPauseMs)
		} else if (tape.hoard() != null) {
			p.next();
			SpinTape.Hoard h = tape.hoard();
			int respins = def.features().holdRespins() > 0 ? def.features().holdRespins() : 3;
			t = p.add(t, T_HOARD_INTRO, BONUS_INTRO, -1, BONUS_HOARD, h.initialCells().length);
			int left = respins;
			int coins = h.initialCells().length;
			for (int i = 0; i < h.respinCells().size(); i++) {
				p.next();
				int n = h.respinCells().get(i).length;
				left = n > 0 ? respins : left - 1;
				coins += n;
				t = p.add(t, T_HOARD_RESPIN, HOARD_RESPIN, -1, i, n, left);
			}
			p.next();
			t = p.add(t, T_HOARD_COLLECT * coins, HOARD_COLLECT, -1, coins);
		} else if (tape.wheel() != null) {
			p.next();
			t = p.add(t, T_WHEEL_INTRO, BONUS_INTRO, -1, BONUS_WHEEL);
			int[] segs = tape.wheel().segments();
			for (int ring = 0; ring < segs.length; ring++) {
				p.next();
				if (ring > 0) t = p.add(t, T_WHEEL_UP, WHEEL_UP, ring, ring);
				int dur = ring < T_WHEEL_RINGS.length ? T_WHEEL_RINGS[ring] : 4000;
				t = p.add(t, dur + T_WHEEL_GLOW, WHEEL_SPIN, ring, segs[ring]);
			}
		}
		// ---- free spins ----
		if (tape.freeSpins() != null) {
			SpinTape.FreeSpins fs = tape.freeSpins();
			p.next();
			t = p.add(t, T_FS_INTRO, FS_INTRO, -1, fs.awarded());
			int sticky = 0;
			int total = fs.awarded();
			for (int i = 0; i < fs.spins().size(); i++) {
				SpinTape.FreeSpin s = fs.spins().get(i);
				p.next();
				p.add(t, 0, FS_SPIN, -1, i, total);
				t = reelPhase(p, def, s.stops(), true, sticky, t, SlotTimeline::fsScale, anticipation);
				sticky = s.stickyMaskAfter();
				if (s.retrigger()) {
					int before = total;
					total = Math.min(def.fsCap(), total + def.retrigger());
					t = p.add(t, T_FS_RETRIGGER, FS_RETRIGGER, -1, total - before, total);
				}
			}
			p.next();
			long featureChips = fs.payFifths() * tape.bet() / 5;
			t = p.add(t, RollUp.durationMs(featureChips, tape.bet(), 600, 8000) + T_FS_OUTRO_HOLD, FS_OUTRO, -1, clampInt(fs.payFifths()));
		}
		if (tape.capHit()) p.add(t, 0, MAX_WIN, -1, def.capMultiple());

		// ---- scale the shared part ----
		TimelineBuilder b = Timeline.builder("slots." + tape.machine().id, seed);
		for (Raw x : p.beats) {
			int at = shared.scale(x.at());
			b.group(x.group()).clock(Clock.SHARED).add(at, shared.scale(x.at() + x.dur()) - at, x.kind(), x.lane(), x.args());
		}
		int gate = shared.scale(t);
		// ---- local part ----
		Local lo = new Local(b, local, gate, p.group + 1);
		if (tape.capHit()) {
			lo.add(local.reduceMotion() ? 0 : T_MAX_WIN, MAX_WIN, -1, def.capMultiple());
			lo.g++;
		}
		if (tape.totalFifths() > 0) {
			WinTier tier = SlotTiers.of(tape.totalFifths(), bigWinTiers);
			int cycleStart = lo.off;
			boolean returned = tier == WinTier.RETURN;
			int d = returned ? 0 : RollUp.durationMs(tape.totalChips(), tape.bet(), 600, 8000);
			if (local.reduceMotion()) d = Math.min(d, T_REDUCED_ROLLUP);
			lo.add(d, ROLLUP, -1, tier.ordinal(), clampInt(tape.totalFifths()));
			int rollEnd = lo.off;
			// the way cycle runs next to the roll-up (decoration, skippable); only when the base spin is the last segment
			lo.off = cycleStart;
			if (!returned && tape.freeSpins() == null) {
				for (long[] w : baseWins) lo.add(T_WAY_CYCLE, WAY_CYCLE, -1, (int) w[0], clampInt(w[1]));
			}
			lo.off = Math.max(lo.off, rollEnd);
			lo.g++;
		}
		for (int i = 0; i < tape.jackpots().size(); i++) {
			SpinTape.JackpotAward j = tape.jackpots().get(i);
			int full = T_JACKPOT[j.tier()];
			int d = local.reduceMotion() ? T_REDUCED_ROLLUP : i == 0 ? full : Math.floorDiv(full * 7, 10);
			lo.add(d, JACKPOT, -1, j.tier(), i);
			lo.g++;
		}
		lo.add(0, END, -1);
		return b.build();
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

	/** Where the timeline pauses for the Treasure Hunt picks: end of the hunt BONUS_INTRO (args[0] = 1), or -1. */
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
}
