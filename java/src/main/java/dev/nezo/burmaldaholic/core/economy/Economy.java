package dev.nezo.burmaldaholic.core.economy;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chip balance API (GAME_DESIGN.md §3). Server-side only, server thread only. Amounts are whole
 * chips ({@code long}, never negative input). Balances are world data (persist, survive death,
 * work for offline players). Games MUST go through this API so loans (garnishment), VIP, Last Chance
 * and statistics observe every change ({@link dev.nezo.burmaldaholic.core.events.CasinoEvents}).
 *
 * <p>Obtain with {@link Economies#get()}. For bets at tables prefer the table helpers
 * ({@code CasinoTableBlockEntity#stake/settle}), which also handle bankrolls, refunds and events.
 *
 * <pre>
 * Economy eco = Economies.get();
 * if (!eco.tryWithdraw(player, 50, Transaction.bet("slots"))) { ...insufficient funds... }
 * eco.deposit(player, 120, Transaction.payout("slots"));
 * // atomic multi-account move (all legs or nothing):
 * eco.batch(server).debit(AccountId.player(a), 100).credit(AccountId.player(b), 95).commit(Transaction.of("extras", "dice_duel_pvp"));
 * </pre>
 */
public interface Economy {
	long balance(ServerPlayer player);

	/** Works for offline players. */
	long balance(MinecraftServer server, UUID playerId);

	/** Atomically removes {@code amount} (to the house) if the player has it. Returns false and changes nothing otherwise. */
	boolean tryWithdraw(ServerPlayer player, long amount, Transaction reason);

	/**
	 * Credits {@code amount} from the house. Credit hooks may divert part (loan garnishment) and
	 * anything above {@code economy.maxBalance} is lost (player gets {@code msg.burmaldaholic.core.balance_capped}).
	 * @return chips actually added to the balance
	 */
	long deposit(ServerPlayer player, long amount, Transaction reason);

	/** Offline-safe credit. */
	long deposit(MinecraftServer server, UUID playerId, long amount, Transaction reason);

	/** Admin: sets the balance (clamped to [0, maxBalance]). No hooks. */
	void setBalance(MinecraftServer server, UUID playerId, long amount, Transaction reason);

	/** Moves chips between two accounts atomically. */
	default TxResult transfer(MinecraftServer server, AccountId from, AccountId to, long amount, Transaction reason) {
		return batch(server).debit(from, amount).credit(to, amount).commit(reason);
	}

	/** Starts an atomic multi-leg transaction. */
	Batch batch(MinecraftServer server);

	/** Owned-casino bankroll accounts. */
	Bankrolls bankrolls(MinecraftServer server);

	/** Registers a hook that sees every garnishable credit before it lands (loan module). */
	void addCreditHook(CreditHook hook);

	/** All-or-nothing set of debits/credits. Nothing happens until {@link #commit}. */
	interface Batch {
		Batch debit(AccountId account, long amount);

		Batch credit(AccountId account, long amount);

		TxResult commit(Transaction reason);
	}

	/** @param failed account that could not cover its debit (null on success) */
	record TxResult(boolean ok, AccountId failed, long lostToCap) {
		public static TxResult success(long lostToCap) {
			return new TxResult(true, null, lostToCap);
		}

		public static TxResult insufficient(AccountId account) {
			return new TxResult(false, account, 0);
		}
	}

	interface Bankrolls {
		/** Creates the account if missing. */
		BankrollInfo open(String id, UUID owner);

		Optional<BankrollInfo> get(String id);

		/**
		 * Reserves worst-case payout for an accepted stake (§18.2 solvency rule).
		 * @return false (nothing reserved) if {@code available < amount}
		 */
		boolean reserve(String id, long amount);

		/** Releases a reservation (after settlement). */
		void release(String id, long amount);

		/** Deletes the account and returns its balance (caller credits it to the owner). */
		long close(String id);
	}

	record BankrollInfo(String id, UUID owner, long balance, long reserved) {
		public long available() {
			return Math.max(0, balance - reserved);
		}
	}

	/**
	 * Sees positive (net) credits to a player before they land. Return how many chips should still
	 * reach the balance (0..amount); the hook is responsible for whatever it diverted (e.g. debt).
	 */
	@FunctionalInterface
	interface CreditHook {
		long beforeCredit(MinecraftServer server, UUID playerId, long amount, Transaction reason);
	}

	/**
	 * Why the balance changed.
	 * @param moduleId owning module ("core", "blackjack", ...)
	 * @param detail   short machine-readable detail ("bet", "payout", "ore", "withdraw"...)
	 * @param kind     category, used e.g. by garnishment (only {@link Kind#garnishable()} kinds)
	 */
	record Transaction(String moduleId, String detail, Kind kind) {
		public enum Kind {
			/** Stake taken for a bet. */
			BET,
			/** Winnings / returned stake of a settled bet. */
			PAYOUT,
			/** Stake returned without a result (server restart, cancelled round). */
			REFUND,
			/** Ores, mobs, trades, contracts, cashback, bonuses. */
			EARNING,
			/** Cashier deposit/withdraw/exchange. */
			CASHIER,
			/** Player/bankroll transfers, loans. */
			TRANSFER,
			/** Operator commands. */
			ADMIN,
			OTHER;

			public boolean garnishable() {
				return this == PAYOUT || this == EARNING;
			}
		}

		public static Transaction of(String moduleId, String detail) {
			return new Transaction(moduleId, detail, Kind.OTHER);
		}

		public static Transaction bet(String moduleId) {
			return new Transaction(moduleId, "bet", Kind.BET);
		}

		public static Transaction payout(String moduleId) {
			return new Transaction(moduleId, "payout", Kind.PAYOUT);
		}

		public static Transaction refund(String moduleId) {
			return new Transaction(moduleId, "refund", Kind.REFUND);
		}

		public static Transaction earning(String detail) {
			return new Transaction("core", detail, Kind.EARNING);
		}
	}
}
