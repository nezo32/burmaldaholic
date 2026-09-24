package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.List;
import java.util.UUID;

/** Pure seating rules (BOTS.md §2.2, §2.4, §3). Both editions implement the same functions. */
public final class SeatingMath {
	private SeatingMath() {}

	/**
	 * Bot seats wanted at a safe point before the purse / global / win-cap limits:
	 * <pre>
	 * MIXED:       max(0, min(count, seats − humans − claimants − (keepFree ? 1 : 0)))
	 * BOTS_ONLY:   count (humans = 1, the host)
	 * HUMANS_ONLY: 0
	 * </pre>
	 * No human seated (and no claimant) → 0: nobody plays with bots alone (§2.1).
	 */
	public static int wantedBots(BotSettings s, int seats, int humans, int claimants) {
		if (humans + claimants <= 0) {
			return 0;
		}
		return switch (s.policy()) {
			case HUMANS_ONLY -> 0;
			case BOTS_ONLY -> Math.max(1, Math.min(s.count(), seats - 1));
			case MIXED -> Math.max(0, Math.min(s.count(), seats - humans - claimants - (s.keepFree() ? 1 : 0)));
		};
	}

	/**
	 * Applies the hard caps: owner {@code maxBots}, the owner's Bots mode for this role, the world-wide
	 * {@code bots.maxActive} budget left, and whether the purse can fund one more money bot.
	 */
	public static int capped(int wanted, OwnerControls owner, BotRole role, int activeBudgetLeft, int affordable) {
		if (!owner.botsMode().allows(role)) {
			return 0;
		}
		int n = Math.min(wanted, owner.maxBots());
		n = Math.min(n, Math.max(0, activeBudgetLeft));
		if (role == BotRole.MONEY) {
			n = Math.min(n, Math.max(0, affordable));
		}
		return Math.max(0, n);
	}

	/** Is BOTS_ONLY selectable now? Only while the host is the only human seated (§2.1). */
	public static boolean botsOnlyAllowed(int humansSeated) {
		return humansSeated <= 1;
	}

	/**
	 * Host selection (§2.4): the keeper if seated, else the human seated longest ({@code seatedOrder} =
	 * humans in the order they sat down in this session). Null if nobody is seated.
	 */
	public static UUID host(UUID keeper, List<UUID> seatedOrder) {
		if (keeper != null && seatedOrder.contains(keeper)) {
			return keeper;
		}
		return seatedOrder.isEmpty() ? null : seatedOrder.getFirst();
	}

	/** How a game picks the bot that yields its seat to a claimant (§3.3). */
	public enum YieldRule {
		/** Poker: the bot that just posted the big blind; ties → smallest stack. */
		POKER_BIG_BLIND,
		/** Chemin de fer: a punter bot first (highest seat), the banker bot last. */
		CHEMMY_PUNTER_FIRST,
		/** Atmosphere games: highest seat number. */
		HIGHEST_SEAT,
		/** PvP lobby: the last bot to join. */
		LAST_JOINED
	}
}
