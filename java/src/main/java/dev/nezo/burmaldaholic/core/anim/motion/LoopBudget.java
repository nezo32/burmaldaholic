package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Slot bookkeeping of the client-wide looping sounds ({@code FxSounds.loop}, at most {@code max} at once). Pure so the
 * leak rules can be tested: before every count or start, entries for which {@code gone} holds are pruned (released,
 * stopped, or dropped by the sound engine on disconnect, {@code /stopsound} or a sound reload, which never call the
 * instance's own stop); {@link #remove} frees a slot at once. Thread-safe (synchronized).
 *
 * @param <T> loop handle type
 */
public final class LoopBudget<T> {
	private final int max;
	private final List<T> entries = new ArrayList<>();

	/** A budget of {@code max ≥ 1} slots. */
	public LoopBudget(int max) {
		if (max < 1) throw new IllegalArgumentException("max " + max);
		this.max = max;
	}

	/** Slot count. */
	public int max() {
		return max;
	}

	/** Removes every entry for which {@code gone} holds; returns how many were removed. */
	public synchronized int prune(Predicate<? super T> gone) {
		int before = entries.size();
		entries.removeIf(gone);
		return before - entries.size();
	}

	/** Prunes, then takes a slot for {@code t}; false (nothing taken) when every slot is in use. */
	public synchronized boolean tryAdd(T t, Predicate<? super T> gone) {
		prune(gone);
		if (entries.size() >= max) return false;
		entries.add(t);
		return true;
	}

	/** Frees {@code t}'s slot (identity); true when it held one. */
	public synchronized boolean remove(T t) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == t) {
				entries.remove(i);
				return true;
			}
		}
		return false;
	}

	/** Slots in use after pruning with {@code gone}. */
	public synchronized int size(Predicate<? super T> gone) {
		prune(gone);
		return entries.size();
	}

	/** Frees every slot. */
	public synchronized void clear() {
		entries.clear();
	}
}
