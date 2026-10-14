package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Fires the events of one motion exactly once and in order at any frame rate (PHASE2B §3.1, PL §3.9, RO §3.9).
 * Advance it from {@code render} with the motion's current time, and from {@code containerTick} as a fallback when
 * the screen is not rendering; each event is delivered once, whatever the step history.
 *
 * <p><b>Hitch rule:</b> an event delivered more than {@link #lateMs()} after its time (default {@link #LATE_MS} =
 * 150 ms) is delivered with {@code silent = true}: listeners update visual state (peg heat, lit pockets) but play no
 * sound and spawn no burst, so a hitch or a late join never causes an audio pile-up.
 *
 * <p>Allocation-free after construction; not thread-safe (one owner, the render thread). A cursor can be re-bound to
 * another event array ({@link #bind}) so pooled objects (balls in flight) reuse their cursor.
 */
public final class EventCursor {
	/** Default lateness after which an event fires silently (PHASE2B §3.1: "event > 150 ms late fires silently"). */
	public static final double LATE_MS = 150;

	/** Receives events. {@code silent}: update state only (late event, skip, late join). */
	@FunctionalInterface
	public interface Listener {
		void fire(MotionEvent e, boolean silent);
	}

	private static final MotionEvent[] NONE = new MotionEvent[0];

	private final double lateMs;
	private MotionEvent[] events;
	private int next;
	/** Events with an index below this are delivered silently (pending after a non-silent forward {@link #reset}). */
	private int silentBelow;

	/** A cursor at the start of {@code events} (sorted by time) with the default {@link #LATE_MS}. */
	public EventCursor(MotionEvent[] events) {
		this(events, LATE_MS);
	}

	/**
	 * @param events sorted by time ({@link MotionEvent#sort}); null = no events
	 * @param lateMs lateness (ms) after which an event fires silently; e.g. 120 for the Plinko/dice audio rule
	 */
	public EventCursor(MotionEvent[] events, double lateMs) {
		if (!(lateMs >= 0)) throw new IllegalArgumentException("lateMs " + lateMs);
		this.lateMs = lateMs;
		bind(events);
	}

	/** Re-binds to another event list (sorted by time) and rewinds to its start. */
	public void bind(MotionEvent[] events) {
		MotionEvent[] e = events == null ? NONE : events;
		if (!MotionEvent.sorted(e)) throw new IllegalArgumentException("events not sorted by time");
		this.events = e;
		this.next = 0;
		this.silentBelow = 0;
	}

	/**
	 * Delivers every not yet delivered event with {@code tMs ≤ nowMs}, in order. A clock that went backwards delivers
	 * nothing (use {@link #reset} to rewind on purpose).
	 *
	 * @param nowMs    the motion's current time (same clock as {@link MotionEvent#tMs})
	 * @param listener receiver; null just consumes the events
	 */
	public void advance(double nowMs, Listener listener) {
		while (next < events.length) {
			MotionEvent e = events[next];
			if (e.tMs() > nowMs) break;
			boolean silent = next < silentBelow || nowMs - e.tMs() > lateMs;
			next++;
			if (listener != null) listener.fire(e, silent);
		}
	}

	/**
	 * Moves the cursor to time {@code toMs}: afterwards the next event delivered is the first with {@code tMs > toMs}.
	 *
	 * <ul>
	 *   <li>Backwards (a test clock rewinding, a replay): events after {@code toMs} are re-armed and will fire again.</li>
	 *   <li>Forwards with {@code silent = true} (skip, late join): the passed events are dropped with no callback; the
	 *       caller sets the end state itself.</li>
	 *   <li>Forwards with {@code silent = false}: the passed events are kept and delivered by the next {@link #advance}
	 *       with {@code silent = true}, so listeners can rebuild visual state from them.</li>
	 * </ul>
	 */
	public void reset(double toMs, boolean silent) {
		int target = firstAfter(toMs);
		if (target <= next) {
			next = target;
			silentBelow = Math.min(silentBelow, target);
		} else if (silent) {
			next = target;
			silentBelow = Math.max(silentBelow, target);
		} else {
			silentBelow = Math.max(silentBelow, target);
		}
	}

	/** Index of the first event with {@code tMs > ms} (binary search). */
	private int firstAfter(double ms) {
		int lo = 0;
		int hi = events.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (events[mid].tMs() <= ms) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	/** Index of the next event to deliver ({@code == size()} when done). */
	public int next() {
		return next;
	}

	/** Number of events. */
	public int size() {
		return events.length;
	}

	/** True when every event was delivered (or skipped). */
	public boolean done() {
		return next >= events.length;
	}

	/** Time of the next event, or {@code +∞} when done. */
	public double nextTime() {
		return next < events.length ? events[next].tMs() : Double.POSITIVE_INFINITY;
	}

	/** Lateness threshold of the hitch rule. */
	public double lateMs() {
		return lateMs;
	}
}
