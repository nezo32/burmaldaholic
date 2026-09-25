package dev.nezo.burmaldaholic.lastchance.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lane J-L3: the Last Chance coin always lands on the server's face (global.md §4.8, §6.3). */
class CoinFlipTimelineTest {
	private static int distanceToFace(int frame, boolean heads) {
		return Math.abs(frame - CoinFlipTimeline.face(heads));
	}

	@Test
	void landsOnThePayloadFaceInEveryMode() {
		for (boolean heads : new boolean[] {true, false})
			for (boolean reduced : new boolean[] {true, false})
				for (boolean flashes : new boolean[] {true, false}) {
					int land = CoinFlipTimeline.landMs(reduced);
					for (int ms = land; ms < CoinFlipTimeline.TOTAL_MS; ms += 7) {
						CoinFlipTimeline.Frame f = CoinFlipTimeline.frame(ms, heads, reduced, flashes);
						assertEquals(CoinFlipTimeline.face(heads), f.coinFrame(), "stays on the face");
						assertTrue(f.landed());
						assertEquals(2, f.title());
					}
					assertFalse(CoinFlipTimeline.frame(land - 1, heads, reduced, flashes).landed(), "no result before the landing");
				}
	}

	@Test
	void spinIsMonotonicAndEndsOnTheRightParity() {
		for (boolean heads : new boolean[] {true, false}) {
			double prev = -1;
			for (int ms = 0; ms <= CoinFlipTimeline.LAND_MS; ms++) {
				double p = CoinFlipTimeline.phase(ms, heads);
				assertTrue(p >= prev, "monotonic");
				prev = p;
			}
			assertEquals(heads ? CoinFlipTimeline.HEADS_HALF_TURNS : CoinFlipTimeline.TAILS_HALF_TURNS, prev, 1e-9);
			assertEquals(CoinFlipTimeline.face(heads), CoinFlipTimeline.frameOfPhase(prev));
		}
	}

	@Test
	void lastFramesOnlyApproachThePayloadFace() {
		for (boolean heads : new boolean[] {true, false}) {
			int turns = heads ? CoinFlipTimeline.HEADS_HALF_TURNS : CoinFlipTimeline.TAILS_HALF_TURNS;
			int prevDistance = Integer.MAX_VALUE;
			boolean inLastHalfTurn = false;
			for (int ms = CoinFlipTimeline.LAUNCH_MS; ms < CoinFlipTimeline.LAND_MS; ms++) {
				double p = CoinFlipTimeline.phase(ms, heads);
				if (p < turns - 1) continue;
				inLastHalfTurn = true;
				int f = CoinFlipTimeline.frame(ms, heads, false, true).coinFrame();
				int d = distanceToFace(f, heads);
				assertTrue(d <= prevDistance, "never past the face and back (ms " + ms + ")");
				if (ms > CoinFlipTimeline.LAND_MS - 150) assertNotEquals(CoinFlipTimeline.face(!heads), f, "the last frames never show the other face");
				prevDistance = d;
			}
			assertTrue(inLastHalfTurn);
		}
	}

	@Test
	void storyboardTimings() {
		assertEquals(26, CoinFlipTimeline.LAND_TICKS, "server delay of the heads sound / totem");
		assertEquals(3300, CoinFlipTimeline.TOTAL_MS);
		assertEquals(0, CoinFlipTimeline.frame(CoinFlipTimeline.TOTAL_MS, true, false, true).alpha(), 1e-9);
		assertEquals(1, CoinFlipTimeline.frame(CoinFlipTimeline.FADE_AT_MS - 1, true, false, true).alpha(), 1e-9);
		assertEquals(-CoinFlipTimeline.LAUNCH_PX, CoinFlipTimeline.frame(CoinFlipTimeline.APEX_MS, true, false, true).coinY(), 1e-6);
		assertEquals(0, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS, true, false, true).coinY(), 1e-9);
		assertEquals(0, CoinFlipTimeline.frame(900, true, true, true).coinY(), 1e-9, "reduced motion: no launch");
		assertTrue(CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS, true, false, true).titleScale() > 1.2, "result pops");
		assertEquals(1, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 200, true, false, true).titleScale(), 1e-9);
	}

	@Test
	void vignetteHonoursFlashesAndReducedMotion() {
		for (int ms = 0; ms < CoinFlipTimeline.TOTAL_MS; ms += 5) {
			for (boolean heads : new boolean[] {true, false}) {
				assertTrue(CoinFlipTimeline.frame(ms, heads, false, true).vignette() <= 0.45 + 1e-9);
				CoinFlipTimeline.Frame off = CoinFlipTimeline.frame(ms, heads, false, false);
				assertTrue(off.vignette() <= 0.25 + 1e-9, "flashes off: ≤ 25 %");
				assertEquals(CoinFlipTimeline.RED_BLACK, off.vignetteColor(), "flashes off: no colour change on landing");
				assertEquals(0, CoinFlipTimeline.frame(ms, heads, true, true).vignette(), 1e-9, "reduced motion: no vignette");
			}
		}
		assertEquals(CoinFlipTimeline.GOLD, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 400, true, false, true).vignetteColor());
		assertEquals(CoinFlipTimeline.RED, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 400, false, false, true).vignetteColor());
	}

	@Test
	void cracksOnlyOnTails() {
		assertEquals(-1, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 300, true, false, true).crack());
		assertEquals(0, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 20, false, false, true).crack());
		assertEquals(1, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS + 300, false, false, true).crack());
		assertEquals(-1, CoinFlipTimeline.frame(CoinFlipTimeline.LAND_MS - 20, false, false, true).crack());
	}
}
