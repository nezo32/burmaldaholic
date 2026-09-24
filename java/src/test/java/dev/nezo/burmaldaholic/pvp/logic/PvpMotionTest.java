package dev.nezo.burmaldaholic.pvp.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PvpMotionTest {
	@Test
	void drumrollHasEightAcceleratingHits() {
		int total = 0;
		for (int t = -1; t < 60; t++) total += PvpMotion.drumHitsBetween(t, t + 1);
		assertEquals(8, total);
		for (int i = 2; i < PvpMotion.DRUM_TICKS.length; i++) {
			assertTrue(PvpMotion.DRUM_TICKS[i] - PvpMotion.DRUM_TICKS[i - 1] <= PvpMotion.DRUM_TICKS[i - 1] - PvpMotion.DRUM_TICKS[i - 2]);
		}
		assertEquals(0, PvpMotion.tremble(5, true));
	}

	@Test
	void flipShowsTheFaceFromTheMidpoint() {
		assertArrayEquals(new double[] {1, 0}, PvpMotion.flip(-1, false));
		assertEquals(0, PvpMotion.flip(80, false)[1]);
		assertEquals(1, PvpMotion.flip(100, false)[1]);
		assertArrayEquals(new double[] {1, 1}, PvpMotion.flip(500, false));
		assertArrayEquals(new double[] {1, 1}, PvpMotion.flip(1, true));
	}

	@Test
	void bannerAndGrudgeEndInvisible() {
		assertEquals(0, PvpMotion.banner(5000, false)[1]);
		assertEquals(1, PvpMotion.banner(1000, false)[1]);
		assertEquals(0, PvpMotion.grudge(PvpMotion.GRUDGE_END_MS, false)[1], 1e-9);
		assertEquals(0, PvpMotion.grudge(350, true)[2]);
		assertEquals(1, PvpMotion.countdownDigit(500, false)[0], 1e-9);
	}

	@Test
	void chipsAndPiles() {
		assertEquals(0, PvpMotion.recordChip(5, 3));
		assertEquals(1, PvpMotion.recordChip(3, 5));
		assertEquals(2, PvpMotion.recordChip(4, 4));
		assertEquals(0, PvpMotion.potPile(900, 100));
		assertEquals(1, PvpMotion.potPile(1000, 100));
		assertEquals(2, PvpMotion.potPile(5000, 100));
	}
}
