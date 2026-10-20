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
				if (ms > late) assertTrue(origin >= late && origin - late <= CrapsHand.STROKE + CrapsHand.SHAKE_MIN, late + ": released on the next stroke " + origin);
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

	@Test
	void theThrowStartsFromTheHandPoseExactly() {
		settledPrevious();
		clock.press();
		now[0] = 333;
		clock.onRoll(2, 0, NEXT, CrapsThrow.RIGHT);
		double release = CrapsHand.releaseAt(333);
		assertEquals(360, release, 1e-9);
		DieSample base = new DieSample(), hand = new DieSample(), thrown = new DieSample();
		for (int d = 0; d < 2; d++) {
			CrapsHand.home(CrapsThrow.RIGHT, PREV, new int[] {3, 4}, d, base);
			CrapsHand.shake(base, d, release, hand);
			now[0] = release;
			clock.pose(d, clock.t(), thrown);
			assertEquals(0, clock.t(), 1e-9);
			assertEquals(hand.x, thrown.x, 1e-9);
			assertEquals(hand.y, thrown.y, 1e-9);
			assertEquals(hand.zc, thrown.zc, 1e-9);
			assertEquals(0, Quat.angleTo(hand.q, thrown.q), 1e-6);
		}
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
