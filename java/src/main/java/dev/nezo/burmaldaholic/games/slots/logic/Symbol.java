package dev.nezo.burmaldaholic.games.slots.logic;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Reel symbols (GAME_DESIGN.md §8). {@link #id()} is the config key used in
 * {@code slots.<tier>.weights/pays}; {@link #langId()} is the suffix of
 * {@code gui.burmaldaholic.slots.symbol.<langId>}. Ordinals are sent to the client — append only.
 */
public enum Symbol {
	BERRIES("berries", "berry", Kind.REGULAR),
	APPLE("apple", "apple", Kind.REGULAR),
	GOLDEN_CARROT("golden_carrot", "carrot", Kind.REGULAR),
	EMERALD("emerald", "emerald", Kind.REGULAR),
	DIAMOND("diamond", "diamond", Kind.REGULAR),
	SEVEN("seven", "seven", Kind.REGULAR),
	WILD("wild", "wild", Kind.WILD),
	CREEPER("creeper", "creeper", Kind.SPECIAL),
	TNT("tnt", "tnt", Kind.SPECIAL),
	PEARL("pearl", "pearl", Kind.SPECIAL),
	CLOCK("clock", "clock", Kind.SPECIAL),
	STAR("star", "star", Kind.SPECIAL);

	public enum Kind {
		/** Fruit, gems, seven: Wild substitutes for these. */
		REGULAR,
		WILD,
		/** Pays only as three natural of a kind; may trigger a chaos event. */
		SPECIAL
	}

	private static final Symbol[] VALUES = values();

	private final String id;
	private final String langId;
	private final Kind kind;

	Symbol(String id, String langId, Kind kind) {
		this.id = id;
		this.langId = langId;
		this.kind = kind;
	}

	public String id() {
		return id;
	}

	public String langId() {
		return langId;
	}

	public Kind kind() {
		return kind;
	}

	public boolean isSpecial() {
		return kind == Kind.SPECIAL;
	}

	public boolean isRegular() {
		return kind == Kind.REGULAR;
	}

	/** {@code gui.burmaldaholic.slots.symbol.<langId>}. */
	public String translationKey() {
		return "gui.burmaldaholic.slots.symbol." + langId;
	}

	public static @Nullable Symbol byId(String id) {
		if (id == null) {
			return null;
		}
		String k = id.toLowerCase(Locale.ROOT);
		for (Symbol s : VALUES) {
			if (s.id.equals(k)) {
				return s;
			}
		}
		return null;
	}

	/** Safe decode of a synced ordinal (unknown → BERRIES). */
	public static Symbol byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BERRIES;
	}

	public static int count() {
		return VALUES.length;
	}
}
