package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.Objects;
import java.util.UUID;

/**
 * Who sits in a seat / takes part in a match: a human or a bot. Games keep seats ABSTRACT over this type
 * and route every decision of a seat through its game's decision interface ({@link BotPolicy} for bots,
 * the human's input / timeout default for humans). Pure; no Minecraft types.
 */
public sealed interface SeatOccupant {
	/** Stable string key: the UUID string for humans, {@code "bot:<id>"} for bots. */
	String key();

	boolean isBot();

	/** Humans: the player name; bots: the name translation key (render with {@code gui.burmaldaholic.bots.display}). */
	String name();

	record Human(UUID id, String name) implements SeatOccupant {
		public Human {
			Objects.requireNonNull(id);
		}

		@Override
		public String key() {
			return id.toString();
		}

		@Override
		public boolean isBot() {
			return false;
		}
	}

	/** @param purse money bots: bank or bankroll; atmosphere bots: {@link Purse#NONE} */
	record Bot(BotProfile profile, BotRole role, Purse purse) implements SeatOccupant {
		public Bot {
			Objects.requireNonNull(profile);
			Objects.requireNonNull(role);
			Objects.requireNonNull(purse);
		}

		@Override
		public String key() {
			return profile.key();
		}

		@Override
		public boolean isBot() {
			return true;
		}

		@Override
		public String name() {
			return profile.nameKey();
		}
	}

	static boolean isBotKey(String key) {
		return key != null && key.startsWith("bot:");
	}
}
