package dev.nezo.burmaldaholic.gametest.vip;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.vip.Contracts;
import dev.nezo.burmaldaholic.vip.VipData;
import dev.nezo.burmaldaholic.vip.logic.CashbackRules;
import dev.nezo.burmaldaholic.vip.logic.ContractRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** VIP tiers, cashback and contracts in a real world (headless server). */
public class VipGameTests {
	private static final Transaction TEST = Transaction.of("vip", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), 0, TEST);
			body.accept(player);
		} finally {
			server.getPlayerList().remove(player);
		}
	}

	private static void play(ServerPlayer player, String game, long bet, long payout) {
		CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, new CasinoEvents.PlayResult(game, bet, payout));
	}

	private static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	@GameTest
	public void wageringPromotesAndFeedsBetLimits(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			helper.assertTrue(CoreServices.vip().tier(server, player.getUUID()) == 0, "starts Bronze");
			play(player, "roulette", 4_999, 0);
			helper.assertTrue(CoreServices.vip().tier(server, player.getUUID()) == 0, "still Bronze below 5 000");
			play(player, "blackjack", 1, 2);
			helper.assertTrue(CoreServices.vip().tier(server, player.getUUID()) == 1, "Silver at 5 000 wagered");
			helper.assertTrue(CoreServices.vip().maxBet(server, player.getUUID()) == 250, "Silver max bet");
			helper.assertTrue(VipData.get(server).player(player.getUUID()).tier == 1, "promotion stored (announced once)");
		});
		helper.succeed();
	}

	@GameTest
	public void cashbackIsRateTimesTheoreticalLoss(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			VipData.Record rec = VipData.get(server).player(player.getUUID());
			rec.wagered = 25_000; // Gold: 2 %
			rec.tier = 2;
			// yesterday: 10 000 wagered at roulette (edge 2.7 %) -> theoretical loss 270 -> cashback floor(0.02 × 270) = 5
			rec.ledger = new CashbackRules.DayLedger(today(server) - 1, 10_000, 20_000, 270);
			play(player, "roulette", 0, 0);
			helper.assertTrue(Economies.get().balance(player) == 5, "cashback paid on rollover, whatever the result: " + Economies.get().balance(player));
			helper.assertTrue(rec.ledger.day() == today(server), "ledger rolled");
		});
		helper.succeed();
	}

	@GameTest
	public void contractCompletesAndRerollCosts(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			VipData.Record rec = VipData.get(server).player(player.getUUID());
			List<ContractRules.Contract> list = new ArrayList<>();
			list.add(new ContractRules.Contract("wager", 10, 30, 0, false, false));
			list.add(new ContractRules.Contract("fish", 8, 30, 0, false, false));
			rec.contracts = new ContractRules.State(today(server), list);
			play(player, "slots", 10, 0);
			helper.assertTrue(list.get(0).done, "wager contract done");
			helper.assertTrue(Economies.get().balance(player) == 30, "reward credited: " + Economies.get().balance(player));
			helper.assertTrue(Contracts.reroll(player, 0) != null, "a finished contract cannot be rerolled");
			helper.assertTrue(Contracts.reroll(player, 1) == null, "reroll ok");
			helper.assertTrue(Economies.get().balance(player) == 20, "reroll cost 10");
			helper.assertTrue(rec.contracts.list.get(1).rerolled, "marked rerolled");
			helper.assertTrue(Contracts.reroll(player, 1) != null, "only one reroll per slot");
		});
		helper.succeed();
	}
}
