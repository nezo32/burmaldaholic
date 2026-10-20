package dev.nezo.burmaldaholic.client.anim;

import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import net.minecraft.util.Util;

/**
 * Frame-rate independent time sources for the client animation runtime (docs/architecture/animation.md §2.1).
 *
 * <ul>
 *   <li><b>Shared time</b>: the smoothed server level time of {@link SmoothClock} (SY-SPEC §1), so the screen, the BER
 *       and other viewers agree and time packets never make it jump ({@link #sharedMs}). Pauses with the game.</li>
 *   <li><b>Local time</b>: wall-clock milliseconds ({@link #localMs}) for decoration that respects
 *       {@code anim.speed} and skip.</li>
 * </ul>
 */
public final class AnimClock {
	private AnimClock() {}

	/**
	 * Freezes the shared clock at level time {@code levelMs} for client GameTests.
	 *
	 * @deprecated forwards to {@link SmoothClock.Test#freezeLevelMs}; kept through v0.2.0 for the game owners' anim
	 *     tests, removed in v0.2.1.
	 */
	@Deprecated
	public static void freezeForTest(double levelMs) {
		SmoothClock.Test.freezeLevelMs(levelMs);
	}

	/**
	 * Releases {@link #freezeForTest}.
	 *
	 * @deprecated use {@code SmoothClock.Test.freezeLevelMs(Double.NaN)}.
	 */
	@Deprecated
	public static void unfreezeForTest() {
		SmoothClock.Test.freezeLevelMs(Double.NaN);
	}

	/** The test level time every client clock reads ({@link SmoothClock.Test#frozenLevelMs}), else NaN. */
	public static double testLevelMs() {
		return SmoothClock.Test.frozenLevelMs();
	}

	/** True while a test froze the clock. */
	public static boolean frozen() {
		return !Double.isNaN(testLevelMs());
	}

	/** Milliseconds since beat 0 of {@code seed} at the current frame; 0 when no level is loaded. */
	public static double sharedMs(TimelineSeed seed, float partialTick) {
		if (!frozen() && !SmoothClock.started() && net.minecraft.client.Minecraft.getInstance().level == null) return 0;
		double ms = levelMs(partialTick);
		long tick = (long) Math.floor(ms / 50.0);
		return seed.elapsedMs(tick, (float) ((ms - tick * 50.0) / 50.0));
	}

	/** The shared level time in ms ({@link SmoothClock#nowMs}); the partial tick matters only before the first sync. */
	public static double levelMs(float partialTick) {
		return SmoothClock.nowMs(partialTick);
	}

	/** Wall-clock milliseconds for local-time beats. */
	public static long localMs() {
		return Util.getMillis();
	}
}
