package dev.nezo.burmaldaholic.comps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** EC-SPEC §4.4: daily bonus amounts and gates (once a day, lifetime wager, active minutes, one per IP). */
class DailyBonusRulesTest {
	private static final long MIN_ACTIVE = 10 * 1200;

	private static CompsRules.State activeToday(long day, long ticks) {
		return CompsRules.State.empty(day).active(ticks);
	}

	@Test
	void amountsPerTier() {
		long[] expected = {25, 50, 120, 300, 750, 2000};
		for (int t = 0; t < expected.length; t++) {
			assertEquals(expected[t], CompsRules.dailyAmount(t, CompsRules.DEFAULT_DAILY));
		}
		assertEquals(2000, CompsRules.dailyAmount(9, CompsRules.DEFAULT_DAILY));
		assertEquals(25, CompsRules.dailyAmount(-1, null));
	}

	@Test
	void gatesInOrder() {
		CompsRules.State ok = activeToday(7, MIN_ACTIVE);
		assertEquals(CompsRules.Daily.CLAIMABLE, CompsRules.daily(ok, 7, 1000, 1000, MIN_ACTIVE, false));
		assertEquals(CompsRules.Daily.LOCKED_WAGER, CompsRules.daily(ok, 7, 999, 1000, MIN_ACTIVE, false));
		assertEquals(CompsRules.Daily.LOCKED_ACTIVE, CompsRules.daily(activeToday(7, MIN_ACTIVE - 20), 7, 5000, 1000, MIN_ACTIVE, false));
		assertEquals(CompsRules.Daily.LOCKED_IP, CompsRules.daily(ok, 7, 5000, 1000, MIN_ACTIVE, true));
		assertEquals(CompsRules.Daily.CLAIMED, CompsRules.daily(ok.claimed(7), 7, 5000, 1000, MIN_ACTIVE, true));
	}

	@Test
	void activityFromYesterdayDoesNotCount() {
		CompsRules.State yesterday = activeToday(6, MIN_ACTIVE * 2);
		assertEquals(CompsRules.Daily.LOCKED_ACTIVE, CompsRules.daily(yesterday, 7, 5000, 1000, MIN_ACTIVE, false));
		assertEquals(CompsRules.Daily.CLAIMABLE, CompsRules.daily(activeToday(6, 0).claimed(6), 7, 5000, 1000, 0, false),
			"a claim yesterday does not block today");
	}

	@Test
	void activityWindow() {
		assertTrue(CompsRules.active(10_000, 10_000 - 1200));
		assertFalse(CompsRules.active(10_000, 10_000 - 1201));
		assertFalse(CompsRules.active(10_000, -1));
		assertTrue(CompsRules.moved(0.4, 0, 0.4));
		assertFalse(CompsRules.moved(0.3, 0, 0.3));
		assertEquals(24000, CompsRules.ticksToNextDay(48000));
		assertEquals(1, CompsRules.ticksToNextDay(47999));
	}
}
