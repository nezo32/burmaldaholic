package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link BetGates#armDefault}: the chip a felt click places with nothing armed (review R-CDE m1). */
class BetGatesTest {
	private static final long[] CHIPS = {1, 5, 25, 100, 500};

	@Test
	void lastUsedWinsWhenItStillFits() {
		assertEquals(25, BetGates.armDefault(CHIPS, 5, 1000, 25));
		assertEquals(25, BetGates.armDefault(CHIPS, 5, 25));
	}

	@Test
	void chipsBelowTheMinimumAreSkipped() {
		assertEquals(5, BetGates.armDefault(CHIPS, 5, 1000, -1), "1 is below the table minimum 5");
		assertEquals(5, BetGates.armDefault(CHIPS, 5, 1000, 1), "a last-used chip below the minimum is not re-armed");
		assertEquals(100, BetGates.armDefault(CHIPS, 100, 1000, -1));
	}

	@Test
	void balanceLimitsTheChoice() {
		assertEquals(5, BetGates.armDefault(CHIPS, 5, 30, 100), "the last used 100 is not affordable");
		assertEquals(1, BetGates.armDefault(CHIPS, 5, 3, -1), "nothing at the minimum is affordable: the smallest affordable chip, never nothing");
		assertEquals(-1, BetGates.armDefault(CHIPS, 5, 0, 25));
		assertEquals(-1, BetGates.armDefault(new long[0], 1, 100, -1));
	}
}
