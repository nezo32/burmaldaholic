package dev.nezo.burmaldaholic.core.anim.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T-SY: tries to break the shared clock (pause, tick rate changes, huge/negative jumps, repeated back-jumps) and the seq gate. */
class SmoothClockAdversarialTest {
	private static final double FRAME = 1000.0 / 60;

	/** Per-run stats. */
	private static final class Stats {
		int snaps;
		double maxErr; // max |display - server|
		double minStep = Double.POSITIVE_INFINITY; // smallest per-frame advance while running
		double maxStep;
		boolean mono = true;
		double lastErr;

		@Override
		public String toString() {
			return "snaps=" + snaps + " maxErr=" + maxErr + " minStep=" + minStep + " maxStep=" + maxStep + " mono=" + mono + " lastErr=" + lastErr;
		}
	}

	/**
	 * A server whose time (ms) is {@code server(w)}; the client observes floor(server/50) at each client tick
	 * ({@code obsEveryMs}); msPerTick and pause given per wall time.
	 */
	interface Scenario {
		double serverMs(double w);

		double msPerTick(double w);

		boolean paused(double w);
	}

	private static Stats run(Scenario s, double seconds, double obsEveryMs) {
		return run(s, seconds, obsEveryMs, false);
	}

	/** {@code noReadersWhilePaused}: nobody calls advance while paused (no casino block in view behind the pause menu). */
	private static Stats run(Scenario s, double seconds, double obsEveryMs, boolean noReadersWhilePaused) {
		SmoothClockCore c = new SmoothClockCore();
		Stats st = new Stats();
		double nextObs = 0;
		double prev = Double.NaN;
		for (double w = 0; w < seconds * 1000; w += FRAME) {
			long n = (long) (w * 1e6);
			while (nextObs <= w) {
				c.observe((long) Math.floor(s.serverMs(nextObs) / 50.0), (long) (nextObs * 1e6));
				nextObs += obsEveryMs;
			}
			if (noReadersWhilePaused && s.paused(w)) continue;
			c.advance(n, s.msPerTick(w), s.paused(w));
			double d = c.nowMs();
			if (!Double.isNaN(prev)) {
				double step = d - prev;
				if (step < 0) st.mono = false;
				if (!s.paused(w) && w > 1000) {
					st.minStep = Math.min(st.minStep, step);
					st.maxStep = Math.max(st.maxStep, step);
				}
			}
			if (c.lastSnap()) st.snaps++;
			prev = d;
			if (w > 1000) st.maxErr = Math.max(st.maxErr, Math.abs(d - s.serverMs(w)));
			st.lastErr = d - s.serverMs(w);
		}
		return st;
	}

	@Test
	void pauseFreezesAndResumesWithoutSnap() {
		// server runs 0-3 s, paused 3-8 s (time stands still), runs again
		Scenario s = new Scenario() {
			public double serverMs(double w) {
				return w < 3000 ? w : w < 8000 ? 3000 : w - 5000;
			}

			public double msPerTick(double w) {
				return 50;
			}

			public boolean paused(double w) {
				return w >= 3000 && w < 8000;
			}
		};
		Stats st = run(s, 12, 50);
		assertTrue(st.mono, st.toString());
		assertEquals(0, st.snaps, st.toString());
		assertTrue(st.maxErr < 120, "pause: " + st);
	}

	@Test
	void pauseWithNoReaderDuringPause() {
		// singleplayer Esc for 60 s with no casino block in view: nobody calls nowMs during the pause
		Scenario s = new Scenario() {
			public double serverMs(double w) {
				return w < 3000 ? w : w < 63000 ? 3000 : w - 60000;
			}

			public double msPerTick(double w) {
				return 50;
			}

			public boolean paused(double w) {
				return w >= 3000 && w < 63000;
			}
		};
		Stats st = run(s, 70, 50, true);
		assertTrue(st.mono, st.toString());
		assertTrue(Math.abs(st.lastErr) < 500, "clock far ahead of the server after an unobserved pause: " + st);
	}

	@Test
	void tickRate5() {
		// 20 TPS for 4 s, then /tick rate 5 (200 ms/tick)
		Scenario s = rateChange(50, 200);
		Stats st = run(s, 12, 50);
		assertTrue(st.mono, st.toString());
		assertEquals(0, st.snaps, "rate 5: " + st);
		assertTrue(Math.abs(st.lastErr) < 100, "rate 5 converged: " + st);
	}

	@Test
	void tickRate60() {
		// 20 TPS for 4 s, then /tick rate 60 (16.67 ms/tick): server time now runs 3x wall
		Scenario s = rateChange(50, 1000.0 / 60);
		Stats st = run(s, 12, 1000.0 / 60);
		assertTrue(st.mono, st.toString());
		// a correct clock tracks the 3x server time: no snap, small error
		assertEquals(0, st.snaps, "rate 60 must not snap: " + st);
		assertTrue(st.maxErr < 600, "rate 60 error: " + st);
	}

	private static Scenario rateChange(double from, double to) {
		return new Scenario() {
			public double serverMs(double w) {
				return w < 4000 ? w : 4000 + (w - 4000) * 50.0 / to;
			}

			public double msPerTick(double w) {
				return w < 4000 ? from : to;
			}

			public boolean paused(double w) {
				return false;
			}
		};
	}

	@Test
	void laggingServer12Tps() {
		// integrated server lagging at 12 TPS (msPerTick still reports 50): server time runs at 0.6x wall
		Scenario s = new Scenario() {
			public double serverMs(double w) {
				return w * 0.6;
			}

			public double msPerTick(double w) {
				return 50;
			}

			public boolean paused(double w) {
				return false;
			}
		};
		Stats st = run(s, 30, 1000.0 / 12);
		System.out.println("lagging 12 TPS: " + st);
		assertTrue(st.mono, st.toString());
		assertTrue(Math.abs(st.lastErr) < 500, "lagging server: clock runs ahead of the server: " + st);
	}

	@Test
	void hugeForwardJump() {
		Scenario s = jumpAt(5000, 60_000);
		Stats st = run(s, 10, 50);
		assertTrue(st.mono, st.toString());
		assertEquals(1, st.snaps, st.toString());
		assertTrue(Math.abs(st.lastErr) < 60, st.toString());
	}

	@Test
	void singleBackJumpNeverFreezes() {
		Scenario s = jumpAt(5000, -1000);
		Stats st = run(s, 15, 50);
		assertTrue(st.mono, st.toString());
		// never visibly frozen: each frame advances at least 0.45x a nominal frame
		assertTrue(st.minStep >= 0.45 * FRAME, "frozen frames after -1 s: " + st);
		assertTrue(Math.abs(st.lastErr) < 60, "converged within 10 s: " + st);
	}

	@Test
	void repeatedBackJumpsNeverFreezeAndStayBounded() {
		// a -1 s jump every 2.5 s for 20 s
		Scenario s = new Scenario() {
			public double serverMs(double w) {
				return w - 1000 * Math.floor(w / 2500);
			}

			public double msPerTick(double w) {
				return 50;
			}

			public boolean paused(double w) {
				return false;
			}
		};
		Stats st = run(s, 20, 50);
		assertTrue(st.mono, st.toString());
		assertTrue(st.minStep >= 0.45 * FRAME, "frozen: " + st);
		System.out.println("repeated -1 s every 2.5 s: " + st);
	}

	private static Scenario jumpAt(double at, double ms) {
		return new Scenario() {
			public double serverMs(double w) {
				return w < at ? w + 100_000 : w + 100_000 + ms;
			}

			public double msPerTick(double w) {
				return 50;
			}

			public boolean paused(double w) {
				return false;
			}
		};
	}

	@Test
	void seqGateNeverDropsLegitimatelyNewer() {
		SeqGate g = new SeqGate();
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(500, 7));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(501, 7));
		assertEquals(SeqGate.Verdict.DROP, g.offer(501, 7));
		assertEquals(SeqGate.Verdict.DROP, g.offer(3, 7));
		// table replaced in place / server restart: new epoch, seq restarts at 1
		assertEquals(SeqGate.Verdict.RESET, g.offer(1, -12345));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(2, -12345));
		// back to the old epoch (e.g. same pos in another dimension): reset again, not dropped
		assertEquals(SeqGate.Verdict.RESET, g.offer(1, 7));
		// overflow: a server counter that wraps to negative is never dropped (passes as seq-less)
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(Long.MAX_VALUE, 7));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(Long.MAX_VALUE + 1, 7));
		// epoch 0 is a real epoch (random may yield it)
		assertEquals(SeqGate.Verdict.RESET, g.offer(1, 0));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(2, 0));
	}
}
