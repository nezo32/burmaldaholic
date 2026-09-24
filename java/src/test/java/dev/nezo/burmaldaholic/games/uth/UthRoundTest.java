package dev.nezo.burmaldaholic.games.uth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.uth.logic.BankRules;
import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits.Problem;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Round state machine (§21.4), limits (§21.2) and the player bank (§21.9). */
class UthRoundTest {
	private static final Paytables P = Paytables.DEFAULT;
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

	private static int[] ordered() {
		int[] d = new int[52];
		for (int i = 0; i < 52; i++) {
			d[i] = i;
		}
		return d;
	}

	@Test
	void dealOrderSeatsThenDealerTwiceThenBoard() {
		UthRound r = UthRound.deal(List.of(new UthRound.Entry(4, C, "c", 1, 0), new UthRound.Entry(0, A, "a", 1, 0),
			new UthRound.Entry(2, B, "b", 1, 0)), ordered());
		List<UthRound.Seat> s = r.seats();
		assertEquals(List.of(0, 2, 4), s.stream().map(x -> x.seat).toList());
		// pass 1: A=0, B=1, C=2, dealer=3; pass 2: A=4, B=5, C=6, dealer=7; board 8..12
		assertEquals(0, s.get(0).hole[0]);
		assertEquals(4, s.get(0).hole[1]);
		assertEquals(2, s.get(2).hole[0]);
		assertEquals(6, s.get(2).hole[1]);
		assertEquals(3, r.dealerCards()[0]);
		assertEquals(7, r.dealerCards()[1]);
		assertEquals(8, r.board()[0]);
		assertEquals(12, r.board()[4]);
		assertEquals(0, r.visibleBoard());
	}

	@Test
	void onePlayBetPerRoundAndStreetOptions() {
		UthRound r = UthRound.deal(List.of(new UthRound.Entry(0, A, "a", 10, 5), new UthRound.Entry(1, B, "b", 10, 0)), ordered());
		UthRound.Seat a = r.seat(A);
		UthRound.Seat b = r.seat(B);
		assertEquals(List.of(Decision.CHECK, Decision.BET_3X, Decision.BET_4X), r.legal(a, true));
		assertEquals(List.of(Decision.CHECK, Decision.BET_4X), r.legal(a, false));
		assertFalse(r.decide(a, Decision.BET_2X, true), "no ×2 preflop");
		assertFalse(r.decide(a, Decision.BET_3X, false), "×3 off");
		assertTrue(r.decide(a, Decision.BET_4X, true));
		assertFalse(r.pending(a));
		assertThrows(IllegalStateException.class, () -> r.advance(P), "B still deciding");
		assertTrue(r.decide(b, Decision.CHECK, true));
		r.advance(P);
		assertEquals(UthRound.Street.FLOP, r.street());
		assertEquals(3, r.visibleBoard());
		assertFalse(r.pending(a), "a Play bet ends the seat's decisions");
		assertEquals(List.of(Decision.CHECK, Decision.BET_2X), r.legal(b, true));
		assertTrue(r.decide(b, Decision.CHECK, true));
		r.advance(P);
		assertEquals(List.of(Decision.FOLD, Decision.BET_1X), r.legal(b, true));
		assertTrue(r.decide(b, Decision.FOLD, true));
		r.advance(P);
		assertTrue(r.settled());
		assertNotNull(a.result);
		assertNotNull(b.result);
		assertEquals(20 + 40 + 5, a.result.staked());
		assertEquals(-20, b.result.net(), "fold: Ante and Blind lost");
		r.advance(P); // idempotent at showdown
		assertTrue(r.settled());
	}

	@Test
	void limitsOfW() {
		// Bronze (max 100): Ante ≤ 16 without Trips
		assertEquals(16, UthLimits.maxAnte(100, 0));
		assertEquals(16, UthLimits.maxAnte(100, 4));
		assertEquals(15, UthLimits.maxAnte(100, 5));
		assertEquals(Problem.NONE, UthLimits.check(16, 0, 1, 100, true, 1, 1000));
		assertEquals(Problem.WORST_CASE_MAX, UthLimits.check(17, 0, 1, 100, true, 1, 1000));
		assertEquals(Problem.WORST_CASE_MAX, UthLimits.check(16, 5, 1, 100, true, 1, 1000));
		assertEquals(Problem.ANTE_MIN, UthLimits.check(40, 0, 50, 1000, true, 1, 1000));
		assertEquals(Problem.TRIPS_OFF, UthLimits.check(10, 5, 1, 1000, false, 1, 1000));
		assertEquals(Problem.TRIPS_MIN, UthLimits.check(10, 1, 1, 1000, true, 5, 1000));
		assertEquals(Problem.INVALID, UthLimits.check(0, 0, 1, 1000, true, 1, 1000));
		assertEquals(Problem.INVALID, UthLimits.check(5, -1, 1, 1000, true, 1, 1000));
		// balance must cover 2A + T now and keep 1 × Ante for the river
		assertEquals(Problem.INSUFFICIENT_FUNDS, UthLimits.check(10, 5, 1, 1000, true, 1, 24));
		assertEquals(Problem.KEEP_FOR_RIVER, UthLimits.check(10, 5, 1, 1000, true, 1, 34));
		assertEquals(Problem.NONE, UthLimits.check(10, 5, 1, 1000, true, 1, 35));
		assertEquals(25, UthLimits.confirmCost(10, 5));
		assertEquals(65, UthLimits.worstCaseTotal(10, 5));
	}

	@Test
	void bankCoverageAndRake() {
		long wc = BankRules.seatWorstCase(10, 10, P);
		assertEquals(5550, wc);
		assertTrue(BankRules.covers(10_000, 4_000, 5_550));
		assertFalse(BankRules.covers(10_000, 5_000, 5_550));
		assertEquals((10_000 - 5_000 - 500) / 505, BankRules.maxCoveredAnte(10_000, 5_000, 10, P));
		assertEquals(0, BankRules.maxCoveredAnte(1_000, 1_000, 0, P));
		assertEquals(0, BankRules.rake(-50, 0.01));
		assertEquals(0, BankRules.rake(99, 0.01));
		assertEquals(1, BankRules.rake(100, 0.01));
		assertEquals(12, BankRules.rake(1_250, 0.01));
		assertTrue(BankRules.canKeepBanking(1_000, 1_000, 1, P));
		assertFalse(BankRules.canKeepBanking(999, 1_000, 1, P));
		assertFalse(BankRules.canKeepBanking(20_000, 1_000, 50, P), "cannot cover one seat at the High-Roller minimum");
		assertEquals(-40, BankRules.bankerNet(10, 30));
	}

	@Test
	void deckIsAPermutation() {
		int[] deck = UthCards.shuffledDeck(new java.util.SplittableRandom(3)::nextInt);
		boolean[] seen = new boolean[52];
		for (int c : deck) {
			assertFalse(seen[c]);
			seen[c] = true;
		}
	}
}
