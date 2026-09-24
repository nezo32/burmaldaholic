package dev.nezo.burmaldaholic.core.anim;

import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window rate budget kept PER COMPONENT (key), with an optional overall cap (global.md §2.9; review of
 * the Bedrock celebrate feature: one global timer let one busy component starve the others). Each key has its
 * own window of {@code perKey} events per {@code windowMs}; the overall cap counts all keys but never lets one
 * key take more than its own share, so a stream of count-up ticks cannot block a fanfare or a toast.
 * Pure and deterministic (time is passed in); not thread-safe (one owner thread).
 */
public final class RateBudget {
	private final int perKey;
	private final int total;
	private final long windowMs;
	private final Map<String, long[]> windows = new HashMap<>();
	private final long[] all;
	private int allNext;

	/**
	 * @param perKey   events per window for one key
	 * @param total    events per window over all keys (≥ perKey; {@code Integer.MAX_VALUE} = no overall cap)
	 * @param windowMs window length
	 */
	public RateBudget(int perKey, int total, long windowMs) {
		if (perKey < 1 || total < perKey || windowMs < 1) throw new IllegalArgumentException("budget");
		this.perKey = perKey;
		this.total = total;
		this.windowMs = windowMs;
		this.all = total == Integer.MAX_VALUE ? null : filled(total);
	}

	private static long[] filled(int n) {
		long[] a = new long[n];
		java.util.Arrays.fill(a, Long.MIN_VALUE / 2);
		return a;
	}

	/** Takes one event for {@code key} at {@code nowMs}; false (nothing taken) when the key or the total is spent. */
	public boolean tryAcquire(String key, long nowMs) {
		long[] ring = windows.computeIfAbsent(key, k -> filled(perKey));
		int oldest = 0;
		for (int i = 1; i < ring.length; i++) if (ring[i] < ring[oldest]) oldest = i;
		if (nowMs - ring[oldest] < windowMs) return false;
		if (all != null && nowMs - all[allNext] < windowMs) return false;
		ring[oldest] = nowMs;
		if (all != null) {
			all[allNext] = nowMs;
			allNext = (allNext + 1) % all.length;
		}
		return true;
	}

	/** Forgets a key (e.g. a player who left). */
	public void forget(String key) {
		windows.remove(key);
	}
}
