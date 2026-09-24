package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Bot quips and emotes (BOTS.md §7.4). Games, {@link TableBots} and the PvP engine report chatter
 * EVENTS here ({@code join, yield, leave, win_big, bust, bad_beat, fold_to_shove, hero_call, human_wins,
 * all_in, blackjack, seven_out, natural, bank_take, banco, pvp_win, pvp_loss, duel_accept, duel_decline,
 * word_got_around, sulk, idle}); the bots module delivers them (rate limits, the table's Bot chatter
 * toggle, per-player mutes, chat line, tin voice, emote particles). Lines about a hand must only be
 * reported after the showdown / reveal. Server thread.
 */
public final class BotChatter {
	/** Delivery (installed by the bots module). */
	@FunctionalInterface
	public interface Sink {
		/**
		 * @param table     table / PvP anchor position (the line is heard around it)
		 * @param humanName the human the line talks about ({@code %1$s}), or null
		 */
		void event(ServerLevel level, BlockPos table, BotProfile bot, String event, @Nullable String humanName);
	}

	private static Sink sink = (level, table, bot, event, humanName) -> {};

	private BotChatter() {}

	/** Bots module only. */
	public static void setSink(Sink s) {
		sink = s == null ? (level, table, bot, event, humanName) -> {} : s;
	}

	/** A chatter event of a seated bot / PvP bot participant. Never throws. */
	public static void event(ServerLevel level, BlockPos table, BotProfile bot, String event, @Nullable String humanName) {
		try {
			sink.event(level, table, bot, event, humanName);
		} catch (RuntimeException e) {
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.warn("Bot chatter failed for {}", event, e);
		}
	}
}
