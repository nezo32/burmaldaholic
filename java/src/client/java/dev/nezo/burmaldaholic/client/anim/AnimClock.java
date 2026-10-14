package dev.nezo.burmaldaholic.client.anim;

import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Frame-rate independent time sources for the client animation runtime (docs/architecture/animation.md §2.1).
 *
 * <ul>
 *   <li><b>Shared time</b>: server game ticks + partial tick, so the screen, the BER and other viewers agree
 *       ({@link #sharedMs}). Pauses with the game (singleplayer pause freezes the reels too).</li>
 *   <li><b>Local time</b>: wall-clock milliseconds ({@link #localMs}) for decoration that respects
 *       {@code anim.speed} and skip.</li>
 * </ul>
 */
public final class AnimClock {
	/** Test freeze hook (PHASE2B §3.2): when not NaN, the level time in ms that every reader sees. */
	private static volatile double frozenLevelMs = Double.NaN;

	private AnimClock() {}

	/**
	 * Freezes the shared clock at level time {@code levelMs} (game ticks · 50, partial tick included) for client
	 * GameTests: in-world renderers then draw a deterministic mid-motion frame. Undo with {@link #unfreezeForTest()}.
	 */
	public static void freezeForTest(double levelMs) {
		frozenLevelMs = levelMs;
	}

	/** Releases {@link #freezeForTest}. */
	public static void unfreezeForTest() {
		frozenLevelMs = Double.NaN;
	}

	/** True while a test froze the clock. */
	public static boolean frozen() {
		return !Double.isNaN(frozenLevelMs);
	}

	/** Milliseconds since beat 0 of {@code seed} at the current frame; 0 when no level is loaded. */
	public static double sharedMs(TimelineSeed seed, float partialTick) {
		double frozen = frozenLevelMs;
		if (!Double.isNaN(frozen)) {
			long tick = (long) Math.floor(frozen / 50.0);
			return seed.elapsedMs(tick, (float) ((frozen - tick * 50.0) / 50.0));
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		return seed.elapsedMs(mc.level.getGameTime(), partialTick);
	}

	/** Game time + partial tick in ms (for BERs that sample several seeds per frame). */
	public static double levelMs(float partialTick) {
		double frozen = frozenLevelMs;
		if (!Double.isNaN(frozen)) return frozen;
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : (mc.level.getGameTime() + (double) partialTick) * 50.0;
	}

	/** Wall-clock milliseconds for local-time beats. */
	public static long localMs() {
		return Util.getMillis();
	}
}
