package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * SY-SPEC §3 (Craps row): {@link DiceSolver#solve(DiceTray, ThrowSpec, int, int[], int, DiceSolver.Initial[])} starts
 * each die in the hand pose at roll time 0, is C0 and C1 there and at the end of the blend, and ends exactly like the
 * plain solve (same faces, rest times, rest points and events).
 */
class DiceHandoffTest {
	static final double H = 1e-4; // ms, finite-difference step

	static DiceSolver.Initial[] hand(Random r, ThrowSpec spec, int n) {
		DiceSolver.Initial[] in = new DiceSolver.Initial[n];
		for (int i = 0; i < n; i++) {
			double[] q = Quat.normalize(new double[] {r.nextGaussian(), r.nextGaussian(), r.nextGaussian(), r.nextGaussian()}, new double[4]);
			in[i] = new DiceSolver.Initial(spec.x() + r.nextDouble() * 2 - 1 + 1.8 * i, spec.y() + r.nextDouble() * 2 - 1, 1.5 + r.nextDouble(),
				r.nextGaussian() * 3, r.nextGaussian() * 3, r.nextGaussian() * 2, q, r.nextGaussian() * 8, r.nextGaussian() * 8, r.nextGaussian() * 8);
		}
		return in;
	}

	/** Linear velocity (x, y, zLow) and world angular velocity from a one-sided difference over [t, t + dt]. */
	static double[] rates(DiceRoll roll, int die, double t, double dt) {
		DieSample a = new DieSample();
		DieSample b = new DieSample();
		roll.sample(die, t, a);
		roll.sample(die, t + dt, b);
		double s = dt / 1000.0;
		// ω ≈ 2·vec(b·a⁻¹)/dt with the sign of the scalar part made positive
		double[] d = Quat.mul(b.q, Quat.conj(a.q, new double[4]), new double[4]);
		double sg = d[0] < 0 ? -1 : 1;
		return new double[] {(b.x - a.x) / s, (b.y - a.y) / s, (b.zLow - a.zLow) / s, 2 * sg * d[1] / s, 2 * sg * d[2] / s, 2 * sg * d[3] / s};
	}

	static void close(double[] want, double[] got, double tol, String at) {
		for (int k = 0; k < want.length; k++)
			assertTrue(Math.abs(want[k] - got[k]) <= tol * Math.max(1, Math.abs(want[k])), at + " [" + k + "] " + want[k] + " vs " + got[k]);
	}

	@Test
	void continuousAtHandoffAndBlendEndAndExactAtRest() {
		Random r = new Random(0xC0FFEE);
		int cases = 0;
		for (DiceFixtures.Case c : DiceFixtures.all()) {
			for (int si = 0; si < 12; si++) {
				int seed = DiceRollTest.seed(si);
				int[] faces = {1 + r.nextInt(6), 1 + r.nextInt(6)};
				DiceSolver.Initial[] in = hand(r, c.spec(), 2);
				DiceRoll base = DiceSolver.solve(c.tray(), c.spec(), seed, faces, c.targetMs());
				DiceRoll roll = DiceSolver.solve(c.tray(), c.spec(), seed, faces, c.targetMs(), in);
				String at = c.name() + " seed " + si;
				assertEquals(base.events().length, roll.events().length, at);
				for (int d = 0; d < 2; d++) {
					DieSample s = new DieSample();
					// C0 at the hand-off: exactly the hand pose (MODEL orientation), also just before it (HELD)
					for (double t : new double[] {0, -50}) {
						roll.sample(d, t, s);
						close(new double[] {in[d].x(), in[d].y(), in[d].zLow()}, new double[] {s.x, s.y, s.zLow}, 1e-9, at + " C0 pos t" + t);
						assertTrue(Math.abs(Quat.dot(s.q, in[d].q())) > 1 - 1e-9, at + " C0 q t" + t);
					}
					// C1 at the hand-off: the hand's linear and angular velocity
					close(new double[] {in[d].vx(), in[d].vy(), in[d].vz(), in[d].wx(), in[d].wy(), in[d].wz()}, rates(roll, d, 0, H), 2e-3, at + " C1 at 0");
					// C0 / C1 at the end of the blend (t1): left and right states and rates agree
					double t1 = roll.seg(d, 1).t1;
					assertEquals(roll.seg(d, 1).sub, Seg.BLEND, at);
					assertTrue(t1 > base.releaseMs(d) && t1 <= base.releaseMs(d) + DiceSolver.BLEND_MS + 1e-9, at + " t1 " + t1);
					DieSample l = new DieSample();
					DieSample rr = new DieSample();
					roll.sample(d, t1 - 1e-7, l);
					roll.sample(d, t1, rr);
					close(new double[] {l.x, l.y, l.zLow}, new double[] {rr.x, rr.y, rr.zLow}, 1e-5, at + " C0 at t1");
					assertTrue(Math.abs(Quat.dot(l.q, rr.q)) > 1 - 1e-9, at + " C0 q at t1");
					close(rates(roll, d, t1 - H, H), rates(roll, d, t1, H), 3e-3, at + " C1 at t1");
					// after the blend the roll is the plain roll: same motion, faces, rest
					for (double t : new double[] {t1 + 1, (t1 + base.restTime(d)) / 2, base.restTime(d), base.restTime(d) + 500}) {
						DieSample p = new DieSample();
						base.sample(d, t, p);
						roll.sample(d, t, s);
						assertEquals(p.phase, s.phase, at + " phase t" + t);
						close(new double[] {p.x, p.y, p.zLow}, new double[] {s.x, s.y, s.zLow}, 1e-9, at + " pos t" + t);
						assertTrue(Math.abs(Quat.dot(p.q, s.q)) > 1 - 1e-9, at + " q t" + t);
					}
					assertEquals(faces[d], roll.topFace(d, roll.restTime(d) + 1), at + " face");
					assertEquals(base.restTime(d), roll.restTime(d), at);
					// the blend never pops: per 1-ms step, bounded motion
					DieSample prev = new DieSample();
					roll.sample(d, 0, prev);
					for (double t = 1; t <= t1; t += 1) {
						roll.sample(d, t, s);
						assertTrue(Math.hypot(s.x - prev.x, s.y - prev.y) < 0.5, at + " jump t" + t);
						assertTrue(Quat.angleTo(s.q, prev.q) < 0.25, at + " spin jump t" + t + " " + Quat.angleTo(s.q, prev.q));
						prev.set(s);
					}
				}
				cases++;
			}
		}
		assertTrue(cases > 0);
	}

	@Test
	void plainSolveIsUnchangedAndInputsAreChecked() {
		DiceFixtures.Case c = DiceFixtures.all().get(0);
		int[] faces = {3, 4};
		DiceRoll a = DiceSolver.solve(c.tray(), c.spec(), 7, faces, c.targetMs());
		assertEquals(DieSample.HELD, a.seg(0, 0).kind);
		assertEquals(0, a.seg(0, 1).sub);
		assertThrows(IllegalArgumentException.class, () -> DiceSolver.solve(c.tray(), c.spec(), 7, faces, c.targetMs(), new DiceSolver.Initial[1]));
		assertThrows(IllegalArgumentException.class, () -> new DiceSolver.Initial(0, 0, 1, 0, 0, 0, new double[] {2, 0, 0, 0}, 0, 0, 0));
	}
}
