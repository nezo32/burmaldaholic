package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by a table block entity whose seats may hold bots (poker, baccarat incl. chemin de fer,
 * UTH, blackjack, roulette, craps). The game keeps its own seat model ABSTRACT over
 * {@link SeatOccupant}; {@link TableBots} calls these hooks ONLY at the game's safe point (BOTS.md §3.1),
 * i.e. when the game calls {@link TableBots#safePoint}.
 */
public interface BotTable {
	/** Game id for config families: {@code poker | chemmy | blackjack | roulette | craps | baccarat | uth}. */
	String botGameId();

	/** MONEY (poker, chemmy, PvP) or ATMOSPHERE (virtual bets). */
	BotRole botRole();

	/** Seats available to occupants (players + bots, excluding a dealer seat). */
	int botSeatCount();

	/** Humans currently seated, in the order they sat down (host selection, BOTS.md §2.4). */
	List<UUID> seatedHumans();

	/** Current occupants in seat order (null = empty seat). */
	List<SeatOccupant> occupants();

	/** Seat a bot in a free seat (called at the safe point); return false if the game refuses. */
	boolean seatBot(SeatOccupant.Bot bot, long stack);

	/**
	 * Remove a bot at the safe point; returns what it holds (stack / bank escrow) so {@link TableBots} can
	 * return it to its purse. Atmosphere bots return 0.
	 */
	long unseatBot(String botKey);

	/** Which bot yields to a claimant (§3.3). */
	SeatingMath.YieldRule yieldRule();

	/** Chips a new money bot sits with (poker buy-in, chemmy bank / punt budget); 0 for atmosphere. */
	default long botBuyIn() {
		return 0;
	}

	/** False for games where decisions can't matter (difficulty hidden, BOTS.md §4.7/§4.8 "luck only"). */
	default boolean botDifficultyMatters() {
		return true;
	}

	/**
	 * The table's {@link TableBots} (the object the block entity owns). Used by the bots module (table
	 * settings screen, {@code /casino table}, avatars, chatter). Games implementing this interface should
	 * return their instance; null = the table offers no bot / private-table settings.
	 */
	default @org.jspecify.annotations.Nullable TableBots tableBots() {
		return null;
	}
}
