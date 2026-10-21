package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.sync.SmoothClockCore;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * T-RO S2 / SYW: the IN-WORLD roulette board samples the spin at {@code SmoothClock − launch·50}
 * ({@code SpectatorBlockEntityRenderer} → {@code RouletteBoardRenderer.wheel}). Simulated with the real
 * {@link SmoothClockCore} under the NetLag profile (state + time packets 300 ms late, ±150 ms jitter, bunched every
 * second) and under ±1 s server time jumps: no frame moves the ball further than 1.15 × its top speed × frame time,
 * and it rests in the server's pocket (a +1 s jump: the one forward snap SY allows a spectator). Control: the old raw clock (the newest observed tick) jumps far beyond that.
 */
class RouletteWorldClockTest {
	private static final double RW_PX = 92;
	private static final double FRAME_MS = 1000 / 60.0;
	/** Top ball speed on the wall (launch 756 °/s + 6 %), px per ms. */
	private static final double V_MAX_PX_PER_MS = 756 * 1.06 / 360 * 2 * Math.PI * RouletteSpin.R_WALL * RW_PX / 1000;
	private static final double BOUND_PX = 1.15 * V_MAX_PX_PER_MS * FRAME_MS;

	private static double dist(RouletteSpin.Sample a, RouletteSpin.Sample b) {
		double ax = a.r * Math.sin(Math.toRadians(a.abs)), ay = -a.r * Math.cos(Math.toRadians(a.abs));
		double bx = b.r * Math.sin(Math.toRadians(b.abs)), by = -b.r * Math.cos(Math.toRadians(b.abs));
		return Math.hypot(ax - bx, ay - by) * RW_PX;
	}

	private record Run(double maxFramePx, int framesOver, int pocket, int result) {}

	/**
	 * One in-world viewer. Server tick k happens at wall {@code k·50}; its time packet / state packet arrives at
	 * {@code k·50 + 300 ± 150}, rounded up to the next 1 s bunch. {@code jumpMs} shifts every server tick after 3 s.
	 */
	private static Run run(int seed, double jumpMs, boolean smooth) {
		Random rnd = new Random(seed * 31L + 7);
		long launch = 10_000;
		int ticks = 140;
		long spinStart = launch + RouletteSpin.NMB_TICKS;
		int result = (seed * 11 + 5) % 37;
		RotorModel rotor = RotorModel.launch(seed * 37.0 % 360, seed % 2 == 0 ? 36 : -36, seed % 2 == 0 ? 1 : -1);
		RouletteSpin pre = RouletteSpin.build(-1, seed, 3, launch, spinStart, ticks, rotor);
		record Obs(double at, long tick) {}
		PriorityQueue<Obs> q = new PriorityQueue<>((a, b) -> Double.compare(a.at, b.at));
		long t0 = launch - 40; // the viewer is watching 2 s before the launch
		double endWall = (spinStart - t0 + ticks) * 50.0 + 4000;
		for (long k = 0; k * 50 <= endWall; k++) {
			double due = Math.ceil((k * 50 + 300 + (rnd.nextDouble() * 2 - 1) * 150) / 1000) * 1000;
			long shift = k * 50 >= 3000 ? Math.round(jumpMs / 50) : 0;
			q.add(new Obs(due, t0 + k + shift));
		}
		// the result's state packet: sent at the SPIN start, same lag profile
		double resultAt = Math.ceil(((spinStart - t0) * 50.0 + 300 + 150) / 1000) * 1000;
		SmoothClockCore core = new SmoothClockCore();
		core.reset(t0, 0);
		long rawTick = t0;
		RouletteSpin cur = pre;
		RouletteSpin.Sample prev = null;
		int over = 0;
		double lastT = Double.NaN;
		double max = 0;
		for (double w = 0; w <= endWall; w += FRAME_MS) {
			long nanos = (long) (w * 1e6);
			while (!q.isEmpty() && q.peek().at <= w) {
				Obs o = q.poll();
				core.observe(o.tick, nanos);
				rawTick = o.tick; // the old board: the client level time as the last packet set it
			}
			core.advance(nanos, 50, false);
			double t = (smooth ? core.nowMs() : rawTick * 50.0) - launch * 50.0;
			if (cur == pre && w >= resultAt) cur = RouletteSpin.build(result, seed, 3, launch, spinStart, ticks, rotor, lastT);
			if (t < 0) continue;
			RouletteSpin.Sample s = cur.sample(t, new RouletteSpin.Sample());
			if (prev != null && prev.visible && s.visible) {
				double d = dist(prev, s);
				if (d > max) max = d;
				if (d > BOUND_PX) over++;
			}
			prev = s;
			lastT = t;
		}
		return new Run(max, over, cur.pocketUnder(cur.restMs() + 100), result);
	}

	@Test
	void theBoardBallNeverJumpsUnderNetLag() {
		for (int seed = 0; seed < 12; seed++) {
			Run r = run(seed, 0, true);
			assertTrue(r.maxFramePx <= BOUND_PX, "seed " + seed + ": a frame moved " + r.maxFramePx + " px (bound " + BOUND_PX + ")");
			assertEquals(r.result, r.pocket, "seed " + seed + ": rests in the server's pocket");
		}
	}

	@Test
	void theBoardBallSurvivesTimeJumps() {
		for (double jump : new double[] {1000, -1000}) {
			for (int seed = 0; seed < 6; seed++) {
				Run r = run(seed, jump, true);
				// −1 s: the clock slows down, never rewinds: every frame bounded. +1 s: a spectator's shared clock
				// snaps forward exactly once by design (SY-SPEC §1, > 500 ms behind); every other frame is bounded
				if (jump < 0) assertTrue(r.maxFramePx <= BOUND_PX, "jump " + jump + " seed " + seed + ": " + r.maxFramePx + " px");
				else assertTrue(r.framesOver <= 1, "jump " + jump + " seed " + seed + ": " + r.framesOver + " frames over the bound");
				assertEquals(r.result, r.pocket, "jump " + jump + " seed " + seed);
			}
		}
	}

	/** Control: the old raw clock (re-set by every bunched packet) jumps the ball far beyond the bound. */
	@Test
	void theRawClockJumps() {
		int jumps = 0;
		for (int seed = 0; seed < 12; seed++) if (run(seed, 0, false).maxFramePx > 3 * BOUND_PX) jumps++;
		assertTrue(jumps >= 10, "raw clock jumped in only " + jumps + " / 12 runs");
	}
}
