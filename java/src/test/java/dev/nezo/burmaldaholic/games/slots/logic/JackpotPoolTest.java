package dev.nezo.burmaldaholic.games.slots.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JackpotPoolTest {
	@Test
	void contributionsKeepTheExactLongRunRate() {
		JackpotPool s = JackpotPool.seeded(5000);
		long added = 0;
		for (int i = 0; i < 1000; i++) {
			JackpotPool.Contribution c = s.contribute(3, 0.01);
			s = c.state();
			added += c.added();
		}
		assertEquals(30, added);
		assertEquals(5030, s.pool());
		JackpotPool n = JackpotPool.seeded(0);
		for (int i = 0; i < 200; i++) {
			n = n.contribute(10, 0.015).state(); // 0.15 per spin
		}
		assertEquals(30, n.pool());
		assertEquals(1, JackpotPool.seeded(0).contribute(100, 0.015).state().pool());
		assertEquals(500_000, JackpotPool.seeded(0).contribute(100, 0.015).state().remMicros());
		assertEquals(0, JackpotPool.seeded(0).contribute(100, 0).added());
		assertEquals(0, JackpotPool.seeded(0).contribute(0, 0.01).added());
	}

	@Test
	void awardIsProportionalToTheSpinBetCappedAtThePool() {
		JackpotPool s = new JackpotPool(61240, 0);
		assertEquals(61240, s.awardFor(300, 300));
		assertEquals(612, s.awardFor(3, 300));
		assertEquals(61240, s.awardFor(600, 300));
		assertEquals(61240, s.awardFor(5, 0));
	}

	@Test
	void poolBelowTheSeedIsToppedUpByTheBank() {
		JackpotPool.Payout r = new JackpotPool(6000, 500_000).pay(300, 300, 5000);
		assertEquals(6000, r.award());
		assertEquals(new JackpotPool(5000, 500_000), r.state());
		assertEquals(5000, r.toppedUp());
		JackpotPool.Payout small = new JackpotPool(60000, 0).pay(3, 300, 5000);
		assertEquals(600, small.award());
		assertEquals(59400, small.state().pool());
		assertEquals(0, small.toppedUp());
	}

	@Test
	void corruptStateIsSanitised() {
		assertEquals(new JackpotPool(0, 0), new JackpotPool(-3, -7));
		assertEquals(0, new JackpotPool(10, JackpotPool.MICROS).remMicros());
	}

	@Test
	void lineBetRange() {
		LineBets copper = new LineBets(1, 50);
		LineBets gold = new LineBets(1, 100);
		LineBets neth = new LineBets(2, 500);
		assertEquals(new LineBets.Range(1, 50), copper.range(100, 1));
		assertEquals(new LineBets.Range(1, 33), gold.range(100, 3));
		assertEquals(new LineBets.Range(1, 100), gold.range(1000, 3));
		assertEquals(new LineBets.Range(2, 200), neth.range(1000, 5));
		assertEquals(new LineBets.Range(2, 500), neth.range(2500, 5));
		assertFalse(neth.range(5, 5).playable());
		assertTrue(neth.range(10, 5).playable());
		assertEquals(300, gold.machineMaxSpinBet(3));
		assertEquals(2500, neth.machineMaxSpinBet(5));
		assertEquals(200, new LineBets.Range(2, 200).clamp(999));
		assertEquals(2, new LineBets.Range(2, 200).clamp(-4));
		assertEquals(2, new LineBets.Range(2, 1).clamp(50));
		assertTrue(LineBets.autoStopsOnWin(60, 3));
		assertFalse(LineBets.autoStopsOnWin(59, 3));
	}
}
