package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;

/**
 * How a settled round against MONEY bots is reported (BOTS.md §5.3). Use {@link #tag} on the
 * {@code PlayResult}: listeners (VIP, contracts, streak, Golden Hour, cashback, big-win broadcast,
 * advancements) read the tags and apply the bot rules — VIP / {@code wager} contract credit ×
 * {@code bots.vipWagerWeight} for chips matched by bots, no streak, no cashback, no Golden Hour, no
 * broadcast when every counterparty was a bot. Rounds with only ATMOSPHERE bots are ordinary house rounds.
 */
public final class BotRounds {
	/** Some of the human's counterparties were money bots. */
	public static final String VS_BOTS = "vs_bots";
	/** Every counterparty was a money bot. */
	public static final String ONLY_BOTS = "only_bots";

	private BotRounds() {}

	public static PlayResult tag(PlayResult r, boolean anyBots, boolean onlyBots) {
		if (onlyBots) {
			return r.withTags(VS_BOTS, ONLY_BOTS);
		}
		return anyBots ? r.withTags(VS_BOTS) : r;
	}

	public static boolean vsBots(PlayResult r) {
		return r.tags().contains(VS_BOTS);
	}

	public static boolean onlyBots(PlayResult r) {
		return r.tags().contains(ONLY_BOTS);
	}
}
