package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK SpeedPolicyTest (decision D1). */
class SpeedPolicyTest {
	private static final double SLOW = SpeedPolicy.speedOf(50);
	private static final double NORMAL = SpeedPolicy.speedOf(100);
	private static final double TURBO = SpeedPolicy.speedOf(150);

	@Test
	void sharedTimeIsNeverSlowed() {
		for (boolean posts : new boolean[] {false, true}) {
			assertEquals(1000, SpeedPolicy.SHARED.scale(1000, SLOW, posts), 0);
			assertEquals(1000, SpeedPolicy.SHARED.scale(1000, NORMAL, posts), 0);
		}
		for (double s : new double[] {0.1, 0.5, 0.99, 1, 1.5, 3}) {
			for (boolean posts : new boolean[] {false, true}) {
				assertTrue(SpeedPolicy.SHARED.scale(1000, s, posts) >= 1000, "shared never slower than real time");
			}
		}
	}

	@Test
	void turboCompressesSharedOnlyWhereTheClientPostsTheResult() {
		assertEquals(1500, SpeedPolicy.SHARED.scale(1000, TURBO, true), 1e-9); // Plinko, house dice
		assertEquals(1000, SpeedPolicy.SHARED.scale(1000, TURBO, false), 0); // roulette, craps throw, PvP
		assertEquals(2000, SpeedPolicy.SHARED.duration(3000, TURBO, true), 1e-9);
	}

	@Test
	void localBeatsFollowTheSetting() {
		assertEquals(500, SpeedPolicy.LOCAL.scale(1000, SLOW, false), 1e-9);
		assertEquals(1500, SpeedPolicy.LOCAL.scale(1000, TURBO, false), 1e-9);
		assertEquals(1400, SpeedPolicy.LOCAL.duration(700, SLOW, true), 1e-9);
		for (SpeedPolicy p : SpeedPolicy.values()) {
			for (double s : new double[] {SLOW, NORMAL, TURBO}) {
				assertEquals(777, p.duration(p.scale(777, s, true), s, true), 1e-9, "duration inverts scale");
			}
		}
	}
}
