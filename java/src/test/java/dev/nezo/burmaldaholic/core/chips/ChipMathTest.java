package dev.nezo.burmaldaholic.core.chips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	/** Review M3: a withdrawal is capped to a bounded number of item stacks; denominations are validated. */
	@Test
	void withdrawalCappedToStacks() {
		assertTrue(ChipMath.isDenomination(500) && ChipMath.isDenomination(1));
		assertFalse(ChipMath.isDenomination(0) || ChipMath.isDenomination(7) || ChipMath.isDenomination(-5));
		assertEquals(1328, ChipMath.capToStacks(1328, 0, 36, 64), "small amounts are untouched");
		long capped = ChipMath.capToStacks(1_000_000_000L, 0, 36, 64);
		assertTrue(capped > 0 && ChipMath.stacks(ChipMath.split(capped), 64) <= 36, "fits in 36 stacks: " + capped);
		assertTrue(ChipMath.stacks(ChipMath.split(capped + 500), 64) > 36, "and is (close to) the largest that does");
		long ones = ChipMath.capToStacks(1_000_000L, 1, 36, 64);
		assertEquals(36 * 64, ones, "all-ones withdrawal: 36 full stacks");
		for (long n : new long[] {0, 1, 63, 64, 65, 36 * 64 * 500L, 36 * 64 * 500L + 499, 123_456_789L}) {
			for (int d : new int[] {0, 500, 100, 25, 5, 1}) {
				long c = ChipMath.capToStacks(n, d, 36, 64);
				assertTrue(c <= n && ChipMath.stacks(d > 0 ? ChipMath.split(c, d) : ChipMath.split(c), 64) <= 36, n + "/" + d);
			}
		}
	}
}
