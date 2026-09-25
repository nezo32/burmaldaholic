package dev.nezo.burmaldaholic.core.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PawnRulesTest {
	@Test
	void vanillaXpTotals() {
		assertEquals(0, PawnRules.totalXpForLevel(0));
		assertEquals(7, PawnRules.totalXpForLevel(1));
		assertEquals(352, PawnRules.totalXpForLevel(16));
		assertEquals(1395, PawnRules.totalXpForLevel(30));
		assertEquals(2920, PawnRules.totalXpForLevel(40));
	}

	@Test
	void xpStakeValue() {
		// levels 25..30: 1395 − 910 = 485 points → floor(485 / 4) = 121 chips
		assertEquals(485, PawnRules.xpPoints(30, 5));
		assertEquals(121, PawnRules.xpStakeValue(30, 5, 4));
		assertTrue(PawnRules.xpStakeAllowed(30, 30, 30));
		assertFalse(PawnRules.xpStakeAllowed(30, 31, 40), "more than current level");
		assertFalse(PawnRules.xpStakeAllowed(40, 31, 30), "more than wager.xp.maxLevels");
		assertFalse(PawnRules.xpStakeAllowed(5, 0, 30));
	}

	@Test
	void heartStakeLimits() {
		assertTrue(PawnRules.heartStakeAllowed(3, 0, 3, 5, 20));
		assertFalse(PawnRules.heartStakeAllowed(4, 0, 3, 5, 20), "max per bet");
		assertFalse(PawnRules.heartStakeAllowed(3, 3, 3, 5, 14), "max total");
		assertFalse(PawnRules.heartStakeAllowed(2, 0, 3, 5, 12), "max health would drop below 10 HP");
		assertTrue(PawnRules.heartStakeAllowed(1, 0, 3, 5, 12));
		assertEquals(1000, PawnRules.soulValue(250, 1000));
		assertEquals(5000, PawnRules.soulValue(5000, 1000));
	}

	@Test
	void betLimits() {
		assertEquals(BetLimits.Violation.NONE, BetLimits.check(50, 1, 0, 100, 1000));
		assertEquals(BetLimits.Violation.TOO_LOW, BetLimits.check(50, 100, 0, 1000, 1000));
		assertEquals(BetLimits.Violation.TIER_MAX, BetLimits.check(150, 1, 0, 100, 1000));
		assertEquals(BetLimits.Violation.TABLE_MAX, BetLimits.check(60, 1, 50, 100, 1000), "table max below tier max");
		assertEquals(BetLimits.Violation.TIER_MAX, BetLimits.check(150, 1, 200, 100, 1000), "tier max below table max");
		assertEquals(BetLimits.Violation.INSUFFICIENT_FUNDS, BetLimits.check(80, 1, 0, 100, 50));
		assertEquals(BetLimits.Violation.INVALID, BetLimits.check(0, 1, 0, 100, 50));
	}
}
