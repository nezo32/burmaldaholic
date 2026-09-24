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
	private AnimClock() {}

	/** Milliseconds since beat 0 of {@code seed} at the current frame; 0 when no level is loaded. */
	public static double sharedMs(TimelineSeed seed, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		return seed.elapsedMs(mc.level.getGameTime(), partialTick);
	}

	/** Game time + partial tick in ms (for BERs that sample several seeds per frame). */
	public static double levelMs(float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : (mc.level.getGameTime() + (double) partialTick) * 50.0;
	}

	/** Wall-clock milliseconds for local-time beats. */
	public static long localMs() {
		return Util.getMillis();
	}
}
