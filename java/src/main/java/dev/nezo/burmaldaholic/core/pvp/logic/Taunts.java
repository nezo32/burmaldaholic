package dev.nezo.burmaldaholic.core.pvp.logic;

import java.util.List;

/**
 * The 8 fixed taunt lines (PVP.md §3.9, §15.4) and their limits. Pure. Line index = position in
 * {@link #LINES}; keys {@code gui.burmaldaholic.pvp.taunt.<id>}.
 */
public final class Taunts {
	public static final List<String> LINES = List.of("gg", "luck", "wow", "rigged", "again", "steel", "bye", "respect");
	/** Friendly lines (villager "yes"); the others are cheeky (villager "no"). */
	private static final List<String> FRIENDLY = List.of("gg", "luck", "steel", "respect");

	private Taunts() {}

	public static boolean valid(int line) {
		return line >= 0 && line < LINES.size();
	}

	public static String key(int line) {
		return "gui.burmaldaholic.pvp.taunt." + LINES.get(line);
	}

	public static boolean friendly(int line) {
		return FRIENDLY.contains(LINES.get(line));
	}

	/**
	 * @param used     taunts already sent in this match
	 * @param lastTick tick of the sender's previous taunt (Long.MIN_VALUE = none)
	 * @return null = allowed, else the error key
	 */
	public static String check(boolean enabled, int used, int maxPerMatch, long lastTick, long now, int cooldownTicks) {
		if (!enabled) {
			return "gui.burmaldaholic.error.disabled";
		}
		if (used >= maxPerMatch) {
			return "gui.burmaldaholic.pvp.taunt.limit";
		}
		if (lastTick != Long.MIN_VALUE && now - lastTick < cooldownTicks) {
			return "gui.burmaldaholic.error.cooldown";
		}
		return null;
	}
}
