package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import org.junit.jupiter.api.Test;

/** S1: the craps hand shakes until the result, the throw's origin is fixed once, and nothing jumps on a late result. */
class CrapsRollClockTest {
	private static final DiceRoll PREV = CrapsThrow.solve(CrapsThrow.RIGHT, 11, 3, 4, 0);
	private static final DiceRoll NEXT = CrapsThrow.solve(CrapsThrow.RIGHT, 0x5EED, 6, 5, 0);

	private final double[] now = {-20_000};
	private final CrapsRollClock clock = new CrapsRollClock(() -> now[0]);

	/** The table already shows roll 1 settled (seen long ago). */
	private void settledPrevious() {
		clock.onRoll(1, 9000, PREV, CrapsThrow.RIGHT);
		now[0] = 0;
	}

	/** Largest per-ms position step and turn of the solved throw itself (its own fastest motion). */
	private static double[] naturalStep(DiceRoll r) {
		DieSample a = new DieSample(), b = new DieSample();
		double pos = 0, ang = 0;
		for (int d = 0; d < 2; d++) {
			r.sample(d, 0, a);
			for (double t = 1; t <= r.restMs() + 50; t += 1) {
				r.sample(d, t, b);
				pos = Math.max(pos, dist(a, b));
				ang = Math.max(ang, Quat.angleTo(a.q, b.q));
				a.set(b);
			}
		}
		return new double[] {pos, ang};
	}

	private static double dist(DieSample a, DieSample b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.zc - b.zc) * (a.zc - b.zc));
	}

	@Test
	void lateResultsNeverJumpAndEndOnTheServerFaces() {
		double[] nat = naturalStep(NEXT);
		double maxPos = Math.max(nat[0] * 1.25, 0.05);
		double maxAng = Math.max(nat[1] * 1.25, 0.08);
		for (int late : new int[] {0, 300, 800, 1500}) {
			now[0] = -20_000;
			CrapsRollClock c = new CrapsRollClock(() -> now[0]);
			c.onRoll(1, 9000, PREV, CrapsThrow.RIGHT);
			now[0] = 0;
			c.press();
			DieSample[] last = {new DieSample(), new DieSample()};
			DieSample cur = new DieSample();
			boolean started = false;
			double origin = Double.NaN;
			for (int ms = 0; ms <= late + 4000; ms++) {
				now[0] = ms;
				if (ms == late) {
					assertTrue(c.waiting(), late + ": still shaking when the result lands");
					// a bunched burst: the result and two more states for the same roll, each with another server age
					assertTrue(c.onRoll(2, 50, NEXT, CrapsThrow.RIGHT));
					origin = ms - c.t();
					assertFalse(c.onRoll(2, 900, NEXT, CrapsThrow.RIGHT));
					assertFalse(c.onRoll(2, -300, NEXT, CrapsThrow.RIGHT));
					assertEquals(origin, ms - c.t(), 1e-9, late + ": a re-sync moved the origin");
				}
				if (ms == late + 40) {
					assertFalse(c.onRoll(2, 4000, NEXT, CrapsThrow.RIGHT));
					assertEquals(origin, ms - c.t(), 1e-9, late + ": a later packet moved the origin");
				}
				double t = c.t();
				if (ms > late) assertTrue(origin >= late + CrapsHand.SETTLE && origin - Math.max(late, CrapsHand.SHAKE_MIN) <= CrapsHand.CYCLE + CrapsHand.SETTLE,
					late + ": released on the next pass through the release pose " + origin);
				for (int d = 0; d < 2; d++) {
					assertTrue(c.pose(d, Math.min(t, NEXT.restMs() + 100), cur));
					if (ms > 0) {
						double step = dist(last[d], cur);
						double turn = Quat.angleTo(last[d].q, cur.q);
						assertTrue(step <= maxPos, late + " ms late: die " + d + " jumped " + step + " u at " + ms + " ms (t " + t + ")");
						assertTrue(turn <= maxAng, late + " ms late: die " + d + " turned " + turn + " rad at " + ms + " ms (t " + t + ")");
					}
					last[d].set(cur);
				}
				double pen = penetration(last[0], last[1]);
				if (t < 0) assertTrue(pen <= 0.005, late + " ms late: the dice overlap by " + pen + " u at " + ms + " ms (t " + t + ")");
				if (t >= 0) started = true;
			}
			assertTrue(started, late + ": thrown");
			// at rest: exactly the solve, with the server's faces
			DieSample solved = new DieSample();
			for (int d = 0; d < 2; d++) {
				c.pose(d, NEXT.restMs() + 100, cur);
				NEXT.sample(d, NEXT.restMs() + 100, solved);
				assertEquals(solved.x, cur.x, 1e-12);
				assertEquals(solved.y, cur.y, 1e-12);
				assertEquals(0, Quat.angleTo(solved.q, cur.q), 1e-9);
				assertEquals(NEXT.faces()[d], CubeGroup.faceUp(cur.q), late + ": die " + d + " face");
			}
		}
	}

	@Test
	void theHandShakesWithoutATimeCap() {
		settledPrevious();
		clock.press();
		DieSample a = new DieSample();
		double minYaw = 1, maxYaw = -1;
		for (int ms = 9_000; ms < 9_120; ms++) {
			now[0] = ms;
			assertTrue(clock.waiting());
			assertEquals(ms, clock.handMs(), 1e-9);
			clock.pose(0, clock.t(), a);
			assertEquals(DieSample.HELD, a.phase);
			minYaw = Math.min(minYaw, a.x);
			maxYaw = Math.max(maxYaw, a.x);
		}
		assertTrue(maxYaw - minYaw > CrapsHand.JIT_U, "still rattling after 9 s: " + (maxYaw - minYaw));
		// a refusal puts the dice down at home
		clock.cancel();
		assertFalse(clock.waiting());
		assertTrue(Double.isNaN(clock.handMs()));
	}

	/** The loop's pose at the release IS the solver's start pose (C0): the plain throw starts where the hand is. */
	@Test
	void theReleasePoseIsTheSolversStartPose() {
		settledPrevious();
		clock.press();
		now[0] = 333;
		clock.onRoll(2, 0, NEXT, CrapsThrow.RIGHT);
		double release = clock.handTimes()[1];
		assertEquals(480, release, 1e-9);
		DieSample before = new DieSample(), at = new DieSample(), start = new DieSample();
		for (int d = 0; d < 2; d++) {
			NEXT.sample(d, 0, start);
			now[0] = release - 1e-6;
			assertTrue(clock.t() < 0 && !Double.isNaN(clock.handMs()), "still in the hand");
			clock.pose(d, clock.t(), before);
			now[0] = release;
			clock.pose(d, clock.t(), at);
			for (DieSample x : new DieSample[] {before, at}) {
				assertEquals(start.x, x.x, 1e-6);
				assertEquals(start.y, x.y, 1e-6);
				assertEquals(start.zc, x.zc, 1e-6);
				assertEquals(0, Quat.angleTo(start.q, x.q), 1e-5);
			}
		}
		// before the result the loop passes through ONE pose every cycle (yaw 0, x on home)
		DieSample base = new DieSample(), a = new DieSample(), b = new DieSample();
		CrapsHand.home(CrapsThrow.RIGHT, PREV, new int[] {3, 4}, 0, base);
		CrapsHand.shake(base, 0, 2 * CrapsHand.CYCLE, a);
		for (int k = 3; k < 40; k++) {
			CrapsHand.shake(base, 0, k * CrapsHand.CYCLE, b);
			assertEquals(a.x, b.x, 1e-9);
			assertEquals(0, Quat.angleTo(a.q, b.q), 1e-9);
		}
	}

	/** The result arriving at any phase of the loop waits for the next release pose: at most one cycle (+ the settle). */
	@Test
	void anyArrivalPhaseWaitsAtMostOneCycle() {
		for (double ready = 0; ready <= 2000; ready += 0.5) {
			double rel = CrapsHand.releaseAt(ready);
			assertEquals(0, rel % CrapsHand.CYCLE, 1e-9, "on a whole cycle: " + rel);
			assertTrue(rel >= Math.max(ready + CrapsHand.SETTLE, CrapsHand.SHAKE_MIN) - 1e-9, "after the settle: " + ready + " -> " + rel);
			assertTrue(rel - Math.max(ready, CrapsHand.SHAKE_MIN) <= CrapsHand.CYCLE + CrapsHand.SETTLE + 1e-9, "waits at most one cycle: " + ready + " -> " + rel);
		}
	}

	/** The dice never overlap: rattle, settle and the throw to rest, over many seeds, faces, sides and arrival phases. */
	@Test
	void theDiceNeverOverlap() {
		DieSample a = new DieSample(), b = new DieSample();
		for (int seed = 1; seed <= 24; seed++) {
			int side = seed % 2 == 0 ? CrapsThrow.LEFT : CrapsThrow.RIGHT;
			int d1 = 1 + seed % 6, d2 = 1 + (seed * 5 + 2) % 6;
			DiceRoll r = CrapsThrow.solve(side, seed * 7919, d1, d2, seed % 3 == 0 ? 6 : 0);
			now[0] = -20_000;
			CrapsRollClock c = new CrapsRollClock(() -> now[0]);
			c.onRoll(1, 9000, PREV, side);
			now[0] = 0;
			c.press();
			int late = (seed * 37) % 400;
			for (int ms = 0; ms <= late + 400 + r.restMs(); ms++) {
				now[0] = ms;
				if (ms == late) c.onRoll(2, 0, r, side);
				double t = c.t();
				c.pose(0, Math.min(t, r.restMs() + 50), a);
				c.pose(1, Math.min(t, r.restMs() + 50), b);
				double pen = penetration(a, b);
				if (t < 0) {
					// the hand and the settle (this package): the two cubes never touch
					assertTrue(pen <= 0.005, "seed " + seed + " side " + side + ": the dice overlap by " + pen + " u in the hand at " + ms + " ms (t " + t + ")");
				} else {
					// the solved flight: the dice centres stay >= 2 u apart over the whole throw (DCa whole-flight separation)
					double dc = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.zc - b.zc) * (a.zc - b.zc));
					assertTrue(dc >= 2 - 1e-9, "seed " + seed + " side " + side + ": centres " + dc + " u apart at t " + t);
				}
			}
		}
	}

	/** Deepest overlap of two dice (cubes of half-edge 1, tray units) by the separating-axis test; 0 = apart. */
	static double penetration(DieSample a, DieSample b) {
		double[][] ax = axes(a.q), bx = axes(b.q);
		double[] d = {b.x - a.x, b.y - a.y, b.zc - a.zc};
		double min = Double.POSITIVE_INFINITY;
		java.util.List<double[]> tests = new java.util.ArrayList<>();
		for (double[] v : ax) tests.add(v);
		for (double[] v : bx) tests.add(v);
		for (double[] u : ax) {
			for (double[] v : bx) {
				double[] c = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
				double n = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
				if (n > 1e-6) tests.add(new double[] {c[0] / n, c[1] / n, c[2] / n});
			}
		}
		for (double[] l : tests) {
			double ra = 0, rb = 0;
			for (double[] u : ax) ra += Math.abs(dot(u, l));
			for (double[] v : bx) rb += Math.abs(dot(v, l));
			double over = ra + rb - Math.abs(dot(d, l));
			if (over <= 0) return 0;
			min = Math.min(min, over);
		}
		return min;
	}

	private static double[][] axes(double[] q) {
		double[][] out = new double[3][3];
		Quat.rotate(q, 1, 0, 0, out[0], 0);
		Quat.rotate(q, 0, 1, 0, out[1], 0);
		Quat.rotate(q, 0, 0, 1, out[2], 0);
		return out;
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	@Test
	void spectatorsStartFromHomeOnArrivalAndTheFirstSightCatchesUp() {
		settledPrevious();
		now[0] = 5000;
		clock.onRoll(2, 800, NEXT, CrapsThrow.LEFT);
		assertEquals(0, clock.t(), 1e-9, "a late packet delays the throw, never jumps into it");
		assertFalse(clock.firstSight());
		CrapsRollClock fresh = new CrapsRollClock(() -> now[0]);
		fresh.onRoll(7, 800, NEXT, CrapsThrow.LEFT);
		assertEquals(800, fresh.t(), 1e-9, "first sight: the throw is already under way");
		assertTrue(fresh.firstSight());
		now[0] = 20_000;
		clock.onRoll(3, CrapsRollClock.STALE_MS + 1, PREV, CrapsThrow.LEFT);
		assertTrue(clock.firstSight() && clock.t() > CrapsRollClock.STALE_MS, "a stale roll shows settled");
	}

	@Test
	void aNewClockSourceKeepsTheThrowInPlace() {
		settledPrevious();
		clock.onRoll(2, 0, NEXT, CrapsThrow.RIGHT);
		now[0] = 400;
		double t = clock.t();
		double[] other = {123_456};
		clock.source(() -> other[0]);
		assertEquals(t, clock.t(), 1e-9);
		other[0] += 100;
		assertEquals(t + 100, clock.t(), 1e-9);
	}
}
