package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Lane J-L6 review: the French call bets are the classical chip distributions (Tier 6, Orphelins 5, Voisins 9 with the
 * double 0-2-3 and 25-29 chips, Jeu Zéro 4), split into GAME_DESIGN §9 standard bets, so their returns are exactly the
 * standard payouts of those bets.
 */
class RacetrackTest {
	private static Set<Integer> set(Integer... n) {
		return new HashSet<>(List.of(n));
	}

	@Test
	void sectionsAreTheClassicalWheelArcs() {
		assertEquals(set(27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33), Racetrack.covered(Racetrack.Section.TIER));
		assertEquals(set(1, 20, 14, 31, 9, 17, 34, 6), Racetrack.covered(Racetrack.Section.ORPHELINS));
		assertEquals(set(22, 18, 29, 7, 28, 12, 35, 3, 26, 0, 32, 15, 19, 4, 21, 2, 25), Racetrack.covered(Racetrack.Section.VOISINS));
		assertEquals(set(12, 35, 3, 26, 0, 32, 15), Racetrack.covered(Racetrack.Section.ZERO));
		// Tier, Orphelins and Voisins split the wheel without overlap
		Set<Integer> all = new HashSet<>();
		int n = 0;
		for (Racetrack.Section s : List.of(Racetrack.Section.TIER, Racetrack.Section.ORPHELINS, Racetrack.Section.VOISINS)) {
			all.addAll(Racetrack.covered(s));
			n += Racetrack.covered(s).size();
		}
		assertEquals(Wheel.POCKETS, all.size());
		assertEquals(Wheel.POCKETS, n);
	}

	@Test
	void voisinsCarriesTwoChipsOnTheTrioAndTheCorner() {
		Map<String, Long> byKey = new TreeMap<>();
		for (Bet b : Racetrack.bets(Racetrack.Section.VOISINS, 5)) {
			byKey.put(b.spot().key(), b.amount());
		}
		assertEquals(7, byKey.size(), byKey.toString());
		assertEquals(10L, byKey.get(Spot.of(BetType.TRIO, 0, 2, 3).key()));
		assertEquals(10L, byKey.get(Spot.of(BetType.CORNER, 25, 26, 28, 29).key()));
		assertEquals(45L, Bets.totalStaked(Racetrack.bets(Racetrack.Section.VOISINS, 5)));
	}

	@Test
	void callBetReturnsAreTheStandardPayouts() {
		long u = 10;
		// Voisins: 0 hits the doubled trio (2u × 12), 26 the doubled corner (2u × 9), 4 a split (u × 18)
		assertEquals(24 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.VOISINS, u), 0, false));
		assertEquals(18 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.VOISINS, u), 26, false));
		assertEquals(18 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.VOISINS, u), 4, false));
		assertEquals(0, Bets.totalReturn(Racetrack.bets(Racetrack.Section.VOISINS, u), 17, false));
		// Tier: every number is on exactly one split
		for (int x : Racetrack.covered(Racetrack.Section.TIER)) {
			assertEquals(18 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.TIER, u), x, false), "tier " + x);
		}
		// Orphelins: 1 is the straight-up, 17 sits on two splits
		assertEquals(36 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.ORPHELINS, u), 1, false));
		assertEquals(36 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.ORPHELINS, u), 17, false));
		assertEquals(18 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.ORPHELINS, u), 9, false));
		// Jeu zéro: 26 straight, 0 on the 0-3 split
		assertEquals(36 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.ZERO, u), 26, false));
		assertEquals(18 * u, Bets.totalReturn(Racetrack.bets(Racetrack.Section.ZERO, u), 0, false));
		// neighbours: five straight-ups; the hit one returns 36 units
		List<Bet> nb = Racetrack.neighbourBets(0, u);
		assertEquals(5, nb.size());
		for (Bet b : nb) {
			assertTrue(b.spot().isValid() && b.type() == BetType.STRAIGHT, b.spot().key());
		}
		assertEquals(36 * u, Bets.totalReturn(nb, 32, false));
	}
}
