package dev.nezo.burmaldaholic.client.dealer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.client.dealer.DealerMotion.Pose;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import org.junit.jupiter.api.Test;

/** Dealer gestures (animation/cards.md §1.4): rest at both ends, the documented peaks, reduce motion halves them. */
class DealerMotionTest {
	@Test
	void everyGestureStartsAndEndsAtRest() {
		for (DealerGesture g : DealerGesture.values()) {
			Pose end = DealerMotion.pose(g, g.ms, 0.5f, false, 0);
			assertEquals(DealerMotion.REST_X, end.rightX(), 1e-6, g + " end");
			assertEquals(DealerMotion.REST_X, end.leftX(), 1e-6);
			if (g == DealerGesture.NONE) continue;
			Pose start = DealerMotion.pose(g, 0, 0.5f, false, 0);
			assertEquals(DealerMotion.REST_X, start.rightX(), 0.02, g + " start");
		}
	}

	@Test
	void peaksAndReducedMotion() {
		Pose deal = DealerMotion.pose(DealerGesture.DEAL, 150, 1f, false, 0);
		assertEquals(-1.1f, deal.rightX(), 0.01);
		assertEquals(0.35f, deal.rightY(), 0.01);
		Pose reduced = DealerMotion.pose(DealerGesture.DEAL, 150, 1f, true, 0);
		assertEquals(DealerMotion.REST_X + (-1.1f - DealerMotion.REST_X) / 2, reduced.rightX(), 0.01);
		Pose peek = DealerMotion.pose(DealerGesture.PEEK, 300, 0, false, 0);
		assertEquals(0.45f, peek.headX(), 0.01);
		for (int t = 0; t < 4000; t += 97) {
			Pose idle = DealerMotion.pose(DealerGesture.NONE, 0, 0, false, t);
			assertTrue(Math.abs(idle.rightX() - DealerMotion.REST_X) <= 0.03 + 1e-6);
		}
	}

	@Test
	void cuesFollowTheTimeline() {
		var tl = UthBeats.showdown(2, 0);
		DealerCueSource.Cue c = DealerCueSource.latest(tl, 100, b -> b.kind().equals(UthBeats.DEALER_FLIP) ? DealerGesture.FLIP : null, b -> 0f);
		assertNotNull(c);
		assertEquals(DealerGesture.FLIP, c.gesture());
		assertEquals(100, c.ageMs(), 1e-9);
		// the second flip (200 ms) restarts the gesture
		c = DealerCueSource.latest(tl, 250, b -> b.kind().equals(UthBeats.DEALER_FLIP) ? DealerGesture.FLIP : null, b -> 0f);
		assertEquals(50, c.ageMs(), 1e-9);
		assertNull(DealerCueSource.latest(tl, 5000, b -> b.kind().equals(UthBeats.DEALER_FLIP) ? DealerGesture.FLIP : null, b -> 0f));
	}

	@Test
	void houseDealerGesturesAreBoundedAndHalvedByReduceMotion() {
		for (DealerGesture g : new DealerGesture[] {DealerGesture.SPIN, DealerGesture.STICK_PULL, DealerGesture.STICK_PUSH, DealerGesture.POT_PUSH}) {
			float peak = 0, peakReduced = 0;
			for (int t = 0; t <= g.ms; t += 5) {
				Pose p = DealerMotion.pose(g, t, 1f, false, 0);
				Pose r = DealerMotion.pose(g, t, 1f, true, 0);
				for (float v : new float[] {p.rightX(), p.rightY(), p.rightZ(), p.leftX(), p.leftY(), p.leftZ(), p.headX()}) {
					assertTrue(Math.abs(v) <= 1.6f, g + " bounded at " + t + "ms: " + p);
				}
				peak = Math.max(peak, Math.abs(p.rightX() - DealerMotion.REST_X));
				peakReduced = Math.max(peakReduced, Math.abs(r.rightX() - DealerMotion.REST_X));
			}
			assertTrue(peak > 0.3f, g + " visibly moves");
			assertEquals(peak / 2, peakReduced, 0.02, g + " reduce motion halves the arm");
		}
	}

	@Test
	void enderPoseRestsOnTheFelt() {
		Pose rest = DealerMotion.ender(Pose.REST);
		assertEquals(DealerMotion.REST_X + DealerMotion.ENDER_ARM_OFFSET, rest.rightX(), 1e-6);
		Pose deal = DealerMotion.pose(DealerGesture.DEAL, 150, 1f, false, 0);
		Pose ender = DealerMotion.ender(deal);
		assertEquals((deal.rightX() - DealerMotion.REST_X) * DealerMotion.ENDER_AMPLITUDE, ender.rightX() - rest.rightX(), 1e-5);
		assertEquals(deal.rightY() * 0.7f, ender.rightY(), 1e-6);
	}
}
