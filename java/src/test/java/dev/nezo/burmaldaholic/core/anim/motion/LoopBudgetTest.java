package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * T-MK D1: the client-wide loop budget never leaks a slot. A loop the sound engine dropped without calling its own
 * stop (disconnect, /stopsound, sound reload) is pruned because the engine no longer reports it active; release frees
 * its slot at once.
 */
class LoopBudgetTest {
	/** Stand-in for a tickable loop: what FxSounds.gone() looks at. */
	private static final class FakeLoop {
		boolean released;
		boolean stopped;
		boolean engineActive = true;
	}

	private static final Predicate<FakeLoop> GONE = l -> l.released || l.stopped || !l.engineActive;

	@Test
	void twoSlotsThenNone() {
		LoopBudget<FakeLoop> b = new LoopBudget<>(2);
		assertTrue(b.tryAdd(new FakeLoop(), GONE));
		assertTrue(b.tryAdd(new FakeLoop(), GONE));
		assertFalse(b.tryAdd(new FakeLoop(), GONE), "third loop refused");
		assertEquals(2, b.size(GONE));
	}

	@Test
	void engineDroppedLoopsFreeTheirSlots() {
		LoopBudget<FakeLoop> b = new LoopBudget<>(2);
		for (int event = 0; event < 5; event++) { // disconnect / stopsound / reload, repeatedly
			FakeLoop a = new FakeLoop();
			FakeLoop c = new FakeLoop();
			assertTrue(b.tryAdd(a, GONE), "event " + event);
			assertTrue(b.tryAdd(c, GONE), "event " + event);
			a.engineActive = false; // SoundManager.stop(): never ticked again, never stop()ped
			c.engineActive = false;
			assertEquals(0, b.size(GONE), "dropped loops pruned, event " + event);
		}
	}

	@Test
	void screenLoopsEvictTheWorldLoop() {
		LoopBudget<FakeLoop> b = new LoopBudget<>(2, 1);
		java.util.List<FakeLoop> evicted = new java.util.ArrayList<>();
		FakeLoop world = new FakeLoop();
		FakeLoop world2 = new FakeLoop();
		assertTrue(b.tryAdd(world, true, GONE, evicted::add), "a WORLD loop takes a free slot");
		assertFalse(b.tryAdd(world2, true, GONE, evicted::add), "WORLD capped at 1 slot");
		FakeLoop screen1 = new FakeLoop();
		FakeLoop screen2 = new FakeLoop();
		assertTrue(b.tryAdd(screen1, false, GONE, evicted::add), "SCREEN takes the free slot");
		assertTrue(evicted.isEmpty());
		assertTrue(b.tryAdd(screen2, false, GONE, evicted::add), "SCREEN evicts the WORLD loop");
		assertEquals(java.util.List.of(world), evicted);
		assertFalse(b.holds(world));
		assertEquals(0, b.worldCount());
		assertFalse(b.tryAdd(new FakeLoop(), false, GONE, evicted::add), "two SCREEN loops: a third SCREEN loop gets NONE");
		assertFalse(b.tryAdd(world2, true, GONE, evicted::add), "a WORLD loop never evicts a SCREEN loop");
		screen1.released = true; // re-request after release
		assertTrue(b.tryAdd(world2, true, GONE, evicted::add));
		assertEquals(1, b.worldCount());
		assertEquals(1, evicted.size());
	}

	@Test
	void releaseFreesAtOnceEvenIfTheEngineStillReportsIt() {
		LoopBudget<FakeLoop> b = new LoopBudget<>(2);
		FakeLoop a = new FakeLoop();
		FakeLoop c = new FakeLoop();
		b.tryAdd(a, GONE);
		b.tryAdd(c, GONE);
		assertTrue(b.remove(a)); // release(): removed from the list immediately
		assertFalse(b.remove(a), "idempotent");
		assertEquals(1, b.size(l -> false), "freed without relying on the predicate");
		assertTrue(b.tryAdd(new FakeLoop(), l -> false));
		b.clear();
		assertEquals(0, b.size(GONE));
	}
}
