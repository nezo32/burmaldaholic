package dev.nezo.burmaldaholic.bots.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Heat stages of a player against house-funded bots (BOTS.md §5.4). {@code threshold} = the player's daily
 * cap ({@code BotLedger.threshold}; 0 = heat disabled). The round that crosses a line is always kept. Pure.
 */
public enum HeatStage {
	/** Below the cap. */
	NONE,
	/** {@code net ≥ cap}: only HARD house bots play the player at poker today. */
	WORD_GOT_AROUND,
	/** {@code net ≥ ceil(cap × bots.sulkMultiplier)}: house bots refuse the player until the next MCD. */
	SULKING;

	public static final long DAY_TICKS = 24_000L;

	public static HeatStage of(long netToday, long threshold, double sulkMultiplier) {
		if (threshold <= 0) {
			return NONE;
		}
		if (netToday >= (long) Math.ceil(threshold * sulkMultiplier)) {
			return SULKING;
		}
		return netToday >= threshold ? WORD_GOT_AROUND : NONE;
	}

	/** Ticks until the next Minecraft day starts (the ledger resets then). */
	public static long ticksToNextDay(long gameTime) {
		return DAY_TICKS - Math.floorMod(gameTime, DAY_TICKS);
	}

	/**
	 * Remembers the highest stage already announced per player per day, so each stage is announced (and the
	 * {@code word_got_around} advancement granted) once when it is crossed.
	 */
	public static final class Watch {
		private record Seen(long day, HeatStage stage) {}

		private final Map<UUID, Seen> seen = new HashMap<>();

		/** @return true if {@code stage} is higher than what was announced today for {@code player} (and records it) */
		public boolean crossed(UUID player, long day, HeatStage stage) {
			Seen s = seen.get(player);
			HeatStage before = s == null || s.day() != day ? NONE : s.stage();
			if (stage.ordinal() <= before.ordinal()) {
				return false;
			}
			seen.put(player, new Seen(day, stage));
			return true;
		}

		/** Admin reset ({@code /casino bots heat <player> reset}). */
		public void reset(UUID player) {
			seen.remove(player);
		}
	}
}
