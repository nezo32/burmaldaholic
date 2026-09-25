package dev.nezo.burmaldaholic.vip.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lane J-L3: VIP tier-up timeline (global.md §4.11); the badge that stays is always the server's tier. */
class TierUpPlanTest {
	@Test
	void singleTierStoryboard() {
		TierUpPlan p = new TierUpPlan(VipRules.SILVER, VipRules.GOLD, false);
		assertEquals(0, p.flicks());
		assertEquals(0, p.badgeScale(50), 1e-9, "badge pops at 100 ms");
		assertTrue(p.badgeScale(100 + 200) > 1.05, "overshoots to ~1.15");
		assertEquals(1, p.badgeScale(100 + TierUpPlan.BADGE_POP_MS), 1e-9);
		double max = 0;
		for (int ms = 100; ms < 450; ms++) max = Math.max(max, p.badgeScale(ms));
		assertEquals(1.15, max, 0.02);
		assertEquals(-1, p.shineFrame(449));
		assertEquals(0, p.shineFrame(450));
		assertEquals(7, p.shineFrame(450 + 7 * 40));
		assertEquals(-1, p.shineFrame(450 + 8 * 40));
		assertEquals(0, p.textAlpha(499), 1e-9);
		assertEquals(1, p.textAlpha(800), 1e-9);
		assertEquals(2600, p.totalMs());
		assertEquals(0, p.alpha(2600), 1e-9);
		assertTrue(p.done(2600) && !p.done(2599));
		assertEquals(4, p.arpeggioNotes(1000));
		assertEquals(1, p.arpeggioNotes(100));
	}

	@Test
	void multiTierJumpFlicksAndEndsOnTheServerTier() {
		TierUpPlan p = new TierUpPlan(VipRules.BRONZE, VipRules.DIAMOND, false);
		assertEquals(3, p.flicks());
		assertEquals(VipRules.SILVER, p.badgeTier(0));
		assertEquals(VipRules.GOLD, p.badgeTier(100));
		assertEquals(VipRules.PLATINUM, p.badgeTier(250));
		for (int ms = p.offsetMs(); ms < p.totalMs(); ms += 3) assertEquals(VipRules.DIAMOND, p.badgeTier(ms));
		assertEquals(300 + 2600, p.totalMs());
	}

	@Test
	void reducedMotionIsAStaticCard() {
		TierUpPlan p = new TierUpPlan(VipRules.BRONZE, VipRules.NETHERITE, true);
		assertEquals(0, p.flicks());
		assertEquals(VipRules.NETHERITE, p.badgeTier(0));
		assertEquals(1, p.badgeScale(0), 1e-9);
		assertEquals(-1, p.shineFrame(500));
		assertEquals(0, p.rays(1000), 1e-9);
		assertFalse(p.embers(VipRules.NETHERITE));
		assertTrue(new TierUpPlan(VipRules.DIAMOND, VipRules.NETHERITE, false).embers(VipRules.NETHERITE));
	}
}
