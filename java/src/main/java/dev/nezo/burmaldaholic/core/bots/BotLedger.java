package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Daily heat / win cap against HOUSE-funded bots (BOTS.md §5.4) and per-table buy-in counters. Backed by
 * {@link BotLedgerData}. Games report every settled round against money bots with {@link #record}.
 */
public final class BotLedger {
	private BotLedger() {}

	/** Net chips the player won from house-funded bots today (MCD). */
	public static long netToday(MinecraftServer server, UUID player) {
		return BotLedgerData.get(server).netToday(player, day(server));
	}

	/** Add an attributed net (see {@code BotEconomyMath.pokerPot / pvp}); fires the heat stages. */
	public static void record(MinecraftServer server, UUID player, long attributedNet) {
		BotLedgerData.get(server).add(player, day(server), attributedNet);
		// TODO(J-B2): heat stage ("Word got around", adaptive heat) and sulk at cap × bots.sulkMultiplier
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

	/** House-funded bots refuse this player until the next MCD. */
	public static boolean sulking(MinecraftServer server, UUID player) {
		long t = threshold(server, player);
		return t > 0 && netToday(server, player) >= (long) Math.ceil(t * CasinoConfig.bots().sulkMultiplier);
	}

	private static long day(MinecraftServer server) {
		return BotEconomyMath.mcDay(server.overworld().getGameTime());
	}
}
