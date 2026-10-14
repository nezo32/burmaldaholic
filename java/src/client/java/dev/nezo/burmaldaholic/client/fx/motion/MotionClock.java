package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import java.util.function.LongSupplier;
import net.minecraft.util.Util;

/**
 * The clock motion is sampled on (PHASE2B §3.2): shared motion samples {@code levelMs − startTick·50} with the
 * partial tick, so the screen, the in-world renderer and other viewers agree at any frame rate (PL §4, RO §3.9,
 * DI §3.4).
 *
 * <p>{@link #testOverride} (null in production) replaces the level time in ms for client GameTests, so a mid-motion
 * frame is deterministic; {@link TableClock#msSince} honours it too. In-world tests can also freeze
 * {@link AnimClock#freezeForTest}.
 */
public final class MotionClock {
	/** Test hook: when non-null, its value is the level time in ms (game ticks · 50 incl. the partial tick). */
	public static volatile LongSupplier testOverride;

	private MotionClock() {}

	/** Level time in ms with the partial tick ({@link #testOverride} when set, else {@link AnimClock#levelMs}). */
	public static double levelMs(float partialTick) {
		LongSupplier o = testOverride;
		return o != null ? o.getAsLong() : AnimClock.levelMs(partialTick);
	}

	/** Milliseconds since game tick {@code startTick} at the current frame. */
	public static double msSince(long startTick, float partialTick) {
		return levelMs(partialTick) - startTick * 50.0;
	}

	/**
	 * Milliseconds since server tick {@code startTick} on a screen's {@link TableClock} (server time estimated from
	 * the last state packet).
	 */
	public static double msSince(TableClock clock, long startTick, float partialTick) {
		return clock.msSince(startTick, partialTick);
	}

	/** Wall-clock ms for LOCAL beats ({@link #testOverride} when set, so test frames stay deterministic). */
	public static long localMs() {
		LongSupplier o = testOverride;
		return o != null ? o.getAsLong() : Util.getMillis();
	}
}
