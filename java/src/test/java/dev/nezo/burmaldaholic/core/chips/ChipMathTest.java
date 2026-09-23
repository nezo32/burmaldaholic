package dev.nezo.burmaldaholic.core.chips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChipMathTest {
	@Test
	void greedySplitUsesLargestDenominations() {
		assertArrayEquals(new long[] {2, 3, 1, 0, 3}, ChipMath.split(1328));
		assertArrayEquals(new long[] {0, 0, 0, 0, 0}, ChipMath.split(0));
		assertArrayEquals(new long[] {0, 0, 0, 1, 4}, ChipMath.split(9));
		for (long n = 0; n < 2000; n++) {
			long[] c = ChipMath.split(n);
			long sum = 0;
			for (int i = 0; i < c.length; i++) {
				sum += c[i] * ChipMath.DENOMINATIONS[i];
			}
			assertEquals(n, sum);
		}
	}

	@Test
	void preferredDenominationSplit() {
		assertArrayEquals(new long[] {0, 0, 21, 0, 3}, ChipMath.split(528, 25));
		assertArrayEquals(ChipMath.split(528), ChipMath.split(528, 7), "unknown denomination = auto");
	}

	@Test
	void withdrawableHonoursDebtAndDefault() {
		assertEquals(11_900, ChipMath.withdrawable(12_500, 600, false));
		assertEquals(0, ChipMath.withdrawable(100, 600, false));
		assertEquals(0, ChipMath.withdrawable(12_500, 600, true));
		assertEquals(3, ChipMath.emeraldsForChips(35, 10));
	}
}
