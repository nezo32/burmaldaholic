package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK: EventCursor fires once, in order, at any frame rate; late events (hitch) fire silently. */
class EventCursorTest {
	private static final double[] STEPS = {4.2, 8.3, 16.7, 33, 500};

	private static MotionEvent[] events(long seed, int n, double spanMs) {
		SplittableRandom r = new SplittableRandom(seed);
		MotionEvent[] e = new MotionEvent[n];
		for (int i = 0; i < n; i++) {
			double t = r.nextInt(4) == 0 && i > 0 ? e[i - 1].tMs() : r.nextDouble() * spanMs; // some simultaneous events
			e[i] = new MotionEvent(t, r.nextInt(5), r.nextInt(3), (float) r.nextDouble(), r.nextDouble(), r.nextDouble(), i);
		}
		return MotionEvent.sort(e);
	}

	private record Fired(MotionEvent e, boolean silent, double now) {}

	@Test
	void everyEventOnceAndInOrderAtEveryFrameRate() {
		for (long seed = 1; seed <= 40; seed++) {
			MotionEvent[] ev = events(seed, 150, 4000);
			for (double step : STEPS) {
				List<Fired> got = new ArrayList<>();
				EventCursor c = new EventCursor(ev);
				double[] now = {0};
				for (double t = -10; t < 4200; t += step) {
					now[0] = t;
					c.advance(t, (e, silent) -> got.add(new Fired(e, silent, now[0])));
				}
				c.advance(1e9, (e, silent) -> got.add(new Fired(e, silent, 1e9)));
				assertEquals(ev.length, got.size(), "count, step " + step);
				for (int i = 0; i < ev.length; i++) {
					assertTrue(ev[i] == got.get(i).e(), "order at " + i + " step " + step);
					Fired f = got.get(i);
					assertTrue(f.now() >= f.e().tMs(), "never early");
					assertEquals(f.now() - f.e().tMs() > EventCursor.LATE_MS, f.silent(), "hitch rule");
					if (step <= 33 && f.now() < 1e9) assertFalse(f.silent(), "no silent event at normal frame rates");
				}
				assertTrue(c.done());
			}
		}
	}

	@Test
	void hitchFiresOldEventsSilently() {
		MotionEvent[] ev = {ev(0), ev(100), ev(300), ev(460), ev(480), ev(500)};
		EventCursor c = new EventCursor(ev);
		List<Boolean> silent = new ArrayList<>();
		c.advance(0, (e, s) -> silent.add(s));
		c.advance(500, (e, s) -> silent.add(s)); // a 500 ms hitch
		assertEquals(List.of(false, true, true, false, false, false), silent);
		EventCursor plinko = new EventCursor(ev, 120);
		List<Boolean> s2 = new ArrayList<>();
		plinko.advance(500, (e, s) -> s2.add(s));
		assertEquals(List.of(true, true, true, false, false, false), s2);
	}

	@Test
	void clockGoingBackFiresNothingAndResetRules() {
		MotionEvent[] ev = {ev(10), ev(20), ev(30), ev(40)};
		EventCursor c = new EventCursor(ev);
		List<Double> fired = new ArrayList<>();
		List<Boolean> silent = new ArrayList<>();
		EventCursor.Listener l = (e, s) -> {
			fired.add(e.tMs());
			silent.add(s);
		};
		c.advance(25, l);
		c.advance(5, l);
		assertEquals(List.of(10.0, 20.0), fired);
		// skip forward silently: dropped
		c.reset(30, true);
		c.advance(35, l);
		assertEquals(List.of(10.0, 20.0), fired);
		// rewind re-arms
		c.reset(15, true);
		c.advance(45, l);
		assertEquals(List.of(10.0, 20.0, 20.0, 30.0, 40.0), fired);
		assertTrue(c.done());
		// forward, not silent: delivered silently by the next advance
		c.bind(ev);
		fired.clear();
		silent.clear();
		c.reset(25, false);
		c.advance(25, l);
		assertEquals(List.of(10.0, 20.0), fired);
		assertEquals(List.of(true, true), silent);
		c.advance(30, l);
		assertEquals(List.of(true, true, false), silent);
		assertEquals(30, c.nextTime() - 10);
	}

	@Test
	void rejectsUnsortedAndHandlesEmpty() {
		assertThrows(IllegalArgumentException.class, () -> new EventCursor(new MotionEvent[] {ev(2), ev(1)}));
		EventCursor c = new EventCursor(null);
		c.advance(1e9, (e, s) -> {
			throw new AssertionError();
		});
		assertTrue(c.done());
		assertEquals(Double.POSITIVE_INFINITY, c.nextTime());
	}

	private static MotionEvent ev(double t) {
		return new MotionEvent(t, 0, 0, 1f, 0, 0, 0);
	}
}
