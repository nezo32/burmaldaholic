package dev.nezo.burmaldaholic.chaos.logic;

import java.util.Locale;
import java.util.Optional;

/** The nine chaos events of GAME_DESIGN.md §13.2 (ids match config keys and lang keys). PURE. */
public enum ChaosEvent {
	CHIP_SHOWER(Kind.GOOD, 18),
	LUCKY_BUFF(Kind.GOOD, 20),
	DIAMOND_RAIN(Kind.GOOD, 4),
	XP_FOUNTAIN(Kind.GOOD, 12),
	CURSE(Kind.BAD, 16),
	MOB_WAVE(Kind.BAD, 12),
	RANDOM_TELEPORT(Kind.NEUTRAL, 8),
	WEATHER_CHANGE(Kind.NEUTRAL, 8),
	/** Server-wide, own cooldown (§13.3). */
	GOLDEN_HOUR(Kind.GOOD, 2);

	public enum Kind {
		GOOD, BAD, NEUTRAL
	}

	private final Kind kind;
	private final int defaultWeight;
	private final String id;

	ChaosEvent(Kind kind, int defaultWeight) {
		this.kind = kind;
		this.defaultWeight = defaultWeight;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	/** {@code chip_shower}, {@code mob_wave}, ... */
	public String id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	/** Ambient weight from §13.2 (config {@code chaos.weight.<id>} overrides it). */
	public int defaultWeight() {
		return defaultWeight;
	}

	public static Optional<ChaosEvent> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String norm = id.trim().toLowerCase(Locale.ROOT);
		for (ChaosEvent e : values()) {
			if (e.id.equals(norm)) {
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}
}
