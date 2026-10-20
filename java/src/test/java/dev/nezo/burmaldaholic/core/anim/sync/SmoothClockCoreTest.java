package dev.nezo.burmaldaholic.core.anim.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.LongToDoubleFunction;
import org.junit.jupiter.api.Test;

/** SY-SPEC §5: the shared clock under steady, jittery, stalled, slow, jumping and resetting servers (fake wall clock). */
class SmoothClockCoreTest {
	private static final double FRAME_MS = 1000.0 / 60;

	/** One simulated run: what the display did each frame. */
	private record Run(int snaps, double maxAbsRateDev, double finalError, boolean monotonic, boolean snapOnlyWhenBehind,
			int aheadFrames) {}

	/**
	 * Server ticks every {@code msPerTick} wall ms (tick k at wall {@code k·msPerTick}), each observed at
	 * {@code sent + latency(k)} (sorted by arrival); {@code tickShift(k)} adds a server time jump. Runs {@code seconds}.
	 */
	private static Run simulate(double msPerTick, LongToDoubleFunction latency, java.util.function.LongUnaryOperator tickShift,
			double seconds, double settleAfterMs) {
		SmoothClockCore c = new SmoothClockCore();
		record Obs(double arrive, long tick) {}
		PriorityQueue<Obs> pending = new PriorityQueue<>((a, b) -> Double.compare(a.arrive, b.arrive));
		long ticks = (long) (seconds * 1000 / msPerTick);
		for (long k = 1; k <= ticks; k++) pending.add(new Obs(k * msPerTick + latency.applyAsDouble(k), tickShift.applyAsLong(k)));
		double prev = Double.NEGATIVE_INFINITY;
		int snaps = 0;
		int ahead = 0;
		double maxDev = 0;
		boolean mono = true;
		boolean snapOk = true;
		double lastErr = 0;
		for (double w = 0; w < seconds * 1000; w += FRAME_MS) {
			long nanos = (long) (w * 1e6);
			while (!pending.isEmpty() && pending.peek().arrive <= w) c.observe(pending.poll().tick, nanos);
			double before = c.nowMs();
			c.advance(nanos, msPerTick, false);
			if (!c.started()) continue;
			double now = c.nowMs();
			if (now < prev) mono = false;
			prev = now;
			if (c.lastSnap()) {
				snaps++;
				if (c.lastTarget() - before <= SmoothClockCore.SNAP_MS) snapOk = false;
			} else if (c.lastRate() < 1 - SmoothClockCore.SLEW - 1e-9) {
				ahead++;
			} else if (w > settleAfterMs) {
				maxDev = Math.max(maxDev, Math.abs(c.lastRate() - 1));
			}
			lastErr = c.lastTarget() - now;
		}
		return new Run(snaps, maxDev, lastErr, mono, snapOk, ahead);
	}

	private static void assertCommon(Run r) {
		assertTrue(r.monotonic(), "display went backwards: " + r);
		assertTrue(r.snapOnlyWhenBehind(), "snapped while not > 500 ms behind: " + r);
		assertTrue(r.maxAbsRateDev() <= SmoothClockCore.SLEW + 1e-9, "|rate-1| > 0.1: " + r);
	}

	@Test
	void steady() {
		Run r = simulate(50, k -> 0, k -> k, 10, 0);
		assertCommon(r);
		assertEquals(0, r.snaps(), r.toString());
		assertEquals(0, r.aheadFrames(), r.toString());
		assertTrue(Math.abs(r.finalError()) < 5, r.toString());
	}

	@Test
	void jitter150() {
		Random rnd = new Random(42);
		double[] lat = new double[1000];
		for (int i = 0; i < lat.length; i++) lat[i] = 150 + (rnd.nextDouble() * 2 - 1) * 150;
		Run r = simulate(50, k -> lat[(int) (k % lat.length)], k -> k, 20, 0);
		assertCommon(r);
		assertEquals(0, r.snaps(), r.toString());
		assertTrue(Math.abs(r.finalError()) < 60, r.toString());
	}

	@Test
	void stallThenBunchOfFive() {
		// ticks sent in [3000, 3800) all arrive at 3800 (a stall then a bunch), 50 ms base latency
		Run r = simulate(50, k -> {
			double sent = k * 50.0;
			return sent >= 3000 && sent < 3800 ? 3800 - sent : 50;
		}, k -> k, 8, 0);
		assertCommon(r);
		assertEquals(0, r.snaps(), "a stall of 800 ms projects forward; no snap: " + r);
		assertEquals(0, r.aheadFrames(), r.toString());
	}

	@Test
	void fifteenTps() {
		Run r = simulate(1000.0 / 15, k -> 30, k -> k, 10, 0);
		assertCommon(r);
		assertEquals(0, r.snaps(), r.toString());
		assertTrue(Math.abs(r.finalError()) < 10, "tracks tick·50 at 15 TPS: " + r);
	}

	@Test
	void jumpForwardTwoSecondsSnapsOnce() {
		Run r = simulate(50, k -> 40, k -> k * 50 >= 4000 ? k + 40 : k, 8, 0);
		assertCommon(r);
		assertEquals(1, r.snaps(), r.toString());
		assertTrue(Math.abs(r.finalError()) < 10, r.toString());
	}

	@Test
	void jumpBackOneSecondSlowsNeverRewinds() {
		Run r = simulate(50, k -> 40, k -> k * 50 >= 4000 ? k - 20 : k, 16, 0);
		assertCommon(r);
		assertEquals(0, r.snaps(), r.toString());
		assertTrue(r.aheadFrames() > 0, "runs at half speed while > 500 ms ahead: " + r);
		assertTrue(Math.abs(r.finalError()) < 20, "converges again: " + r);
	}

	@Test
	void epochReset() {
		SmoothClockCore c = new SmoothClockCore();
		c.observe(1000, 0);
		c.advance(0, 50, false);
		int e0 = c.epoch();
		List<Double> seen = new ArrayList<>();
		for (int i = 1; i <= 60; i++) {
			c.observe(1000 + i / 3, (long) (i * FRAME_MS * 1e6));
			c.advance((long) (i * FRAME_MS * 1e6), 50, false);
			seen.add(c.nowMs());
		}
		for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1));
		c.reset(10, (long) (61 * FRAME_MS * 1e6)); // rejoin / dimension change: the only way back
		assertEquals(e0 + 1, c.epoch());
		assertEquals(500.0, c.nowMs(), 1e-9);
		c.clear();
		assertFalse(c.started());
		assertEquals(e0 + 2, c.epoch());
	}

	@Test
	void pausedFreezesAndDoesNotSnapAfterwards() {
		SmoothClockCore c = new SmoothClockCore();
		c.observe(100, 0);
		double w = 0;
		for (; w < 1000; w += FRAME_MS) {
			c.observe(100 + (long) (w / 50), (long) (w * 1e6));
			c.advance((long) (w * 1e6), 50, false);
		}
		double atPause = c.nowMs();
		for (double p = 0; p < 3000; p += FRAME_MS) c.advance((long) ((w + p) * 1e6), 50, true);
		assertEquals(atPause, c.nowMs(), 1e-9);
		w += 3000;
		c.advance((long) (w * 1e6), 50, false);
		assertFalse(c.lastSnap(), "the projections do not age while paused");
	}
}
