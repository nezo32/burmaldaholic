package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;

/**
 * Bot nameplate motion (global.md §4.12), PURE: the thinking-dots cycle, the "acted" pulse and the join / leave
 * scale, all as vanilla text-display transformation keys the client interpolates (no per-frame packets). The
 * visuals are identical for every decision (global §6.8): the dots depend only on the tick, never on the hand.
 */
public final class PlateAnim {
	/** Thinking dots advance every 10 ticks (≤ 1 text update per 10 t per bot). */
	public static final int DOTS_TICKS = 10;
	/** Join / leave: scale 0 ↔ 1 over 6 ticks. */
	public static final int JOIN_TICKS = 6;
	/** Acted: 1 → 1.15 over 4 ticks, back to 1 over 4 ticks. */
	public static final int PULSE_TICKS = 4;
	public static final float PULSE_SCALE = 1.15f;
	/** Plate background: {@code bg.darkest} at 25 % (0x40140822) instead of vanilla black. */
	public static final int BACKGROUND = 0x40140822;

	/** Glyphs: bot (U+E190) and the three thinking dots (U+E191–E193), default font sheet E1. */
	public static final String BOT_GLYPH = "";
	private static final String[] DOTS = {"", "", ""};
	/**
	 * Difficulty pills of the {@code burmaldaholic:bots} font (sheet {@code textures/font/bots/badges.png}): shape and
	 * pips, not colour alone — Easy one pip on green, Normal two on gold, Hard three on red, Mixed a split pill.
	 */
	private static final String[] PILLS = {"", "", "", ""};

	private PlateAnim() {}

	/** Thinking-dots glyph at server tick {@code tick} (1, 2, 3 dots cycling). */
	public static String dots(long tick) {
		return DOTS[(int) Math.floorMod(tick / DOTS_TICKS, 3L)];
	}

	/** Difficulty pill glyph. */
	public static String pill(BotDifficulty level) {
		return PILLS[switch (level) {
			case EASY -> 0;
			case NORMAL -> 1;
			case HARD -> 2;
			case MIXED -> 3;
		}];
	}

	/** One transformation key: target scale, interpolation duration (ticks) and the tick it is sent. */
	public record Key(int atTick, float scale, int duration) {}

	/** Join: sent at spawn with scale 0 (no interpolation), then 1 over {@link #JOIN_TICKS} on the next tick. */
	public static Key[] join() {
		return new Key[] {new Key(0, 0f, 0), new Key(1, 1f, JOIN_TICKS)};
	}

	/** Leave: scale 0 over {@link #JOIN_TICKS}; the entity is removed when it is done. */
	public static Key[] leave() {
		return new Key[] {new Key(0, 0f, JOIN_TICKS)};
	}

	/** Ticks after a leave starts at which the plate entity may be discarded. */
	public static int leaveDoneTicks() {
		return JOIN_TICKS + 1;
	}

	/** Acted pulse: up to 1.15 over 4 t, back to 1 over 4 t. */
	public static Key[] pulse() {
		return new Key[] {new Key(0, PULSE_SCALE, PULSE_TICKS), new Key(PULSE_TICKS, 1f, PULSE_TICKS)};
	}
}
