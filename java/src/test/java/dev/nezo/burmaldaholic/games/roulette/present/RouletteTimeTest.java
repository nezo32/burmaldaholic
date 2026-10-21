package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** S1: the roulette spin time stays monotonic and smooth while the raw shared clock jumps with every late packet. */
class RouletteTimeTest {
	private static final double FRAME = 1000 / 60.0;

	@Test
	void jitteryPacketsNeverMoveTimeBackwardsOrJump() {
		RouletteTime time = new RouletteTime();
		Random rng = new Random(7);
		double offset = 0;
		double prev = Double.NaN;
		double maxStep = 0;
		for (int k = 0; k < 600; k++) {
			double wall = k * FRAME;
			// every ~6 frames a state packet re-syncs the raw clock by ±200 ms of latency jitter
			if (k % 6 == 0) offset = (rng.nextDouble() - 0.5) * 400;
			double t = time.advance(1, wall + offset, wall);
			if (!Double.isNaN(prev)) {
				assertTrue(t >= prev, "backwards at frame " + k);
				maxStep = Math.max(maxStep, t - prev);
			}
			prev = t;
		}
		assertTrue(maxStep <= FRAME * (1 + RouletteTime.SLEW) + 1e-9, "a frame advanced " + maxStep + " ms");
	}

	@Test
	void followsASteadyOffsetAndSnapsOnlyFarAhead() {
		RouletteTime time = new RouletteTime();
		assertEquals(0, time.advance(5, 0, 0), 1e-9);
		// the raw clock moves 300 ms ahead: caught up at +10 % (about 3 s), never overshoots
		double t = 0;
		for (int k = 1; k <= 300; k++) {
			double wall = k * FRAME;
			t = time.advance(5, wall + 300, wall);
			assertTrue(t <= wall + 300 + 1e-9);
		}
		assertEquals(300 * FRAME + 300, t, 10);
		// a 2 s hitch ahead snaps (nothing sensible to show in between)
		double wall = 301 * FRAME;
		assertEquals(wall + 2300, time.advance(5, wall + 2300, wall), 1e-9);
		// a new launch re-anchors at once
		assertEquals(-40, time.advance(6, -40, wall + FRAME), 1e-9);
	}
}
