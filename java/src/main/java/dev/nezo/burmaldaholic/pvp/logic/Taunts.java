package dev.nezo.burmaldaholic.pvp.logic;

import java.util.List;
import java.util.Locale;

/**
 * The 8 fixed taunt lines of PVP.md §3.9 ({@code gui.burmaldaholic.pvp.taunt.<id>}). The index is what
 * {@code PvpService#taunt(player, line)} takes. Pure.
 */
public final class Taunts {
	/** Order = line index (0 … 7). */
	public static final List<String> IDS = List.of("gg", "luck", "wow", "rigged", "again", "steel", "bye", "respect");

	private Taunts() {}

	public static String key(int line) {
		return "gui.burmaldaholic.pvp.taunt." + IDS.get(line);
	}

	public static boolean valid(int line) {
		return line >= 0 && line < IDS.size();
	}

	/** Line index of an id ("gg") or a 1-based number ("3"); -1 when unknown. */
	public static int parse(String text) {
		if (text == null) {
			return -1;
		}
		String t = text.trim().toLowerCase(Locale.ROOT);
		int i = IDS.indexOf(t);
		if (i >= 0) {
			return i;
		}
		try {
			int n = Integer.parseInt(t);
			return valid(n - 1) ? n - 1 : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
