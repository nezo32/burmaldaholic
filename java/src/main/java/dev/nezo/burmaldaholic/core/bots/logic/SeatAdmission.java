package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * "A human wants a seat" (BOTS.md §3.2). Pure decision; {@code TableBots.admit} gathers the facts and
 * turns the verdict into a result / message.
 */
public final class SeatAdmission {
	private SeatAdmission() {}

	public enum Verdict {
		/** Sit now (a seat is free, or the player already sits). */
		SIT,
		/** Every seat is taken and a bot will yield at the next safe point ({@code …bots.seat_after_round}). */
		CLAIMANT,
		/** Private table, not invited ({@code …bots.error.private_table}). */
		PRIVATE,
		/** Someone else's BOTS_ONLY session ({@code …bots.error.bots_only_table}). */
		BOTS_ONLY,
		/** Every seat is human ({@code gui.burmaldaholic.error.table_full}). */
		FULL
	}

	/**
	 * @param alreadySeated   the player sits at this table
	 * @param alreadyClaimant the player already waits for a bot's seat
	 * @param accessOk        {@code TableAccess.mayJoin} (or private tables disabled)
	 * @param policy          the session's effective policy
	 * @param isHost          the player is the session host
	 * @param humansSeated    humans seated now
	 * @param freeSeats       empty seats not reserved for earlier claimants
	 * @param unclaimedBots   bot seats not yet reserved for earlier claimants
	 */
	public static Verdict admit(boolean alreadySeated, boolean alreadyClaimant, boolean accessOk, SeatPolicy policy, boolean isHost,
			int humansSeated, int freeSeats, int unclaimedBots) {
		if (alreadySeated) {
			return Verdict.SIT;
		}
		if (!accessOk) {
			return Verdict.PRIVATE;
		}
		if (policy == SeatPolicy.BOTS_ONLY && humansSeated > 0 && !isHost) {
			return Verdict.BOTS_ONLY;
		}
		if (alreadyClaimant) {
			return Verdict.CLAIMANT;
		}
		if (freeSeats > 0) {
			return Verdict.SIT;
		}
		return unclaimedBots > 0 ? Verdict.CLAIMANT : Verdict.FULL;
	}
}
