package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Translated bot name pool (BOTS.md §7.1, §11.3): stable ASCII ids, keys
 * {@code gui.burmaldaholic.bots.name.<id>}, theme pools. Ids are drawn from the BOT rng without
 * replacement among the ids unused at that table / match. Same list in Bedrock
 * ({@code core/logic/bots/roster.ts}).
 */
public final class BotRoster {
	public enum Theme { ANY, PIGLIN, ENDER }

	public static final List<String> ANY = List.of("lucky_steve", "grandpa_pavel", "creeper42", "mr_blocksworth", "diamond_dora",
		"aunt_zoya", "redstone_rick", "emerald_emma", "sir_oinksalot", "baba_valya", "uncle_grisha", "kuzmich", "cobble_carl",
		"slime_sam", "brewing_bella", "captain_boat", "bee_bea", "torch_tanya", "axolotl_al", "lady_luckless", "iron_ivan");
	public static final List<String> PIGLIN = List.of("nether_nick", "goldie_nuggets", "piglin_pete", "bartering_boris",
		"madame_crimson", "tusk_tony");
	public static final List<String> ENDER = List.of("enderman_ed", "madame_ender", "shulker_shura", "pearl_polly", "void_viktor");

	/** Old poker names / ids saved by earlier versions → new ids. */
	public static final Map<String, String> LEGACY = Map.of("diamond_dave", "diamond_dora", "Diamond Dave", "diamond_dora");

	private BotRoster() {}

	public static String nameKey(String id) {
		return "gui.burmaldaholic.bots.name." + id;
	}

	/** Candidate ids for a theme, themed ones first (Piglin Parlor: piglin + any; End lounge: ender + any). */
	public static List<String> pool(Theme theme) {
		List<String> out = new ArrayList<>();
		if (theme == Theme.PIGLIN) {
			out.addAll(PIGLIN);
		} else if (theme == Theme.ENDER) {
			out.addAll(ENDER);
		}
		out.addAll(ANY);
		return out;
	}

	/** An unused id (themed ids first); if every id is used, any id of the pool. */
	public static String drawName(BotRng rng, Theme theme, Collection<String> used) {
		List<String> themed = theme == Theme.PIGLIN ? PIGLIN : theme == Theme.ENDER ? ENDER : List.of();
		List<String> free = themed.stream().filter(id -> !used.contains(id)).toList();
		if (free.isEmpty()) {
			free = pool(theme).stream().filter(id -> !used.contains(id)).toList();
		}
		return free.isEmpty() ? rng.pick(pool(theme)) : rng.pick(free);
	}

	/** A complete new bot: id, unused name, level (MIXED resolved with {@code mix}), personality of that level. */
	public static BotProfile create(BotRng rng, BotDifficulty setting, int[] mix, Theme theme, Collection<String> usedNames,
			boolean personalities) {
		BotDifficulty level = setting.pick(rng, mix);
		return new BotProfile(BotProfile.newId(rng), drawName(rng, theme, usedNames), level, Personality.pick(rng, level, personalities));
	}
}
