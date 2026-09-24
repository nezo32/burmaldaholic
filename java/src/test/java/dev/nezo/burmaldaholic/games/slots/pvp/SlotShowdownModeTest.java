package dev.nezo.burmaldaholic.games.slots.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.slots.logic.Paylines;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Params;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Rules;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Tape;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PVP.md §16.3 S1–S12 and §16.8 B1/B2 for Slot Showdown (same vectors as Bedrock B-M3). */
class SlotShowdownModeTest {
	private static final SlotShowdownMode MODE = SlotShowdownMode.withDefaults();
	/** Rule switches for the hand-built vectors: HOT and Underdog off unless the vector is about them. */
	private static final Rules PLAIN = new Rules(false, false, true, true, 500, 100);
	/** A row / grid that scores nothing on any payline. */
	private static final Symbol[] BLANK_ROW = {Symbol.APPLE, Symbol.EMERALD, Symbol.DIAMOND};

	// ---- helpers --------------------------------------------------------------------------------

	/** Grid with the given rows (null = blank row). rows[0] = top, [1] = middle, [2] = bottom. */
	private static int[] grid(Symbol[] top, Symbol[] middle, Symbol[] bottom) {
		Symbol[][] rows = {top == null ? BLANK_ROW : top, middle == null ? BLANK_ROW : middle, bottom == null ? BLANK_ROW : bottom};
		int[] g = new int[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				g[r * 3 + c] = rows[r][c].ordinal();
			}
		}
		return g;
	}

	private static int[] middle(Symbol a, Symbol b, Symbol c) {
		return grid(null, new Symbol[] {a, b, c}, null);
	}

	private static int[] blank() {
		return grid(null, null, null);
	}

	private static Symbol[] three(Symbol s) {
		return new Symbol[] {s, s, s};
	}

	/** 3-line table with the given 3-of-a-kind pays (all symbols present). */
	private static SlotTable custom(Map<String, Double> pays) {
		Map<String, Integer> w = Map.ofEntries(Map.entry("berries", 10), Map.entry("apple", 10), Map.entry("golden_carrot", 10),
			Map.entry("emerald", 10), Map.entry("diamond", 10), Map.entry("seven", 10), Map.entry("wild", 5), Map.entry("creeper", 5),
			Map.entry("pearl", 5), Map.entry("clock", 5), Map.entry("star", 5));
		return SlotTable.of(w, pays, new double[] {2, 3}, 3, false);
	}

	/** grids[player][round]; seat order = identity unless given. */
	private static Tape tape(int[] seatOrder, int[][][] grids) {
		int[] hot = new int[grids[0].length];
		java.util.Arrays.fill(hot, Symbol.BERRIES.ordinal());
		return new Tape(seatOrder, hot, grids);
	}

	private static long[] totals(Tape t, SlotTable table, Rules rules) {
		return ShowdownScoring.play(t, table, rules).totals();
	}

	// ---- S1 / S2: reference numbers ---------------------------------------------------------------

	@Test
	void s1MeanPointsExact() {
		double[] want = {0.897598, 2.793399, 4.746735};
		Tier[] tiers = Tier.values();
		for (int i = 0; i < tiers.length; i++) {
			SlotTable t = SlotTable.defaults(tiers[i]);
			double mean = ShowdownScoring.meanLinePoints(t, Rules.DEFAULTS) * t.lines();
			assertEquals(want[i], mean, 1e-6, tiers[i].id());
		}
	}

	@Test
	void s2KaboomProbabilityExact() {
		double[] want = {0.003375, 0.001535, 0.001080};
		Tier[] tiers = Tier.values();
		for (int i = 0; i < tiers.length; i++) {
			SlotTable t = SlotTable.defaults(tiers[i]);
			double p = exactKaboom(t);
			// the spec rounds Netherite (exact 0.0010745: five overlapping lines) to 0.001080
			assertEquals(want[i], p, want[i] * 0.01, tiers[i].id());
		}
	}

	/** Enumerates which of the 9 cells are Creeper / TNT (a line of three Creepers or three TNT). */
	private static double exactKaboom(SlotTable t) {
		double total = 0;
		for (Symbol bomb : new Symbol[] {Symbol.CREEPER, Symbol.TNT}) {
			if (t.weight(bomb) > 0) {
				// only one bomb symbol per default tier, so the two cases never overlap
				double q = t.weight(bomb) / (double) t.totalWeight();
				for (int mask = 0; mask < 512; mask++) {
					boolean hit = false;
					for (int line = 1; line <= t.lines(); line++) {
						boolean all = true;
						for (int c = 0; c < 3; c++) {
							all &= (mask >> (Paylines.row(line, c) * 3 + c) & 1) == 1;
						}
						hit |= all;
					}
					if (hit) {
						int k = Integer.bitCount(mask);
						total += Math.pow(q, k) * Math.pow(1 - q, 9 - k);
					}
				}
			}
		}
		return total;
	}

	@Test
	void s1s2MonteCarlo() {
		double[] mean = {0.897598, 2.793399, 4.746735};
		double[] kaboom = {0.003375, 0.001535, 0.001080};
		Tier[] tiers = Tier.values();
		SplittableRandom rnd = new SplittableRandom(0x5107_5A0L);
		int spins = 10_000_000;
		for (int i = 0; i < tiers.length; i++) {
			SlotTable t = SlotTable.defaults(tiers[i]);
			long points = 0;
			long booms = 0;
			int[] cells = new int[9];
			for (int s = 0; s < spins; s++) {
				for (int c = 0; c < 9; c++) {
					cells[c] = SlotEngine.drawSymbol(t, rnd::nextInt).ordinal();
				}
				ShowdownScoring.SpinScore sc = ShowdownScoring.spin(cells, t, Rules.DEFAULTS, null);
				points += sc.basePoints();
				if (sc.kaboom()) {
					booms++;
				}
			}
			assertEquals(mean[i], points / (double) spins, mean[i] * 0.003, tiers[i].id() + " mean");
			assertEquals(kaboom[i], booms / (double) spins, kaboom[i] * 0.05, tiers[i].id() + " kaboom");
		}
	}

	// ---- S3 / S4: line points and HOT --------------------------------------------------------------

	@Test
	void s3CopperBerries() {
		SlotTable copper = SlotTable.defaults(Tier.COPPER);
		int[] g = middle(Symbol.BERRIES, Symbol.BERRIES, Symbol.APPLE);
		assertEquals(3, ShowdownScoring.spin(g, copper, Rules.DEFAULTS, Symbol.APPLE).basePoints());
		assertEquals(6, ShowdownScoring.spin(g, copper, Rules.DEFAULTS, Symbol.BERRIES).basePoints());
		assertEquals(3, ShowdownScoring.spin(g, copper, PLAIN, Symbol.BERRIES).basePoints(), "HOT switched off");
	}

	@Test
	void s4GoldWildDiamond() {
		SlotTable gold = SlotTable.defaults(Tier.GOLD);
		int[] g = middle(Symbol.WILD, Symbol.DIAMOND, Symbol.DIAMOND);
		assertEquals(50, ShowdownScoring.spin(g, gold, Rules.DEFAULTS, Symbol.EMERALD).basePoints());
		assertEquals(100, ShowdownScoring.spin(g, gold, Rules.DEFAULTS, Symbol.DIAMOND).basePoints());
		assertEquals(50, ShowdownScoring.spin(g, gold, Rules.DEFAULTS, Symbol.APPLE).basePoints());
	}

	@Test
	void specialPoints() {
		SlotTable neth = SlotTable.defaults(Tier.NETHERITE);
		assertEquals(10, ShowdownScoring.spin(middle(Symbol.PEARL, Symbol.PEARL, Symbol.PEARL), neth, Rules.DEFAULTS, null).basePoints());
		assertEquals(50, ShowdownScoring.spin(middle(Symbol.CLOCK, Symbol.CLOCK, Symbol.CLOCK), neth, Rules.DEFAULTS, null).basePoints());
		assertEquals(500, ShowdownScoring.spin(middle(Symbol.STAR, Symbol.STAR, Symbol.STAR), neth, Rules.DEFAULTS, null).basePoints());
		assertEquals(250, ShowdownScoring.spin(middle(Symbol.WILD, Symbol.WILD, Symbol.WILD), neth, Rules.DEFAULTS, Symbol.SEVEN).basePoints(),
			"three Wilds are never HOT");
		ShowdownScoring.SpinScore tnt = ShowdownScoring.spin(middle(Symbol.TNT, Symbol.TNT, Symbol.TNT), neth, Rules.DEFAULTS, null);
		assertEquals(0, tnt.basePoints());
		assertTrue(tnt.kaboom());
		assertEquals(0, tnt.payingLines());
		Rules noBoom = new Rules(true, true, false, true, 500, 100);
		assertFalse(ShowdownScoring.spin(middle(Symbol.TNT, Symbol.TNT, Symbol.TNT), neth, noBoom, null).kaboom());
	}

	// ---- S5 KABOOM ---------------------------------------------------------------------------------

	@Test
	void s5Kaboom() {
		SlotTable t = custom(Map.of("apple", 20.0, "emerald", 57.0));
		int[] r0 = middle(Symbol.EMERALD, Symbol.EMERALD, Symbol.EMERALD);
		int[] r1 = grid(three(Symbol.APPLE), three(Symbol.CREEPER), null);
		Tape tp = tape(new int[] {0, 1}, new int[][][] {{r0, r1, blank()}, {blank(), blank(), blank()}});
		ShowdownScoring.Match m = ShowdownScoring.play(tp, t, PLAIN);
		assertEquals(57, m.rounds().get(0).totals()[0]);
		assertEquals(48, m.rounds().get(1).totals()[0]);
		PvpEvent boom = m.rounds().get(1).events().get(0);
		assertEquals(ShowdownScoring.KABOOM, boom.kind());
		assertEquals(57L, boom.data().get("before"));
		assertEquals(28L, boom.data().get("after"));
		// several KABOOM lines in one spin halve once
		int[] two = grid(three(Symbol.CREEPER), three(Symbol.CREEPER), null);
		Tape tp2 = tape(new int[] {0, 1}, new int[][][] {{r0, two}, {blank(), blank()}});
		assertEquals(28, totals(tp2, t, PLAIN)[0]);
		// switched off: scores 0, no halving
		Rules off = new Rules(false, false, false, true, 500, 100);
		assertEquals(77, totals(tp, t, off)[0]);
	}

	// ---- S6 / S7 SWAP ------------------------------------------------------------------------------

	private static final SlotTable SWAP_TABLE = custom(Map.of("apple", 10.0, "seven", 90.0, "golden_carrot", 40.0));

	/** Round 0 totals A 10, B 90, C 40; round 1: the given pearl players score a Pearl line only. */
	private static Tape swapTape(int[] seatOrder, boolean aPearl, boolean cPearl) {
		int[] pearl = middle(Symbol.PEARL, Symbol.PEARL, Symbol.PEARL);
		return tape(seatOrder, new int[][][] {
			{middle(Symbol.APPLE, Symbol.APPLE, Symbol.APPLE), aPearl ? pearl : blank(), blank()},
			{middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN), blank(), blank()},
			{middle(Symbol.GOLDEN_CARROT, Symbol.GOLDEN_CARROT, Symbol.GOLDEN_CARROT), cPearl ? pearl : blank(), blank()}});
	}

	@Test
	void s6Swap() {
		ShowdownScoring.Match m = ShowdownScoring.play(swapTape(new int[] {0, 1, 2}, true, false), SWAP_TABLE, PLAIN);
		assertArrayEquals(new long[] {10, 90, 40}, m.rounds().get(0).totals());
		assertArrayEquals(new long[] {10, 0, 0}, m.rounds().get(1).spinPoints());
		assertArrayEquals(new long[] {90, 20, 40}, m.rounds().get(1).totals());
		PvpEvent swap = m.rounds().get(1).events().get(0);
		assertEquals(ShowdownScoring.SWAP, swap.kind());
		assertEquals(0, swap.seat());
		assertEquals(1L, swap.data().get("other"));
		assertEquals(20L, swap.data().get("from"));
		assertEquals(90L, swap.data().get("to"));
		// the leader itself rolling Pearls swaps with nobody
		int[] pearl = middle(Symbol.PEARL, Symbol.PEARL, Symbol.PEARL);
		Tape leader = tape(new int[] {0, 1}, new int[][][] {{middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN), pearl}, {blank(), blank()}});
		assertArrayEquals(new long[] {100, 0}, totals(leader, SWAP_TABLE, PLAIN));
		// switched off: 10 points only
		Rules off = new Rules(false, false, true, false, 500, 100);
		assertArrayEquals(new long[] {20, 90, 40}, totals(swapTape(new int[] {0, 1, 2}, true, false), SWAP_TABLE, off));
	}

	@Test
	void s7TwoSwapsSequential() {
		// seat order A, B, C: A 20 ⇄ B 90 → A 90, B 20; then C 50 ⇄ leader A 90
		assertArrayEquals(new long[] {50, 20, 90}, totals(swapTape(new int[] {0, 1, 2}, true, true), SWAP_TABLE, PLAIN));
		// seat order C, A, B: C 50 ⇄ B 90 → C 90, B 50; then A 20 ⇄ leader C 90
		assertArrayEquals(new long[] {90, 50, 20}, totals(swapTape(new int[] {2, 0, 1}, true, true), SWAP_TABLE, PLAIN));
	}

	@Test
	void swapLeaderTieUsesSeatOrder() {
		// B and C tied on 90; A swaps with the earliest in seat order
		SlotTable t = custom(Map.of("seven", 90.0));
		int[] seven = middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN);
		int[] pearl = middle(Symbol.PEARL, Symbol.PEARL, Symbol.PEARL);
		int[][][] g = {{blank(), pearl}, {seven, blank()}, {seven, blank()}};
		assertArrayEquals(new long[] {90, 90, 10}, totals(tape(new int[] {0, 2, 1}, g), t, PLAIN));
		assertArrayEquals(new long[] {90, 10, 90}, totals(tape(new int[] {1, 0, 2}, g), t, PLAIN));
	}

	// ---- S8 TIME WARP ------------------------------------------------------------------------------

	@Test
	void s8TimeWarp() {
		SlotTable neth = SlotTable.defaults(Tier.NETHERITE);
		int[] clock = middle(Symbol.CLOCK, Symbol.CLOCK, Symbol.CLOCK);
		int[] diamond = middle(Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND);
		Tape t = tape(new int[] {0, 1}, new int[][][] {{blank(), clock, diamond}, {blank(), blank(), blank()}});
		ShowdownScoring.Match m = ShowdownScoring.play(t, neth, PLAIN);
		assertEquals(50, m.rounds().get(1).spinPoints()[0]);
		assertEquals(100, m.rounds().get(2).spinPoints()[0]);
		assertEquals(2, m.rounds().get(2).multiplier()[0]);
		assertTrue(m.rounds().get(1).events().stream().anyMatch(e -> e.kind().equals(ShowdownScoring.TIME_WARP)));
		// warp is used once
		Tape t4 = tape(new int[] {0, 1}, new int[][][] {{clock, diamond, diamond, blank()}, {blank(), blank(), blank(), blank()}});
		ShowdownScoring.Match m4 = ShowdownScoring.play(t4, neth, PLAIN);
		assertEquals(100, m4.rounds().get(1).spinPoints()[0]);
		assertEquals(50, m4.rounds().get(2).spinPoints()[0]);
		// on the last spin: no effect, no event
		Tape last = tape(new int[] {0, 1}, new int[][][] {{blank(), clock}, {blank(), blank()}});
		ShowdownScoring.Match ml = ShowdownScoring.play(last, neth, PLAIN);
		assertTrue(ml.rounds().get(1).events().stream().noneMatch(e -> e.kind().equals(ShowdownScoring.TIME_WARP)));
		assertEquals(50, ml.totals()[0]);
	}

	// ---- S9 Underdog -------------------------------------------------------------------------------

	@Test
	void s9Underdog() {
		assertArrayEquals(new int[] {0, 1}, ShowdownScoring.underdogs(new long[] {12, 12, 30}));
		assertArrayEquals(new int[] {}, ShowdownScoring.underdogs(new long[] {20, 20, 20}));
		// in play: totals before the final [12, 12, 30]; the 12s score ×2 on the final spin
		SlotTable t = custom(Map.of("apple", 12.0, "seven", 30.0, "diamond", 5.0));
		int[] d = middle(Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND);
		Tape tp = tape(new int[] {0, 1, 2}, new int[][][] {
			{middle(Symbol.APPLE, Symbol.APPLE, Symbol.APPLE), d},
			{middle(Symbol.APPLE, Symbol.APPLE, Symbol.APPLE), d},
			{middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN), d}});
		Rules underdog = new Rules(false, true, true, true, 500, 100);
		ShowdownScoring.Match m = ShowdownScoring.play(tp, t, underdog);
		assertArrayEquals(new int[] {0, 1}, m.rounds().get(1).underdogs());
		assertArrayEquals(new long[] {10, 10, 5}, m.rounds().get(1).spinPoints());
		assertArrayEquals(new long[] {22, 22, 35}, m.totals());
		assertArrayEquals(new long[] {17, 17, 35}, totals(tp, t, PLAIN), "boost switched off");
		// multipliers stack: TIME WARP × Underdog
		SlotTable neth = SlotTable.defaults(Tier.NETHERITE);
		int[] clock = middle(Symbol.CLOCK, Symbol.CLOCK, Symbol.CLOCK);
		int[] seven = middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN);
		Tape stack = tape(new int[] {0, 1}, new int[][][] {{clock, middle(Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND)}, {seven, blank()}});
		ShowdownScoring.Match ms = ShowdownScoring.play(stack, neth, underdog);
		assertEquals(4, ms.rounds().get(1).multiplier()[0]);
		assertEquals(200, ms.rounds().get(1).spinPoints()[0]);
	}

	// ---- S10 tie-breaks ----------------------------------------------------------------------------

	@Test
	void s10TieBreaks() {
		SlotTable t = custom(Map.of("apple", 5.0, "golden_carrot", 10.0, "emerald", 50.0));
		int[] twoFives = grid(three(Symbol.APPLE), three(Symbol.APPLE), null);
		int[] ten = middle(Symbol.GOLDEN_CARROT, Symbol.GOLDEN_CARROT, Symbol.GOLDEN_CARROT);
		// equal totals 10: A 2 lines vs B 1 line → A
		Outcome o = ShowdownScoring.play(tape(new int[] {1, 0}, new int[][][] {{twoFives}, {ten}}), t, PLAIN).outcome();
		assertArrayEquals(new int[] {0}, o.winners());
		assertArrayEquals(new int[] {0, 1}, o.rankOrder());
		// equal totals 60 and lines 2: best spin 60 vs 50 → A
		int[] fiftyTen = grid(three(Symbol.EMERALD), three(Symbol.GOLDEN_CARROT), null);
		int[] fifty = middle(Symbol.EMERALD, Symbol.EMERALD, Symbol.EMERALD);
		Outcome o2 = ShowdownScoring.play(tape(new int[] {1, 0}, new int[][][] {{fiftyTen, blank()}, {fifty, ten}}), t, PLAIN).outcome();
		assertArrayEquals(new long[] {60, 60}, o2.points());
		assertArrayEquals(new int[] {0}, o2.winners());
		// all equal → split; rank order ties follow the seat order
		Outcome o3 = ShowdownScoring.play(tape(new int[] {2, 0, 1}, new int[][][] {{fifty}, {fifty}, {blank()}}), t, PLAIN).outcome();
		assertArrayEquals(new int[] {0, 1}, o3.winners());
		assertArrayEquals(new int[] {0, 1, 2}, o3.rankOrder());
		assertArrayEquals(new int[] {2, 0, 1}, o3.seatOrder());
	}

	// ---- S11 / B2 fairness, B1 RNG independence ---------------------------------------------------------

	@Test
	void s11FairnessMonteCarlo() {
		SplittableRandom seed = new SplittableRandom(20260924L);
		int matches = 200_000;
		for (Tier tier : Tier.values()) {
			for (int n : new int[] {2, 3, 6}) {
				Params p = MODE.defaults(tier);
				PvpRng rng = PvpRng.of(seed.split());
				double[] share = new double[n];
				long[] stakes = new long[n];
				java.util.Arrays.fill(stakes, 100);
				for (int k = 0; k < matches; k++) {
					Outcome o = MODE.score(MODE.draw(rng, n, p), stakes, p);
					for (int w : o.winners()) {
						share[w] += 1.0 / o.winners().length;
					}
				}
				for (int i = 0; i < n; i++) {
					assertEquals(1.0 / n, share[i] / matches, 0.004, tier.id() + " n=" + n + " seat " + i);
				}
			}
		}
	}

	@Test
	void b1DrawNeverDependsOnBots() {
		Params p = MODE.defaults(Tier.GOLD);
		Tape a = MODE.draw(PvpRng.of(new SplittableRandom(42)), 4, p);
		Tape b = MODE.draw(PvpRng.of(new SplittableRandom(42)), 4, p);
		assertEquals(MODE.encodeTape(a), MODE.encodeTape(b));
		Outcome oa = MODE.score(a, new long[] {100, 100, 100, 100}, p);
		Outcome ob = MODE.score(b, new long[] {100, 100, 100, 100}, p);
		assertArrayEquals(oa.points(), ob.points());
		assertArrayEquals(oa.winners(), ob.winners());
		assertEquals(oa.events(), ob.events());
	}

	@Test
	void conservationWithSplits() {
		PvpRng rng = PvpRng.of(new SplittableRandom(7));
		Params p = MODE.defaults(Tier.COPPER);
		for (int k = 0; k < 20_000; k++) {
			int n = 2 + k % 5;
			long[] stakes = new long[n];
			java.util.Arrays.fill(stakes, 10 + k % 97);
			Outcome o = MODE.score(MODE.draw(rng, n, p), stakes, p);
			long pot = PvpMath.pot(stakes);
			long rake = PvpMath.rake(pot, 300);
			long[] pay = PvpMath.split(pot - rake, o.winners(), o.seatOrder(), n);
			long sum = 0;
			for (long x : pay) {
				sum += x;
			}
			assertEquals(pot - rake, sum);
			assertEquals(n, o.rankOrder().length);
		}
	}

	// ---- S12 tape size + codecs -----------------------------------------------------------------------

	@Test
	void s12TapeSizeAndRoundTrip() {
		Params p = new Params("netherite", 10, Rules.DEFAULTS);
		Tape t = MODE.draw(PvpRng.of(new SplittableRandom(99)), 6, p);
		JsonElement json = MODE.encodeTape(t);
		assertTrue(json.toString().length() <= 700, "tape json " + json.toString().length() + " chars");
		Tape back = MODE.decodeTape(json);
		assertArrayEquals(t.seatOrder(), back.seatOrder());
		assertArrayEquals(t.hot(), back.hot());
		assertTrue(java.util.Arrays.deepEquals(t.grids(), back.grids()));
		assertEquals(json, MODE.encodeTape(back));
	}

	@Test
	void paramsRoundTripAndValidate() {
		Params p = new Params("gold", 10, new Rules(false, true, false, true, 123, 60));
		assertEquals(p, MODE.decodeParams(MODE.encodeParams(p)));
		assertNull(MODE.validate(p));
		assertEquals(5, MODE.defaults().spins());
		assertNotNull(MODE.validate(new Params("gold", 4, Rules.DEFAULTS)));
		assertNotNull(MODE.validate(new Params("iron", 5, Rules.DEFAULTS)));
		Params legacy = MODE.decodeParams(com.google.gson.JsonParser.parseString("{\"tier\":\"copper\",\"spins\":3}"));
		assertEquals(Rules.DEFAULTS, legacy.rules());
	}

	@Test
	void hotSymbolsAreRegularAndUniform() {
		Params p = new Params("copper", 10, Rules.DEFAULTS);
		PvpRng rng = PvpRng.of(new SplittableRandom(3));
		int[] counts = new int[Symbol.count()];
		int draws = 0;
		for (int k = 0; k < 30_000; k++) {
			for (int h : MODE.draw(rng, 2, p).hot()) {
				counts[h]++;
				draws++;
			}
		}
		for (Symbol s : Symbol.values()) {
			if (ShowdownScoring.HOT_CANDIDATES.contains(s)) {
				assertEquals(1.0 / 6, counts[s.ordinal()] / (double) draws, 0.004, s.id());
			} else {
				assertEquals(0, counts[s.ordinal()], s.id());
			}
		}
	}

	// ---- timeline -------------------------------------------------------------------------------------

	@Test
	void timelineRevealsOnlyTheCurrentRound() {
		Params p = new Params("gold", 3, Rules.DEFAULTS);
		Tape t = MODE.draw(PvpRng.of(new SplittableRandom(11)), 3, p);
		Outcome o = MODE.score(t, new long[] {50, 50, 50}, p);
		List<Step> steps = MODE.timeline(t, o, p);
		int waits = 0, spins = 0, scores = 0;
		for (Step s : steps) {
			switch (s.kind()) {
				case "round_wait" -> {
					waits++;
					assertTrue(s.waitForAll());
					assertEquals(100, s.ticks());
					assertEquals(t.hot()[s.round()], s.data().get("hot").getAsInt());
				}
				case "spin" -> {
					spins++;
					for (int i = 0; i < 3; i++) {
						int[] g = new com.google.gson.Gson().fromJson(s.data().getAsJsonArray("grids").get(i), int[].class);
						assertArrayEquals(t.grids()[i][s.round()], g);
					}
				}
				case "score" -> {
					scores++;
					assertTrue(s.round() < 2, "no score step after the final spin");
					assertFalse(s.data().toString().contains("\"grids\""));
				}
				case "underdog" -> assertEquals(2, s.round());
				default -> throw new AssertionError(s.kind());
			}
		}
		assertEquals(3, waits);
		assertEquals(3, spins);
		assertEquals(2, scores);
		// final totals are never in a step
		ShowdownScoring.Match m = ShowdownScoring.play(t, MODE.table(Tier.GOLD), p.rules());
		JsonObject lastScore = steps.stream().filter(s -> s.kind().equals("score")).reduce((a, b) -> b).orElseThrow().data();
		assertEquals(m.rounds().get(1).totals()[0], lastScore.getAsJsonArray("totals").get(0).getAsLong());
	}

	@Test
	void outcomeEventsCarryKaboomForPhoenix() {
		SlotTable t = custom(Map.of("emerald", 57.0, "seven", 90.0));
		int[] em = middle(Symbol.EMERALD, Symbol.EMERALD, Symbol.EMERALD);
		int[] boom = middle(Symbol.CREEPER, Symbol.CREEPER, Symbol.CREEPER);
		int[] seven = middle(Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN);
		Outcome o = ShowdownScoring.play(tape(new int[] {0, 1}, new int[][][] {{em, boom, seven}, {em, blank(), blank()}}), t, PLAIN).outcome();
		assertArrayEquals(new int[] {0}, o.winners());
		assertTrue(ShowdownScoring.phoenix(o, 0));
		assertFalse(ShowdownScoring.phoenix(o, 1));
	}
}
