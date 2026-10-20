package dev.nezo.burmaldaholic.core.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The chunk-unload play-out queue of {@link TableLifecycle}: deferred inside (nested) updates, never stuck after a throw. */
class DeferredUnloadsTest {
	private final List<String> played = new ArrayList<>();
	private final DeferredUnloads<String> q = new DeferredUnloads<>(played::add);

	@Test
	void outsideAnUpdatePlaysAtOnce() {
		q.offer("a");
		assertEquals(List.of("a"), played);
	}

	@Test
	void insideNestedUpdatesWaitsForTheOutermostExit() {
		q.enter();
		q.offer("a");
		q.enter();
		q.offer("b");
		q.exit();
		assertTrue(played.isEmpty(), "the inner exit does not play out");
		q.exit();
		assertEquals(List.of("a", "b"), played);
		assertEquals(0, q.depth());
	}

	@Test
	@SuppressWarnings("unchecked")
	void aPlayOutThatReentersAnUpdateIsPlayedToo() {
		DeferredUnloads<String>[] self = new DeferredUnloads[1];
		List<String> seen = new ArrayList<>();
		self[0] = new DeferredUnloads<>(c -> {
			seen.add(c);
			if (c.equals("a")) { // the play-out loads a chunk: a nested update in which another chunk unloads
				self[0].enter();
				self[0].offer("b");
				self[0].exit();
			}
		});
		self[0].enter();
		self[0].offer("a");
		self[0].exit();
		assertEquals(List.of("a", "b"), seen);
	}

	@Test
	void anUpdateThatThrewIsResetAtTheNextTick() {
		q.enter();
		q.offer("a"); // the update throws here: exit() never runs
		assertEquals(1, q.depth());
		assertTrue(played.isEmpty());
		assertTrue(q.tickStart(), "the stuck depth is reported");
		assertEquals(List.of("a"), played, "the waiting table is played out at the next tick");
		assertEquals(0, q.depth());
		q.offer("b");
		assertEquals(List.of("a", "b"), played, "and later unloads play out at once again");
		assertFalse(q.tickStart(), "a healthy tick needs no reset");
	}

	@Test
	void clearForgetsEverything() {
		q.enter();
		q.offer("a");
		q.clear();
		assertEquals(0, q.depth());
		assertEquals(0, q.pending());
		q.tickStart();
		assertTrue(played.isEmpty());
	}
}
