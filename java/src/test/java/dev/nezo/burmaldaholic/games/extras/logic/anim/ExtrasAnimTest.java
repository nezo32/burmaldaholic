package dev.nezo.burmaldaholic.games.extras.logic.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Fidelity tests of extras-pvp.md §14 (items 1–3, 5) for the pure extras motion (SX2). */
class ExtrasAnimTest {
	// ---- 1. coin --------------------------------------------------------------------------------------------------

	@Test
	void coinFrameFunction() {
		assertEquals(0, CoinAnim.frame(0));
		assertEquals(11, CoinAnim.frame(1));
		assertEquals(6, CoinAnim.frame(0.5));
		assertEquals(0, CoinAnim.frame(10));
		assertEquals(11, CoinAnim.frame(11));
		assertEquals(6, CoinAnim.frame(1.5));
		for (double h = 0; h < 12; h += 0.01) {
			int f = CoinAnim.frame(h);
			assertTrue(f >= 0 && f < CoinAnim.FRAMES);
		}
	}

	@Test
	void coinTossEndsOnTheServerFace() {
		for (boolean heads : new boolean[] {true, false}) {
			int face = CoinAnim.faceFrame(heads);
			int other = CoinAnim.faceFrame(!heads);
			// the terminal pose, skip, late open and reduce motion all show the face
			assertEquals(face, CoinAnim.toss(heads, true, CoinAnim.TOTAL_MS, false).frame());
			assertEquals(face, CoinAnim.toss(heads, false, 1e9, false).frame());
			assertEquals(face, CoinAnim.terminal(heads).frame());
			assertEquals(face, CoinAnim.toss(heads, false, CoinAnim.REDUCED_MS, true).frame());
			// from the first contact on, the frame is the face
			for (double ms = CoinAnim.FLIGHT_END_MS; ms <= CoinAnim.TOTAL_MS + 100; ms += 5) {
				assertEquals(face, CoinAnim.toss(heads, true, ms, false).frame(), "ms " + ms);
			}
			// the last 3 frames (60 fps) before the contact never show the opposite full face
			for (int k = 1; k <= 3; k++) {
				assertNotEquals(other, CoinAnim.toss(heads, true, CoinAnim.FLIGHT_END_MS - k * 16.7, false).frame());
			}
			// the landing is vertical only and never above the apex
			for (double ms = 0; ms < CoinAnim.TOTAL_MS; ms += 10) {
				CoinAnim.Pose p = CoinAnim.toss(heads, true, ms, false);
				assertTrue(p.dy() >= -CoinAnim.APEX - 1e-9 && p.dy() <= 3 + 1e-9, "dy " + p.dy());
			}
		}
	}

	@Test
	void coinGlintOnlyOnWin() {
		assertTrue(CoinAnim.toss(true, true, 1300, false).glint() >= 0);
		assertTrue(CoinAnim.toss(true, false, 1300, false).glint() < 0);
	}

	@Test
	void duelLandingEndsOnTheFace() {
		SeedMix.FxRng rng = new SeedMix.FxRng(7);
		for (int i = 0; i < 200; i++) {
			double h0 = rng.nextDouble() * 20;
			for (boolean heads : new boolean[] {true, false}) {
				double target = CoinAnim.landTarget(h0, heads);
				assertTrue(target >= h0 + 1.5);
				assertEquals(heads, ((long) target) % 2 == 0);
				double[] end = CoinAnim.duelLand(h0, -40, heads, 2000, 1);
				assertEquals(CoinAnim.faceFrame(heads), (int) end[2]);
				assertEquals(1, end[3]);
				assertEquals(40, end[0], 1e-9);
				assertEquals(CoinAnim.faceFrame(heads), (int) CoinAnim.duelLand(h0, -40, heads, 499.999, 0)[2]);
			}
		}
	}

	// ---- 2. wheel -------------------------------------------------------------------------------------------------

	@Test
	void wheelLandsOnTheTargetForEveryIndex() {
		int n = Wheel.DEFAULT_SEGMENTS.size();
		double s = WheelAnim.segment(n);
		SeedMix.FxRng rng = new SeedMix.FxRng(11);
		for (int target = 0; target < n; target++) {
			for (int k = 0; k < 100; k++) {
				double from = (rng.nextDouble() - 0.5) * 2000;
				int seed = SeedMix.mix(target, k);
				double rest = WheelAnim.restAngle(target, n, seed);
				double room = WheelAnim.settleRoom(rest, target, n);
				double end = WheelAnim.angle(from, rest, 4000, 4000, room);
				// final angle ≡ rest (mod 360) within 0.01°
				double diff = (((end - rest) % 360) + 540) % 360 - 180;
				assertEquals(0, diff, 0.01);
				assertEquals(target, WheelAnim.segmentAt(end, n));
				// the settle stays inside the target segment
				for (double ms = 3750; ms <= 4000; ms += 5) {
					assertEquals(target, WheelAnim.segmentAt(WheelAnim.angle(from, rest, 4000, ms, room), n), "settle ms " + ms);
				}
				// the rest angle is never within 15 % of a boundary
				double off = rest + target * s;
				assertTrue(Math.abs(off) <= 0.15 * s + 1e-9);
				// reduce motion and skip end on the same angle
				assertEquals(target, WheelAnim.segmentAt(WheelAnim.reduced(from, rest, WheelAnim.REDUCED_MS), n));
				assertEquals(end, WheelAnim.skip(from + 123, end, WheelAnim.SKIP_MS), 1e-9);
			}
		}
	}

	@Test
	void wheelCurveIsTheSameForEveryOutcome() {
		// no near-miss shaping: the normalised progress at a given time does not depend on the target
		double p1 = progress(0, WheelAnim.restAngle(3, 54, 1), 2000);
		double p2 = progress(0, WheelAnim.restAngle(40, 54, 1), 2000);
		assertEquals(p1, p2, 0.02);
	}

	private static double progress(double from, double rest, double ms) {
		double end = WheelAnim.angle(from, rest, 4000, 4000, 1);
		return (WheelAnim.angle(from, rest, 4000, ms, 1) - from) / (end - from);
	}

	@Test
	void wheelPartyHairKeepsLengthAndFinalAngle() {
		SeedMix.FxRng rng = new SeedMix.FxRng(3);
		for (int i = 0; i < 100; i++) {
			double from = rng.nextDouble() * 360;
			double rest = rng.nextDouble() * 360;
			double end = WheelAnim.angleHair(from, rest, 5000, 5000, WheelAnim.hairCrawl(rng.nextDouble() * 3));
			assertEquals(0, (((end - rest) % 360) + 540) % 360 - 180, 0.01);
			// the last second crawls ≤ 8°
			double before = WheelAnim.angleHair(from, rest, 5000, 4000, 2);
			assertTrue(end - before <= WheelAnim.HAIR_MAX_DEG + 1e-9);
			// monotone after the pull-back
			double prev = WheelAnim.angleHair(from, rest, 5000, 300, 2);
			for (double ms = 300; ms <= 5000; ms += 10) {
				double a = WheelAnim.angleHair(from, rest, 5000, ms, 2);
				assertTrue(a >= prev - 1e-9);
				prev = a;
			}
		}
	}

	@Test
	void flapperAndPegs() {
		double pitch = WheelAnim.segment(54);
		assertEquals(-14, WheelAnim.flapper(-pitch / 2, pitch), 1e-9);
		assertEquals(0, WheelAnim.flapper(-pitch / 2 + 0.9 * pitch, pitch), 0.5);
		assertEquals(WheelAnim.pegCount(0, pitch) + 1, WheelAnim.pegCount(pitch, pitch));
		assertTrue(WheelAnim.tickPitch(0, 10) > WheelAnim.tickPitch(10, 10));
	}

	// ---- 3. plinko ------------------------------------------------------------------------------------------------

	@Test
	void plinkoEndsInTheServerBinForAllPaths() {
		for (int path = 0; path < 4096; path++) {
			int bin = Integer.bitCount(path);
			assertEquals(bin, PlinkoAnim.bin(path));
			PlinkoAnim.Ball end = PlinkoAnim.sample(path, 200, PlinkoAnim.totalMs(200));
			assertEquals(PlinkoAnim.binX(bin), end.x(), 1e-9);
			assertTrue(end.landed());
			// the column after row r is the number of rights so far
			for (int r = 0; r < 12; r++) {
				PlinkoAnim.Ball b = PlinkoAnim.sample(path, 200, PlinkoAnim.contactMs(200, r) + 1e-6);
				assertEquals(PlinkoAnim.pegX(r, PlinkoAnim.rights(path, r)), b.x(), 1e-6, "path " + path + " row " + r);
			}
			// mini boards
			double[] u = PlinkoAnim.unit(path, 12, 12);
			assertEquals(bin - 6, u[0], 1e-9);
		}
	}

	@Test
	void plinkoNeverLightsANeighbour() {
		int path = 0b101101010110;
		Set<Integer> hit = new HashSet<>();
		for (double ms = 0; ms < PlinkoAnim.totalMs(200); ms += 5) {
			for (int r = 0; r < 12; r++) {
				for (int j = 0; j <= r; j++) {
					if (PlinkoAnim.pegState(path, 200, ms, r, j) != 0) hit.add(r * 100 + j);
				}
			}
		}
		assertEquals(12, hit.size());
		for (int r = 0; r < 12; r++) assertTrue(hit.contains(r * 100 + PlinkoAnim.rights(path, r)));
	}

	@Test
	void plinkoTiers() {
		assertEquals(0, PlinkoAnim.tier(0.6));
		assertEquals(1, PlinkoAnim.tier(1));
		assertEquals(2, PlinkoAnim.tier(2));
		assertEquals(3, PlinkoAnim.tier(33));
		assertEquals(4, PlinkoAnim.tier(170));
		assertTrue(PlinkoAnim.pegPitch(0, 5) < PlinkoAnim.pegPitch(0b11111, 5));
	}

	// ---- 4. scratch mask ------------------------------------------------------------------------------------------

	@Test
	void scratchMaskEraseAndEdges() {
		ScratchMask m = ScratchMask.solo();
		assertTrue(m.untouched());
		assertEquals(0, m.edge(5, 5));
		assertEquals(165, m.coveredCount());
		m.erase(5, 5);
		assertFalse(m.covered(5, 5));
		assertEquals(4, m.edge(5, 4)); // south neighbour scratched
		assertEquals(8, m.edge(6, 5));
		assertEquals(2, m.edge(4, 5));
		assertEquals(1, m.edge(5, 6));
		assertTrue(m.covered(-1, 0)); // outside = covered, no torn lip on the border
		m.eraseLine(0, 0, 60, 44, 6);
		assertTrue(m.erased() > 0.1 && m.erased() < 0.6);
	}

	@Test
	void autoSwipeClearsTheCell() {
		for (ScratchMask m : new ScratchMask[] {ScratchMask.solo(), ScratchMask.showdown()}) {
			int w = m.nx() * ScratchMask.TILE;
			int h = m.ny() * ScratchMask.TILE;
			double r = ScratchMask.swipeRadius(h);
			double[] prev = ScratchMask.swipePoint(w, h, 0);
			for (int i = 1; i <= 60; i++) {
				double[] p = ScratchMask.swipePoint(w, h, i / 60.0);
				m.eraseLine(prev[0], prev[1], p[0], p[1], r);
				prev = p;
			}
			assertTrue(m.erased() >= ScratchMask.DISSOLVE_AT, "swipe erased " + m.erased());
		}
	}

	@Test
	void dissolveOrderIsAPermutation() {
		int[] o = ScratchMask.dissolveOrder(165, 42);
		Set<Integer> seen = new HashSet<>();
		for (int i : o) seen.add(i);
		assertEquals(165, seen.size());
		assertEquals(java.util.Arrays.toString(o), java.util.Arrays.toString(ScratchMask.dissolveOrder(165, 42)));
	}
}
