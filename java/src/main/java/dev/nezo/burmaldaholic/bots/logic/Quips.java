package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Bot quip lines (BOTS.md §11.4): {@code dialog.burmaldaholic.bots.<event>.<1..n>} and the emote of each
 * event (§7.2). Variant counts are the spec's code constant. Pure.
 */
public final class Quips {
	/** Variant counts per event (BOTS.md §11.4, 70 lines). */
	public static final Map<String, Integer> VARIANTS = Map.ofEntries(
		Map.entry("join", 4), Map.entry("yield", 3), Map.entry("leave", 3), Map.entry("win_big", 4), Map.entry("bust", 4),
		Map.entry("bad_beat", 3), Map.entry("fold_to_shove", 3), Map.entry("hero_call", 3), Map.entry("human_wins", 4),
		Map.entry("all_in", 3), Map.entry("blackjack", 3), Map.entry("seven_out", 3), Map.entry("natural", 3),
		Map.entry("bank_take", 3), Map.entry("banco", 3), Map.entry("pvp_win", 3), Map.entry("pvp_loss", 3),
		Map.entry("duel_accept", 3), Map.entry("duel_decline", 2), Map.entry("word_got_around", 3), Map.entry("sulk", 3),
		Map.entry("idle", 4));

	/** Particle emote at the avatar / table centre (BOTS.md §7.2). */
	public enum Emote { NONE, HAPPY, ANGRY, NOTE, SMOKE }

	private Quips() {}

	public static String key(String event, int variant) {
		return "dialog.burmaldaholic.bots." + event + "." + variant;
	}

	/** A random line of {@code event} from the BOT rng, or null for an unknown event. */
	public static @Nullable String pick(String event, BotRng rng) {
		Integer n = VARIANTS.get(event);
		if (n == null || n <= 0) {
			return null;
		}
		return key(event, 1 + rng.nextInt(n));
	}

	public static Emote emote(String event) {
		return switch (event) {
			case "win_big", "pvp_win", "blackjack", "natural", "bank_take", "banco" -> Emote.HAPPY;
			case "bust", "bad_beat", "pvp_loss", "seven_out", "sulk" -> Emote.ANGRY;
			case "join", "duel_accept" -> Emote.NOTE;
			case "leave", "yield" -> Emote.SMOKE;
			default -> Emote.NONE;
		};
	}
}
