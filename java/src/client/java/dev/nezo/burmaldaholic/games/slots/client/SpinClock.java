package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import java.util.function.DoubleSupplier;

/**
 * Timeline time of one spin on this screen (docs/architecture/animation.md §3.2–§3.3, slots.md §2.2):
 *
 * <ul>
 *   <li>the SHARED part follows the server clock ({@code (gameTime − startTick + partialTick) × 50}) so the screen,
 *       the cabinet BER and spectators land on the same tick (F10); the preview uses wall time instead;</li>
 *   <li>after the reveal gate the LOCAL part follows wall time (the builder already scaled it by {@code anim.speed});</li>
 *   <li><b>skip</b> compresses time: {@code t} runs to the end of the current skip group in 200 ms (reels) / 150 ms
 *       (other) — "skip never skips the result, it compresses time" (SLOTS.md §1.4);</li>
 *   <li><b>holds</b> freeze {@code t} at an interactive point (Treasure Hunt picks, "Spin the wheel") until released;</li>
 *   <li>late join: arriving after 85 % of the shared part shows the settled state (no replayed celebrations).</li>
 * </ul>
 */
public final class SpinClock {
	private Timeline timeline;
	private final DoubleSupplier shared;
	private final int sharedEnd;
	private double offset;
	private double localGateWall = Double.NaN;
	private boolean sampled;
	private boolean late;
	// fast-forward (skip)
	private double ffFromT;
	private double ffToT;
	private double ffStartWall = Double.NaN;
	private double ffDur;
	// hold
	private double holdAt = Double.POSITIVE_INFINITY;
	private boolean holding;
	private double heldT;
	private double lastT;

	/**
	 * @param timeline the spin timeline
	 * @param shared   shared ms since beat 0 (server clock for the real screen; wall time for the preview)
	 */
	public SpinClock(Timeline timeline, DoubleSupplier shared) {
		this.timeline = timeline;
		this.shared = shared;
		this.sharedEnd = timeline.sharedEndMs();
	}

	/** Server-driven clock of a published spin. */
	public static SpinClock server(Timeline timeline, TimelineSeed seed, java.util.function.Supplier<Float> partialTick) {
		return new SpinClock(timeline, () -> dev.nezo.burmaldaholic.client.anim.AnimClock.sharedMs(seed, partialTick.get()));
	}

	/** Wall-clock driven (preview / solo fallback) starting now. */
	public static SpinClock wall(Timeline timeline, long startWallMs) {
		return new SpinClock(timeline, () -> net.minecraft.util.Util.getMillis() - (double) startWallMs);
	}

	public Timeline timeline() {
		return timeline;
	}

	/** Same spin, more of its tape revealed (the shared part is unchanged): the local part may have grown. */
	public void retarget(Timeline next) {
		this.timeline = next;
	}

	/** Timeline time for this frame. */
	public double t(double wallMs) {
		double base = shared.getAsDouble();
		if (!sampled) {
			sampled = true;
			late = sharedEnd > 0 && base >= 0.85 * sharedEnd && base > 1000;
			if (late) offset = timeline.endMs() - base;
		}
		double raw;
		if (base + offset < sharedEnd || late) {
			raw = base + offset;
		} else {
			if (Double.isNaN(localGateWall)) localGateWall = wallMs - (base + offset - sharedEnd);
			raw = sharedEnd + (wallMs - localGateWall);
		}
		if (!Double.isNaN(ffStartWall)) {
			double u = Math.min(1, (wallMs - ffStartWall) / ffDur);
			double ff = ffFromT + (ffToT - ffFromT) * u;
			if (u >= 1) {
				// continue from the group end in real time
				ffStartWall = Double.NaN;
				shiftTo(ffToT, raw);
				raw = ffToT;
			} else {
				raw = Math.max(raw, ff);
			}
		}
		if (holding) {
			shiftTo(heldT, raw);
			raw = heldT;
		} else if (raw >= holdAt && lastT < holdAt) {
			holding = true;
			heldT = holdAt;
			shiftTo(holdAt, raw);
			raw = holdAt;
		}
		lastT = Math.max(0, raw);
		return lastT;
	}

	private void shiftTo(double target, double raw) {
		double d = target - raw;
		if (Double.isNaN(localGateWall)) {
			offset += d;
		} else {
			localGateWall -= d;
		}
	}

	/** Arrived after 85 % of the shared part (draw the settled state, no replayed cues). */
	public boolean late() {
		return late;
	}

	/** Skip: compress to the end of the skip group running now ({@code reels} → 200 ms, else 150 ms). */
	public void skip(double wallMs, boolean reels) {
		if (holding) return;
		int g = timeline.groupAt(lastT);
		double end = g < 0 ? lastT : timeline.groupEnd(g);
		if (end <= lastT + 1) {
			// between groups: jump to the next beat start
			double next = Double.POSITIVE_INFINITY;
			for (var b : timeline.beats()) if (b.at() > lastT) {
				next = b.at();
				break;
			}
			if (Double.isInfinite(next)) return;
			end = next;
		}
		end = Math.min(end, holdAt);
		ffFromT = lastT;
		ffToT = end;
		ffStartWall = wallMs;
		ffDur = reels ? 200 : 150;
	}

	/** Jumps straight to the end (Esc / close / reduce motion interrupt, F8). */
	public void finish(double wallMs) {
		holding = false;
		holdAt = Double.POSITIVE_INFINITY;
		ffStartWall = Double.NaN;
		shiftTo(timeline.endMs(), t(wallMs));
		lastT = timeline.endMs();
	}

	public boolean fastForwarding() {
		return !Double.isNaN(ffStartWall);
	}

	/** Freezes time when it reaches {@code tMs} (an interactive point). */
	public void holdAt(double tMs) {
		this.holdAt = tMs;
	}

	public boolean holding() {
		return holding;
	}

	/** Releases a hold; time continues from {@code resumeAt} (≥ the hold point). */
	public void release(double resumeAt) {
		if (!holding) {
			holdAt = Double.POSITIVE_INFINITY;
			return;
		}
		holding = false;
		heldT = Math.max(heldT, resumeAt);
		double d = heldT - lastT;
		if (Double.isNaN(localGateWall)) offset += d;
		else localGateWall -= d;
		lastT = heldT;
		holdAt = Double.POSITIVE_INFINITY;
	}

	public boolean done() {
		return lastT >= timeline.endMs();
	}
}
