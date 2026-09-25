package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Faithfulness of the shared ball path (tables.md §0.6.1–2, §1.3). */
class RouletteBallPathTest {
	private static final int[] SPINS = {2000, 2999, 3000, 5000, 15000};

	private static double mod360(double a) {
		return ((a % 360) + 360) % 360;
	}

	@Test
	void everyPathEndsInTheDrawnPocket() {
		for (int spin : SPINS) {
			for (int result = 0; result < Wheel.POCKETS; result++) {
				for (int seed = 0; seed < 64; seed++) {
					RouletteBallPath p = RouletteBallPath.of(result, seed * 7919 - 31, spin);
					double end = p.rel(spin);
					assertEquals(mod360(RouletteBallPath.pocketAngle(result)), mod360(end), 1e-9, "rel(1) = P(result)");
					assertEquals(result, p.pocketUnder(spin));
					assertEquals(result, p.pocketUnder(spin * 1.5), "after the spin the ball rides the head in its pocket");
					assertEquals(RouletteBallPath.R_POCKET, p.radius(spin), 1e-12);
					assertTrue(p.settled(spin * 0.9 + 1e-6));
					assertFalse(p.settled(spin * 0.9 - 1));
					assertNotEquals(0, p.h1 + p.h2, "the ball never starts its hops over the result");
					assertEquals(spin >= RouletteBallPath.TWO_HOPS_MIN_MS, p.twoHops());
				}
			}
		}
	}

	@Test
	void radiusAndAngleAreContinuous() {
		for (int spin : SPINS) {
			for (int seed = 0; seed < 16; seed++) {
				RouletteBallPath p = RouletteBallPath.of(seed % 37, seed, spin);
				double prevR = p.radius(0);
				double prevA = p.rel(0);
				double step = 0.25;
				for (double t = step; t <= spin + 50; t += step) {
					double r = p.radius(t);
					double a = p.rel(t);
					assertTrue(Math.abs(r - prevR) < 0.01, "r jumps at t=" + t + " (" + prevR + " → " + r + ")");
					assertTrue(Math.abs(a - prevA) < 12, "angle jumps at t=" + t);
					assertTrue(r > 0.5 && r < 1.0, "r in the bowl");
					prevR = r;
					prevA = a;
				}
			}
		}
	}

	@Test
	void theBallNeverDwellsInAWrongPocket() {
		for (int spin : new int[] {2000, 2500}) {
			for (int result = 0; result < Wheel.POCKETS; result++) {
				for (int seed = 0; seed < 64; seed++) {
					RouletteBallPath p = RouletteBallPath.of(result, seed, spin);
					double wrong = 0;
					double dt = 1;
					for (double t = RouletteBallPath.U_HOP1 * spin; t < RouletteBallPath.U_SETTLED * spin; t += dt) {
						int under = p.pocketUnder(t);
						if (under >= 0 && under != result) {
							wrong += dt;
						}
					}
					assertTrue(wrong <= 120, "over a non-result pocket for " + wrong + " ms (result " + result + ", seed " + seed + ")");
				}
			}
		}
	}

	@Test
	void theBallOrbitsCounterClockwiseAndTheHeadStops() {
		RouletteBallPath p = RouletteBallPath.of(17, 42, 5000);
		assertTrue(p.ball(100) < p.ball(0), "the ball runs counter-clockwise at launch");
		assertTrue(p.head(100) > p.head(0), "the head turns clockwise");
		assertEquals(p.head(5000), p.head(8000), 1e-12, "the head stands still after the spin");
		assertEquals(0, p.headSpeed(5000), 1e-12);
		assertEquals(RouletteBallPath.HEAD_TURN, p.head(5000) - p.head(0), 1e-9);
		assertTrue(p.headReduced(5000) - p.headReduced(0) <= 0.4 * 360 * 5, "reduced motion ≤ 0.4 rev/s");
	}

	@Test
	void cuesAreOrderedAndSpaced() {
		for (int seed = 0; seed < 64; seed++) {
			RouletteBallPath p = RouletteBallPath.of(seed % 37, seed, 5000);
			List<RouletteBallPath.Cue> cues = p.cues();
			assertEquals("roulette_spin", cues.get(0).sound());
			int lastRoll = -1000;
			for (int i = 1; i < cues.size(); i++) {
				assertTrue(cues.get(i).atMs() >= cues.get(i - 1).atMs());
				if (cues.get(i).sound().equals("roulette_ball_roll")) {
					assertTrue(cues.get(i).atMs() - lastRoll >= 250);
					lastRoll = cues.get(i).atMs();
				}
			}
			assertEquals("roulette_ball_settle", cues.get(cues.size() - 1).sound());
		}
	}

	@Test
	void headFrames() {
		assertEquals(0, RouletteBallPath.headFrame(0, 74));
		assertEquals(0, RouletteBallPath.headFrame(360, 74));
		assertEquals(1, RouletteBallPath.headFrame(360.0 / 74, 74));
		assertEquals(73, RouletteBallPath.headFrame(-360.0 / 74, 74));
		assertEquals(0, RouletteBallPath.headFrame(-1, 74));
		for (int f = 0; f < 74; f++) {
			assertEquals(f, RouletteBallPath.headFrame(RouletteBallPath.frameAngle(f, 74) + 2, 74));
		}
	}

	@Test
	void deterministic() {
		RouletteBallPath a = RouletteBallPath.of(5, 99, 5000);
		RouletteBallPath b = RouletteBallPath.of(5, 99, 5000);
		for (double t = 0; t <= 5000; t += 37) {
			assertEquals(a.ball(t), b.ball(t), 0);
			assertEquals(a.radius(t), b.radius(t), 0);
		}
	}
}
