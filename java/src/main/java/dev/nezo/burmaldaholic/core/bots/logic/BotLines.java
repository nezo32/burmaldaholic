package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.Map;
import java.util.Set;

/**
 * Bot quip events and their number of translated variants (BOTS.md §7.4, §11.4:
 * {@code dialog.burmaldaholic.bots.<event>.<1..n>}). The server has no language files, so the counts are
 * data here; {@code BotLinesTest} checks them against the EN and RU lang fragments.
 */
public final class BotLines {
	public static final Map<String, Integer> VARIANTS = Map.ofEntries(
		Map.entry("join", 4), Map.entry("yield", 3), Map.entry("leave", 3), Map.entry("win_big", 4), Map.entry("bust", 4),
		Map.entry("bad_beat", 3), Map.entry("fold_to_shove", 3), Map.entry("hero_call", 3), Map.entry("human_wins", 4),
		Map.entry("all_in", 3), Map.entry("blackjack", 3), Map.entry("seven_out", 3), Map.entry("natural", 3),
		Map.entry("bank_take", 3), Map.entry("banco", 3), Map.entry("pvp_win", 3), Map.entry("pvp_loss", 3),
		Map.entry("duel_accept", 3), Map.entry("duel_decline", 2), Map.entry("word_got_around", 3), Map.entry("sulk", 3),
		Map.entry("idle", 4));

	private BotLines() {}

	public static Set<String> events() {
		return VARIANTS.keySet();
	}

	/** Variants of an event (0 = unknown event: nothing is said). */
	public static int variants(String event) {
		return VARIANTS.getOrDefault(event, 0);
	}

	/** {@code dialog.burmaldaholic.bots.<event>.<variant>} (variant 1-based). */
	public static String key(String event, int variant) {
		return "dialog.burmaldaholic.bots." + event + "." + variant;
	}
}
