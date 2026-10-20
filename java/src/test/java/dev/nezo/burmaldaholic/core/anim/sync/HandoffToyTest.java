package dev.nezo.burmaldaholic.core.anim.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The kit's own handoff test with a toy reel (SY-SPEC §3, §5): a strip of 10 cells of 100 units cruising at 0.5
 * units/ms; it may only hand off at a cell boundary; the solver brakes at a constant rate from the handoff pose and
 * velocity to the target cell plus whole turns. Plus ResultAnchor / AnchorSlot / SeqGate / SyncLead.
 */
class HandoffToyTest {
	static final double V = 0.5;
	static final double CELL = 100;
	static final double STRIP = 1000;

	static final WaitLoop<Double> CRUISE = new WaitLoop<>() {
		@Override
		public Double pose(double t) {
			return V * t;
		}

		@Override
		public Double velocity(double t) {
			return V;
		}

		@Override
		public double nextExit(double t) {
			return Math.ceil(V * t / CELL) * CELL / V;
		}
	};

	/** Constant deceleration from (p0, v0) to rest on {@code cell} plus whole turns, lasting at least minMs. */
	static final HandoffSolver<Double, Integer> BRAKE = (p0, v0, cell, minMs) -> {
		double end = Math.floor(p0 / STRIP) * STRIP + cell * CELL;
		while (end - p0 < Math.max(CELL, v0 * minMs / 2)) end += STRIP;
		double fEnd = end;
		double d = end - p0;
		double dur = 2 * d / v0;
		return new Track<>() {
			@Override
			public Double sample(double tau) {
				if (tau >= dur) return fEnd;
				return p0 + v0 * tau - v0 * tau * tau / (2 * dur);
			}

			@Override
			public double durationMs() {
				return dur;
			}
		};
	};

	@Test
	void handoffIsC0AndC1AndEndsExactly() {
		Handoff<Double, Integer> h = new Handoff<>(CRUISE, BRAKE);
		assertTrue(h.arrive(7, 1234, 1500));
		double tH = h.handoffT();
		assertEquals(0, (V * tH) % CELL, 1e-9, "hands off on a cell boundary");
		double eps = 1e-3;
		assertEquals(CRUISE.pose(tH), h.sample(tH), 1e-9, "C0");
		double vBefore = (h.sample(tH - eps) - h.sample(tH - 2 * eps)) / eps;
		double vAfter = (h.sample(tH + 2 * eps) - h.sample(tH + eps)) / eps;
		assertEquals(vBefore, vAfter, 1e-4, "C1");
		assertTrue(h.endT() - tH >= 1500);
		assertEquals(7 * CELL, h.sample(h.endT() + 100) % STRIP, 0.0, "final pose is the server's cell exactly");
	}

	@Test
	void harnessBoundsUnderLateAndBunchedArrivals() {
		for (double[] arrivals : new double[][] {{400}, {3000}, {800, 800, 816, 1800}, {16.7}}) {
			HandoffHarness.Result<Double> r = HandoffHarness.run(CRUISE, BRAKE, 3, arrivals, 16.7, 1200, (a, b) -> Math.abs(a - b));
			assertTrue(r.maxFrameDelta() <= 1.15 * V * 16.7, "max frame delta " + r);
			assertEquals(3 * CELL, r.finalPose() % STRIP, 0.0, "final pose " + r);
			assertEquals(0, r.rebuilds(), "later packets never rebuild the path");
		}
	}

	@Test
	void anchorActorIsLocalAndMonotonicSpectatorIsShared() {
		ResultAnchor spectator = ResultAnchor.of(100, 10_000, 100 * 50.0 + 200, 3);
		assertEquals(300, spectator.tMs(10_050, 100 * 50.0 + 300), 1e-9);
		assertEquals(300, spectator.tMs(10_060, 100 * 50.0 + 250), 1e-9, "never steps back");
		assertFalse(spectator.lateJoin(2000));
		assertTrue(ResultAnchor.of(100, 0, 100 * 50.0 + 2500, 0).lateJoin(2000), "late joiner draws the settled state");
		ResultAnchor actor = spectator.asActor(120);
		assertTrue(actor.actor());
		assertEquals(120 + 500, actor.tMs(10_500, 0), 1e-9, "actor: local time since arrival, never late");
		assertFalse(actor.lateJoin(1));
	}

	@Test
	void anchorSlotBuildsOncePerKey() {
		AnchorSlot slot = new AnchorSlot();
		ResultAnchor a = slot.obtain(1, 5, () -> ResultAnchor.of(10, 0));
		assertSame(a, slot.obtain(1, 5, () -> ResultAnchor.of(99, 0)));
		assertNotSame(a, slot.obtain(1, 6, () -> ResultAnchor.of(11, 0)));
		assertNotSame(slot.current(), slot.obtain(2, 6, () -> ResultAnchor.of(11, 0)));
	}

	@Test
	void seqGate() {
		SeqGate g = new SeqGate();
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(5, 77));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(6, 77));
		assertEquals(SeqGate.Verdict.DROP, g.offer(6, 77));
		assertEquals(SeqGate.Verdict.DROP, g.offer(4, 77));
		assertEquals(SeqGate.Verdict.RESET, g.offer(1, 12), "new epoch resets");
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(2, 12));
		assertEquals(SeqGate.Verdict.ACCEPT, g.offer(-1, 0), "no seq passes");
		assertEquals(2, g.lastSeq());
	}

	@Test
	void syncLeadClamps() {
		assertEquals(104, SyncLead.startTick(100, SyncLead.SLOTS));
		assertEquals(106, SyncLead.startTick(100, SyncLead.ROULETTE));
		assertEquals(140, SyncLead.startTick(100, 99));
		assertEquals(100, SyncLead.startTick(100, -3));
	}
}
