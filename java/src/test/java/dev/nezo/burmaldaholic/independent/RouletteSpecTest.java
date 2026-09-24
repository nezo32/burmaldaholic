package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.SlipLimits;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §9 re-derived: layout geometry, payouts, 2.70 % everywhere, la partage, limits. */
class RouletteSpecTest {
	private static final Set<Integer> SPEC_RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final String SPEC_ORDER =
		"0-32-15-19-4-21-2-25-17-34-6-27-13-36-11-30-8-23-10-5-24-16-33-1-20-14-31-9-22-18-29-7-28-12-35-3-26";

	@Test
	void wheelAndColoursMatchSpec() {
		assertEquals(SPEC_RED, Wheel.RED);
		assertEquals(SPEC_ORDER, String.join("-", Wheel.ORDER.stream().map(String::valueOf).toList()));
		assertEquals(37, new HashSet<>(Wheel.ORDER).size(), "every pocket once");
		// Real European wheels alternate red/black after 0.
		for (int i = 1; i < 37; i++) {
			int n = Wheel.ORDER.get(i);
			int m = Wheel.ORDER.get(i % 36 + 1);
			if (i < 36) {
				assertTrue(Wheel.RED.contains(n) != Wheel.RED.contains(m), "alternating colours at " + n + "/" + m);
			}
		}
		assertEquals(Wheel.Color.GREEN, Wheel.color(0));
	}

	/** Geometry from the spec layout (3 columns × 12 rows), built here independently. */
	@Test
	void spotCountsMatchLayout() {
		Map<BetType, Integer> expected = Map.ofEntries(
			Map.entry(BetType.STRAIGHT, 37),
			Map.entry(BetType.SPLIT, 24 + 33 + 3), // horizontal 12×2, vertical 11×3, 0-1/0-2/0-3
			Map.entry(BetType.STREET, 12),
			Map.entry(BetType.TRIO, 2),
			Map.entry(BetType.CORNER, 11 * 2),
			Map.entry(BetType.FIRST_FOUR, 1),
			Map.entry(BetType.SIX_LINE, 11),
			Map.entry(BetType.DOZEN, 3),
			Map.entry(BetType.COLUMN, 3),
			Map.entry(BetType.RED, 1), Map.entry(BetType.BLACK, 1), Map.entry(BetType.ODD, 1),
			Map.entry(BetType.EVEN, 1), Map.entry(BetType.LOW, 1), Map.entry(BetType.HIGH, 1));
		for (BetType t : BetType.values()) {
			List<Spot> all = Spot.all(t);
			assertEquals(expected.get(t), all.size(), t.id());
			assertEquals(all.size(), new HashSet<>(all).size(), t + " unique");
			for (Spot s : all) {
				assertTrue(s.isValid(), s.key());
				assertEquals(s, Spot.parseKey(s.key()).orElseThrow(), "key round trip " + s.key());
			}
		}
	}

	@Test
	void invalidGeometryIsRejected() {
		assertFalse(Spot.of(BetType.SPLIT, 3, 4).isValid(), "3-4 are not adjacent (row wrap)");
		assertFalse(Spot.of(BetType.SPLIT, 34, 37).isValid());
		assertFalse(Spot.of(BetType.SPLIT, 0, 4).isValid());
		assertFalse(Spot.of(BetType.STREET, 2, 3, 4).isValid());
		assertFalse(Spot.of(BetType.CORNER, 3, 4, 6, 7).isValid());
		assertFalse(Spot.of(BetType.CORNER, 34, 35, 37, 38).isValid());
		assertFalse(Spot.of(BetType.SIX_LINE, 34, 35, 36, 37, 38, 39).isValid());
		assertFalse(Spot.of(BetType.TRIO, 0, 1, 3).isValid());
		assertFalse(Spot.of(BetType.STRAIGHT, 37).isValid());
		assertFalse(Spot.of(BetType.DOZEN, 1, 2, 3).isValid());
		assertTrue(Spot.parseKey("split:1-5").isEmpty());
		assertTrue(Spot.parseKey("straight:x").isEmpty());
	}

	/** Every bet covers k numbers and pays (36/k − 1):1 ⇒ RTP 36/37 (HE 2.70 %) for every spot. */
	@Test
	void everySpotHasTheSameEdge() {
		Map<BetType, Integer> specPayout = Map.ofEntries(
			Map.entry(BetType.STRAIGHT, 35), Map.entry(BetType.SPLIT, 17), Map.entry(BetType.STREET, 11), Map.entry(BetType.TRIO, 11),
			Map.entry(BetType.CORNER, 8), Map.entry(BetType.FIRST_FOUR, 8), Map.entry(BetType.SIX_LINE, 5), Map.entry(BetType.DOZEN, 2),
			Map.entry(BetType.COLUMN, 2), Map.entry(BetType.RED, 1), Map.entry(BetType.BLACK, 1), Map.entry(BetType.ODD, 1),
			Map.entry(BetType.EVEN, 1), Map.entry(BetType.LOW, 1), Map.entry(BetType.HIGH, 1));
		for (BetType t : BetType.values()) {
			assertEquals(specPayout.get(t), t.payout(), t.id());
			for (Spot s : Spot.all(t)) {
				long ret = 0;
				for (int r = 0; r < 37; r++) {
					ret += Bets.betReturn(new Bets.Bet(s, 100), r, false);
				}
				assertEquals(3600, ret, s.key() + " RTP = 36/37");
				assertEquals(0, Bets.betReturn(new Bets.Bet(s, 100), 0, false) * (s.covers(0) ? 0 : 1), s.key() + " loses on 0 unless it covers 0");
			}
		}
		// Outside sets by definition
		assertEquals(List.of(1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34), Spot.outsideNumbers(BetType.COLUMN, 1));
		assertEquals(18, Spot.outsideNumbers(BetType.ODD, 0).size());
		assertFalse(Spot.outsideNumbers(BetType.EVEN, 0).contains(0), "0 is neither odd nor even");
	}

	@Test
	void laPartageHalvesEvenMoneyOnZeroOnly() {
		Spot red = Spot.all(BetType.RED).getFirst();
		Spot dozen = Spot.all(BetType.DOZEN).getFirst();
		assertEquals(5, Bets.betReturn(new Bets.Bet(red, 11), 0, true), "floor(11/2)");
		assertEquals(0, Bets.betReturn(new Bets.Bet(dozen, 10), 0, true), "dozens still lose");
		assertEquals(0, Bets.betReturn(new Bets.Bet(red, 10), 0, false));
		long ret = 0;
		for (int r = 0; r < 37; r++) {
			ret += Bets.betReturn(new Bets.Bet(red, 100), r, true);
		}
		assertEquals(1 - 0.0135, ret / 3700.0, 1e-4, "HE 1.35 % with la partage");
	}

	@Test
	void worstCaseIsMaxOverAllPockets() {
		List<Bets.Bet> slip = List.of(new Bets.Bet(Spot.of(BetType.STRAIGHT, 17), 10), new Bets.Bet(Spot.all(BetType.BLACK).getFirst(), 20),
			new Bets.Bet(Spot.of(BetType.SPLIT, 17, 20), 5));
		// 17 is black: 360 + 40 + 90
		assertEquals(490, Bets.worstCase(slip, false));
	}

	@Test
	void limits() {
		// Bronze tier max 100: total ≤ 100, each inside bet ≤ 25, each bet ≥ 1.
		SlipLimits l = SlipLimits.of(1, 100, 0.25, 1, 0);
		Spot s = Spot.of(BetType.STRAIGHT, 7);
		assertTrue(l.checkAdd(List.of(), List.of(new Bets.Bet(s, 25))).isEmpty());
		assertEquals(SlipLimits.Code.INSIDE_MAX, l.checkAdd(List.of(new Bets.Bet(s, 20)), List.of(new Bets.Bet(s, 6))).orElseThrow().code());
		Spot red = Spot.all(BetType.RED).getFirst();
		assertEquals(SlipLimits.Code.TOTAL_MAX, l.checkAdd(List.of(new Bets.Bet(red, 90)), List.of(new Bets.Bet(s, 11))).orElseThrow().code());
		assertTrue(l.checkAdd(List.of(new Bets.Bet(red, 75)), List.of(new Bets.Bet(s, 25))).isEmpty(), "exactly the max");
		// High roller: min total 100, max 2 × tier max
		SlipLimits hr = SlipLimits.of(1, 1000, 0.25, 2, 100);
		assertEquals(2000, hr.totalMax());
		assertEquals(SlipLimits.Code.MIN_TOTAL, hr.checkSpin(List.of(new Bets.Bet(red, 99))).orElseThrow().code());
		assertTrue(hr.checkSpin(List.of(new Bets.Bet(red, 100))).isEmpty());
	}
}
