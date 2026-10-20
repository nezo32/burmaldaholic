package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Trays, throw specs, the cup shake (DI §10.1-10) and the sampling contract (DI §10.1-9) of the dice kit. */
class DiceKitTest {
	@Test
	void wallNormalsPointInside() {
		for (DiceFixtures.Case c : DiceFixtures.all()) {
			DiceTray t = c.tray();
			DiceTray.Box z = t.restZone(0);
			double cx = (z.x0() + z.x1()) / 2;
			double cy = (z.y0() + z.y1()) / 2;
			for (DiceTray.Wall w : t.allWalls()) assertTrue(w.distance(cx, cy) > 0, c.name() + " wall " + w);
			assertTrue(t.floor().contains(cx, cy), c.name());
			assertTrue(t.floor().contains(c.spec().x(), c.spec().y()), c.name() + " release on the floor");
		}
	}

	@Test
	void rightLaneIsTheMirror() {
		DiceTray l = DiceTray.duelLane(DiceTray.Side.LEFT);
		DiceTray r = DiceTray.duelLane(DiceTray.Side.RIGHT);
		assertEquals(l.walls().size(), r.walls().size());
		for (int i = 0; i < l.walls().size(); i++) {
			DiceTray.Wall a = l.walls().get(i);
			DiceTray.Wall b = r.walls().get(i);
			assertEquals(a.nx(), -b.nx(), 1e-12);
			assertEquals(a.ny(), b.ny(), 1e-12);
		}
		assertEquals(DiceTray.DUEL_W - l.restZone(0).x1(), r.restZone(0).x0(), 1e-12);
		ThrowSpec sl = ThrowSpec.cup(DiceTray.Side.LEFT);
		ThrowSpec sr = ThrowSpec.cup(DiceTray.Side.RIGHT);
		assertEquals(DiceTray.DUEL_W - sl.x(), sr.x(), 1e-12);
		assertEquals(Math.PI, sr.dir(), 1e-12);
	}

	@Test
	void shakeIntervalsShrinkToTheFloor() {
		double prev = Double.POSITIVE_INFINITY;
		for (int i = 0; i < 40; i++) {
			double iv = Shake.interval(i);
			assertTrue(iv <= prev && iv >= Shake.FLOOR_INTERVAL);
			prev = iv;
			assertEquals(Shake.reversal(i) - (i == 0 ? 0 : Shake.reversal(i - 1)), iv, 1e-9);
		}
		assertEquals(Shake.FLOOR_INTERVAL, Shake.interval(39), 1e-9);
	}

	@Test
	void releaseFallsOnAReversal() {
		for (double ready : new double[] {0, 100, 419, 420, 421, 777, 2000, 9000}) {
			double w = Shake.windUpAt(ready);
			assertTrue(w >= Math.max(ready, Shake.SHAKE_MIN) - 1e-9);
			int k = Shake.reversalAtOrAfter(w);
			assertEquals(Shake.reversal(k), w, 1e-9, "wind-up on a reversal");
			if (k > 0) assertTrue(Shake.reversal(k - 1) < Math.max(ready, Shake.SHAKE_MIN), "the first reversal after ready");
			assertEquals(w + Shake.WIND_UP + Shake.RELEASE_IN_FLICK, Shake.releaseAt(w), 1e-9);
		}
	}

	@Test
	void rattleHitsPerDieAreApartAndCounterPhaseIsTheNegation() {
		Shake a = Shake.of(77, false);
		Shake b = Shake.of(91, true);
		double w = Shake.windUpAt(700);
		MotionEvent[] ev = a.rattle(w, 2);
		assertTrue(MotionEvent.sorted(ev));
		for (int d = 0; d < 2; d++) {
			double last = -1e9;
			for (MotionEvent e : ev) {
				if (e.obj() != d) continue;
				assertTrue(e.tMs() - last >= 45, "rattle hits ≥ 45 ms apart");
				assertTrue(e.tMs() < w, "silence during the wind-up");
				last = e.tMs();
			}
		}
		for (double t = 0; t < w + 600; t += 3.7) {
			assertEquals(a.offsetX(t, w), -b.offsetX(t, w), 1e-12);
			assertTrue(Math.abs(a.offsetX(t, w)) <= Shake.AMP_PX + 1e-9);
		}
		assertEquals(0, a.offsetX(w + Shake.WIND_UP + 1, w), 1e-12);
		assertEquals(Shake.F_UP, a.frame(w + 10, w));
		assertEquals(Shake.F_TIP70, a.frame(w + Shake.WIND_UP + Shake.FLICK - 1, w));
		assertEquals(Shake.F_REST, a.frame(w + Shake.WIND_UP + Shake.FLICK + Shake.RECOVER + 5, w));
	}

	@Test
	void eventsFireOnceInOrderAtAnyFrameRate() {
		DiceFixtures.Case c = DiceFixtures.all().get(0);
		DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), 4242, new int[] {6, 1}, c.targetMs());
		for (double step : new double[] {4.2, 8.3, 16.7, 33, 500}) {
			EventCursor cur = new EventCursor(r.events());
			List<MotionEvent> got = new ArrayList<>();
			for (double t = -20; t <= r.restMs() + 600; t += step) cur.advance(t, (e, silent) -> got.add(e));
			assertEquals(r.events().length, got.size(), "step " + step);
			for (int i = 0; i < got.size(); i++) assertEquals(r.events()[i], got.get(i));
		}
	}

	@Test
	void sampleIsPureAndAllocationFree() {
		DiceFixtures.Case c = DiceFixtures.all().get(5);
		DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), 99, new int[] {2, 5}, c.targetMs());
		DieSample a = new DieSample();
		DieSample b = new DieSample();
		// history independence
		r.sample(0, 800, a);
		for (double t = 0; t < 2000; t += 13) r.sample(0, t, b);
		r.sample(0, 800, b);
		assertEquals(a.x, b.x, 0);
		assertEquals(a.q[0], b.q[0], 0);
		com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().threadId();
		for (int i = 0; i < 20000; i++) r.sample(i & 1, (i * 0.37) % 1900, a); // warm up
		long before = mx.getThreadAllocatedBytes(tid);
		for (int i = 0; i < 10000; i++) r.sample(i & 1, (i * 0.37) % 1900, a);
		long after = mx.getThreadAllocatedBytes(tid);
		assertTrue(after - before < 1024, "allocated " + (after - before) + " bytes over 10k samples");
	}

	@Test
	void samplingNeverSolvesAndTheCacheSolvesOnce() {
		DiceFixtures.Case c = DiceFixtures.all().get(0);
		DiceRollCache cache = new DiceRollCache(4);
		long key = DiceRollCache.key(17, 0, 1234);
		long before = DiceSolver.SOLVES.get();
		DiceRoll r = cache.get(key, () -> DiceSolver.solve(c.tray(), c.spec(), 1234, new int[] {4, 4}, c.targetMs()));
		assertEquals(before + 1, DiceSolver.SOLVES.get());
		DieSample s = new DieSample();
		for (int f = 0; f < 10_000; f++) {
			DiceRoll same = cache.get(key, () -> DiceSolver.solve(c.tray(), c.spec(), 1234, new int[] {4, 4}, c.targetMs()));
			assertTrue(same == r);
			same.sample(f & 1, f * 0.25, s);
			same.topFace(f & 1, f * 0.25, s);
		}
		assertEquals(before + 1, DiceSolver.SOLVES.get(), "10k sampled frames solve 0 times");
		for (int k = 0; k < 6; k++) cache.get(DiceRollCache.key(k, 1, 9), () -> r);
		assertEquals(4, cache.size());
	}

	@Test
	void worstCaseSolveStaysUnder20ms() {
		java.util.List<DiceFixtures.Case> cases = DiceFixtures.all();
		for (int i = 0; i < 2000; i++) DiceSolver.solve(cases.get(i % cases.size()).tray(), cases.get(i % cases.size()).spec(), i, new int[] {1, 2}, cases.get(i % cases.size()).targetMs());
		long worst = 0;
		int worstAttempt = 0;
		int scripted = 0;
		int solves = 0;
		for (DiceFixtures.Case c : cases)
			for (int i = 0; i < 1500; i++) {
				// best of 3 runs per seed: the machine is shared, this measures the solver, not the scheduler
				long dt = Long.MAX_VALUE;
				DiceRoll r = null;
				for (int rep = 0; rep < 3; rep++) {
					long t0 = System.nanoTime();
					r = DiceSolver.solve(c.tray(), c.spec(), 0x7700 + i * 31, new int[] {1 + i % 6, 6 - i % 6}, c.targetMs());
					dt = Math.min(dt, System.nanoTime() - t0);
				}
				if (dt > worst) worst = dt;
				worstAttempt = Math.max(worstAttempt, r.attempt());
				assertTrue(r.attempt() <= DiceSolver.ATTEMPTS + 2 * DiceSolver.FALLBACK_ATTEMPTS, "attempt cap");
				solves++;
				if (r.attempt() == DiceSolver.ATTEMPTS + 2 * DiceSolver.FALLBACK_ATTEMPTS) scripted++;
			}
		System.out.printf("worst solve %.2f ms, max attempt index %d, scripted drops %d/%d%n", worst / 1e6, worstAttempt, scripted, solves);
		// the scripted drop (never-fail path) stays rare
		assertTrue(scripted * 200 <= solves, "scripted drops " + scripted + "/" + solves);
		assertTrue(worst < 20_000_000L, "worst solve " + worst / 1e6 + " ms");
	}

	@Test
	void rejectsBadInput() {
		DiceFixtures.Case c = DiceFixtures.all().get(0);
		boolean thrown = false;
		try {
			DiceSolver.solve(c.tray(), c.spec(), 1, new int[] {7, 1}, 1200);
		} catch (IllegalArgumentException e) {
			thrown = true;
		}
		assertTrue(thrown);
	}
}
