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

	/** Tag prefix carrying the bots' share of the counterparty money in basis points ({@code bot_share:5000}). */
	public static final String SHARE_PREFIX = "bot_share:";

	/**
	 * Adds the bots' share of the money on the other side of the human (BOTS.md §5.3: PvP = bot stakes /
	 * other participants' stakes), read by {@link #botShare}. Use after {@link #tag}.
	 */
	public static PlayResult withShare(PlayResult r, double botShare) {
		long bp = Math.round(Math.max(0, Math.min(1, botShare)) * 10000);
		return r.withTags(SHARE_PREFIX + bp);
	}

	/** The bots' share of the round (0 … 1): the share tag, else 1 for any round tagged vs bots, else 0. */
	public static double botShare(PlayResult r) {
		for (String t : r.tags()) {
			if (t.startsWith(SHARE_PREFIX)) {
				try {
					return Math.max(0, Math.min(1, Long.parseLong(t.substring(SHARE_PREFIX.length())) / 10000.0));
				} catch (NumberFormatException ignored) {
					break;
				}
			}
		}
		return vsBots(r) ? 1 : 0;
	}

	public static boolean vsBots(PlayResult r) {
		return r.tags().contains(VS_BOTS);
	}

	public static boolean onlyBots(PlayResult r) {
		return r.tags().contains(ONLY_BOTS);
	}
}
