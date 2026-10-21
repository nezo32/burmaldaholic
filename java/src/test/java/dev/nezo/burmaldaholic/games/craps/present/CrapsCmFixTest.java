package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import org.junit.jupiter.api.Test;

/** CM fix round for T-CM M1 / M1b / M2 / M4: next shooter's side, non-crossing stick and home moves, counter reset. */
class CrapsCmFixTest {
	private static double dist(DieSample a, DieSample b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.zc - b.zc) * (a.zc - b.zc));
	}

	/** M2: 400 throws, rest from either side, home at either end (a seven-out passes the dice): ≥ 2 u at every ms. */
	@Test
	void theStickNeverDragsTheDiceThroughEachOther() {
		CrapsStick.Out o = new CrapsStick.Out();
		DieSample s = new DieSample();
		double worst = Double.POSITIVE_INFINITY;
		for (double px : new double[] {7, 9, 15}) {
			for (int seed = 1; seed <= 200; seed++) {
				int side = seed % 2 == 0 ? CrapsThrow.LEFT : CrapsThrow.RIGHT;
				int homeSide = (seed / 2) % 2 == 0 ? side : 1 - side;
				DiceRoll r = CrapsThrow.solve(side, seed * 7919, 1 + seed % 6, 1 + (seed * 5) % 6, seed % 3 == 0 ? 6 : 0);
				CrapsBeats beats = CrapsBeats.of(r.restMs());
				double[] rest = new double[4], home = new double[4];
				for (int d = 0; d < 2; d++) {
					r.sample(d, 1e9, s);
					rest[2 * d] = s.x * px;
					rest[2 * d + 1] = s.y * px;
					double[] h = CrapsThrow.release(homeSide, d);
					home[2 * d] = h[0] * px;
					home[2 * d + 1] = h[1] * px;
				}
				double lastX0 = Double.NaN, lastY0 = 0, lastX1 = 0, lastY1 = 0, maxStep = 0;
				for (double t = beats.STICK - 5; t <= beats.IDLE + 50; t += 1) {
					CrapsStick.sample(t, beats, rest, home, -10, 2 * px, o);
					double dc = Math.hypot(o.x[0] - o.x[1], o.y[0] - o.y[1]) / px;
					worst = Math.min(worst, dc);
					assertTrue(dc >= 2 - 1e-9, "px " + px + " seed " + seed + " t " + t + " phase " + o.phase + ": " + dc + " u");
					if (!Double.isNaN(lastX0)) {
						maxStep = Math.max(maxStep, Math.max(Math.hypot(o.x[0] - lastX0, o.y[0] - lastY0), Math.hypot(o.x[1] - lastX1, o.y[1] - lastY1)) / px);
					}
					lastX0 = o.x[0];
					lastY0 = o.y[0];
					lastX1 = o.x[1];
					lastY1 = o.y[1];
				}
				// a drag across the whole felt in 400 ms stays a drag (no teleport within the stick's own phases)
				assertTrue(maxStep < 0.6, "px " + px + " seed " + seed + ": stick step " + maxStep + " u/ms");
				for (int d = 0; d < 2; d++) {
					assertEquals(home[2 * d], o.x[d], 1e-6);
					assertEquals(home[2 * d + 1], o.y[d], 1e-6);
				}
			}
		}
		System.out.println("CM: closest stick drag " + worst + " u");
	}

	/** M1: the synced next side puts dice home and the hand at the next shooter's end: the throw starts at the hand. */
	@Test
	void theHandRattlesWhereTheNextShooterThrowsFrom() {
		DiceRoll prev = CrapsThrow.solve(CrapsThrow.RIGHT, 5, 2, 5, 0);
		DiceRoll next = CrapsThrow.solve(CrapsThrow.LEFT, 77, 6, 1, 0);
		double[] now = {-20_000};
		CrapsRollClock c = new CrapsRollClock(() -> now[0]);
		c.nextSide(CrapsThrow.RIGHT);
		c.onRoll(1, 9000, prev, CrapsThrow.RIGHT);
		c.nextSide(CrapsThrow.LEFT); // seven-out: the dice pass to the left
		now[0] = 0;
		c.press();
		DieSample a = new DieSample(), b = new DieSample();
		c.pose(0, 0, a);
		double[] l = CrapsThrow.release(CrapsThrow.LEFT, 0);
		assertTrue(Math.hypot(a.x - l[0], a.y - l[1]) < 0.2, "the hand rattles at the left end");
		now[0] = 300;
		assertTrue(c.onRoll(2, 0, next, CrapsThrow.LEFT));
		assertTrue(-c.t() <= CrapsHand.CYCLE + CrapsHand.SETTLE + 1e-9, "the usual quick release: " + -c.t() + " ms");
	}

	/** M1b + desync fallback: a throw from the other end than the hand settles slowly, the dice passing apart. */
	@Test
	void aThrowFromTheOtherEndSettlesWithoutJumpOrTouch() {
		for (int side : new int[] {CrapsThrow.LEFT, CrapsThrow.RIGHT}) {
			for (int arrive : new int[] {0, 90, 250, 1700}) {
				DiceRoll first = CrapsThrow.solve(side, 300 + arrive, 3, 3, 0);
				double[] now = {0};
				CrapsRollClock c = new CrapsRollClock(() -> now[0]);
				c.nextSide(1 - side);
				c.press();
				DieSample[] last = {new DieSample(), new DieSample()}, cur = {new DieSample(), new DieSample()};
				for (int ms = 0; ms < arrive + 4200; ms++) {
					now[0] = ms;
					if (ms == arrive) c.onRoll(1, 0, first, side);
					double t = Math.min(c.t(), first.restMs());
					for (int d = 0; d < 2; d++) {
						c.pose(d, t, cur[d]);
						if (ms > 0) {
							assertTrue(dist(last[d], cur[d]) <= 0.14, "side " + side + " arrive " + arrive + " die " + d + " at " + ms + ": " + dist(last[d], cur[d]));
							if (c.t() < 0) assertTrue(Quat.angleTo(last[d].q, cur[d].q) <= 0.08, "turn at " + ms);
						}
						last[d].set(cur[d]);
					}
					double dc = dist(cur[0], cur[1]);
					assertTrue(dc >= 2 - 1e-9, "side " + side + " arrive " + arrive + ": " + dc + " u at " + ms + " (t " + c.t() + ")");
				}
				for (int d = 0; d < 2; d++) assertEquals(first.faces()[d], CubeGroup.faceUp(cur[d].q));
			}
		}
	}

	/** A next-side change while dice home is moving reverses from where it is (no jump), any number of times. */
	@Test
	void diceHomeReversesSmoothly() {
		double[] now = {0};
		CrapsRollClock c = new CrapsRollClock(() -> now[0]);
		c.nextSide(CrapsThrow.RIGHT);
		double[] h = new double[4], p = new double[4];
		c.home(p);
		double worst = 0;
		for (int ms = 0; ms <= 5000; ms++) {
			now[0] = ms;
			if (ms == 100) c.nextSide(CrapsThrow.LEFT);
			if (ms == 500) c.nextSide(CrapsThrow.RIGHT);
			if (ms == 700) c.nextSide(CrapsThrow.LEFT);
			c.home(h);
			for (int d = 0; d < 2; d++) worst = Math.max(worst, Math.hypot(h[2 * d] - p[2 * d], h[2 * d + 1] - p[2 * d + 1]));
			assertTrue(Math.hypot(h[0] - h[2], h[1] - h[3]) >= 2, "apart at " + ms);
			System.arraycopy(h, 0, p, 0, 4);
		}
		assertTrue(worst <= 0.1, "home step " + worst + " u/ms");
		assertEquals(CrapsThrow.LEFT, c.nextSide());
		assertFalse(c.homeMoving());
		double[] l = CrapsThrow.release(CrapsThrow.LEFT, 1);
		assertEquals(l[0], h[2], 1e-9);
	}

	/** M4: a roll counter lower than the last one is another table at this place: a first sight (server age), not ignored. */
	@Test
	void aCounterGoingBackwardsIsANewTable() {
		double[] now = {10_000};
		CrapsRollClock c = new CrapsRollClock(() -> now[0]);
		DiceRoll r = CrapsThrow.solve(CrapsThrow.RIGHT, 9, 1, 2, 0);
		assertTrue(c.onRoll(40, 9000, r, CrapsThrow.RIGHT));
		now[0] = 20_000;
		assertTrue(c.onRoll(1, 300, r, CrapsThrow.RIGHT), "a new (lower) counter is a new roll");
		assertTrue(c.firstSight());
		assertEquals(300, c.t(), 1e-9);
	}

	/** M1c: a spectator roll during (or bunched with) the home slide glides both dice into the start: no jump, ≥ 2 u, exact start. */
	@Test
	void aSpectatorRollDuringTheHomeSlideGlidesIn() {
		DiceRoll prev = CrapsThrow.solve(CrapsThrow.RIGHT, 11, 3, 4, 0);
		DiceRoll next = CrapsThrow.solve(CrapsThrow.LEFT, 4242, 5, 2, 0);
		for (int gap : new int[] {0, 40, 300, 700, 1050, 1200}) {
			double[] now = {-20_000};
			CrapsRollClock c = new CrapsRollClock(() -> now[0]);
			c.nextSide(CrapsThrow.RIGHT);
			c.onRoll(1, 9000, prev, CrapsThrow.RIGHT);
			now[0] = 0;
			c.nextSide(CrapsThrow.LEFT);
			DieSample[] last = {new DieSample(), new DieSample()}, cur = {new DieSample(), new DieSample()};
			boolean rolled = false;
			for (int ms = 0; ms <= 4500; ms++) {
				now[0] = ms;
				if (ms == gap) {
					c.onRoll(2, 0, next, CrapsThrow.LEFT);
					rolled = true;
				}
				for (int d = 0; d < 2; d++) {
					if (rolled) c.pose(d, Math.min(c.t(), next.restMs()), cur[d]);
					else c.homePose(d, cur[d]);
					if (ms > 0) assertTrue(dist(last[d], cur[d]) <= 0.14, "gap " + gap + " die " + d + " at " + ms + ": " + dist(last[d], cur[d]));
					if (rolled && Math.abs(c.t()) < 0.5) {
						DieSample s0 = new DieSample();
						next.sample(d, 0, s0);
						assertEquals(s0.x, cur[d].x, 0.06, "release pose");
					}
					last[d].set(cur[d]);
				}
				assertTrue(dist(cur[0], cur[1]) >= 2 - 1e-9, "gap " + gap + " apart at " + ms);
			}
			for (int d = 0; d < 2; d++) assertEquals(next.faces()[d], CubeGroup.faceUp(cur[d].q));
		}
	}
}
