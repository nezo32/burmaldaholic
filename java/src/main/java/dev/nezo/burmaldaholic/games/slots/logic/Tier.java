package dev.nezo.burmaldaholic.games.slots.logic;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** The three machines (GAME_DESIGN.md §8.2–8.4). */
public enum Tier {
	COPPER("copper", 1, false),
	GOLD("gold", 3, true),
	NETHERITE("netherite", 5, true);

	private final String id;
	private final int lines;
	private final boolean progressive;

	Tier(String id, int lines, boolean progressive) {
		this.id = id;
		this.lines = lines;
		this.progressive = progressive;
	}

	/** Config / registry id suffix: {@code slot_machine_<id>}, {@code slots.<id>.*}. */
	public String id() {
		return id;
	}

	/** Paylines played (all lines always played). */
	public int lines() {
		return lines;
	}

	/** Has a world progressive jackpot (house machines only; owned machines never do, §8.5). */
	public boolean progressive() {
		return progressive;
	}

	/** Block id / lang key suffix {@code slot_machine_<id>}. */
	public String blockName() {
		return "slot_machine_" + id;
	}

	public static @Nullable Tier byId(String id) {
		if (id == null) {
			return null;
		}
		String k = id.toLowerCase(Locale.ROOT);
		for (Tier t : values()) {
			if (t.id.equals(k)) {
				return t;
			}
		}
		return null;
	}
}
