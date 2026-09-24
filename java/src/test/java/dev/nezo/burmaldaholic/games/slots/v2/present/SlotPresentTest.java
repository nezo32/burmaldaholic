package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Anticipation;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTiers;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewEngine;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewMachines;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewTapes;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lane J-L9 pure presentation tests (slots.md §2.1 fidelity F1–F10, §10.3 honesty, §4 storyboards): final frame
 * == server result for every preview tape, at normal speed, turbo, reduce motion and at the reveal gate; reel
 * landing physics (F4); scrolled filler is the real strip ending at the stop (F2); wheel lands inside the drawn
 * wedge; the i-th pick shows entry i; roll-up words end on the server tier.
 */
class SlotPresentTest {
	private static final TimingProfile NORMAL = TimingProfile.SHARED;
	private static final TimingProfile TURBO = TimingProfile.SHARED.withSpeed(200);
	private static final TimingProfile REDUCED = new TimingProfile(100, true, false);

	private static Timeline timeline(PreviewTapes.Scenario s, TimingProfile shared, TimingProfile local) {
		return SlotTimeline.build(s.tape(), s.def(), shared, local, 1234, true, null);
	}

	private static SlotFrames.Outcome outcome(PreviewTapes.Scenario s) {
		return new SlotFrames.Outcome(s.def(), s.tape(), s.restStops(), null, s.terminalCells());
	}

	@Test
	void everyPreviewTapeEndsOnTheServerWindow() {
		for (PreviewTapes.Scenario s : PreviewTapes.all()) {
			for (TimingProfile[] p : new TimingProfile[][] {{NORMAL, NORMAL}, {TURBO, NORMAL}, {NORMAL, REDUCED}, {TURBO, REDUCED}}) {
				Timeline tl = timeline(s, p[0], p[1]);
				SlotFrames.Outcome o = outcome(s);
				SlotFrames.Frame terminal = SlotFrames.INSTANCE.terminal(o);
				assertEquals(terminal, SlotFrames.INSTANCE.frame(o, tl, tl.endMs()), s.name() + " end");
				assertEquals(terminal, SlotFrames.INSTANCE.frame(o, tl, tl.sharedEndMs()), s.name() + " reveal gate");
				assertEquals(terminal, SlotFrames.INSTANCE.frame(o, tl, tl.endMs() + 60_000), s.name() + " long after");
			}
		}
	}

	@Test
	void previewScenariosCoverEveryPath() {
		PreviewTapes.Scenario loss = PreviewTapes.get("ow_loss");
		assertEquals(0, loss.tape().totalFifths());
		assertEquals(WinTier.RETURN, SlotTiers.of(PreviewTapes.get("ow_returned").tape().totalFifths(), null));
		assertEquals(WinTier.WIN, SlotTiers.of(PreviewTapes.get("ow_win").tape().totalFifths(), null));
		assertEquals(WinTier.NICE, SlotTiers.of(PreviewTapes.get("ow_nice").tape().totalFifths(), null));
		assertEquals(WinTier.BIG, SlotTiers.of(PreviewTapes.get("end_big").tape().totalFifths(), null));
		assertEquals(WinTier.MEGA, SlotTiers.of(PreviewTapes.get("end_mega").tape().totalFifths(), null));
		assertEquals(WinTier.EPIC, SlotTiers.of(PreviewTapes.get("end_epic").tape().totalFifths(), null));
		assertNotNull(PreviewTapes.get("ow_fs").tape().freeSpins());
		assertNotNull(PreviewTapes.get("ow_hunt").tape().hunt());
		assertNotNull(PreviewTapes.get("ne_hoard").tape().hoard());
		assertNotNull(PreviewTapes.get("end_wheel").tape().wheel());
		assertFalse(PreviewTapes.get("ow_jackpot").tape().jackpots().isEmpty());
		assertTrue(PreviewTapes.get("end_maxwin").tape().capHit());
		assertTrue(PreviewTapes.get("ne_buy").tape().bought());
		Timeline tumble = timeline(PreviewTapes.get("ne_tumble"), NORMAL, NORMAL);
		assertTrue(count(tumble, SlotTimeline.TUMBLE_FALL) >= 3);
		Timeline ant = timeline(PreviewTapes.get("ow_anticipation"), NORMAL, NORMAL);
		assertTrue(count(ant, SlotTimeline.ANTICIPATE) >= 1);
		Timeline endFs = timeline(PreviewTapes.get("end_maxwin"), NORMAL, NORMAL);
		assertTrue(count(endFs, SlotTimeline.WILD_STICK) >= 1);
		assertTrue(count(endFs, SlotTimeline.MAX_WIN) == 1);
	}

	private static int count(Timeline tl, String kind) {
		int n = 0;
		for (Beat b : tl.beats()) if (b.kind().equals(kind)) n++;
		return n;
	}

	@Test
	void turboHalvesTheSharedReelPart() {
		PreviewTapes.Scenario s = PreviewTapes.get("ow_win");
		Timeline n = timeline(s, NORMAL, NORMAL);
		Timeline t = timeline(s, TURBO, NORMAL);
		assertTrue(t.sharedEndMs() <= n.sharedEndMs() / 2 + 5, n.sharedEndMs() + " vs " + t.sharedEndMs());
		assertEquals(1200, lastStop(n));
		assertEquals(600, lastStop(t));
	}

	private static int lastStop(Timeline tl) {
		int m = 0;
		for (Beat b : tl.beats()) if (b.kind().equals(SlotTimeline.REEL_LAND)) m = Math.max(m, b.end());
		return m;
	}

	@Test
	void reelLandingIsPhysicalAndExact() {
		MachineDef def = PreviewMachines.def(Machine.END);
		for (int r = 0; r < 5; r++) {
			for (int stop = 0; stop < def.stripLength(r); stop += 7) {
				for (boolean ant : new boolean[] {false, true}) {
					int land = ant ? 600 : 350;
					int stopMs = ant ? 2050 : 600 + 150 * r;
					ReelMotion m = new ReelMotion(120, stopMs - land, land, ant, 3, stop, def.stripLength(r));
					assertEquals(stop, m.top(stopMs), 1e-9);
					assertEquals(stop, m.top(stopMs + 5000), 1e-9);
					assertEquals(3, m.top(0), 1e-9);
					assertTrue(m.maxOvershoot() <= 0.18, "overshoot " + m.maxOvershoot());
					assertTrue(m.maxOvershoot() > 0.02, "a visible bounce");
					// speed is continuous across the splice (same speed on both sides)
					double before = m.speed(m.landAt() - 2);
					double after = m.speed(m.landAt() + 1);
					assertEquals(before, after, 0.004, "speed at the landing start");
					assertTrue(m.blurred(m.landAt() - 1) && m.blurred(m.landAt() + 1), "the splice is hidden under blur");
					// settles within 200 ms after the overshoot peak: never more than 200 ms of the landing left after it
					double peakT = 0;
					double peak = -1;
					for (int i = 0; i <= 400; i++) {
						double tt = m.landAt() + land * i / 400.0;
						double o = stop - m.top(tt);
						if (o > peak) {
							peak = o;
							peakT = tt;
						}
					}
					assertTrue(stopMs - peakT <= 200 + 1e-6, "settle " + (stopMs - peakT));
				}
			}
		}
	}

	@Test
	void scrolledFillerIsTheStripEndingAtTheStop() {
		// after the splice every visible row is a strip cell within the last few cells before t_r (F2, SLOTS §10.3 c)
		MachineDef def = PreviewMachines.def(Machine.OVERWORLD);
		ReelMotion m = new ReelMotion(120, 250, 350, false, 5, 17, def.stripLength(0));
		for (double t = m.landAt(); t <= m.stopMs(); t += 5) {
			double top = m.top(t);
			assertTrue(top <= 17 + m.landDistance() + 1e-9 && top >= 17 - 0.18, "top " + top);
		}
	}

	@Test
	void anticipatedReelKeepsTheSameStripAndSpeed() {
		PreviewTapes.Scenario s = PreviewTapes.get("ow_anticipation");
		Timeline tl = timeline(s, NORMAL, NORMAL);
		SlotScript script = new SlotScript(tl, s.def(), s.restStops(), null);
		SlotScript.Phase p = script.phase(0);
		boolean any = false;
		for (int r = 0; r < 5; r++) {
			if (!p.anticipated[r]) continue;
			any = true;
			ReelMotion m = p.reels[r];
			assertEquals(ReelMotion.V, m.cruiseVel(m.landAt() - 1), 1e-12, "full speed until its landing");
			assertEquals(s.tape().stops()[r], m.stop());
		}
		assertTrue(any);
		// anticipation happens iff the visible condition holds (the reason is recomputed from visible cells)
		int[] landed = PreviewEngine.window(s.def(), s.tape().stops());
		boolean condition = false;
		for (int k = 0; k < 4 && !condition; k++) condition = Anticipation.reason(s.def(), new Window(landed), k, false, 0) != 0;
		assertTrue(condition);
		PreviewTapes.Scenario plain = PreviewTapes.get("ow_win");
		int[] w = PreviewEngine.window(plain.def(), plain.tape().stops());
		boolean c2 = false;
		for (int k = 0; k < 4; k++) c2 |= Anticipation.reason(plain.def(), new Window(w), k, false, 0) != 0;
		assertEquals(c2, count(timeline(plain, NORMAL, NORMAL), SlotTimeline.ANTICIPATE) > 0);
	}

	@Test
	void tumblesContinueFromTheTumbledWindow() {
		// a free spin after a tumbling spin starts from the cells the screen showed (continuity), not the strip window
		PreviewTapes.Scenario s = PreviewTapes.get("ne_fs");
		Timeline tl = timeline(s, NORMAL, NORMAL);
		SlotScript script = new SlotScript(tl, s.def(), s.restStops(), null);
		for (int i = 1; i < script.phases().size(); i++) {
			assertArrayEquals(script.finalCells(script.phase(i - 1)), script.phase(i).rest, "phase " + i);
		}
		SlotFrames.Sampler sampler = new SlotFrames.Sampler(script);
		SlotScript.Phase p1 = script.phase(1);
		sampler.sample(p1.spinUpAt + 1);
		// at the very start of the spin-up the rows still show the rest cells
		for (int r = 0; r < 5; r++) {
			if (p1.reels[r] == null) continue;
			int idx = (int) Math.floor(sampler.top[r]);
			for (int k = 0; k < 3; k++) {
				int d = idx + k - p1.restStops[r];
				if (d >= 0 && d < 3) assertEquals(p1.rest[r * 3 + d], sampler.movingCell(r, k));
			}
		}
	}

	@Test
	void sharedAndLocalClocks() {
		for (PreviewTapes.Scenario s : PreviewTapes.all()) {
			Timeline tl = timeline(s, NORMAL, NORMAL);
			for (Beat b : tl.beats()) {
				boolean local = b.kind().equals(SlotTimeline.ROLLUP) || b.kind().equals(SlotTimeline.JACKPOT) || b.kind().equals(SlotTimeline.END)
					|| b.kind().equals(SlotTimeline.WAY_CYCLE); // the way cycle runs next to the roll-up after the gate
				assertEquals(local ? Clock.LOCAL : Clock.SHARED, b.clock(), s.name() + " " + b);
			}
		}
	}

	@Test
	void rollUpWordsUpgradeAndEndOnTheServerTier() {
		long bet = 50;
		for (long win : new long[] {800, 2_000, 6_000, 25_000}) {
			WinTier tier = WinTier.of(win, bet, WinTierTable.SLOTS);
			WinTier prev = WinTier.LOSS;
			for (int i = 0; i <= 1000; i++) {
				WinTier w = CelebrationPlan.wordAt(i / 1000.0, win, bet, WinTierTable.SLOTS, tier);
				assertTrue(w.ordinal() >= prev.ordinal(), "monotonic word");
				prev = w;
			}
			assertEquals(tier, CelebrationPlan.wordAt(1, win, bet, WinTierTable.SLOTS, tier));
			for (long[] up : CelebrationPlan.upgrades(win, bet, WinTierTable.SLOTS, tier)) {
				double u = up[1] / 1e6;
				assertEquals(WinTier.values()[(int) up[0]], CelebrationPlan.wordAt(u, win, bet, WinTierTable.SLOTS, tier));
				assertTrue(u <= 1, "an upgrade exactly at the threshold happens on the last frame");
			}
		}
		assertEquals(0, CelebrationPlan.progressAt(0, 100), 1e-12);
		assertEquals(1, CelebrationPlan.progressAt(100, 100), 1e-12);
	}

	@Test
	void wheelLandsInsideTheDrawnWedge() {
		for (int ring = 0; ring < 3; ring++) {
			int[] wedges = PreviewMachines.wheelRing(ring);
			for (int seg = 0; seg < wedges.length; seg++) {
				for (int seed = 0; seed < 40; seed++) {
					WheelMotion w = new WheelMotion(wedges.length, seg, seed * 17.5, 4500, SeedMix.mix(seed, ring));
					double end = w.finalAngle();
					assertEquals(seg, w.wedgeAt(end));
					double a = 360.0 / wedges.length;
					double phi = ((-end) % 360 + 360) % 360;
					double inWedge = (phi - seg * a) / a;
					assertTrue(inWedge >= 0.2 - 1e-9 && inWedge <= 0.8 + 1e-9, "central 60 %: " + inWedge);
					assertTrue(end - seed * 17.5 >= 3 * 360, "at least three turns");
				}
			}
		}
	}

	@Test
	void huntRevealsEntryIOnPickI() {
		HuntBoard board = new HuntBoard();
		int[] entries = {5, 2, -1, 10, 0, 3, 1, 1, 2, 25, 1, 1, 1, 1, 1};
		int[] clicks = {14, 0, 7, 3, 9};
		for (int i = 0; i < clicks.length; i++) {
			assertTrue(board.press(clicks[i], i * 1000));
			assertFalse(board.canPick(2), "one pending pick at a time");
			assertEquals(clicks[i], board.reveal(entries[i], i * 1000 + 100));
			assertEquals(entries[i], board.entry(clicks[i]));
		}
		assertTrue(board.ended(), "the creeper ends the hunt");
		assertEquals(5 + 2 + 10, board.totalTimesBet());
		board.revealRest(java.util.Arrays.copyOfRange(entries, 5, 15), 9000);
		int dimmed = 0;
		for (int c = 0; c < 15; c++) if (board.state(c) == HuntBoard.State.DIMMED) dimmed++;
		assertEquals(10, dimmed);
		assertEquals(3, board.entry(1), "first remaining chest in reading order shows the next entry");
		assertEquals(5 + 2 + 10, board.totalTimesBet(), "dimmed prizes do not pay");
	}

	@Test
	void wayPathsConnectAdjacentReelsOnly() {
		int[] out = new int[80];
		int mask = 0b111_111_111_111_111; // everything
		int n = WinShowPlan.segments(mask, 5, out);
		assertEquals(36, n);
		for (int i = 0; i < n; i++) assertEquals(out[2 * i] / 3 + 1, out[2 * i + 1] / 3);
		assertEquals(3, WinShowPlan.reelsOf(0b000_000_001_010_100));
	}

	@Test
	void metersNeverCountDownWithoutAWin() {
		MeterModel m = new MeterModel();
		m.update(new long[] {1000, 2000, 10_000, 50_000}, 0);
		assertEquals(900, m.shown(0, 0));
		assertEquals(1000, m.shown(0, 400));
		m.update(new long[] {1010, 2000, 10_000, 50_000}, 1000);
		long prev = 0;
		for (int t = 1000; t <= 2100; t += 50) {
			long v = m.shown(0, t);
			assertTrue(v >= prev);
			prev = v;
		}
		m.update(new long[] {200, 2000, 10_000, 50_000}, 3000);
		assertTrue(m.showWon(0, 3100));
		assertEquals(1010, m.shown(0, 3100), "shows the old pool while WON");
		assertEquals(200, m.shown(0, 3000 + MeterModel.WON_MS + MeterModel.DROP_MS));
	}

	@Test
	void everyFreeSpinIsAReelPhaseAndTheFrameNeverShowsAWrongLandedWindow() {
		for (String name : List.of("ow_fs", "ne_fs", "end_fs", "ne_buy")) {
			PreviewTapes.Scenario s = PreviewTapes.get(name);
			Timeline tl = timeline(s, NORMAL, NORMAL);
			SlotScript script = new SlotScript(tl, s.def(), s.restStops(), null);
			int expected = s.tape().freeSpins().spins().size() + (s.tape().bought() ? 0 : 1);
			assertEquals(expected, script.phases().size(), name);
			SlotFrames.Sampler f = new SlotFrames.Sampler(script);
			for (SlotScript.Phase p : script.phases()) {
				for (int r = 0; r < 5; r++) {
					if (p.reels[r] == null) continue;
					// at each reel's stop the three rows are exactly the paid strip window (F2)
					f.sample(p.stopAt(r));
					for (int y = 0; y < 3; y++) assertEquals(p.landed[r * 3 + y], f.cells[r * 3 + y], name + " spin " + p.spin + " reel " + r);
				}
			}
		}
	}

	@Test
	void celebrationAndJackpotParameters() {
		assertEquals(2000, CelebrationPlan.jackpot(1).lengthMs());
		assertEquals(4000, CelebrationPlan.jackpot(4).lengthMs());
		assertEquals(0.30f, CelebrationPlan.jackpot(4).flashPeak());
		assertEquals(0, CelebrationPlan.flash(100, false, 0.3), 1e-12, "flashes off");
		assertTrue(CelebrationPlan.flash(0, true, 0.9) <= 0.30, "≤ 30 % alpha");
		assertEquals(0, CelebrationPlan.shake(100, true, 3), 1e-12, "reduce motion: no shake");
		assertEquals(1, CelebrationPlan.plateSlam(200), 1e-12);
		SoundPlan.Gate gate = new SoundPlan.Gate(CelebrationPlan.TICK_GAP_MS);
		int fired = 0;
		for (int ms = 0; ms < 1000; ms += 5) if (gate.tryFire(ms)) fired++;
		assertTrue(fired <= 15, "roll-up ticks ≤ 15/s: " + fired);
		SoundPlan.Budget budget = new SoundPlan.Budget(SoundPlan.MAX_PER_SECOND);
		int played = 0;
		for (int ms = 0; ms < 1000; ms++) if (budget.tryFire(ms)) played++;
		assertEquals(SoundPlan.MAX_PER_SECOND, played);
	}

	@Test
	void stickyReelsDoNotSpinAndStayUntilTheOutro() {
		PreviewTapes.Scenario s = PreviewTapes.get("end_maxwin");
		Timeline tl = timeline(s, NORMAL, NORMAL);
		SlotScript script = new SlotScript(tl, s.def(), s.restStops(), null);
		int sticky = 0;
		for (SlotScript.Phase p : script.phases()) {
			for (int r = 1; r <= 3; r++) if ((sticky & (1 << (r - 1))) != 0) assertEquals(null, p.reels[r], "sticky reel " + r + " spins");
			sticky = script.stickyMaskAt(p.lastStopMs + 1000);
		}
		assertTrue(sticky != 0);
		assertEquals(0, script.stickyMaskAt(tl.endMs()));
		List<SpinTape.FreeSpin> spins = s.tape().freeSpins().spins();
		assertEquals(spins.get(spins.size() - 1).stickyMaskAfter(), script.stickyMaskAt(script.phases().get(script.phases().size() - 1).lastStopMs + 900));
	}
}
