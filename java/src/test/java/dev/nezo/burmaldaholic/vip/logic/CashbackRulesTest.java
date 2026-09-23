package dev.nezo.burmaldaholic.vip.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** §12 cashback (CHANGED 2026-09): floor(rate × Σ stake × house edge), never +EV. */
class CashbackRulesTest {
	@Test
	void amountIsRateTimesTheoreticalLoss() {
		assertEquals(12, CashbackRules.amount(600, 0.02));
		assertEquals(0, CashbackRules.amount(49, 0.02));
		assertEquals(0, CashbackRules.amount(1000, 0));
		assertEquals(0, CashbackRules.amount(0, 0.05));
		assertEquals(0, CashbackRules.amount(-5, 0.05));
		assertEquals(3, CashbackRules.amount(100, 0.03));
	}

	@Test
	void edgesAreTheLowestOfEachGame() {
		assertEquals(0.0041, CashbackRules.houseEdge("blackjack"));
		assertEquals(0.027, CashbackRules.houseEdge("roulette"));
		assertEquals(0.0396, CashbackRules.houseEdge("slots"));
		assertEquals(0, CashbackRules.houseEdge("craps"), "Odds bets have 0 % edge and are indistinguishable");
		assertEquals(0, CashbackRules.houseEdge("poker"));
		assertEquals(0, CashbackRules.houseEdge("something_new"));
		assertEquals(0.02, CashbackRules.houseEdge("extras"));
	}

	@Test
	void ledgerRollsAtTheDayBoundary() {
		CashbackRules.Roll r = CashbackRules.record(null, 5, "roulette", 100, 0);
		assertNull(r.closed());
		r = CashbackRules.record(r.ledger(), 5, "poker", 50, 100);
		assertEquals(150, r.ledger().staked());
		assertEquals(100, r.ledger().returned());
		assertEquals(2.7, r.ledger().theo(), 1e-9, "poker never counts");
		CashbackRules.DayLedger day5 = r.ledger();
		CashbackRules.Roll next = CashbackRules.record(day5, 6, "blackjack", 1000, 2000);
		assertNotNull(next.closed());
		assertEquals(day5, next.closed());
		assertEquals(6, next.ledger().day());
		assertEquals(4.1, next.ledger().theo(), 1e-9, "paid whatever the result");
		assertNull(CashbackRules.roll(next.ledger(), 6).closed());
		assertEquals(next.ledger(), CashbackRules.roll(next.ledger(), 9).closed());
	}

	/**
	 * Low-edge, high-variance play (blackjack-like even money, HE 0.41 %, Netherite 5 %, 5 × 1 000
	 * a day): the old rate × max(0, net loss) formula would pay more than the edge; the spec
	 * formula keeps the effective edge at HE × (1 − rate) > 0.
	 */
	@Test
	void monteCarloNeverPositiveEv() {
		SplittableRandom rng = new SplittableRandom(4242);
		double rate = 0.05;
		double he = CashbackRules.houseEdge("blackjack");
		double pWin = (1 - he) / 2;
		long staked = 0;
		long returned = 0;
		long paidNew = 0;
		long paidOld = 0;
		for (int d = 0; d < 40_000; d++) {
			CashbackRules.DayLedger l = CashbackRules.DayLedger.empty(d);
			for (int i = 0; i < 5; i++) {
				long payout = rng.nextDouble() < pWin ? 2000 : 0;
				l = CashbackRules.record(l, d, "blackjack", 1000, payout).ledger();
			}
			staked += l.staked();
			returned += l.returned();
			paidNew += CashbackRules.amount(l.theo(), rate);
			paidOld += (long) Math.floor(Math.max(0, l.staked() - l.returned()) * rate);
		}
		double edge = 1 - (double) returned / staked;
		double oldEffective = 1 - (double) (returned + paidOld) / staked;
		double newEffective = 1 - (double) (returned + paidNew) / staked;
		assertTrue(oldEffective < 0, "the old formula was +EV here: " + oldEffective);
		assertTrue((double) paidNew / staked < he, "cashback below the edge");
		assertEquals(edge - rate * he, newEffective, 1e-3);
		assertTrue(rate * he < he);
	}

	@Test
	void expectedCashbackBelowExpectedLossForEveryGameAndRate() {
		for (String game : CashbackRules.LOWEST_EDGE.keySet()) {
			double he = CashbackRules.houseEdge(game);
			for (double rate : new double[] {0.02, 0.05, 0.5}) {
				long stake = 1_000_000;
				double theo = CashbackRules.theoreticalLoss(stake, he);
				assertTrue(CashbackRules.amount(theo, rate) <= theo, game);
				if (theo > 0) {
					assertTrue(CashbackRules.amount(theo, rate) < theo, game);
				}
			}
		}
	}
}
