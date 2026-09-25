package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * What a bot's chips are (BOTS.md §1): MONEY = real chips from a {@link Purse} (poker, chemin de fer,
 * PvP, tournaments); ATMOSPHERE = virtual bets beside humans at a house-banked table (never touch an account).
 */
public enum BotRole {
	MONEY, ATMOSPHERE
}
