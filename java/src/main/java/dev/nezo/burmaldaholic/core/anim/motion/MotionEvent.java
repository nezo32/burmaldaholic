package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.Arrays;
import java.util.Comparator;

/**
 * One timed event of a motion (a peg contact, a fret landing, a die hitting the wall, a rest). Immutable; built once
 * with the motion and replayed by an {@link EventCursor}.
 *
 * <p>{@code kind} values are defined by the producing class (e.g. {@code DiceEvent.WALL}, {@code PlinkoPath} contact
 * kinds); MK never interprets them.
 *
 * @param tMs      time in ms on the motion's own clock (0 = the motion's start)
 * @param kind     producer-defined event kind
 * @param obj      object index (die 0/1, ball slot, …); -1 when not object-specific
 * @param strength 0–1 impact strength (drives sound volume, dust count)
 * @param x        event position, first coordinate, in the producer's units (art px, tray units, pocket coordinate…)
 * @param y        event position, second coordinate
 * @param aux      producer-defined extra (row, peg index, diamond index, hop number…)
 */
public record MotionEvent(double tMs, int kind, int obj, float strength, double x, double y, int aux) {
	/** Order of an event list: by time; ties keep their insertion order (stable sort). */
	public static final Comparator<MotionEvent> BY_TIME = Comparator.comparingDouble(MotionEvent::tMs);

	/** Sorts {@code events} in place by time (stable, so simultaneous events keep their producer order). */
	public static MotionEvent[] sort(MotionEvent[] events) {
		Arrays.sort(events, BY_TIME);
		return events;
	}

	/** True when {@code events} is sorted by time (non-decreasing). */
	public static boolean sorted(MotionEvent[] events) {
		for (int i = 1; i < events.length; i++) if (events[i].tMs < events[i - 1].tMs) return false;
		return true;
	}

	/** A copy shifted by {@code dtMs} (e.g. an event of a ball dropped later, on the screen's common clock). */
	public MotionEvent shifted(double dtMs) {
		return new MotionEvent(tMs + dtMs, kind, obj, strength, x, y, aux);
	}
}
