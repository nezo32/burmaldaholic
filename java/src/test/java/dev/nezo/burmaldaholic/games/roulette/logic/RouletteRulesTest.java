package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RouletteRulesTest {
	@Test
	void wheelHas18Red18BlackOneZeroAndOrderIsAPermutation() {
		int red = 0;
		int black = 0;
		for (int n = 1; n <= 36; n++) {
			if (Wheel.color(n) == Wheel.Color.RED) {
				red++;
			} else {
				black++;
			}
		}
		assertEquals(18, red);
		assertEquals(18, black);
		assertEquals(Wheel.Color.GREEN, Wheel.color(0));
		assertEquals(37, new HashSet<>(Wheel.ORDER).size());
		assertEquals(0, Wheel.ORDER.get(0));
		assertEquals(32, Wheel.ORDER.get(1));
		assertEquals(26, Wheel.ORDER.get(36));
		for (int n = 0; n <= 36; n++) {
			assertEquals(n, Wheel.ORDER.get(Wheel.wheelIndex(n)));
		}
	}

	@Test
	void spotCountsPerType() {
		Map<BetType, Integer> expected = new EnumMap<>(BetType.class);
		expected.put(BetType.STRAIGHT, 37);
		expected.put(BetType.SPLIT, 60);
		expected.put(BetType.STREET, 12);
		expected.put(BetType.TRIO, 2);
		expected.put(BetType.CORNER, 22);
		expected.put(BetType.FIRST_FOUR, 1);
		expected.put(BetType.SIX_LINE, 11);
		expected.put(BetType.DOZEN, 3);
		expected.put(BetType.COLUMN, 3);
		for (BetType t : BetType.values()) {
			List<Spot> all = Spot.all(t);
			assertEquals(expected.getOrDefault(t, 1), all.size(), t.id());
			assertEquals(all.size(), new HashSet<>(all).size(), "unique " + t);
			all.forEach(s -> assertTrue(s.isValid(), s.key()));
		}
	}

	@Test
	void everySpotHasTheSameHouseEdge() {
		for (BetType t : BetType.values()) {
			for (Spot s : Spot.all(t)) {
				assertEquals(36, (t.payout() + 1) * s.numbers().size(), s.key());
				long total = 0;
				for (int r = 0; r <= 36; r++) {
					total += Bets.betReturn(new Bet(s, 37), r, false);
				}
				// RTP = 36/37 exactly (HE 2.70 %).
				assertEquals(36L * 37, total, s.key());
			}
		}
	}

	@Test
	void payoutTable() {
		assertEquals(360, Bets.betReturn(new Bet(Spot.of(BetType.STRAIGHT, 17), 10), 17, false));
		assertEquals(360, Bets.betReturn(new Bet(Spot.of(BetType.STRAIGHT, 0), 10), 0, false));
		assertEquals(180, Bets.betReturn(new Bet(Spot.of(BetType.SPLIT, 17, 20), 10), 20, false));
		assertEquals(180, Bets.betReturn(new Bet(Spot.of(BetType.SPLIT, 0, 2), 10), 0, false));
		assertEquals(120, Bets.betReturn(new Bet(Spot.of(BetType.STREET, 34, 35, 36), 10), 35, false));
		assertEquals(120, Bets.betReturn(new Bet(Spot.of(BetType.TRIO, 0, 1, 2), 10), 0, false));
		assertEquals(90, Bets.betReturn(new Bet(Spot.of(BetType.CORNER, 1, 2, 4, 5), 10), 5, false));
		assertEquals(90, Bets.betReturn(new Bet(Spot.of(BetType.FIRST_FOUR, 0, 1, 2, 3), 10), 3, false));
		assertEquals(60, Bets.betReturn(new Bet(Spot.of(BetType.SIX_LINE, 1, 2, 3, 4, 5, 6), 10), 6, false));
		assertEquals(30, Bets.betReturn(new Bet(Spot.all(BetType.DOZEN).get(2), 10), 25, false));
		assertEquals(30, Bets.betReturn(new Bet(Spot.all(BetType.COLUMN).get(1), 10), 35, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.RED).get(0), 10), 1, false));
		assertEquals(0, Bets.betReturn(new Bet(Spot.all(BetType.RED).get(0), 10), 2, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.BLACK).get(0), 10), 2, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.ODD).get(0), 10), 35, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.EVEN).get(0), 10), 36, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.LOW).get(0), 10), 18, false));
		assertEquals(20, Bets.betReturn(new Bet(Spot.all(BetType.HIGH).get(0), 10), 19, false));
		assertEquals(0, Bets.betReturn(new Bet(Spot.all(BetType.STRAIGHT).get(5), 10), 6, false));
	}

	@Test
	void zeroRuleAndLaPartage() {
		for (BetType t : List.of(BetType.RED, BetType.BLACK, BetType.ODD, BetType.EVEN, BetType.LOW, BetType.HIGH)) {
			Bet b = new Bet(Spot.all(t).get(0), 11);
			assertEquals(0, Bets.betReturn(b, 0, false));
			assertEquals(5, Bets.betReturn(b, 0, true), "half, floored");
			long total = 0;
			for (int r = 0; r <= 36; r++) {
				total += Bets.betReturn(new Bet(b.spot(), 74), r, true);
			}
			assertEquals(18 * 148 + 37, total); // RTP 36.5/37 → HE 1.35 %
		}
		// Dozens/columns still lose everything on 0.
		assertEquals(0, Bets.betReturn(new Bet(Spot.all(BetType.DOZEN).get(0), 10), 0, true));
		assertEquals(0, Bets.betReturn(new Bet(Spot.all(BetType.COLUMN).get(0), 10), 0, true));
	}

	@Test
	void geometryRejectsImpossibleSpots() {
		assertFalse(Spot.of(BetType.SPLIT, 3, 4).isValid());
		assertFalse(Spot.of(BetType.SPLIT, 0, 4).isValid());
		assertFalse(Spot.of(BetType.SPLIT, 1, 5).isValid());
		assertFalse(Spot.of(BetType.SPLIT, 17).isValid());
		assertFalse(Spot.of(BetType.STREET, 2, 3, 4).isValid());
		assertFalse(Spot.of(BetType.CORNER, 3, 4, 6, 7).isValid());
		assertFalse(Spot.of(BetType.CORNER, 34, 35, 37, 38).isValid());
		assertFalse(Spot.of(BetType.SIX_LINE, 2, 3, 4, 5, 6, 7).isValid());
		assertFalse(Spot.of(BetType.SIX_LINE, 34, 35, 36, 37, 38, 39).isValid());
		assertFalse(Spot.of(BetType.TRIO, 0, 1, 3).isValid());
		assertFalse(Spot.of(BetType.FIRST_FOUR, 1, 2, 3, 4).isValid());
		assertFalse(Spot.of(BetType.STRAIGHT, 37).isValid());
		assertFalse(Spot.of(BetType.STRAIGHT, -1).isValid());
		assertFalse(Spot.of(BetType.RED, 1, 3).isValid());
		assertFalse(Spot.of(BetType.DOZEN, 1, 2, 3).isValid());
		assertTrue(Spot.of(BetType.SPLIT, 20, 17).isValid(), "order does not matter");
		assertTrue(Spot.of(BetType.CORNER, 32, 33, 35, 36).isValid());
		assertTrue(Spot.of(BetType.SIX_LINE, 31, 32, 33, 34, 35, 36).isValid());
	}

	@Test
	void keysRoundTrip() {
		for (BetType t : BetType.values()) {
			for (Spot s : Spot.all(t)) {
				assertEquals(Optional.of(s), Spot.parseKey(s.key()));
			}
		}
		assertEquals(Optional.empty(), Spot.parseKey("split:3-4"));
		assertEquals(Optional.empty(), Spot.parseKey("nope:1"));
		assertEquals(Optional.empty(), Spot.parseKey("straight:x"));
		assertEquals(2, Spot.all(BetType.DOZEN).get(1).outsideIndex());
	}

	@Test
	void worstCaseIsTheMaxOverAllOutcomes() {
		Spot s17 = Spot.of(BetType.STRAIGHT, 17);
		assertEquals(360, Bets.worstCase(List.of(new Bet(s17, 10)), false));
		Spot red = Spot.all(BetType.RED).get(0);
		Spot black = Spot.all(BetType.BLACK).get(0);
		assertEquals(20, Bets.worstCase(List.of(new Bet(red, 10), new Bet(black, 10)), false));
		// 17 is black: straight 17 + black both win.
		assertEquals(380, Bets.worstCase(List.of(new Bet(s17, 10), new Bet(red, 10), new Bet(black, 10)), false));
		assertEquals(0, Bets.worstCase(List.of(), false));
	}

	@Test
	void mergeCombinesTheSameSpot() {
		Spot s = Spot.of(BetType.SPLIT, 17, 20);
		List<Bet> slip = Bets.merge(List.of(), new Bet(s, 5));
		slip = Bets.merge(slip, new Bet(Spot.of(BetType.SPLIT, 20, 17), 7));
		assertEquals(1, slip.size());
		assertEquals(12, slip.get(0).amount());
	}

	@Test
	void limits() {
		// Tier max 1000, inside ≤ 250.
		SlipLimits l = SlipLimits.of(1, 1000, 0.25, 1.0, 0);
		assertEquals(250, l.insideMax());
		assertEquals(1000, l.totalMax());
		Spot s = Spot.of(BetType.STRAIGHT, 7);
		assertTrue(l.checkAdd(List.of(), List.of(new Bet(s, 250))).isEmpty());
		assertEquals(SlipLimits.Code.INSIDE_MAX, l.checkAdd(List.of(new Bet(s, 200)), List.of(new Bet(s, 51))).orElseThrow().code());
		Spot red = Spot.all(BetType.RED).get(0);
		assertTrue(l.checkAdd(List.of(), List.of(new Bet(red, 1000))).isEmpty(), "outside bets only limited by the total");
		assertEquals(SlipLimits.Code.TOTAL_MAX, l.checkAdd(List.of(new Bet(red, 900)), List.of(new Bet(s, 101))).orElseThrow().code());
		assertEquals(SlipLimits.Code.INVALID_AMOUNT, l.checkAdd(List.of(), List.of(new Bet(s, 0))).orElseThrow().code());
		assertEquals(SlipLimits.Code.INVALID_POSITION, l.checkAdd(List.of(), List.of(new Bet(Spot.of(BetType.SPLIT, 3, 4), 5))).orElseThrow().code());
		assertEquals(50, l.maxAddable(List.of(new Bet(s, 200)), s));
		assertEquals(100, l.maxAddable(List.of(new Bet(red, 900)), Spot.all(BetType.BLACK).get(0)));

		SlipLimits min5 = SlipLimits.of(5, 1000, 0.25, 1.0, 0);
		SlipLimits.Violation v = min5.checkAdd(List.of(), List.of(new Bet(s, 4))).orElseThrow();
		assertEquals(SlipLimits.Code.BET_TOO_LOW, v.code());
		assertEquals(5, v.value());

		// Rebet: the whole batch is validated together.
		List<Bet> rebet = List.of(new Bet(s, 150), new Bet(s, 150));
		assertEquals(SlipLimits.Code.INSIDE_MAX, l.checkAdd(List.of(), rebet).orElseThrow().code());
	}

	@Test
	void highRollerLimits() {
		SlipLimits l = SlipLimits.of(1, 1000, 0.25, 2.0, 100);
		assertEquals(2000, l.totalMax());
		assertEquals(500, l.insideMax());
		Spot red = Spot.all(BetType.RED).get(0);
		assertEquals(SlipLimits.Code.MIN_TOTAL, l.checkSpin(List.of(new Bet(red, 99))).orElseThrow().code());
		assertTrue(l.checkSpin(List.of(new Bet(red, 100))).isEmpty());
		assertTrue(l.checkSpin(List.of()).isEmpty());
		// Tiny tier max: inside max never below a single minimum bet.
		SlipLimits tiny = SlipLimits.of(1, 2, 0.25, 1.0, 0);
		assertEquals(1, tiny.insideMax());
	}

	@Test
	void monteCarloRtpMatchesSpec() {
		CasinoRng rng = new OddsService(new SplittableRandom(20260923L)).fair();
		int spins = 2_000_000;
		int[] counts = new int[37];
		List<Bet> slip = List.of(
			new Bet(Spot.all(BetType.RED).get(0), 10),
			new Bet(Spot.all(BetType.ODD).get(0), 10),
			new Bet(Spot.all(BetType.DOZEN).get(0), 10),
			new Bet(Spot.all(BetType.HIGH).get(0), 10));
		long staked = 0;
		long returned = 0;
		for (int i = 0; i < spins; i++) {
			int r = rng.nextInt(37);
			counts[r]++;
			staked += Bets.totalStaked(slip);
			returned += Bets.totalReturn(slip, r, false);
		}
		double rtp = (double) returned / staked;
		assertEquals(36.0 / 37.0, rtp, 0.003);
		// Chi-square uniformity (36 dof; 99.9 % critical value ≈ 67.99).
		double expected = spins / 37.0;
		double chi = 0;
		for (int c : counts) {
			chi += (c - expected) * (c - expected) / expected;
		}
		assertTrue(chi < 68, "chi² " + chi);
	}

	@Test
	void betTypeIds() {
		assertEquals("first_four", BetType.FIRST_FOUR.id());
		assertEquals(Optional.of(BetType.SIX_LINE), BetType.byId("six_line"));
		assertEquals(Optional.empty(), BetType.byId("SIX_LINE"));
		Set<BetType> even = new HashSet<>();
		for (BetType t : BetType.values()) {
			if (t.evenMoney()) {
				even.add(t);
			}
		}
		assertEquals(Set.of(BetType.RED, BetType.BLACK, BetType.ODD, BetType.EVEN, BetType.LOW, BetType.HIGH), even);
	}
}
