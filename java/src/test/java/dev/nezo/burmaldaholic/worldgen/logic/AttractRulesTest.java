package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Lane J-L3: casino attract mode (global.md §4.13). */
class AttractRulesTest {
	private static final Geometry.Box BOX = new Geometry.Box(0, 60, 0, 19, 70, 11);

	@Test
	void bulbsRunAroundTheRoofline() {
		List<double[]> bulbs = AttractRules.bulbs(BOX);
		assertEquals((20 + 12) * 2 / AttractRules.BULB_SPACING, bulbs.size());
		for (double[] b : bulbs) {
			assertEquals(71.15, b[1], 1e-9);
			boolean onEdge = b[0] == 0 || b[0] == 20 || b[2] == 0 || b[2] == 12;
			assertTrue(onEdge, "on the perimeter");
		}
	}

	@Test
	void chaseLightsOneInFour() {
		int n = 40;
		for (long t = 0; t < 30; t++) {
			int lit = 0;
			for (int i = 0; i < n; i++) if (AttractRules.lit(i, t)) lit++;
			assertEquals(n / AttractRules.CHASE_GROUP, lit);
		}
		assertNotEquals(AttractRules.lit(0, 0), AttractRules.lit(0, AttractRules.CHASE_TICKS));
	}

	@Test
	void arrivalWaveVisitsEveryBulbOnce() {
		int n = 50;
		boolean[] seen = new boolean[n];
		for (long t = 0; t < AttractRules.ARRIVAL_WAVE_TICKS; t++) for (int i = 0; i < n; i++) if (AttractRules.arrivalLit(i, n, t)) seen[i] = true;
		for (int i = 0; i < n - 1; i++) assertTrue(seen[i], "bulb " + i);
		for (int i = 0; i < n; i++) assertFalse(AttractRules.arrivalLit(i, n, AttractRules.ARRIVAL_WAVE_TICKS));
	}

	@Test
	void ambientRollsOncePerFortyTicks() {
		int rolls = 0;
		for (long t = 0; t < 400; t++) if (AttractRules.rolls(5, 64, -3, t)) rolls++;
		assertEquals(10, rolls);
	}

	@Test
	void chimeTimer() {
		long never = Long.MIN_VALUE / 2;
		assertFalse(AttractRules.chime(1199, 0, never, true, false), "idle < 60 s");
		assertTrue(AttractRules.chime(1200, 0, never, true, false));
		assertFalse(AttractRules.chime(1200, 0, never, false, false), "nobody near");
		assertFalse(AttractRules.chime(1200, 0, never, true, true), "someone playing");
		assertFalse(AttractRules.chime(2000, 0, 1200, true, false), "120 s cooldown");
		assertTrue(AttractRules.chime(3600, 0, 1200, true, false));
	}

	@Test
	void themedLocations() {
		assertEquals(AttractRules.VILLAGE, AttractRules.theme(CasinoKind.VILLAGE_CASINO));
		assertEquals(AttractRules.PIGLIN, AttractRules.theme(CasinoKind.PIGLIN_PARLOR));
		assertEquals(AttractRules.END, AttractRules.theme(CasinoKind.HIGH_ROLLER));
		assertNotEquals(AttractRules.VILLAGE.bulbOn(), AttractRules.END.bulbOn());
	}
}
