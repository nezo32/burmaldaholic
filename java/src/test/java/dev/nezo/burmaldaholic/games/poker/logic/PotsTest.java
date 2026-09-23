package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PotsTest {
	private static final Pots.RakeConfig RAKE = new Pots.RakeConfig(0.05, 3, true);

	@Test
	void uncalledBet() {
		assertEquals(new Pots.Uncalled(1, 200), Pots.uncalledBet(new long[] {100, 300, 50}));
		assertNull(Pots.uncalledBet(new long[] {200, 200, 50}));
		assertEquals(new Pots.Uncalled(2, 40), Pots.uncalledBet(new long[] {0, 20, 60}), "folded contributions count as callers");
	}

	@Test
	void singlePot() {
		assertEquals(List.of(new Pots.Pot(300, List.of(0, 1, 2), List.of(0, 1, 2))),
			Pots.buildPots(new long[] {100, 100, 100}, new boolean[3]));
	}

	@Test
	void shortAllInMakesASidePot() {
		assertEquals(List.of(new Pots.Pot(150, List.of(0, 1, 2), List.of(0, 1, 2)), new Pots.Pot(300, List.of(1, 2), List.of(1, 2))),
			Pots.buildPots(new long[] {50, 200, 200}, new boolean[3]));
	}

	@Test
	void threeAllInLevels() {
		List<Pots.Pot> pots = Pots.buildPots(new long[] {25, 100, 250, 250}, new boolean[4]);
		assertEquals(List.of(100L, 225L, 300L), pots.stream().map(Pots.Pot::amount).toList());
		assertEquals(List.of(List.of(0, 1, 2, 3), List.of(1, 2, 3), List.of(2, 3)), pots.stream().map(Pots.Pot::eligible).toList());
	}

	@Test
	void foldedChipsGoInButFoldedPlayersAreNeverEligible() {
		assertEquals(List.of(new Pots.Pot(100, List.of(0, 1), List.of(0, 1, 2, 3)), new Pots.Pot(100, List.of(0), List.of(0, 2))),
			Pots.buildPots(new long[] {80, 30, 80, 10}, new boolean[] {false, false, true, true}));
	}

	@Test
	void mergesLevelsWithTheSameEligibleSet() {
		assertEquals(List.of(new Pots.Pot(260, List.of(0, 1), List.of(0, 1, 2))),
			Pots.buildPots(new long[] {100, 100, 60}, new boolean[] {false, false, true}));
	}

	@Test
	void chipsAreConserved() {
		long[] totals = {13, 400, 77, 400, 250, 0};
		boolean[] folded = {true, false, false, false, true, true};
		assertEquals(1140, Pots.buildPots(totals, folded).stream().mapToLong(Pots.Pot::amount).sum());
	}

	@Test
	void rake() {
		assertEquals(9, Pots.rakeFor(199, 2, true, 10, RAKE), "5 % floored");
		assertEquals(30, Pots.rakeFor(10_000, 3, true, 10, RAKE), "capped at 3 BB");
		assertEquals(0, Pots.rakeFor(500, 2, false, 10, RAKE), "no flop, no drop");
		assertEquals(25, Pots.rakeFor(500, 2, false, 10, new Pots.RakeConfig(0.05, 3, false)));
		assertEquals(0, Pots.rakeFor(500, 1, true, 10, RAKE), "a lone human vs bots is never raked");
		assertEquals(0, Pots.rakeFor(500, 0, true, 10, RAKE));
		assertEquals(0, Pots.rakeFor(500, 2, true, 10, new Pots.RakeConfig(0, 3, true)));
		assertEquals(0, Pots.rakeFor(500, 2, true, 10, new Pots.RakeConfig(0.05, 0, true)), "cap 0 = no rake");
		for (long pot = 1; pot < 50_000; pot += 7) {
			long r = Pots.rakeFor(pot, 2, true, 50, RAKE);
			assertTrue(r <= pot * 0.05 + 1e-9 && r <= 150, "pot " + pot);
			assertEquals(Math.min(pot / 20, 150), r, "exact 5 % for pot " + pot);
		}
	}

	@Test
	void splitPot() {
		assertArrayEquals(new long[] {50, 50}, Pots.splitPot(100, 2));
		assertArrayEquals(new long[] {51, 50}, Pots.splitPot(101, 2), "odd chip to the first winner left of the button");
		assertArrayEquals(new long[] {101, 101, 100}, Pots.splitPot(302, 3));
		assertArrayEquals(new long[0], Pots.splitPot(100, 0));
		assertEquals(List.of(0, 1, 2, 3), List.of(3, 0, 1, 2).stream().map(i -> Pots.orderFromButton(i, 2, 4)).toList());
	}
}
