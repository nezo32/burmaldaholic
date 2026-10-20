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

/**
 * T-CM (independent tester): S1 "craps is horrible on bad internet" on the pure clock / hand / throw / stick, beyond the
 * developer's 0–1.5 s cases: results 0–4 s late, bunched + re-sent packets, both shooter sides and puck positions.
 * Every frame (1 ms) the dice must not jump (≤ 1.25 × the solve's own fastest per-ms step), their centres stay ≥ 2 u
 * apart at ALL times (hand, settle, flight, rest, stick), the release pose is the solver's start pose, the rest is the
 * server's faces, and the stick leaves the dice exactly where the next hand picks them up.
 */
class CrapsCmTesterTest {
	private static final DiceRoll PREV_R = CrapsThrow.solve(CrapsThrow.RIGHT, 11, 3, 4, 0);
	private static final DiceRoll PREV_L = CrapsThrow.solve(CrapsThrow.LEFT, 11, 3, 4, 0);

	private static double dist(DieSample a, DieSample b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.zc - b.zc) * (a.zc - b.zc));
	}

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

	/** Results 0–4 s late, bunched and re-sent: no jump, ≥ 2 u apart always, exact release and rest. */
	@Test
	void lateUpToFourSecondsBunchedNeverJumpsNorTouches() {
		int[] lates = {0, 1, 37, 119, 241, 480, 1000, 1999, 2500, 3001, 3999, 4000};
		int n = 0;
		for (int side : new int[] {CrapsThrow.RIGHT, CrapsThrow.LEFT}) {
			for (int pb : new int[] {0, 6, 10}) {
				for (int li = 0; li < lates.length; li++) {
					int late = lates[li];
					int seed = 1000 + 31 * li + 7 * pb + side;
					int d1 = 1 + (li + pb) % 6, d2 = 1 + (li * 5 + side) % 6;
					DiceRoll next = CrapsThrow.solve(side, seed, d1, d2, pb);
					double[] nat = naturalStep(next);
					double maxPos = Math.max(nat[0] * 1.25, 0.05), maxAng = Math.max(nat[1] * 1.25, 0.08);
					double[] now = {-20_000};
					CrapsRollClock c = new CrapsRollClock(() -> now[0]);
					c.onRoll(1, 9000, side == CrapsThrow.RIGHT ? PREV_R : PREV_L, side);
					now[0] = 0;
					c.press();
					c.press(); // a double press changes nothing
					DieSample[] last = {new DieSample(), new DieSample()};
					DieSample[] cur = {new DieSample(), new DieSample()};
					double origin = Double.NaN;
					String tag = "side " + side + " pb " + pb + " late " + late;
					for (int ms = 0; ms <= late + 400 + next.restMs(); ms++) {
						now[0] = ms;
						if (ms < late) {
							assertTrue(c.waiting(), tag + ": shaking until the result");
							assertEquals(ms, c.handMs(), 1e-9, tag);
						}
						if (ms == late) {
							assertTrue(c.onRoll(2, 50, next, side));
							origin = ms - c.t();
							// bunched: two more states of the same roll with other server ages, and a re-send later
							assertFalse(c.onRoll(2, 4000, next, side));
							assertFalse(c.onRoll(2, -800, next, side));
							assertTrue(c.fromHand(), tag);
						}
						if (ms == late + 150) assertFalse(c.onRoll(2, 7000, next, side));
						if (ms >= late) assertEquals(origin, ms - c.t(), 1e-9, tag + ": the origin moved");
						double t = c.t();
						double tt = Math.min(t, next.restMs() + 100);
						for (int d = 0; d < 2; d++) {
							assertTrue(c.pose(d, tt, cur[d]));
							if (t < 0 && ms < late) assertEquals(DieSample.HELD, cur[d].phase, tag + ": held while waiting");
							if (ms > 0) {
								double step = dist(last[d], cur[d]);
								double turn = Quat.angleTo(last[d].q, cur[d].q);
								assertTrue(step <= maxPos, tag + ": die " + d + " jumped " + step + " u at " + ms + " (t " + t + ")");
								assertTrue(turn <= maxAng, tag + ": die " + d + " turned " + turn + " rad at " + ms + " (t " + t + ")");
							}
							last[d].set(cur[d]);
						}
						double dc = dist(cur[0], cur[1]);
						assertTrue(dc >= 2 - 1e-9, tag + ": centres " + dc + " u apart at " + ms + " ms (t " + t + ")");
						if (ms > late && Math.abs(t) < 0.5) {
							// the release pose IS the solver's start pose
							DieSample s0 = new DieSample();
							for (int d = 0; d < 2; d++) {
								next.sample(d, t, s0);
								assertEquals(s0.x, cur[d].x, 1e-6, tag);
								assertEquals(s0.y, cur[d].y, 1e-6, tag);
								assertEquals(0, Quat.angleTo(s0.q, cur[d].q), 1e-5, tag);
							}
						}
					}
					assertTrue(origin - late <= CrapsHand.CYCLE + CrapsHand.SETTLE + Math.max(0, CrapsHand.SHAKE_MIN - late) + 1e-9,
						tag + ": waited " + (origin - late) + " ms after the result");
					for (int d = 0; d < 2; d++) assertEquals(next.faces()[d], CubeGroup.faceUp(cur[d].q), tag + ": face of die " + d);
					n++;
				}
			}
		}
		assertEquals(72, n);
	}

	/**
	 * Shooter rotation to the other end of the Pass line (seat in the other half, e.g. two humans on seats 1 and 4):
	 * the dice sit at the LAST roll's dice home (the stick dragged them there), but the new throw starts at the new
	 * shooter's home. The hand of the new shooter settles across the felt in ≤ 200 ms and a spectator's dice teleport.
	 */
	@Test
	void shooterOnTheOtherSideDoesNotJump() {
		DiceRoll next = CrapsThrow.solve(CrapsThrow.LEFT, 4242, 5, 2, 0);
		double[] nat = naturalStep(next);
		double maxPos = Math.max(nat[0] * 1.25, 0.05);
		// spectator: dice home of the previous (right) roll vs the left throw's start
		DieSample home = new DieSample(), start = new DieSample();
		CrapsHand.home(CrapsThrow.RIGHT, PREV_R, new int[] {3, 4}, 0, home);
		next.sample(0, 0, start);
		double spectatorJump = Math.hypot(home.x - start.x, home.y - start.y);
		// shooter: the settle from the right home to the left release
		double[] now = {-20_000};
		CrapsRollClock c = new CrapsRollClock(() -> now[0]);
		c.onRoll(1, 9000, PREV_R, CrapsThrow.RIGHT);
		now[0] = 0;
		c.press();
		DieSample a = new DieSample(), b = new DieSample();
		double worst = 0;
		for (int ms = 0; ms < 1200; ms++) {
			now[0] = ms;
			if (ms == 300) c.onRoll(2, 0, next, CrapsThrow.LEFT);
			c.pose(0, Math.min(c.t(), next.restMs()), b);
			if (ms > 0) worst = Math.max(worst, dist(a, b));
			a.set(b);
		}
		assertTrue(spectatorJump <= maxPos && worst <= maxPos,
			"side change: spectator jump " + spectatorJump + " u in one frame, shooter settle step " + worst + " u/ms (limit " + maxPos + ")");
	}

	/**
	 * The first roll of a fresh table (no previous roll: the clock and the screen default to RIGHT) by a lone player in
	 * seat 0 (sideOfSeat(0, 6) = LEFT): the hand rattles at the right end, then the dice cross the felt in the settle.
	 */
	@Test
	void theFirstRollOfAFreshTableStartsWhereTheHandIs() {
		assertEquals(CrapsThrow.LEFT, CrapsThrow.sideOfSeat(0, 6), "a lone player in seat 0 throws from the left");
		DiceRoll first = CrapsThrow.solve(CrapsThrow.LEFT, 99, 4, 3, 0);
		double maxPos = Math.max(naturalStep(first)[0] * 1.25, 0.05);
		double[] now = {0};
		CrapsRollClock c = new CrapsRollClock(() -> now[0]);
		c.press();
		DieSample a = new DieSample(), b = new DieSample();
		double worst = 0;
		for (int ms = 0; ms < 900; ms++) {
			now[0] = ms;
			if (ms == 250) c.onRoll(1, 0, first, CrapsThrow.LEFT);
			c.pose(0, Math.min(c.t(), first.restMs()), b);
			if (ms > 0) worst = Math.max(worst, dist(a, b));
			a.set(b);
		}
		assertTrue(worst <= maxPos, "first roll of a fresh table: the hand moves " + worst + " u/ms (limit " + maxPos + ")");
	}

	/** The stick drags the dice home without overlap and leaves them exactly at the release points (next hand = no jump). */
	@Test
	void theStickKeepsTheDiceApartAndLeavesThemAtTheHand() {
		double px = 7; // T1: cell 14 px, 1 u = 7 px
		CrapsStick.Out o = new CrapsStick.Out();
		DieSample s = new DieSample();
		double worst = Double.POSITIVE_INFINITY;
		String where = "";
		int bad = 0;
		for (int seed = 1; seed <= 40; seed++) {
			boolean hit = false;
			int side = seed % 2 == 0 ? CrapsThrow.LEFT : CrapsThrow.RIGHT;
			DiceRoll r = CrapsThrow.solve(side, seed * 977, 1 + seed % 6, 1 + (seed * 7) % 6, seed % 4 == 0 ? 8 : 0);
			CrapsBeats beats = CrapsBeats.of(r.restMs());
			double[] rest = new double[4], home = new double[4];
			for (int d = 0; d < 2; d++) {
				r.sample(d, 1e9, s);
				rest[2 * d] = s.x * px;
				rest[2 * d + 1] = s.y * px;
				double[] h = CrapsThrow.release(side, d);
				home[2 * d] = h[0] * px;
				home[2 * d + 1] = h[1] * px;
			}
			for (double t = beats.STICK - 5; t <= beats.IDLE + 50; t += 1) {
				CrapsStick.sample(t, beats, rest, home, -10, 2 * px, o);
				double dc = Math.hypot(o.x[0] - o.x[1], o.y[0] - o.y[1]) / px;
				if (dc < 2 - 1e-9) hit = true;
				if (dc < worst) {
					worst = dc;
					where = "seed " + seed + " t " + t + " phase " + o.phase + " (STICK " + beats.STICK + ", IDLE " + beats.IDLE + ")";
				}
			}
			if (hit) bad++;
			CrapsStick.sample(beats.IDLE + 1, beats, rest, home, -10, 2 * px, o);
			for (int d = 0; d < 2; d++) {
				assertEquals(home[2 * d], o.x[d], 1e-6, "seed " + seed + " die " + d + " x at home");
				assertEquals(home[2 * d + 1], o.y[d], 1e-6, "seed " + seed + " die " + d + " y at home");
			}
		}
		System.out.println("T-CM: closest stick drag " + worst + " u at " + where + "; " + bad + "/40 rolls overlap");
		assertTrue(worst >= 2 - 1e-9, "the stick drags the dice into each other in " + bad + "/40 rolls: centres " + worst + " u at " + where);
	}

	/** The server's reveal waits for the rest: text / bet window never before the dice rest on any client solve. */
	@Test
	void revealTrailsTheRest() {
		for (int seed = 0; seed < 64; seed++) {
			DiceRoll r = CrapsThrow.solve(seed % 2, seed * 131 + 5, 1 + seed % 6, 1 + (seed / 6) % 6, 0);
			int rt = CrapsBeats.revealTicks(r.restMs());
			assertTrue(rt * 50 >= r.restMs() + 330, "seed " + seed + ": reveal " + rt + " ticks, rest " + r.restMs());
			assertTrue(r.restMs() >= CrapsThrow.TARGET_MIN - 1 && r.restMs() <= CrapsThrow.TARGET_MAX + 1 || r.fallback(), "seed " + seed + " rest " + r.restMs());
		}
	}

	/** Server cost: the roll solves the throw on the server tick (≤ 4 seeds); it must stay well under a tick. */
	@Test
	void theServerSolveIsCheap() {
		for (int i = 0; i < 20; i++) CrapsThrow.solve(i % 2, 777 + i, 3, 4, 0); // warm-up
		long worst = 0;
		for (int i = 0; i < 200; i++) {
			long t0 = System.nanoTime();
			CrapsThrow.solve(i % 2, 9000 + i * 17, 1 + i % 6, 1 + (i / 6) % 6, i % 3 == 0 ? 5 : 0);
			worst = Math.max(worst, System.nanoTime() - t0);
		}
		System.out.println("T-CM: worst server solve " + worst / 1e6 + " ms");
		assertTrue(worst < 25_000_000L, "a solve took " + worst / 1e6 + " ms on the server tick");
	}
}
