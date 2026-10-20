package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Owner playtest S1 (bad connection): the result reaches the client 0 / 300 / 800 ms (and 2–4 s) after the SPIN start.
 * Until then the client draws the result-free orbit; the solved path then starts from the ball's drawn position and
 * speed ({@link RouletteSpin#build(int, int, int, long, long, int, RotorModel, double)}). Simulated at 60 fps exactly as
 * {@code RouletteScreen} does it: no frame moves the ball further than its speed allows, the frame after the result is
 * where the old orbit would have been, the speed is continuous, and the ball still rests in the drawn pocket.
 */
class RouletteLateResultTest {
	private static final double RW_PX = 92;
	private static final double FRAME_MS = 1000 / 60.0;
	/** The fastest ball (launch 756 °/s + 6 %) on the wall: px per 60 fps frame, plus 10 % slack. */
	private static final double MAX_FRAME_PX = 756 * 1.06 / 360 * 2 * Math.PI * RouletteSpin.R_WALL * RW_PX / 60 * 1.1;
	/** At the join frame the new path is within this of where the result-free orbit would be (px). */
	private static final double JOIN_PX = 1.0;
	private static final int[] SPIN_TICKS = {40, 60, 100, 140, 200, 300};

	private static double px(RouletteSpin.Sample s) {
		return s.r * Math.sin(Math.toRadians(s.abs)) * RW_PX;
	}

	private static double py(RouletteSpin.Sample s) {
		return -s.r * Math.cos(Math.toRadians(s.abs)) * RW_PX;
	}

	private static double dist(RouletteSpin.Sample a, RouletteSpin.Sample b) {
		return Math.hypot(px(a) - px(b), py(a) - py(b));
	}

	private static RotorModel rotor(int seed) {
		double[] angles = {0, 37.25, 123.5, 359.99, 211, 90.01};
		double[] speeds = {36, -36, 150, -150, 0, 80};
		return RotorModel.launch(angles[seed % 6], speeds[seed % 6], seed % 2 == 0 ? 1 : -1);
	}

	/** One client: draws the pre-result spin until the packet arrives, then the joined spin; checks every frame. */
	private static Result play(int result, int seed, int ticks, double lateMs, boolean join) {
		long launch = 2_000_000L + seed;
		long spinStart = launch + RouletteSpin.NMB_TICKS;
		int last = seed % 5 == 0 ? -1 : (seed * 7) % 37;
		RotorModel r = rotor(seed);
		RouletteSpin pre = RouletteSpin.build(-1, seed, last, launch, spinStart, ticks, r);
		double arrival = (spinStart - launch) * 50.0 + lateMs; // late after the draw (the SPIN start packet)
		RouletteSpin cur = pre;
		RouletteSpin.Sample prev = null;
		double lastDrawn = Double.NaN;
		Result out = new Result();
		for (int k = 0; ; k++) {
			double t = k * FRAME_MS;
			if (cur == pre && t >= arrival) {
				cur = RouletteSpin.build(result, seed, last, launch, spinStart, ticks, r, join ? lastDrawn : Double.NaN);
				RouletteSpin.Sample would = pre.sample(t, new RouletteSpin.Sample());
				RouletteSpin.Sample now = cur.sample(t, new RouletteSpin.Sample());
				out.joinGapPx = dist(would, now);
				// C1 at the join itself: the solved stage B starts at the orbit's own speed
				double tj = cur.joinMs() + 0.01;
				out.joinOmegaRatio = cur.sample(tj, new RouletteSpin.Sample()).omega / pre.sample(tj, new RouletteSpin.Sample()).omega;
				out.spin = cur;
			}
			RouletteSpin.Sample s = cur.sample(t, new RouletteSpin.Sample());
			if (prev != null && prev.visible && s.visible && dist(prev, s) > out.maxFramePx) {
				out.maxFramePx = dist(prev, s);
				out.maxAt = "t " + Math.round(t) + " phase " + prev.phase + "->" + s.phase + " r " + prev.r + "->" + s.r + " abs " + prev.abs + "->" + s.abs
					+ " join " + (cur == pre ? -1 : Math.round(cur.joinMs())) + " drop " + Math.round(cur.dropMs()) + " rest " + Math.round(cur.restMs());
			}
			prev = s;
			lastDrawn = t;
			if (cur != pre && t > cur.restMs() + 200) break;
			if (k > 100_000) throw new AssertionError("never rests");
		}
		return out;
	}

	private static final class Result {
		RouletteSpin spin;
		double joinGapPx;
		double joinOmegaRatio;
		double maxFramePx;
		String maxAt = "";
	}

	@Test
	void aLateResultContinuesTheOrbitWithoutAJump() {
		int runs = 0;
		for (double late : new double[] {0, 300, 800}) {
			for (int ticks : SPIN_TICKS) {
				for (int seed = 0; seed < 24; seed++) {
					int result = (seed * 11 + ticks) % 37;
					String at = "late " + late + " ticks " + ticks + " seed " + seed + " result " + result;
					Result res = play(result, seed, ticks, late, true);
					RouletteSpin sp = res.spin;
					assertTrue(res.joinGapPx <= JOIN_PX, at + ": the ball jumped " + res.joinGapPx + " px when the result arrived");
					assertEquals(1, res.joinOmegaRatio, 0.02, at + ": speed continuous at the join");
					assertTrue(res.maxFramePx <= MAX_FRAME_PX, at + ": a frame moved " + res.maxFramePx + " px (max " + MAX_FRAME_PX + ")");
					assertEquals(result, sp.pocketUnder(sp.restMs() + 100), at + ": rests in the drawn pocket");
					assertTrue(sp.restMs() <= sp.spinEndMs() - RouletteSpin.END_MARGIN_MS + sp.overrunMs() + 1e-6, at);
					// the default spin (7 s) and longer absorb 800 ms of lag inside the SPIN phase
					if (ticks >= 100) assertEquals(0, sp.overrunMs(), at + ": overrun");
					runs++;
				}
			}
		}
		assertEquals(3 * SPIN_TICKS.length * 24, runs);
	}

	/** The on-time client (the result there before the SPIN start) builds exactly the shared spin. */
	@Test
	void anOnTimeJoinIsTheSharedSpin() {
		RotorModel r = rotor(3);
		RouletteSpin a = RouletteSpin.build(17, 3, 4, 100, 120, 140, r);
		RouletteSpin b = RouletteSpin.build(17, 3, 4, 100, 120, 140, r, a.spinStartMs() - 5);
		RouletteSpin.Sample x = new RouletteSpin.Sample();
		RouletteSpin.Sample y = new RouletteSpin.Sample();
		for (double t = 0; t < a.restMs() + 500; t += 7) {
			a.sample(t, x);
			b.sample(t, y);
			assertEquals(x.abs, y.abs, 0, "t " + t);
			assertEquals(x.r, y.r, 0, "t " + t);
		}
		assertEquals(a.restMs(), b.restMs(), 0);
	}

	/** The old behaviour (path solved from the SPIN start) jumps at 800 ms late: the test above would catch it. */
	@Test
	void withoutTheJoinThe800MsLateBallJumps() {
		int jumps = 0;
		for (int seed = 0; seed < 24; seed++) {
			Result res = play((seed * 11 + 140) % 37, seed, 140, 800, false);
			if (res.joinGapPx > 5) jumps++;
		}
		assertTrue(jumps >= 16, "old path jumped in only " + jumps + " / 24 spins");
	}

	/** 2–4 s late: still continuous; the shortest spins may rest after the SPIN end (the screen waits for the rest). */
	@Test
	void aVeryLateResultStillLandsSmoothly() {
		for (double late : new double[] {2000, 4000}) {
			for (int ticks : SPIN_TICKS) {
				for (int seed = 0; seed < 8; seed++) {
					int result = (seed * 5 + 3) % 37;
					String at = "late " + late + " ticks " + ticks + " seed " + seed;
					Result res = play(result, seed, ticks, late, true);
					assertTrue(res.joinGapPx <= JOIN_PX, at + ": jump " + res.joinGapPx);
					assertTrue(res.maxFramePx <= MAX_FRAME_PX, at + ": frame " + res.maxFramePx + " at " + res.maxAt);
					assertEquals(result, res.spin.pocketUnder(res.spin.restMs() + 100), at);
					assertTrue(res.spin.overrunMs() <= RouletteSpin.MAX_OVERRUN_MS, at);
				}
			}
		}
	}
}
