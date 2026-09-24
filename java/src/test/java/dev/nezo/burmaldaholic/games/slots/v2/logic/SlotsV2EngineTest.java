package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** SLOTS.md §15 pure-logic tests 1–3, 9–12 (strips, ways, tumbles, integer money, cap, anticipation, codec). */
class SlotsV2EngineTest {
	private static final int[][][] COUNTS = {
		{{0, 2, 3, 3, 3, 4, 4, 5, 5, 5, 6}, {4, 1, 0, 3, 3, 4, 4, 5, 5, 5, 6}, {4, 1, 2, 3, 3, 4, 4, 5, 5, 4, 5}, {4, 1, 0, 3, 3, 4, 4, 5, 5, 5, 6},
			{0, 2, 3, 3, 3, 4, 4, 5, 5, 5, 6}},
		{{0, 1, 2, 2, 3, 3, 3, 5, 5, 4, 4}, {2, 1, 2, 2, 2, 3, 3, 4, 4, 5, 4}, {2, 1, 3, 2, 2, 3, 3, 4, 4, 4, 4}, {2, 1, 2, 2, 2, 3, 3, 4, 4, 5, 4},
			{0, 1, 2, 2, 3, 3, 3, 5, 5, 4, 4}},
		{{0, 1, 0, 2, 3, 4, 5, 7, 7, 8, 8}, {1, 1, 2, 2, 3, 4, 5, 6, 6, 7, 8}, {1, 1, 3, 2, 3, 4, 4, 6, 7, 7, 7}, {1, 1, 2, 2, 3, 4, 5, 6, 6, 7, 8},
			{0, 1, 0, 2, 3, 4, 5, 7, 7, 8, 8}}};

	@Test
	void stripsAreAppendixA() {
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			for (int r = 0; r < 5; r++) {
				int[] c = new int[11];
				for (int x : d.strips()[r]) c[x]++;
				assertArrayEquals(COUNTS[m.ordinal()][r], c, m + " reel " + (r + 1));
				assertEquals(m.defaultStripLength, d.stripLength(r));
			}
			assertTrue(SlotDefaults.validateStrips(m, d.strips(), d.bonusReelsMask()).isEmpty(), m.id);
		}
		// a wild on reel 1 and a close scatter pair are rejected
		MachineDef o = SlotDefaults.def(Machine.OVERWORLD);
		int[][] bad = o.strips().clone();
		bad[0] = bad[0].clone();
		bad[0][0] = 0;
		bad[0][1] = 1;
		bad[0][2] = 1;
		List<String> errors = SlotDefaults.validateStrips(Machine.OVERWORLD, bad, o.bonusReelsMask());
		assertEquals(2, errors.size(), errors.toString());
	}

	private static int[] window(String cells) {
		String[] codes = SlotDefaults.codes(Machine.OVERWORLD);
		String[] rows = cells.trim().split("\\s+");
		int[] c = new int[15];
		for (int i = 0; i < 15; i++) c[i] = SlotDefaults.parseStrip(codes, rows[i])[0];
		return c;
	}

	@Test
	void waysHandBuilt() {
		MachineDef d = SlotDefaults.def(Machine.OVERWORLD);
		// reel-major: r1(top..bottom) r2 … — Diamond counts 2, 3 (one Wild), 1 → 6 ways, stops at reel 3
		Ways.Result r = Ways.evaluate(d, window("DI DI GO  DI WD DI  DI AP CA  AP CA WH  BE BE BE"), 0);
		assertEquals(1, r.wins().size());
		Ways.WayWin w = r.wins().getFirst();
		assertEquals(3, w.symbol());
		assertEquals(3, w.k());
		assertEquals(6, w.ways());
		assertEquals(24, w.payFifths()); // 0.8 × bet × 6 ways
		// several symbols at once; the Wild on reel 2 substitutes for both
		r = Ways.evaluate(d, window("DI AP CA  WD WH BE  DI AP GO  DI AP EM  DI AP EM"), 0);
		assertEquals(List.of(3, 7), r.wins().stream().map(Ways.WayWin::symbol).toList());
		assertEquals(20 + 4, r.payFifths()); // Diamond ×5 (1 way) 20 + Apple ×5 (1 way) 4
		// no wild-only ways; a wild on reel 1 would not count (never on the strips, checked anyway)
		r = Ways.evaluate(d, window("WD WD WD  WD WD WD  WD WD WD  AP CA WH  AP CA WH"), 0);
		assertEquals(0, r.payFifths());
		// scatters and bonus symbols
		r = Ways.evaluate(d, window("BN SC AP  SC CA WH  BN AP CA  AP CA WH  BN BE BE"), 0);
		assertEquals(2, r.scatters());
		assertTrue(r.bonusTriggered(d));
		// sticky mask forces reels 2–4 to wilds
		r = Ways.evaluate(SlotDefaults.def(Machine.END), new Window(new int[15]).cells(), 0b111);
		assertEquals(0, r.payFifths());
	}

	@Test
	void netherTumbleChainIsDeterministicAndMatchesFastEnumerator() {
		MachineDef d = SlotDefaults.def(Machine.NETHER);
		SplittableRandom rnd = new SplittableRandom(7);
		int longest = 0;
		for (int i = 0; i < 20_000; i++) {
			int[] st = new int[5];
			for (int r = 0; r < 5; r++) st[r] = rnd.nextInt(32);
			Tumble.Chain a = Tumble.run(d, st, d.ladder());
			Tumble.Chain b = Tumble.run(d, st, d.ladder());
			assertEquals(a.payFifths(), b.payFifths());
			assertEquals(a.finalWindow(), b.finalWindow());
			long sum = 0;
			for (Tumble.Step s : a.steps()) sum += s.payFifths();
			assertEquals(sum, a.payFifths());
			assertEquals(0, a.steps().getLast().result().payFifths());
			longest = Math.max(longest, a.tumbles());
		}
		assertTrue(longest >= 3);
	}

	/** §15 test 9: integer money, jackpot floor rule, pool conservation, at every ladder bet. */
	@Test
	void drawsAreIntegerExactAndConservePools() {
		long[][] bets = {{5, 10, 20, 50, 100}, {10, 20, 50, 100, 250, 500}, {50, 100, 250, 500, 1000, 2500, 5000}};
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			MachineDef.Features f = d.features();
			for (long bet : bets[m.ordinal()]) {
				SlotRng rng = SlotRng.seeded((int) (bet * 31 + m.ordinal()));
				long[] pools = {0, f.seedChips(1) + 777, f.seedChips(2) + 5_555, f.seedChips(3) + 12_345, f.seedChips(4) + 99_999};
				for (int i = 0; i < 3_000; i++) {
					long[] before = pools.clone();
					boolean buy = d.buyPriceFifths() > 0 && i % 50 == 0;
					SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, bet, buy, false, pools), rng);
					assertEquals(0, t.totalFifths() * bet % 5, "integer chips");
					assertTrue(t.totalFifths() <= d.capMultiple() * 5L);
					long[] after = pools.clone();
					SlotDraw.applyAwards(d, t, after);
					long[] replay = before.clone();
					for (SpinTape.JackpotAward j : t.jackpots()) {
						long seed = f.seedChips(j.tier());
						long expect = Math.floorDiv((replay[j.tier()]) * Math.min(bet, f.jackpotRef()), f.jackpotRef());
						assertEquals(expect, j.chips(), "floor((seed + increment) × r)");
						long inc = replay[j.tier()] - seed;
						long incAfter = Jackpots.incrementAfter(inc, bet, f.jackpotRef());
						long minted = j.chips() - (inc - incAfter);
						assertTrue(minted >= 0 && minted <= seed, "bank mints at most the seed share");
						replay[j.tier()] = seed + incAfter;
					}
					assertArrayEquals(replay, after);
					pools = after;
					SpinTape back = TapeCodec.decode(TapeCodec.encode(t));
					assertEquals(t, back);
				}
			}
		}
	}

	@Test
	void ownedMachinesPayFixedJackpotsInsideTheCap() {
		MachineDef d = SlotDefaults.def(Machine.NETHER);
		SlotRng rng = SlotRng.seeded(99);
		int seen = 0;
		for (int i = 0; i < 200_000 && seen < 3; i++) {
			SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, 20, false, true, null), rng);
			if (t.jackpots().isEmpty()) continue;
			seen++;
			assertEquals(0, t.progressiveChips());
			for (SpinTape.JackpotAward j : t.jackpots()) {
				assertTrue(j.owned());
				assertEquals(d.features().ownedMult()[j.tier() - 1] * 20L, j.chips());
			}
			assertEquals(t.totalChips(), t.payoutChips());
		}
		assertTrue(seen > 0);
		assertEquals(2000L * 20, SlotDraw.reservation(d, 20));
	}

	/** §15 test 10: forced tapes above the cap end the feature at the cap and set MAX WIN. */
	@Test
	void maxWinCapEndsTheFeature() {
		MachineDef d = SlotDefaults.def(Machine.END);
		MachineDef tiny = new MachineDef(d.machine(), d.codes(), d.roles(), d.strips(), d.paysFifths(), d.scatterFifths(), d.bonusReelsMask(),
			d.freeSpins(), d.retrigger(), d.fsCap(), d.fsMultiplier(), d.ladder(), d.ladderFree(), 3, d.buyPriceFifths(), d.features());
		SlotRng rng = SlotRng.seeded(5);
		int hits = 0;
		for (int i = 0; i < 200 && hits < 20; i++) {
			SpinTape t = SlotDraw.draw(new SlotDraw.Request(tiny, 100, true, false, new long[5]), rng);
			if (!t.capHit()) continue;
			hits++;
			assertEquals(15, t.totalFifths());
			long run = 0;
			List<SpinTape.FreeSpin> spins = t.freeSpins().spins();
			for (int k = 0; k < spins.size(); k++) {
				if (k < spins.size() - 1) assertTrue(run + spins.get(k).payFifths() < 15, "feature continued after the cap");
				run += spins.get(k).payFifths();
			}
			assertTrue(run >= 15);
			Timeline tl = SlotTimeline.build(t, tiny, TimingProfile.SHARED, TimingProfile.SHARED, 1);
			assertTrue(tl.beats().stream().anyMatch(b -> b.kind().equals(SlotTimeline.MAX_WIN)));
		}
		assertTrue(hits > 0);
	}

	/** Independent statement of SLOTS.md §10.3 used by the property test. */
	private static boolean conditionRef(MachineDef d, Window w, int k) {
		int sc = 0;
		int coins = 0;
		for (int r = 0; r <= k; r++) {
			for (int y = 0; y < 3; y++) {
				if (w.at(r, y) == d.scatter()) sc++;
				if (w.at(r, y) == d.coin()) coins++;
			}
		}
		if (sc >= 2 && k < 4) return true; // every default strip has a scatter
		boolean has0 = hasBonus(d, w, 0), has1 = hasBonus(d, w, 1), has2 = hasBonus(d, w, 2);
		if (d.machine() == Machine.OVERWORLD && k >= 2 && k < 4 && has0 && has2) return true;
		if (d.machine() == Machine.END && k == 2 && has1 && has2) return true;
		return d.machine() == Machine.NETHER && coins >= 4 && k < 4 && coins + 2 * (4 - k) >= 6;
	}

	private static boolean hasBonus(MachineDef d, Window w, int r) {
		for (int y = 0; y < 3; y++) if (w.at(r, y) == d.bonus()) return true;
		return false;
	}

	/** §15 test 12 / slots.md F3: anticipation happens iff the condition holds on already-stopped reels. */
	@Test
	void anticipationIsHonest() {
		SplittableRandom rnd = new SplittableRandom(3);
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			int anticipated = 0;
			for (int i = 0; i < 200_000; i++) {
				int[] st = new int[5];
				for (int r = 0; r < 5; r++) st[r] = rnd.nextInt(d.stripLength(r));
				Window w = Window.fromStops(d, st);
				int[] times = Anticipation.stopTimes(d, w, true);
				assertEquals(600, times[0]);
				for (int r = 1; r < 5; r++) {
					int gap = times[r] - times[r - 1];
					boolean expect = conditionRef(d, w, r - 1);
					assertEquals(expect ? 1000 : 150, gap, m + " " + java.util.Arrays.toString(st) + " reel " + (r + 1));
					if (expect) anticipated++;
				}
				assertArrayEquals(Anticipation.baseStopTimes(), Anticipation.stopTimes(d, w, false));
			}
			assertTrue(anticipated > 0, m.id);
		}
	}

	@Test
	void timelineLandsOnThePaidWindowAndGatesTheResult() {
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			SlotRng rng = SlotRng.seeded(11 + m.ordinal());
			long[] pools = new long[5];
			for (int t = 1; t <= 4; t++) pools[t] = d.features().seedChips(t);
			for (int i = 0; i < 2_000; i++) {
				SpinTape tape = SlotDraw.draw(new SlotDraw.Request(d, 100, false, false, pools), rng);
				Timeline tl = SlotTimeline.build(tape, d, TimingProfile.SHARED, TimingProfile.SHARED, i);
				Timeline turbo = SlotTimeline.build(tape, d, TimingProfile.SHARED.withSpeed(200), TimingProfile.SHARED, i);
				List<Beat> lands = tl.beats().stream().filter(b -> b.kind().equals(SlotTimeline.REEL_LAND) && b.group() == 0).toList();
				assertEquals(5, lands.size());
				for (Beat b : lands) assertEquals(tape.stops()[b.lane()], b.arg(1), "reel lands on the drawn stop");
				int gate = tl.sharedEndMs();
				for (Beat b : tl.beats()) {
					if (b.clock() == Clock.LOCAL) assertTrue(b.at() >= gate, "local beat before the gate");
				}
				Beat end = tl.beats().getLast();
				assertEquals(SlotTimeline.END, end.kind());
				assertTrue(turbo.sharedEndMs() <= gate / 2 + 5 * 30, "turbo halves shared time");
				assertNotNull(tl.toCanonicalJson());
			}
		}
	}

	@Test
	void worstCaseTapeFitsThePersistenceBudget() {
		List<SpinTape.FreeSpin> spins = new ArrayList<>();
		for (int i = 0; i < 40; i++) spins.add(new SpinTape.FreeSpin(new int[] {44, 44, 44, 44, 44}, 7, true, 25_000));
		SpinTape t = new SpinTape(Machine.END, 5000, false, new int[] {44, 44, 44, 44, 44}, new SpinTape.FreeSpins(14, spins, 40 * 25_000L),
			null, null, new SpinTape.Wheel(new int[] {19, 15, 11}),
			List.of(new SpinTape.JackpotAward(4, 99_999_999_999L, false), new SpinTape.JackpotAward(3, 9_999_999, false)), 25_000, true);
		String s = TapeCodec.encode(t);
		assertTrue(s.length() < 1200, s.length() + " chars");
		assertEquals(t, TapeCodec.decode(s));
		assertFalse(s.contains(" "));
	}
}
