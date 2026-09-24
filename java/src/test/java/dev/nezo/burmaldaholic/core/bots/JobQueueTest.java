package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.JobQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Heavy-job scheduler (BOTS.md §7.5, §12.2 "budget fallback"). */
class JobQueueTest {
	/** Counts samples up to {@code total}. */
	private static final class Samples implements BotWork {
		final int total;
		int done;

		Samples(int total) {
			this.total = total;
		}

		@Override
		public int step(int budget) {
			int n = Math.min(budget, total - done);
			done += n;
			return n;
		}

		@Override
		public boolean done() {
			return done >= total;
		}

		@Override
		public Object result() {
			return done;
		}

		@Override
		public int progress() {
			return done;
		}
	}

	private final List<RuntimeException> errors = new ArrayList<>();
	private final JobQueue q = new JobQueue(errors::add);

	@Test
	void finishesWithinBudget() {
		List<Object> out = new ArrayList<>();
		q.submit(new Samples(3000), 100, () -> true, out::add);
		assertEquals(2000, q.step(1, 2, 2000));
		assertTrue(out.isEmpty());
		q.step(2, 2, 2000);
		assertEquals(List.of(3000), out);
		assertEquals(0, q.size());
	}

	@Test
	void deadlineUsesPartialResultEvenWithoutASlot() {
		List<Object> out = new ArrayList<>();
		q.submit(new Samples(1_000_000), 3, () -> true, r -> out.add("a" + r));
		q.submit(new Samples(1_000_000), 3, () -> true, r -> out.add("b" + r));
		q.submit(new Samples(1_000_000), 3, () -> true, r -> out.add("c" + r)); // never gets a slot (2)
		for (long t = 1; t <= 3; t++) {
			q.step(t, 2, 60);
		}
		assertEquals(List.of("a60", "b60", "c0"), out, "30 samples per tick each; c falls back at its deadline");
		assertEquals(0, q.size());
	}

	@Test
	void slotsAndFairShare() {
		Samples a = new Samples(10_000);
		Samples b = new Samples(10_000);
		Samples c = new Samples(10_000);
		q.submit(a, 100, () -> true, r -> {});
		q.submit(b, 100, () -> true, r -> {});
		q.submit(c, 100, () -> true, r -> {});
		assertEquals(2000, q.step(1, 2, 2000));
		assertEquals(1000, a.done);
		assertEquals(1000, b.done);
		assertEquals(0, c.done, "FIFO: c waits for a slot");
	}

	@Test
	void staleJobsAreDroppedSilently() {
		AtomicBoolean wanted = new AtomicBoolean(true);
		List<Object> out = new ArrayList<>();
		q.submit(new Samples(5000), 100, wanted::get, out::add);
		q.step(1, 1, 1000);
		wanted.set(false);
		q.step(2, 1, 1000);
		assertTrue(out.isEmpty());
		assertEquals(0, q.size());
	}

	@Test
	void failuresFallBackAndNeverBreakTheQueue() {
		List<Object> out = new ArrayList<>();
		q.submit(new BotWork() {
			@Override
			public int step(int budget) {
				throw new IllegalStateException("boom");
			}

			@Override
			public boolean done() {
				return false;
			}

			@Override
			public Object result() {
				return "x";
			}

			@Override
			public int progress() {
				return 0;
			}
		}, 100, () -> true, out::add);
		q.submit(new Samples(10), 100, () -> true, r -> {
			throw new IllegalStateException("callback");
		});
		q.submit(new Samples(10), 100, () -> true, out::add);
		q.step(1, 3, 2000);
		assertEquals(2, errors.size());
		assertEquals(2, out.size());
		assertNull(out.get(0), "failed job → null (policy falls back)");
		assertEquals(10, out.get(1));
	}
}
