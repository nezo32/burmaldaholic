package dev.nezo.burmaldaholic.multiplayer.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OwnerLimitsAndStatsTest {
	@Test
	void emptyFieldsMeanNoOwnerLimit() {
		OwnerLimits.Parsed p = OwnerLimits.parse("", " ", 50_000);
		assertTrue(p.ok());
		assertEquals(0, p.min());
		assertEquals(0, p.max());
	}

	@Test
	void parsesGroupedNumbers() {
		OwnerLimits.Parsed p = OwnerLimits.parse("10", "1 000", 50_000);
		assertTrue(p.ok());
		assertEquals(10, p.min());
		assertEquals(1000, p.max());
		assertEquals(12500, OwnerLimits.parseField("12 500"));
	}

	@Test
	void rejectsJunkAndInvertedLimits() {
		assertEquals(OwnerLimits.Error.INVALID, OwnerLimits.parse("abc", "", 50_000).error());
		assertEquals(OwnerLimits.Error.INVALID, OwnerLimits.parse("-5", "", 50_000).error());
		assertEquals(OwnerLimits.Error.INVALID, OwnerLimits.parse("99999999999999999999", "", 50_000).error());
		assertEquals(OwnerLimits.Error.MIN_OVER_MAX, OwnerLimits.parse("500", "100", 50_000).error());
		assertEquals(OwnerLimits.Error.MIN_OVER_MAX, OwnerLimits.validate(500, 100, 50_000).error());
	}

	@Test
	void maxCappedByTheGlobalTableMax() {
		assertEquals(OwnerLimits.Error.OVER_GLOBAL, OwnerLimits.validate(0, 60_000, 50_000).error());
		assertEquals(OwnerLimits.Error.OVER_GLOBAL, OwnerLimits.validate(60_000, 0, 50_000).error());
		assertTrue(OwnerLimits.validate(0, 50_000, 50_000).ok());
	}

	@Test
	void effectiveMinimum() {
		assertEquals(1, OwnerLimits.effectiveMin(0, 0));
		assertEquals(25, OwnerLimits.effectiveMin(1, 25));
		assertEquals(10, OwnerLimits.effectiveMin(10, 2));
	}

	@Test
	void statsRecordHandlePayoutsRakeAndProfit() {
		CasinoStats s = new CasinoStats();
		s.recordRound(3, 100, 0);
		s.recordRound(3, 50, 100);
		s.recordRake(3, 7);
		assertEquals(150, s.today().handle);
		assertEquals(100, s.today().paid);
		assertEquals(7, s.today().rake);
		assertEquals(2, s.today().rounds);
		assertEquals(57, s.today().profit());
		assertEquals(57, s.total().profit());
	}

	@Test
	void aNewDayResetsTodayButKeepsTotals() {
		CasinoStats s = new CasinoStats();
		s.recordRound(3, 100, 0);
		s.recordRound(4, 10, 20);
		assertEquals(4, s.day());
		assertEquals(10, s.today().handle);
		assertEquals(-10, s.today().profit());
		assertEquals(110, s.total().handle);
		assertEquals(2, s.total().rounds);
		s.roll(9);
		assertEquals(0, s.today().handle);
		assertEquals(0, CasinoStats.dayOf(23_999));
		assertEquals(1, CasinoStats.dayOf(24_000));
	}
}
