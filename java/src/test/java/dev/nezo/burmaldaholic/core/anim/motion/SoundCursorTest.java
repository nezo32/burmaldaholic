package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PHASE2B §4 MK SoundCursorTest: the voicing rules of {@code client.fx.motion.SoundCursor} live in the pure
 * {@link SoundMixer}; this drives it exactly as SoundCursor does (EventCursor → offer non-silent events → flush per
 * frame): 30 ms merge windows, per-id limiter, lead-object voicing, hitch > 150 ms silent.
 */
class SoundCursorTest {
	private record Played(String id, float volume, int obj) {}

	private static final class Sink implements SoundMixer.Voice {
		final List<Played> played = new ArrayList<>();

		@Override
		public void play(String id, float volume, float pitch, int obj, boolean positional, double x, double y, double z) {
			played.add(new Played(id, volume, obj));
		}
	}

	/** Runs cursors (one per object, events on a common clock) at a frame step, offering every voiced event as "id". */
	private static Sink run(SoundMixer m, double step, double endMs, int crowd, int lead, MotionEvent[]... perObject) {
		Sink sink = new Sink();
		List<EventCursor> cursors = new ArrayList<>();
		for (MotionEvent[] ev : perObject) cursors.add(new EventCursor(ev));
		for (double t = 0; t <= endMs; t += step) {
			m.crowd(crowd, lead);
			for (EventCursor c : cursors) {
				c.advance(t, (e, silent) -> {
					if (!silent) m.offer(e.kind() == 9 ? "bin" : "peg", 0.4f, 1f, e.obj(), e.tMs());
				});
			}
			m.flush(sink);
		}
		return sink;
	}

	private static MotionEvent ev(double t, int obj) {
		return new MotionEvent(t, 0, obj, 1f, 0, 0, 0);
	}

	@Test
	void contactsWithin30msMergeIntoOneLouderSound() {
		SoundMixer m = new SoundMixer();
		// two dice hit the wall 12 ms apart, then again 100 ms later 40 ms apart
		MotionEvent[] d1 = {ev(100, 0), ev(200, 0)};
		MotionEvent[] d2 = {ev(112, 1), ev(240, 1)};
		// 30 fps: 100 and 112 fire in the same frame → one sound, louder
		Sink s = run(m, 33, 400, 2, 0, d1, d2);
		assertEquals(3, s.played.size(), "100+112 merge; 200 and 240 are 40 ms apart");
		assertEquals(0.5f, s.played.get(0).volume(), 1e-6, "merged = louder by 0.1");
		assertEquals(0.4f, s.played.get(1).volume(), 1e-6);
		// 60 fps: 112 fires one frame after 100 was voiced → still merged (dropped), never a second click
		Sink s60 = run(new SoundMixer(), 16.7, 400, 2, 0, d1, d2);
		assertEquals(3, s60.played.size());
	}

	@Test
	void perIdLimiterAndAlwaysIds() {
		SoundMixer m = new SoundMixer().limit("peg", 12).always("bin");
		MotionEvent[] pegs = new MotionEvent[60];
		for (int i = 0; i < 60; i++) pegs[i] = ev(i * 40.0, 0); // 25 contacts per second for 2.4 s
		MotionEvent[] bins = new MotionEvent[30];
		for (int i = 0; i < 30; i++) bins[i] = new MotionEvent(5 + i * 80.0, 9, 1, 1f, 0, 0, 0);
		Sink s = run(m, 8.3, 2500, 1, 0, pegs, bins);
		long pegCount = s.played.stream().filter(p -> p.id().equals("peg")).count();
		long binCount = s.played.stream().filter(p -> p.id().equals("bin")).count();
		assertTrue(pegCount <= 12 * 3 && pegCount >= 24, "≤ 12 peg sounds per second: " + pegCount);
		assertEquals(30, binCount, "always-ids are never limited");
		// sliding window: no 1 s span holds more than 12 pegs
		List<Double> times = new ArrayList<>();
		for (MotionEvent e : pegs) times.add(e.tMs());
		SoundMixer m2 = new SoundMixer().limit("peg", 12);
		List<Double> voiced = new ArrayList<>();
		for (double t : times) {
			if (m2.offer("peg", 1, 1, 0, t) == SoundMixer.QUEUED) voiced.add(t);
			m2.flush(null);
		}
		for (int i = 12; i < voiced.size(); i++) assertTrue(voiced.get(i) - voiced.get(i - 12) >= 1000);
	}

	@Test
	void leadObjectVoicing() {
		// 4 balls in flight: the lead (obj 3) is always voiced, the others every second contact
		SoundMixer m = new SoundMixer(0, 0.1f, 1000).leadVoicing(2, 2);
		MotionEvent[][] balls = new MotionEvent[4][];
		for (int b = 0; b < 4; b++) {
			balls[b] = new MotionEvent[10];
			for (int i = 0; i < 10; i++) balls[b][i] = ev(i * 200.0 + b * 50, b);
		}
		Sink s = run(m, 16.7, 2100, 4, 3, balls);
		assertEquals(10, s.played.stream().filter(p -> p.obj() == 3).count());
		for (int b = 0; b < 3; b++) {
			int obj = b;
			assertEquals(5, s.played.stream().filter(p -> p.obj() == obj).count(), "every second contact of ball " + b);
		}
		// craps: lead die only (othersEvery 0) once two dice are in flight
		SoundMixer craps = new SoundMixer(0, 0.1f, 1000).leadVoicing(1, 0);
		Sink c = run(craps, 16.7, 2100, 2, 0, balls[0], balls[1]);
		assertTrue(c.played.stream().allMatch(p -> p.obj() == 0));
		// with ≤ crowdAbove objects everyone is voiced
		SoundMixer few = new SoundMixer(0, 0.1f, 1000).leadVoicing(2, 2);
		assertEquals(20, run(few, 16.7, 2100, 2, 1, balls[0], balls[1]).played.size());
	}

	@Test
	void hitchIsSilent() {
		SoundMixer m = new SoundMixer();
		MotionEvent[] ev = {ev(0, 0), ev(100, 0), ev(300, 0), ev(400, 0), ev(460, 0)};
		// one 500 ms hitch after t = 0: events older than 150 ms at t = 500 are silent
		Sink s = new Sink();
		EventCursor c = new EventCursor(ev);
		for (double t : new double[] {0, 500}) {
			c.advance(t, (e, silent) -> {
				if (!silent) m.offer("peg", 0.4f, 1, 0, e.tMs());
			});
			m.flush(s);
		}
		assertEquals(3, s.played.size(), "t=0 voiced; 100 and 300 silent; 400 and 460 voiced (460 merges? no: 60 ms apart)");
	}
}
