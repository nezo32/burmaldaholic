package dev.nezo.burmaldaholic.client.anim;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import java.util.function.Consumer;

/**
 * Plays a {@link Timeline} on the client (docs/architecture/animation.md §2.1). The SHARED part is
 * sampled from server time (never faster, never skipped by the viewer); the LOCAL part starts when the
 * shared part ends (or when the result arrives, for purely local timelines) and follows wall time scaled by
 * the viewer's {@code anim.speed} (the builder already scaled local durations) and skip.
 *
 * <p>Rules implemented here (shared by every screen and BER):
 * <ul>
 *   <li>catch-up: when the viewer arrives after 85 % of the shared part, {@link #settled()} is true and the
 *       owner draws the terminal state (tables.md §0.1);</li>
 *   <li>skip: jumps to the end of the current LOCAL skip group; shared beats cannot be skipped by a viewer
 *       (server skip requests re-publish a shorter timeline instead);</li>
 *   <li>overrun: a new timeline for the same object calls {@link #finishNow()} on the old one first.</li>
 * </ul>
 * SKELETON: complete and allocation-free, but not used by any screen yet (lanes adopt it).
 */
public final class TimelinePlayer {
	private final Timeline timeline;
	private final int sharedEnd;
	private double localStartMs = Double.NaN;
	private int skippedUntil = -1;
	private boolean finished;
	private boolean sampled;
	private boolean arrivedLate;

	public TimelinePlayer(Timeline timeline) {
		this.timeline = timeline;
		this.sharedEnd = timeline.sharedEndMs();
	}

	public Timeline timeline() {
		return timeline;
	}

	/**
	 * Timeline time for this frame: shared ms until the reveal gate, then gate + local wall time since the
	 * gate (plus skips).
	 */
	public double timeAt(double sharedMs, long localNowMs) {
		if (!sampled) {
			sampled = true;
			arrivedLate = sharedEnd > 0 && sharedMs >= 0.85 * sharedEnd;
			if (arrivedLate) finished = true;
		}
		if (finished) return timeline.endMs();
		if (sharedMs < sharedEnd) return Math.max(0, sharedMs);
		if (Double.isNaN(localStartMs)) localStartMs = localNowMs;
		double t = sharedEnd + (localNowMs - localStartMs);
		return Math.max(t, skippedUntil);
	}

	/** True when the viewer arrived after 85 % of the shared part: draw the terminal state, no tweens. */
	public boolean arrivedLate() {
		return arrivedLate;
	}

	/** Skip (click / Space / Esc): jump to the end of the local group running at {@code t}. */
	public void skip(double t) {
		int g = timeline.groupAt(t);
		if (g < 0) return;
		for (Beat b : timeline.beats())
			if (b.group() == g && b.clock() == Clock.SHARED && b.end() > t) return; // shared: not skippable here
		skippedUntil = Math.max(skippedUntil, timeline.groupEnd(g));
	}

	public void finishNow() {
		finished = true;
	}

	public boolean done(double t) {
		return finished || t >= timeline.endMs();
	}

	/** Visits the beats active at {@code t}. */
	public void sample(double t, Consumer<Beat> visitor) {
		timeline.forEachActive(t, visitor);
	}
}
