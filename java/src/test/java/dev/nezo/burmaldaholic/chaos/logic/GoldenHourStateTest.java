package dev.nezo.burmaldaholic.chaos.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldenHourStateTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();

	@Test
	void lifecycleAndCooldown() {
		GoldenHourState s = new GoldenHourState();
		assertFalse(s.isActive(0));
		assertTrue(s.canStart(0));
		s.start(1000, 3600, 24000);
		assertEquals(1, s.id);
		assertTrue(s.isActive(1000));
		assertEquals(3600, s.remaining(1000));
		assertEquals(1, s.remaining(4599));
		assertFalse(s.isActive(4600));
		assertEquals(0, s.remaining(4600));
		assertFalse(s.canStart(2000), "active");
		assertFalse(s.canStart(4600 + 23999), "cooldown counts from the end");
		assertTrue(s.canStart(4600 + 24000));
		assertTrue(s.canStart(500), "clock went backwards");
	}

	@Test
	void stopEndsNowAndRestartsCooldown() {
		GoldenHourState s = new GoldenHourState();
		s.start(0, 3600, 24000);
		s.stop(100, 24000);
		assertFalse(s.isActive(100));
		assertFalse(s.canStart(100 + 23999));
		assertTrue(s.canStart(100 + 24000));
		s.stop(200, 0); // not active: no-op
		assertFalse(s.canStart(200));
	}

	@Test
	void bonusIsNetWinTimesMultiplierMinusOne() {
		assertEquals(100, GoldenHourState.bonus(100, 2.0, 0, 5000).amount());
		assertEquals(150, GoldenHourState.bonus(100, 2.5, 0, 5000).amount());
		assertEquals(33, GoldenHourState.bonus(33, 2.0, 0, 5000).amount());
		assertEquals(16, GoldenHourState.bonus(33, 1.5, 0, 5000).amount(), "floored");
		assertEquals(0, GoldenHourState.bonus(0, 2.0, 0, 5000).amount(), "push");
		assertEquals(0, GoldenHourState.bonus(-50, 2.0, 0, 5000).amount(), "loss");
		assertEquals(0, GoldenHourState.bonus(100, 1.0, 0, 5000).amount(), "multiplier 1 = no bonus");
	}

	@Test
	void capPerPlayerPerGoldenHour() {
		GoldenHourState s = new GoldenHourState();
		s.start(0, 3600, 0);
		GoldenHourState.Bonus b1 = s.award(A, 4000, 2.0, 5000);
		assertEquals(4000, b1.amount());
		assertFalse(b1.capReached());
		GoldenHourState.Bonus b2 = s.award(A, 4000, 2.0, 5000);
		assertEquals(1000, b2.amount());
		assertTrue(b2.capReached());
		GoldenHourState.Bonus b3 = s.award(A, 4000, 2.0, 5000);
		assertEquals(0, b3.amount());
		assertFalse(b3.capReached(), "announced once");
		assertEquals(5000, s.award(B, 10000, 2.0, 5000).amount(), "cap is per player");
		s.start(10000, 3600, 0);
		assertEquals(4000, s.award(A, 4000, 2.0, 5000).amount(), "new Golden Hour resets the tally");
	}

	@Test
	void exactCapHitIsReported() {
		GoldenHourState.Bonus b = GoldenHourState.bonus(5000, 2.0, 0, 5000);
		assertEquals(5000, b.amount());
		assertTrue(b.capReached());
		assertEquals(0, GoldenHourState.bonus(100, 2.0, 0, 0).amount(), "cap 0 disables the bonus");
	}

	/** Monte-Carlo: with the default 2× and cap, Golden Hour exactly doubles net wins below the cap. */
	@Test
	void doublesNetWinningsBelowCap() {
		java.util.SplittableRandom r = new java.util.SplittableRandom(42);
		GoldenHourState s = new GoldenHourState();
		s.start(0, 3600, 0);
		long net = 0;
		long bonus = 0;
		for (int i = 0; i < 1000; i++) {
			long win = r.nextInt(5);
			net += win;
			bonus += s.award(A, win, 2.0, Long.MAX_VALUE).amount();
		}
		assertEquals(net, bonus);
	}
}
