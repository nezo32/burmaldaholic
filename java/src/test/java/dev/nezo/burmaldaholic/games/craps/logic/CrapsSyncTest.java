package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** CS §9.2: CrapsSync v2 round trip; v1 is still decoded (side from the old shooter_dir rule, the client solves). */
class CrapsSyncTest {
	@Test
	void v2RoundTrip() {
		CrapsSync s = new CrapsSync(42, 9_876_543_210L, 6, 1, -77, 8, 0, 3, 4, 2, 1, 1811, CrapsSync.ANIM_V, 0);
		int[] a = s.encode();
		assertEquals(CrapsSync.VERSION, a[0]);
		assertEquals(s, CrapsSync.decode(a));
	}

	@Test
	void v2IsAcceptedWithTheLastShooterAsNext() {
		CrapsSync s = CrapsSync.decode(new int[] {2, 5, 0, 1234, 3, 4, 99, 0, 7, 1, 2, 0, 1, 1700, 2});
		assertEquals(1, s.shooterSide());
		assertEquals(1, s.nextSide(), "v2 has no next side: the dice stay at the last shooter's end");
		assertEquals(1700, s.restMs());
	}

	@Test
	void v1IsAccepted() {
		int[] v1 = {1, 5, 0, 1234, 3, 4, 99, 0, 7, 1, 2, 0};
		CrapsSync s = CrapsSync.decode(v1);
		assertEquals(5, s.rolls());
		assertEquals(1234L, s.rollTime());
		assertEquals(1, s.shooterSide(), "shooter_dir 1 means left (the v1 screen rule)");
		assertEquals(0, s.restMs(), "unknown: the client solves");
		assertEquals(1, s.animV());
		assertThrows(IllegalArgumentException.class, () -> CrapsSync.decode(new int[] {3, 1, 2}));
	}
}
