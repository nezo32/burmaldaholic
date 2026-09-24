package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.economy.Economies;
import net.minecraft.server.MinecraftServer;

/**
 * Money-bot funding (BOTS.md §5.1). A bot's chips are escrowed in the BANK like a human's buy-in, so
 * every pot / escrow keeps one holder: <ul>
 *   <li>BANK purse: nothing moves when the bot sits (the bank mints) or leaves (the bank sinks); the
 *       daily house-funded bot buy-in counter ({@code bots.tableBuyInsPerDay}) applies;</li>
 *   <li>BANKROLL purse: sit = bankroll → bank (fails if {@code available} &lt; amount: the bot does not sit);
 *       leave = bank → bankroll (what the bot holds).</li>
 * </ul>
 * Use {@link #account} as the bot's leg in an {@code Economy.batch} when a pot is settled against a
 * human (e.g. PvP settle credits a bot's payout to {@code account(purse)} — the bank for house bots).
 */
public final class BotPurses {
	private BotPurses() {}

	/** Ledger account that stands for the bot's purse. */
	public static AccountId account(Purse purse) {
		return purse.kind() == Purse.Kind.BANKROLL ? AccountId.bankroll(purse.bankrollId()) : AccountId.HOUSE;
	}

	/** A bot sits with {@code amount}: moves bankroll chips into the bank escrow. */
	public static boolean fund(MinecraftServer server, Purse purse, long amount, String gameId) {
		if (amount <= 0 || purse.kind() != Purse.Kind.BANKROLL) {
			return true;
		}
		Economy.TxResult tx = Economies.get().transfer(server, AccountId.bankroll(purse.bankrollId()), AccountId.HOUSE, amount,
			new Transaction(gameId, "bot_buy_in", Transaction.Kind.TRANSFER));
		return tx.ok();
	}

	/** A bot leaves with {@code amount}: returns it from the bank escrow to its purse. */
	public static void settle(MinecraftServer server, Purse purse, long amount, String gameId) {
		if (amount <= 0 || purse.kind() != Purse.Kind.BANKROLL) {
			return;
		}
		Economies.get().transfer(server, AccountId.HOUSE, AccountId.bankroll(purse.bankrollId()), amount,
			new Transaction(gameId, "bot_cash_out", Transaction.Kind.TRANSFER));
	}
}
