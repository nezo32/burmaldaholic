package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;

/**
 * The clock motion is sampled on (PHASE2B §3.2): shared motion samples {@code levelMs − startTick·50} on the ONE
 * shared {@link SmoothClock} (SY-SPEC §1), so the screen, the in-world renderer and other viewers agree at any frame
 * rate (PL §4, RO §3.9, DI §3.4) and time packets never make motion jump.
 */
public final class MotionClock {
	private MotionClock() {}

	/** Level time in ms ({@link SmoothClock#nowMs}, which honours every test hook). */
	public static double levelMs(float partialTick) {
		return SmoothClock.nowMs(partialTick);
	}

	/** Milliseconds since game tick {@code startTick} at the current frame. */
	public static double msSince(long startTick, float partialTick) {
		return levelMs(partialTick) - startTick * 50.0;
	}

	/** Milliseconds since server tick {@code startTick} on a screen's {@link TableClock} (the same shared clock). */
	public static double msSince(TableClock clock, long startTick, float partialTick) {
		return clock.msSince(startTick, partialTick);
	}

	/**
	 * Wall-clock ms for LOCAL beats ({@link AnimClock#localMs}); under {@link SmoothClock.Test#freezeLevelMs} the test
	 * level time instead, so LOCAL beats freeze with the shared ones.
	 */
	public static long localMs() {
		double t = AnimClock.testLevelMs();
		return Double.isNaN(t) ? AnimClock.localMs() : (long) Math.floor(t);
	}
}
