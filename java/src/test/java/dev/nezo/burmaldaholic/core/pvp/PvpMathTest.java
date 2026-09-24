package dev.nezo.burmaldaholic.core.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import org.junit.jupiter.api.Test;

/** PVP.md §16.1 C1–C4 and W1 (same vectors as bedrock src/core/logic/pvp/math.test.ts). */
class PvpMathTest {
	@Test
	void rakeC1C2() {
		long[] pots = {16, 20, 34, 50, 200, 400, 999, 1000, 10000};
		long[] want = {0, 1, 1, 2, 6, 12, 30, 30, 300};
		for (int i = 0; i < pots.length; i++) {
			assertEquals(want[i], PvpMath.rake(pots[i], 300), "pot " + pots[i]);
		}
		assertEquals(0, PvpMath.rake(12345, 0));
		assertEquals(2, PvpMath.rake(30, 500));
		assertEquals(3, PvpMath.rake(25, 1000));
	}

	@Test
	void splitC3C4() {
		// A = 0, B = 1, C = 2
		assertArrayEquals(new long[] {48, 49}, PvpMath.split(97, new int[] {0, 1}, new int[] {1, 0}, 2));
		assertArrayEquals(new long[] {33, 33, 34}, PvpMath.split(100, new int[] {0, 1, 2}, new int[] {2, 0, 1}, 3));
	}

	@Test
	void wheelW1() {
		long[] stakes = {50, 150, 800};
		long[] us = {0, 49, 50, 199, 200, 999};
		int[] owners = {0, 0, 1, 1, 2, 2};
		for (int i = 0; i < us.length; i++) {
			assertEquals(owners[i], PvpMath.sliceOwner(stakes, us[i]));
		}
	}
}
