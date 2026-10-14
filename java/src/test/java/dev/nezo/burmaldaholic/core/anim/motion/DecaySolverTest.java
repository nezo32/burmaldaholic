package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK DecaySolverTest: 10k random cases hit the target ±1e-9, monotone profile, bound path. */
class DecaySolverTest {
	@Test
	void tenThousandRandomTargetsHitExactly() {
		SplittableRandom r = new SplittableRandom(20261014);
		double[] reach = new double[2];
		for (int i = 0; i < 10_000; i++) {
			double ws = r.nextDouble(300, 1000);
			double wd = r.nextDouble(150, ws - 30);
			double tt = r.nextDouble(0.6, 7);
			DecaySolver.reach(ws, wd, tt, 0.2, 6, reach);
			double target = reach[0] + r.nextDouble() * (reach[1] - reach[0]);
			double kNat = r.nextDouble(0.2, 6);
			DecaySolver.Solution s = DecaySolver.solve(ws, wd, tt, target, kNat, 0.2, 6);
			assertTrue(s.ok(), "case " + i);
			assertEquals(target, s.theta(), 1e-9, "case " + i);
			assertEquals(target, s.angle(tt), 1e-9, "angle(T) == theta, case " + i);
			assertTrue(s.k() >= 0.2 && s.k() <= 6);
			assertEquals(ws, s.omega(0), 1e-9);
			assertEquals(wd, s.omega(tt), 1e-9);
			if (i % 50 == 0) monotone(s);
		}
	}

	private static void monotone(DecaySolver.Solution s) {
		double prevW = Double.POSITIVE_INFINITY;
		double prevA = -1;
		for (int j = 0; j <= 400; j++) {
			double t = s.T() * j / 400;
			double w = s.omega(t);
			double a = s.angle(t);
			assertTrue(w <= prevW + 1e-9, "speed never increases");
			assertTrue(a >= prevA - 1e-9, "angle never goes back");
			assertTrue(s.decel(t) >= 0);
			// angle is the integral of omega (midpoint check)
			if (j > 0) {
				double tm = s.T() * (j - 0.5) / 400;
				assertEquals(s.omega(tm) * s.T() / 400, a - prevA, 1e-3 * Math.max(1, a - prevA));
			}
			prevW = w;
			prevA = a;
		}
	}

	@Test
	void thetaIsStrictlyDecreasingInK() {
		double prev = Double.POSITIVE_INFINITY;
		for (double k = 0.2; k <= 6; k += 0.01) {
			double th = DecaySolver.theta(k, 756, 306, 4.5);
			assertTrue(th < prev);
			prev = th;
		}
		assertEquals(0.5, DecaySolver.bigG(1e-9), 1e-9);
		assertEquals(DecaySolver.bigG(1e-3 - 1e-12), DecaySolver.bigG(1e-3), 1e-9, "series meets the closed form");
	}

	@Test
	void boundPathReportsResidual() {
		double[] reach = new double[2];
		DecaySolver.reach(756, 306, 4.5, 0.2, 6, reach);
		DecaySolver.Solution high = DecaySolver.solve(756, 306, 4.5, reach[1] + 40, 1, 0.2, 6);
		assertFalse(high.ok());
		assertEquals(0.2, high.k(), 0);
		assertEquals(40, high.residual(), 1e-9);
		DecaySolver.Solution low = DecaySolver.solve(756, 306, 4.5, reach[0] - 25, 1, 0.2, 6);
		assertFalse(low.ok());
		assertEquals(6, low.k(), 0);
		assertEquals(-25, low.residual(), 1e-9);
		// RO §2.4 default: about 285° of reach at spinTicks 100 (T ≈ 2.55 s)
		DecaySolver.reach(756 * Math.exp(-0.75 / 4.2), 306, 2.55, 0.2, 6, reach);
		assertTrue(reach[1] - reach[0] > 250, "reach " + (reach[1] - reach[0]));
	}

	@Test
	void kForDecelMatchesInitialDeceleration() {
		double ws = 600;
		double wd = 306;
		double tt = 3;
		double decel = ws / 4.2; // stage A: ω·/τ_b
		double k = DecaySolver.kForDecel(ws, wd, tt, decel, 0.05, 20);
		DecaySolver.Solution s = DecaySolver.solve(ws, wd, tt, DecaySolver.theta(k, ws, wd, tt), k, 0.05, 20);
		assertEquals(decel, s.decel(0), 1e-6);
		assertEquals(k, s.k(), 1e-9);
	}
}
