package dev.nezo.burmaldaholic.core.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * Chip balance API. Server-side only. Amounts are whole chips ({@code long}, never negative input).
 * Games MUST go through this API (never edit the attachment directly) so loans, VIP, Last Chance
 * and statistics observe every change via {@link dev.nezo.burmaldaholic.core.events.CasinoEvents}.
 *
 * <p>Obtain with {@link Economies#get()}.
 */
public interface Economy {
	long balance(ServerPlayer player);

	/** Atomically removes {@code amount} if the player has it. Returns false (and changes nothing) otherwise. */
	boolean tryWithdraw(ServerPlayer player, long amount, Transaction reason);

	void deposit(ServerPlayer player, long amount, Transaction reason);

	/** Why the balance changed: owning module + short machine-readable detail (e.g. "bet", "payout"). */
	record Transaction(String moduleId, String detail) {
		public static Transaction of(String moduleId, String detail) {
			return new Transaction(moduleId, detail);
		}
	}
}
