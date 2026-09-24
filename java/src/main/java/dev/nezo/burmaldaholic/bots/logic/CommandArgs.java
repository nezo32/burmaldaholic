package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Arguments of {@code /casino table bots <humans|mixed|bots> [count] [easy|normal|hard|mixed]} (BOTS.md §8.7). Pure. */
public final class CommandArgs {
	public static final List<String> POLICIES = List.of("humans", "mixed", "bots");
	public static final List<String> DIFFICULTIES = List.of("easy", "normal", "hard", "mixed");

	private CommandArgs() {}

	public static @Nullable SeatPolicy policy(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "humans" -> SeatPolicy.HUMANS_ONLY;
			case "mixed" -> SeatPolicy.MIXED;
			case "bots" -> SeatPolicy.BOTS_ONLY;
			default -> null;
		};
	}

	public static @Nullable BotDifficulty difficulty(String word) {
		return DIFFICULTIES.contains(word.toLowerCase(Locale.ROOT)) ? BotDifficulty.byId(word, BotDifficulty.NORMAL) : null;
	}

	/**
	 * The wanted settings: the policy, the given count (or the current one; at least 1 when switching a
	 * table without bots to a bot policy) and the given difficulty (or the current one).
	 */
	public static BotSettings apply(BotSettings current, SeatPolicy policy, @Nullable Integer count, @Nullable BotDifficulty difficulty) {
		int c = count != null ? count : current.count();
		if (count == null && policy != SeatPolicy.HUMANS_ONLY && c <= 0) {
			c = 1;
		}
		return new BotSettings(policy, c, difficulty != null ? difficulty : current.difficulty(), current.keepFree(), current.chatter(), current.speed());
	}
}
