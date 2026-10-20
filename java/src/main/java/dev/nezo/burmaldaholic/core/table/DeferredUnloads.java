package dev.nezo.burmaldaholic.core.table;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The queue behind {@link TableLifecycle}'s chunk-unload play-out (pure, unit-tested): while a distance-manager update runs
 * ({@link #enter} / {@link #exit}, which may nest) an unloading chunk is only queued; the outermost {@link #exit} plays the
 * queue out. If an update throws, its {@code exit} never runs: {@link #tickStart} (start of every server tick, never inside
 * an update) then resets the depth and plays out what waited, so tables never wait until server stop. Server thread only.
 */
public final class DeferredUnloads<T> {
	private final List<T> pending = new ArrayList<>();
	private final Consumer<T> playOut;
	private int depth;

	public DeferredUnloads(Consumer<T> playOut) {
		this.playOut = playOut;
	}

	public void enter() {
		depth++;
	}

	public void exit() {
		if (--depth > 0) {
			return;
		}
		depth = 0;
		flush();
	}

	/** An unloading chunk: queued inside an update, played out at once outside one. */
	public void offer(T chunk) {
		if (depth > 0) {
			pending.add(chunk);
		} else {
			playOut.accept(chunk);
		}
	}

	/**
	 * Start of a server tick (no update can be running): a depth left above 0 by an update that threw is reset and the
	 * queue played out. Returns whether a reset was needed.
	 */
	public boolean tickStart() {
		boolean stuck = depth != 0;
		depth = 0;
		flush();
		return stuck;
	}

	/** Server stopped: forget everything. */
	public void clear() {
		pending.clear();
		depth = 0;
	}

	public int depth() {
		return depth;
	}

	public int pending() {
		return pending.size();
	}

	private void flush() {
		while (!pending.isEmpty()) {
			List<T> batch = new ArrayList<>(pending);
			pending.clear();
			batch.forEach(playOut);
		}
	}
}
