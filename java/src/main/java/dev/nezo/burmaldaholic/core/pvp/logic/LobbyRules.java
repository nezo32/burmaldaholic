package dev.nezo.burmaldaholic.core.pvp.logic;

import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import java.util.List;

/**
 * Pure lobby rules (PVP.md §3.3.2, §3.15.1, BOTS.md §3.4, §3.6): host succession, bot seat filling and
 * the start rule.
 */
public final class LobbyRules {
	private LobbyRules() {}

	/** A participant in join order: {@code key} = occupant key, {@code bot} = a bot. */
	public record Seat(String key, boolean bot) {}

	/**
	 * New host when {@code leaving} leaves: the earliest-joined remaining human (bots never host).
	 *
	 * @return the key, or null when no human remains (the lobby is cancelled)
	 */
	public static String nextHost(List<Seat> seats, String leaving) {
		for (Seat s : seats) {
			if (!s.bot() && !s.key().equals(leaving)) {
				return s.key();
			}
		}
		return null;
	}

	/**
	 * How many bots to seat now.
	 *
	 * @param wanted     {@code BotSettings.count}: MIXED upper bound / BOTS_ONLY exact
	 * @param maxPlayers the mode's max players
	 * @param seated     participants now (humans + bots)
	 * @param botsSeated bots now
	 * @param maxPerMatch {@code bots.pvp.maxPerMatch}
	 * @param keepFree   MIXED: keep one seat for a walk-in (delayed fill only; at the start every seat fills)
	 * @param atStart    the lobby is starting (host Start / full / timer) — BOTS_ONLY seats all at once too
	 */
	public static int botsToSeat(SeatPolicy policy, int wanted, int maxPlayers, int seated, int botsSeated, int maxPerMatch,
			boolean keepFree, boolean atStart) {
		if (policy == SeatPolicy.HUMANS_ONLY) {
			return 0;
		}
		int target = Math.min(wanted, maxPerMatch);
		if (policy == SeatPolicy.BOTS_ONLY) {
			target = Math.max(1, target);
		}
		int free = maxPlayers - seated;
		if (policy == SeatPolicy.MIXED && keepFree && !atStart) {
			free--;
		}
		return Math.max(0, Math.min(target - botsSeated, free));
	}

	/**
	 * Should a lobby of an equal-stakes mode start now (and would it be cancelled instead)?
	 *
	 * @return START, CANCEL or WAIT
	 */
	public static Decision startRule(int seated, int maxPlayers, boolean hostPressedStart, boolean timerEnded, int botsAtStart) {
		if (seated >= maxPlayers) {
			return Decision.START;
		}
		if (hostPressedStart || timerEnded) {
			return seated + botsAtStart >= 2 ? Decision.START : timerEnded ? Decision.CANCEL : Decision.WAIT;
		}
		return Decision.WAIT;
	}

	public enum Decision { WAIT, START, CANCEL }
}
