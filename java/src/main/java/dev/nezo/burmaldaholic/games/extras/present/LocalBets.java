package dev.nezo.burmaldaholic.games.extras.present;

import java.util.HashMap;
import java.util.Map;

/**
 * The client session's local bets of the single-spot extras games (R-CDE m5): one {@link LocalBet} per game and machine
 * position, so two Plinko machines (or two Wheels) keep their own bet and Rebet, and a bet never carries from one machine
 * to the next. The item games (Coin Flip, Dice Duel) have no machine: their key is the game alone ({@link #HAND}).
 * Cleared when the client leaves the world ({@link #clear}). PURE (no Minecraft types: the position is a
 * {@code BlockPos.asLong()}); unit-tested in {@code ExtrasFlowTest}.
 */
public final class LocalBets {
	/** The key of a game without a machine (an item in the hand). */
	public static final long HAND = Long.MIN_VALUE;

	private static final Map<String, LocalBet> BETS = new HashMap<>();

	private LocalBets() {}

	/** The bet of {@code game} at the machine {@code pos} ({@code BlockPos.asLong()}, or {@link #HAND}); created empty. */
	public static synchronized LocalBet of(String game, long pos) {
		return BETS.computeIfAbsent(game + "@" + pos, k -> new LocalBet(0));
	}

	/** Forgets every bet (disconnect / a new world). */
	public static synchronized void clear() {
		BETS.clear();
	}

	/** Number of bets held (tests). */
	public static synchronized int size() {
		return BETS.size();
	}
}
