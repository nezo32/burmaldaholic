package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK BounceChainTest: exact target, lattice alternation, decaying hops, kick-back, track parity. */
class BounceChainTest {
	@Test
	void rouletteRattleEndsExactlyOnTheLattice() {
		BounceChain.Lattice lat = BounceChain.Lattice.pockets();
		int kicks = 0;
		int[] hopsSeen = new int[8];
		for (int seed = 0; seed < 4000; seed++) {
			MotionRng rng = MotionRng.of(seed, "RATTLE", 0);
			double start = 3.37 + (seed % 17) * 0.11;
			int scatter = 5 + seed % 12; // s ∈ [5, 16]
			double target = Math.ceil(start) + scatter;
			BounceChain c = BounceChain.plan(start, 45, target, lat, BounceChain.Params.roulette(), rng);
			String id = "seed " + seed;
			assertTrue(c.hops() >= 1 && c.hops() <= 7, id);
			hopsSeen[c.hops()]++;
			assertEquals(target, c.landAt(c.hops() - 1), 0, id + " exact target");
			assertEquals(BounceChain.FLOOR, c.landClass(c.hops() - 1), id);
			double prev = start;
			for (int j = 0; j < c.hops(); j++) {
				assertTrue(lat.on(c.landAt(j), c.landClass(j)), id + " snapped");
				if (c.isKick(j)) {
					kicks++;
					assertEquals(prev - 1, c.landAt(j), 1e-12, id + " kick-back −1 pocket");
					assertEquals(BounceChain.FRET, c.landClass(j - 1), id + " kick off a fret");
					assertTrue(j >= c.hops() - 3, id + " kick in the last 3 hops");
				} else {
					assertTrue(c.landAt(j) > prev, id + " monotone");
					if (j > 0 && !c.isKick(j - 1)) assertNotEquals(c.landClass(j - 1), c.landClass(j), id + " alternates fret/floor");
				}
				if (j > 0) {
					assertTrue(c.airMs(j) < c.airMs(j - 1), id + " airtimes decrease");
					assertTrue(c.apex(j) < c.apex(j - 1), id + " heights decrease");
				}
				assertTrue(c.takeoffMs(j) - (j == 0 ? 0 : c.landMs(j - 1)) <= 90 + 1e-9, id + " skid ≤ 90 ms");
				prev = c.landAt(j);
			}
			assertEquals(4, c.apex(0), 1e-9, id + " first hop 4 px");
			assertTrue(c.endMs() > 300 && c.endMs() < 1600, id + " duration " + c.endMs());
		}
		assertTrue(kicks > 600 && kicks < 1800, "kick-backs ≈ 30 %: " + kicks);
		for (int n = 4; n <= 7; n++) assertTrue(hopsSeen[n] > 0, "hop count " + n + " " + java.util.Arrays.toString(hopsSeen));
	}

	@Test
	void samplesAreContinuousAndMatchTheTrack() {
		for (int seed = 0; seed < 200; seed++) {
			BounceChain c = BounceChain.plan(2.4, 40, 12, BounceChain.Lattice.pockets(), BounceChain.Params.roulette(), MotionRng.of(seed, "R", 1));
			Track tr = c.appendTo(Track.builder().start(0, 2.4, 0), 0, 1).build();
			double[] s = new double[4];
			double[] t = new double[2];
			double[] prev = {2.4, 0};
			for (double ms = 0; ms <= c.endMs() + 10; ms += 0.5) {
				c.sample(ms, s);
				tr.sample(ms, t);
				assertEquals(s[0], t[0], 1e-9, "along at " + ms);
				assertEquals(s[1], t[1], 1e-9, "z at " + ms);
				assertTrue(s[1] >= 0, "z ≥ 0");
				assertTrue(Math.abs(s[0] - prev[0]) < 0.2 && Math.abs(s[1] - prev[1]) < 0.2, "C0 at " + ms);
				prev[0] = s[0];
				prev[1] = s[1];
			}
			c.sample(1e9, s);
			assertArrayEquals(new double[] {12, 0, 0, 0}, s, 0);
		}
	}

	@Test
	void continuousSettleAndDeterminism() {
		BounceChain a = BounceChain.plan(131.2, 30, 136, BounceChain.Lattice.NONE, BounceChain.Params.plinkoBin(), MotionRng.of(7, "BIN", 0));
		BounceChain b = BounceChain.plan(131.2, 30, 136, BounceChain.Lattice.NONE, BounceChain.Params.plinkoBin(), MotionRng.of(7, "BIN", 0));
		assertEquals(2, a.hops());
		assertEquals(136, a.landAt(1), 0);
		assertEquals(3.5, a.apex(0), 1e-9);
		assertEquals(3.5 * 0.35 * 0.35, a.apex(1), 1e-9, "e = 0.35: ≈ 0.4 px");
		assertEquals(900, a.g(), 15, "g ≈ 900 px/s² (PL §2.2)");
		for (int j = 0; j < 2; j++) {
			assertEquals(a.landAt(j), b.landAt(j), 0);
			assertEquals(a.landMs(j), b.landMs(j), 0);
		}
		// in-place bounce (target == start) is allowed without a lattice
		BounceChain still = BounceChain.plan(5, 0, 5, BounceChain.Lattice.NONE, BounceChain.Params.chipSettle(), MotionRng.of(1, "C", 0));
		assertEquals(5, still.landAt(still.hops() - 1), 0);
	}

	@Test
	void timeScaleCompressesTheTail() {
		BounceChain full = BounceChain.plan(0, 45, 10, BounceChain.Lattice.pockets(), BounceChain.Params.roulette(), MotionRng.of(3, "R", 0));
		BounceChain c06 = BounceChain.plan(0, 45, 10, BounceChain.Lattice.pockets(), BounceChain.Params.roulette().withTimeScale(0.6),
			MotionRng.of(3, "R", 0));
		assertEquals(full.hops(), c06.hops());
		assertEquals(full.endMs() * 0.6, c06.endMs(), 1e-9);
	}
}
