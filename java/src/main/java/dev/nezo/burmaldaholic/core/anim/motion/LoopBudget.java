package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Slot bookkeeping of the client-wide looping sounds ({@code FxSounds.loop}, at most {@code max} at once). Pure, so
 * the leak and priority rules can be tested.
 *
 * <ul>
 *   <li><b>No leaks:</b> before every count or start, entries for which {@code gone} holds are pruned (released,
 *       stopped, or dropped by the sound engine on disconnect, {@code /stopsound} or a sound reload, which never call
 *       the instance's own stop). {@link #remove} frees a slot at once.</li>
 *   <li><b>Priority:</b> a SCREEN loop (the player's open table screen) always wins over a WORLD loop (an in-world
 *       table renderer). WORLD loops hold at most {@code maxWorld} slots, and when a SCREEN loop finds every slot taken,
 *       the oldest WORLD loop is evicted (its {@code evict} callback releases it). A WORLD loop never evicts anything.</li>
 * </ul>
 *
 * <p>Thread-safe (synchronized).
 *
 * @param <T> loop handle type
 */
public final class LoopBudget<T> {
	private final int max;
	private final int maxWorld;
	private final List<T> entries = new ArrayList<>();
	private final List<Boolean> world = new ArrayList<>();

	/** A budget of {@code max ≥ 1} slots, at most 1 of them for WORLD loops. */
	public LoopBudget(int max) {
		this(max, 1);
	}

	/** A budget of {@code max ≥ 1} slots, at most {@code maxWorld} (0..max) of them for WORLD loops. */
	public LoopBudget(int max, int maxWorld) {
		if (max < 1 || maxWorld < 0 || maxWorld > max) throw new IllegalArgumentException("max " + max + " world " + maxWorld);
		this.max = max;
		this.maxWorld = maxWorld;
	}

	/** Slot count. */
	public int max() {
		return max;
	}

	/** Slots WORLD loops may hold. */
	public int maxWorld() {
		return maxWorld;
	}

	/** Removes every entry for which {@code gone} holds; returns how many were removed. */
	public synchronized int prune(Predicate<? super T> gone) {
		int removed = 0;
		for (int i = entries.size() - 1; i >= 0; i--) {
			if (gone.test(entries.get(i))) {
				entries.remove(i);
				world.remove(i);
				removed++;
			}
		}
		return removed;
	}

	/** {@link #tryAdd(Object, boolean, Predicate, Consumer)} as a SCREEN loop without eviction callback. */
	public synchronized boolean tryAdd(T t, Predicate<? super T> gone) {
		return tryAdd(t, false, gone, null);
	}

	/**
	 * Prunes, then takes a slot for {@code t}.
	 *
	 * @param isWorld true for a WORLD loop (capped at {@code maxWorld}, never evicts), false for a SCREEN loop
	 * @param gone    prune predicate
	 * @param evict   called (under this budget's lock) with the WORLD loop evicted to make room for a
	 *                SCREEN loop; it should stop that loop. May be null.
	 * @return false (nothing taken) when no slot can be had
	 */
	public synchronized boolean tryAdd(T t, boolean isWorld, Predicate<? super T> gone, Consumer<? super T> evict) {
		prune(gone);
		if (isWorld) {
			if (worldCount() >= maxWorld || entries.size() >= max) return false;
		} else if (entries.size() >= max) {
			int victim = world.indexOf(Boolean.TRUE);
			if (victim < 0) return false;
			T w = entries.remove(victim);
			world.remove(victim);
			if (evict != null) evict.accept(w);
		}
		entries.add(t);
		world.add(isWorld);
		return true;
	}

	/** Frees {@code t}'s slot (identity); true when it held one. */
	public synchronized boolean remove(T t) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == t) {
				entries.remove(i);
				world.remove(i);
				return true;
			}
		}
		return false;
	}

	/** True while {@code t} holds a slot (identity; no pruning). */
	public synchronized boolean holds(T t) {
		for (T e : entries) if (e == t) return true;
		return false;
	}

	/** Slots in use after pruning with {@code gone}. */
	public synchronized int size(Predicate<? super T> gone) {
		prune(gone);
		return entries.size();
	}

	/** WORLD slots in use (no pruning). */
	public synchronized int worldCount() {
		int n = 0;
		for (Boolean b : world) if (b) n++;
		return n;
	}

	/** Frees every slot. */
	public synchronized void clear() {
		entries.clear();
		world.clear();
	}
}
