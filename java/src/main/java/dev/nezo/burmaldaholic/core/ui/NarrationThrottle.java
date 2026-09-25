package dev.nezo.burmaldaholic.core.ui;

/**
 * The narrator throttle of the casino screens (docs/architecture/animation.md §2.12, cards.md §0.6): at most one
 * narration per {@link #GAP_MS}; a message offered too early waits and is replaced by any newer one (the latest public
 * state is what gets read), then {@link #poll} releases it once the gap has passed. Pure; times in ms.
 *
 * @param <T> the message type (a {@code Component} on the client)
 */
public final class NarrationThrottle<T> {
	public static final int GAP_MS = 600;

	private final int gapMs;
	private long lastMs;
	private boolean spoke;
	private T pending;

	public NarrationThrottle() {
		this(GAP_MS);
	}

	public NarrationThrottle(int gapMs) {
		this.gapMs = Math.max(0, gapMs);
	}

	/** Offers {@code message} at {@code nowMs}: returns it when it may be spoken now, else keeps it pending and returns null. */
	public T offer(T message, long nowMs) {
		if (message == null) return null;
		if (!spoke || nowMs - lastMs >= gapMs) {
			spoke = true;
			lastMs = nowMs;
			pending = null;
			return message;
		}
		pending = message;
		return null;
	}

	/** The pending message once the gap has passed (call every tick), else null. */
	public T poll(long nowMs) {
		if (pending == null || nowMs - lastMs < gapMs) return null;
		T m = pending;
		pending = null;
		lastMs = nowMs;
		return m;
	}

	public boolean hasPending() {
		return pending != null;
	}

	/** Forgets the pending message (screen closed). */
	public void clear() {
		pending = null;
	}
}
