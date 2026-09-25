package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.Objects;

/**
 * Who a bot is (BOTS.md §1, §7.1): id ({@code b} + 7 base-36 chars, unique per table session / match),
 * stable name id ({@code gui.burmaldaholic.bots.name.<nameId>}, saved so the name survives a restart),
 * concrete level (never MIXED) and personality. Immutable.
 */
public record BotProfile(String id, String nameId, BotDifficulty level, Personality personality) {
	public BotProfile {
		Objects.requireNonNull(id);
		Objects.requireNonNull(nameId);
		if (level == BotDifficulty.MIXED) {
			throw new IllegalArgumentException("a bot's level is concrete; resolve MIXED with BotDifficulty#pick");
		}
		Objects.requireNonNull(personality);
	}

	/** Seat / participant key ("bot:" prefix never collides with a UUID string). */
	public String key() {
		return "bot:" + id;
	}

	public String nameKey() {
		return BotRoster.nameKey(nameId);
	}

	/** New random id {@code b + 7 base-36 chars} from the BOT rng. */
	public static String newId(BotRng rng) {
		StringBuilder b = new StringBuilder("b");
		for (int i = 0; i < 7; i++) {
			b.append(Character.forDigit(rng.nextInt(36), 36));
		}
		return b.toString();
	}
}
