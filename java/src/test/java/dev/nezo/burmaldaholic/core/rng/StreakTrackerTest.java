package dev.nezo.burmaldaholic.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StreakTrackerTest {
	@Test
	void countsConsecutiveWinsAndLosses() {
		StreakTracker t = new StreakTracker();
		UUID p = UUID.randomUUID();
		assertEquals(1, t.record(p, 1));
		assertEquals(2, t.record(p, 5));
		assertEquals(2, t.record(p, 0)); // push keeps streak
		assertEquals(-1, t.record(p, -1));
		assertEquals(-2, t.record(p, -1));
		assertEquals(1, t.record(p, 1));
		t.reset(p);
		assertEquals(0, t.streak(p));
	}
}
