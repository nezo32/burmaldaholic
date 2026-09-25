package dev.nezo.burmaldaholic.loan.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lane J-L3: the Debt Collectors' arrival (global.md §4.9). */
class ArrivalPlanTest {
	@Test
	void threeKnocks() {
		assertEquals(1, ArrivalPlan.knocksDue(0));
		assertEquals(2, ArrivalPlan.knocksDue(280));
		assertEquals(3, ArrivalPlan.knocksDue(520));
		assertEquals(3, ArrivalPlan.knocksDue(5000));
	}

	@Test
	void vignettePulsesTwiceWithinTheFlashRule() {
		int peaks = 0;
		double prev = 0;
		boolean rising = false;
		for (int ms = 0; ms <= 1600; ms++) {
			double a = ArrivalPlan.vignette(ms, true);
			assertTrue(a >= 0 && a <= 0.30 + 1e-9);
			if (a < prev && rising) peaks++;
			rising = a > prev;
			prev = a;
		}
		assertEquals(2, peaks);
		assertEquals(0, ArrivalPlan.vignette(1500, true), 1e-9);
		assertEquals(0.20, ArrivalPlan.vignette(500, false), 1e-9, "flashes off: static 20 %");
		assertEquals(0, ArrivalPlan.vignette(1000, false), 1e-9);
	}

	@Test
	void smokeColumnsStayThinAndClimb() {
		double lastY = -1;
		for (int i = 0; i < ArrivalPlan.SMOKE_PER_MEMBER; i++) {
			double[] s = ArrivalPlan.smoke(i, 3);
			assertTrue(Math.hypot(s[0], s[2]) <= 0.35 + 1e-9);
			assertTrue(s[1] > lastY);
			assertTrue(s[3] >= 0 && s[3] < ArrivalPlan.SMOKE_SPREAD_MS);
			lastY = s[1];
		}
	}

	@Test
	void cardAndShake() {
		assertEquals(0, ArrivalPlan.cardAlpha(0), 1e-9);
		assertEquals(1, ArrivalPlan.cardAlpha(1000), 1e-9);
		assertEquals(0, ArrivalPlan.cardAlpha(ArrivalPlan.CARD_MS), 1e-9);
		assertEquals(0, ArrivalPlan.knockShake(30, true), 1e-9);
		assertEquals(0, ArrivalPlan.knockShake(200, false), 1e-9);
		assertTrue(Math.abs(ArrivalPlan.knockShake(290, false)) > 0);
	}
}
