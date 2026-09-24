package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Daily heat / win cap against HOUSE-funded bots (BOTS.md §5.4), per-table daily house buy-ins (§5.1) and
 * the crash-safe record of what bankroll-funded bots hold (§3.5). Backed by {@link BotLedgerData}.
 * Games report every settled round against house-funded money bots with {@link #record} (owner-funded
 * bots are not tracked); tables consult {@link #stage} at their safe point.
 */
public final class BotLedger {
	private BotLedger() {}

	/** Net chips the player won from house-funded bots today (MCD). */
	public static long netToday(MinecraftServer server, UUID player) {
		return BotLedgerData.get(server).netToday(player, day(server));
	}

	/**
	 * Add an attributed net (see {@code BotEconomyMath.pokerPot / pvp}) and return the player's heat
	 * stage after it. The round that crosses a line is kept (no clawback); the stage takes effect at the
	 * next safe point of each table the player sits at ({@code TableBots}).
	 */
	public static HeatStage record(MinecraftServer server, UUID player, long attributedNet) {
		if (attributedNet != 0) {
			BotLedgerData.get(server).add(player, day(server), attributedNet);
		}
		return stage(server, player);
	}

	/** Adaptive heat input: one poker hand vs house bots, the player's net in big blinds ({@code bots.adaptiveHeat}). */
	public static void recordPokerHand(MinecraftServer server, UUID player, double bbNet) {
		if (CasinoConfig.bots().adaptiveHeat) {
			BotLedgerData.get(server).recordPokerHand(player, bbNet);
		}
	}

	/** Winning player (&gt; +20 BB/100 over ≥ 200 hands vs house bots): new house bots at their poker tables are one level up. */
	public static boolean adaptive(MinecraftServer server, UUID player) {
		return CasinoConfig.bots().adaptiveHeat && BotLedgerData.get(server).adaptive(player);
	}

	/** Threshold of the player (0 = heat disabled). */
	public static long threshold(MinecraftServer server, UUID player) {
		var b = CasinoConfig.bots();
		if (b.dailyWinCapTierMultiple <= 0) {
			return 0;
		}
		long tierMax = VipTiers.maxBet(CoreServices.vip().tier(server, player));
		return BotEconomyMath.dailyCap(tierMax, b.dailyWinCapMin, b.dailyWinCapTierMultiple);
	}

	/** Heat stage of the player today (§5.4). */
	public static HeatStage stage(MinecraftServer server, UUID player) {
		return BotEconomyMath.heatStage(netToday(server, player), threshold(server, player), CasinoConfig.bots().sulkMultiplier);
	}

	/** House-funded bots refuse this player until the next MCD. */
	public static boolean sulking(MinecraftServer server, UUID player) {
		return stage(server, player) == HeatStage.SULKING;
	}

	/** "Word got around" or worse: only HARD house bots play the player at poker. */
	public static boolean hardOnly(MinecraftServer server, UUID player) {
		return stage(server, player).atLeast(HeatStage.HARD_ONLY);
	}

	/** Ticks until the heat resets (next MCD), for {@code …bots.error.capped}. */
	public static long ticksToReset(MinecraftServer server) {
		return BotEconomyMath.ticksToNextDay(server.overworld().getGameTime());
	}

	/** {@code /casino bots heat <player> reset}: today's net, the adaptive-heat stats and the told heat lines. */
	public static void reset(MinecraftServer server, UUID player) {
		BotLedgerData.get(server).reset(player); // today's net AND the adaptive-heat stats
		TableBots.HEAT_NOTICES.reset(player); // the heat lines may be told again
	}

	/** Today's Minecraft day (the heat window). */
	public static long today(MinecraftServer server) {
		return day(server);
	}

	// ---- per-table house buy-ins (§5.1) ------------------------------------------------------------

	/** House-funded bot buy-ins this table may still fund today ({@code Integer.MAX_VALUE} = unlimited). */
	public static int buyInsLeft(MinecraftServer server, String tableKey) {
		return BotEconomyMath.buyInsLeft(CasinoConfig.bots().tableBuyInsPerDay, BotLedgerData.get(server).buyInsToday(tableKey, day(server)));
	}

	public static void countBuyIn(MinecraftServer server, String tableKey) {
		BotLedgerData.get(server).countBuyIn(tableKey, day(server));
	}

	// ---- bankroll escrow (crash safety, §3.5) --------------------------------------------------------

	static void putEscrow(MinecraftServer server, String tableKey, String botId, String bankrollId, long amount) {
		BotLedgerData.get(server).putEscrow(tableKey, botId, bankrollId, amount);
	}

	static void clearEscrow(MinecraftServer server, String tableKey, String botId) {
		BotLedgerData.get(server).removeEscrow(tableKey, botId);
	}

	/**
	 * Returns every recorded bankroll-bot holding of {@code tableKey} (null = all tables) to its bankroll
	 * and forgets it. Used for orphans: on SERVER_STARTED (a crash left them) and at the first safe point
	 * of a (re)loaded table, before any of its bots exist. Idempotent. Returns the chips returned.
	 */
	static long returnOrphans(MinecraftServer server, String tableKey) {
		BotLedgerData data = BotLedgerData.get(server);
		long total = 0;
		for (BotLedgerData.Escrow e : tableKey == null ? data.escrows() : data.escrowsOf(tableKey)) {
			data.removeEscrow(e.tableKey(), e.botId());
			if (Economies.get().bankrolls(server).get(e.bankrollId()).isEmpty()) {
				Burmaldaholic.LOGGER.warn("Orphaned bot stack of {} at {}: bankroll {} is gone, {} chips stay in the bank",
					e.botId(), e.tableKey(), e.bankrollId(), e.amount());
				continue;
			}
			Economy.TxResult tx = Economies.get().transfer(server, AccountId.HOUSE, AccountId.bankroll(e.bankrollId()), e.amount(),
				new Economy.Transaction("bots", "orphan_return", Economy.Transaction.Kind.REFUND));
			if (tx.ok()) {
				total += e.amount();
			}
		}
		return total;
	}

	private static long day(MinecraftServer server) {
		return BotEconomyMath.mcDay(server.overworld().getGameTime());
	}
}
