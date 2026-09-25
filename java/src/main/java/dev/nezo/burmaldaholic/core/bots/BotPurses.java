package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Money-bot funding (BOTS.md §5.1). A bot's chips are escrowed in the BANK like a human's buy-in, so
 * every pot / escrow keeps one holder: <ul>
 *   <li>BANK purse (unowned table / anchor): nothing moves when the bot sits (the bank mints) or leaves
 *       (the bank sinks); the daily house-funded bot buy-in counter ({@code bots.tableBuyInsPerDay}) applies;</li>
 *   <li>BANKROLL purse (owned, {@code bots.owned.funding} = OWNER_BANKROLL and the owner set Bots =
 *       Allowed): sit = bankroll → bank, only if {@code balance − reserved} covers it (else the bot does
 *       not sit); leave = bank → bankroll (what the bot holds). The bank never funds bots at owned casinos.</li>
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

	/** Owned-casino data of a table / anchor position (empty = house table). */
	public static Optional<OwnedTable> ownership(ServerLevel level, @Nullable BlockPos pos) {
		return pos == null ? Optional.empty() : CoreServices.tableOwnership().owner(level, pos);
	}

	/**
	 * Owner controls that actually apply (§6.2): unowned → the keeper's controls with Bots always
	 * ALLOWED; owned → the owner's controls ({@code ownerSet}) or {@link OwnerControls#ownedDefaults}
	 * (Atmosphere only, private forbidden) until the owner saved some; the charter's legacy "bots off"
	 * switch forces OFF.
	 */
	public static OwnerControls effectiveControls(Optional<OwnedTable> owned, OwnerControls stored, boolean ownerSet, int seats) {
		if (owned.isEmpty()) {
			return new OwnerControls(BotsMode.ALLOWED, stored.hostMayChange(), stored.maxBots(), stored.allowPrivate());
		}
		OwnerControls c = ownerSet ? stored : OwnerControls.ownedDefaults(seats);
		if (!owned.get().bots()) {
			return new OwnerControls(BotsMode.OFF, c.hostMayChange(), c.maxBots(), c.allowPrivate());
		}
		return c;
	}

	/**
	 * The purse new bots of {@code role} use here: atmosphere → {@link Purse#NONE}; unowned → BANK;
	 * owned with Bots = Allowed and {@code bots.owned.funding} = OWNER_BANKROLL → the bankroll; else null
	 * (no money bots at this table).
	 */
	public static @Nullable Purse purseFor(Optional<OwnedTable> owned, OwnerControls effective, BotRole role) {
		if (role == BotRole.ATMOSPHERE) {
			return Purse.NONE;
		}
		if (owned.isEmpty()) {
			return Purse.BANK;
		}
		if (CasinoConfig.bots().owned.funding != BotsConfig.Funding.OWNER_BANKROLL || effective.botsMode() != BotsMode.ALLOWED) {
			return null;
		}
		return Purse.bankroll(owned.get().bankrollId());
	}

	/** Chips the purse can put up now: BANK unlimited; BANKROLL {@code balance − reserved} (0 if it is gone). */
	public static long available(MinecraftServer server, Purse purse) {
		if (purse.kind() != Purse.Kind.BANKROLL) {
			return Long.MAX_VALUE;
		}
		return Economies.get().bankrolls(server).get(purse.bankrollId()).map(Economy.BankrollInfo::available).orElse(0L);
	}

	/** New money bots with {@code buyIn} each the purse can fund at {@code tableKey} now (§5.1). */
	public static int affordable(MinecraftServer server, Purse purse, long buyIn, String tableKey) {
		return switch (purse.kind()) {
			case NONE -> Integer.MAX_VALUE;
			case BANK -> buyIn <= 0 ? Integer.MAX_VALUE : BotLedger.buyInsLeft(server, tableKey);
			case BANKROLL -> BotEconomyMath.affordable(available(server, purse), buyIn);
		};
	}

	/**
	 * A bot sits with {@code amount}: moves bankroll chips into the bank escrow. False (nothing moved)
	 * if {@code balance − reserved} of the bankroll does not cover it. BANK / NONE: nothing moves, true.
	 */
	public static boolean fund(MinecraftServer server, Purse purse, long amount, String gameId) {
		if (amount <= 0 || purse.kind() != Purse.Kind.BANKROLL) {
			return true;
		}
		if (available(server, purse) < amount) {
			return false;
		}
		Economy.TxResult tx = Economies.get().transfer(server, AccountId.bankroll(purse.bankrollId()), AccountId.HOUSE, amount,
			new Transaction(gameId, "bot_buy_in", Transaction.Kind.TRANSFER));
		return tx.ok();
	}

	/**
	 * A bot leaves with {@code amount}: returns it from the bank escrow to its purse. A bankroll that no
	 * longer exists (charter broken) cannot take it back: the chips stay in the bank (returns false).
	 */
	public static boolean settle(MinecraftServer server, Purse purse, long amount, String gameId) {
		if (amount <= 0 || purse.kind() != Purse.Kind.BANKROLL) {
			return true;
		}
		return Economies.get().transfer(server, AccountId.HOUSE, AccountId.bankroll(purse.bankrollId()), amount,
			new Transaction(gameId, "bot_cash_out", Transaction.Kind.TRANSFER)).ok();
	}
}
