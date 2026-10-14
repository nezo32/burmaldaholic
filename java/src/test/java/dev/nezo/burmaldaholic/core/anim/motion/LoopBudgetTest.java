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
