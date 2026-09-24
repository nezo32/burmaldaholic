package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * FIFO scheduler of heavy bot work (BOTS.md §7.5), pure so it can be unit-tested; {@code BotJobs} drives
 * it from the server tick. Per {@link #step}:
 * <ol>
 *   <li>stale jobs ({@code stillWanted} false) are dropped wherever they wait — never called back;</li>
 *   <li>jobs whose deadline passed finish with their partial result, even if they never got a slot;</li>
 *   <li>the first {@code slots} live jobs share the unit budget (even split, leftovers go to later jobs);</li>
 *   <li>a job that is done (or reaches its deadline) is removed and {@code onDone(result)} is called.</li>
 * </ol>
 * A job that throws is finished with {@code null} ("fall back to the simple rule"); a throwing
 * callback never breaks the queue. Errors go to {@code errors}.
 */
public final class JobQueue {
	private record Job(BotWork work, long deadline, BooleanSupplier stillWanted, Consumer<Object> onDone) {}

	private final LinkedList<Job> queue = new LinkedList<>();
	private final Consumer<RuntimeException> errors;

	public JobQueue(Consumer<RuntimeException> errors) {
		this.errors = errors;
	}

	/** Queues {@code work}; {@code deadline} is an absolute tick (the think deadline). */
	public void submit(BotWork work, long deadline, BooleanSupplier stillWanted, Consumer<Object> onDone) {
		queue.add(new Job(work, deadline, stillWanted, onDone));
	}

	public int size() {
		return queue.size();
	}

	/** Drops everything without calling back (server stop). */
	public void clear() {
		queue.clear();
	}

	/**
	 * One tick of work.
	 *
	 * @return units used this tick
	 */
	public int step(long now, int slots, int budget) {
		List<Job> finished = new ArrayList<>();
		List<Job> active = new ArrayList<>();
		for (Iterator<Job> it = queue.iterator(); it.hasNext();) {
			Job j = it.next();
			boolean wanted;
			try {
				wanted = j.stillWanted().getAsBoolean();
			} catch (RuntimeException e) {
				errors.accept(e);
				wanted = false;
			}
			if (!wanted) {
				it.remove();
			} else if (now >= j.deadline()) {
				it.remove();
				finished.add(j);
			} else if (active.size() < Math.max(1, slots)) {
				active.add(j);
			}
		}
		int used = 0;
		int left = Math.max(0, budget);
		for (int i = 0; i < active.size(); i++) {
			Job j = active.get(i);
			int share = Math.max(1, left / (active.size() - i));
			try {
				int u = Math.max(0, Math.min(share, j.work().step(share)));
				used += u;
				left = Math.max(0, left - u);
				if (j.work().done()) {
					queue.remove(j);
					finished.add(j);
				}
			} catch (RuntimeException e) {
				errors.accept(e);
				queue.remove(j);
				call(j, null);
			}
		}
		for (Job j : finished) {
			Object result;
			try {
				result = j.work().result();
			} catch (RuntimeException e) {
				errors.accept(e);
				result = null;
			}
			call(j, result);
		}
		return used;
	}

	private void call(Job j, Object result) {
		try {
			j.onDone().accept(result);
		} catch (RuntimeException e) {
			errors.accept(e);
		}
	}
}
