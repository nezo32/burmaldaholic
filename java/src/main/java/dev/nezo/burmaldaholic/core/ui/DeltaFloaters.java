package dev.nezo.burmaldaholic.core.ui;

import dev.nezo.burmaldaholic.core.anim.Ease;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Floating balance deltas next to the HUD chip counter (global.md §4.1.1 "Floating delta", extras.md §9 delta pills):
 * a label spawns at the counter and rises {@link #RISE} px over {@link #LIFE_MS} ({@code outCubic}), fading out over
 * the last {@link #FADE_MS}. Deltas within {@link #MERGE_MS} of the newest label merge into it (sum). At most
 * {@link #MAX} labels; older ones are pushed up by {@link #STACK} px. Reduce motion: labels stay in place and fade.
 * Pure; index 0 = newest.
 */
public final class DeltaFloaters {
	public static final int LIFE_MS = 1400;
	public static final int FADE_MS = 500;
	public static final int MERGE_MS = 400;
	public static final int MAX = 3;
	public static final int RISE = 6;
	public static final int STACK = 11;

	private static final class Label {
		long amount;
		final long bornMs;
		long lastMs;

		Label(long amount, long bornMs) {
			this.amount = amount;
			this.bornMs = bornMs;
			this.lastMs = bornMs;
		}
	}

	private final Deque<Label> labels = new ArrayDeque<>();

	/** Adds a balance change (ignored when 0). */
	public void add(long delta, long nowMs) {
		if (delta == 0) return;
		prune(nowMs);
		Label newest = labels.peekFirst();
		if (newest != null && nowMs - newest.lastMs < MERGE_MS && Long.signum(newest.amount) == Long.signum(delta)) {
			newest.amount += delta;
			newest.lastMs = nowMs;
			return;
		}
		labels.addFirst(new Label(delta, nowMs));
		while (labels.size() > MAX) labels.removeLast();
	}

	public void clear() {
		labels.clear();
	}

	private void prune(long nowMs) {
		Iterator<Label> it = labels.iterator();
		while (it.hasNext()) if (nowMs - it.next().lastMs >= LIFE_MS) it.remove();
	}

	/** Live labels at {@code nowMs}. */
	public int size(long nowMs) {
		prune(nowMs);
		return labels.size();
	}

	private Label get(int i) {
		int k = 0;
		for (Label l : labels) if (k++ == i) return l;
		throw new IndexOutOfBoundsException(i);
	}

	public long amount(int i) {
		return get(i).amount;
	}

	/** Opacity: 1 until the last {@link #FADE_MS}, then linear to 0. */
	public float alpha(int i, long nowMs) {
		long age = nowMs - get(i).lastMs;
		long fadeStart = LIFE_MS - FADE_MS;
		if (age <= fadeStart) return 1f;
		return (float) Math.max(0, 1 - (age - fadeStart) / (double) FADE_MS);
	}

	/** Vertical offset in px (negative = up): the rise of this label plus the push of the newer ones. */
	public int yOffset(int i, long nowMs, boolean reduceMotion) {
		int push = -STACK * i;
		if (reduceMotion) return push;
		long age = nowMs - get(i).bornMs;
		double t = Math.max(0, Math.min(1, age / (double) LIFE_MS));
		return push - (int) Math.round(RISE * Ease.OUT_CUBIC.apply(t));
	}
}
