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
 *   <li>late join (SPECTATORS only): arriving after the whole shared part shows the settled state (no replayed
 *       celebrations);</li>
 *   <li>S1 / SY-SPEC §2: the SPINNING player (actor) is anchored at the result's ARRIVAL on local wall time,
 *       {@code t = tH + (wall − arrival)}: never "late", never jumps, unaffected by server time packets; only
 *       spectators read the shared clock;</li>
 *   <li>{@code t} is monotonic (only skip / finish / hold move it, and only forward).</li>
 * </ul>
 * The shared clock comes from a swappable {@link TimeSource} ({@link #timeSource}) so the SY {@code SmoothClock}
 * (p2/sy) can replace {@code AnimClock} without touching this class, and tests can drive it.
 */
public final class SpinClock {
	/** Where shared (server) time comes from; swappable (SY SmoothClock, tests). */
	public interface TimeSource {
		/** Shared ms since beat 0 of {@code seed} (server clock). */
		double sharedMs(TimelineSeed seed, float partialTick);

		/** Shared level time in ms (the in-world cabinet). */
		double levelMs(float partialTick);

		/** Local wall clock, ms. */
		double wallMs();
	}

	/** Production source: {@code AnimClock} (SY will point this at {@code SmoothClock}). */
	public static final TimeSource ANIM_CLOCK = new TimeSource() {
		@Override
		public double sharedMs(TimelineSeed seed, float partialTick) {
			return dev.nezo.burmaldaholic.client.anim.AnimClock.sharedMs(seed, partialTick);
		}

		@Override
		public double levelMs(float partialTick) {
			return dev.nezo.burmaldaholic.client.anim.AnimClock.levelMs(partialTick);
		}

		@Override
		public double wallMs() {
			return net.minecraft.util.Util.getMillis();
		}
	};

	private static volatile TimeSource source = ANIM_CLOCK;

	/** The current shared-time source. */
	public static TimeSource timeSource() {
		return source;
	}

	/** Swaps the shared-time source ({@code null} = {@link #ANIM_CLOCK}). */
	public static void timeSource(TimeSource s) {
		source = s == null ? ANIM_CLOCK : s;
	}

	private Timeline timeline;
	private final boolean actor;
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
	private double lastT = Double.NEGATIVE_INFINITY;

	/**
	 * @param timeline the spin timeline
	 * @param shared   shared ms since beat 0 (server clock for the real screen; wall time for the preview)
	 */
	public SpinClock(Timeline timeline, DoubleSupplier shared) {
		this(timeline, shared, false);
	}

	private SpinClock(Timeline timeline, DoubleSupplier shared, boolean actor) {
		this.timeline = timeline;
		this.shared = shared;
		this.sharedEnd = timeline.sharedEndMs();
		this.actor = actor;
	}

	/** Spectator: server-driven clock of a published spin (shared time, monotonic). */
	public static SpinClock server(Timeline timeline, TimelineSeed seed, java.util.function.Supplier<Float> partialTick) {
		TimeSource src = source;
		return new SpinClock(timeline, () -> src.sharedMs(seed, partialTick.get()));
	}

	/**
	 * Actor (the player who pressed Spin): {@code t = tH + (wall − arrivalWallMs)} on the local wall clock (SY-SPEC
	 * §2 {@code ResultAnchor.asActor(tH)}); {@code tH} may be negative (the pre-roll hands off before beat 0).
	 */
	public static SpinClock actor(Timeline timeline, double arrivalWallMs, double tH) {
		TimeSource src = source;
		return new SpinClock(timeline, () -> tH + (src.wallMs() - arrivalWallMs), true);
	}

	/** Wall-clock driven (preview / solo fallback) starting now. */
	public static SpinClock wall(Timeline timeline, long startWallMs) {
		return new SpinClock(timeline, () -> net.minecraft.util.Util.getMillis() - (double) startWallMs);
	}

	/** Anchored on the actor's local time (never late). */
	public boolean actor() {
		return actor;
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
			// S1: the actor is never late (its own spin); a spectator who arrives after the whole shared part settles
			late = !actor && sharedEnd > 0 && base >= sharedEnd;
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
		// monotonic: a server time packet that moves the shared clock back never moves the reels back
		if (raw > lastT) lastT = raw;
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

	/** A spectator who arrived after the whole shared part (draw the settled state, no replayed cues). */
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
