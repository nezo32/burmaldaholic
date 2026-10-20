package dev.nezo.burmaldaholic.gametest.comps;

import dev.nezo.burmaldaholic.comps.CompsModule;
import dev.nezo.burmaldaholic.comps.CompsService;
import dev.nezo.burmaldaholic.comps.FreeBets;
import dev.nezo.burmaldaholic.comps.logic.CompsRules;
import dev.nezo.burmaldaholic.comps.net.CompsActionPayload;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.vip.VipData;
import dev.nezo.burmaldaholic.vip.VipService;
import dev.nezo.burmaldaholic.vip.logic.CashbackRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * EC-2 comps in a real world (headless server): token stakes, comp-point accrual, the cashback toast at the day roll and
 * the daily bonus — through the same entry points the payload receivers use ({@code CompsModule.handle},
 * {@code CoinFlipGame.action}, {@code PLAY_RESOLVED}).
 */
public class CompsGameTests {
	private static final Transaction TEST = Transaction.of("comps", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), 0, TEST);
			body.accept(player);
		} finally {
			CounterSessions.close(player);
			server.getPlayerList().remove(player);
		}
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static long today(MinecraftServer server) {
		return now(server) / 24000L;
	}

	private static void holdToken(ServerPlayer player, long value) {
		long expires = now(player.level().getServer()) + CompsRules.TOKEN_LIFETIME_TICKS;
		player.setItemInHand(InteractionHand.MAIN_HAND, FreeBets.create(player.getUUID(), value, expires));
	}

	private static int tokens(ServerPlayer player) {
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(FreeBets.TOKEN)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@GameTest
	public void tokenCoinFlipPaysWinningsOnlyAndNoVipCredit(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			holdToken(player, 50);
			CompoundTag args = new CompoundTag();
			args.putString("side", "heads");
			args.putLong("amount", 999); // the bet field is ignored with a token in hand
			CoinFlipGame.action(player, "flip", args);
			long balance = Economies.get().balance(player);
			helper.assertTrue(tokens(player) == 0, "the token was used");
			helper.assertTrue(balance == 0 || balance == 48, "win pays floor(50 × 0.96) = 48 winnings only, loss pays 0: " + balance);
			helper.assertTrue(VipService.wagered(server, player.getUUID()) == 0, "a token round gives no VIP credit");
			helper.assertTrue(CompsService.state(player).pointsMilli() == 0, "a token round gives no comp points");
		});
		helper.succeed();
	}

	@GameTest
	public void tokenPushReturnsTheToken(GameTestHelper helper) {
		withPlayer(helper, player -> {
			holdToken(player, 10);
			Result<Stake> r = ExtrasGames.takeStake(player, ExtrasGames.COIN, new CompoundTag(), 0, true);
			helper.assertTrue(r.isOk() && r.value().kind() == Stake.Kind.TOKEN && r.value().value() == 10, "token stake taken");
			helper.assertTrue(tokens(player) == 0, "token in escrow");
			Stakes.settle(player, r.value(), Stakes.Outcome.PUSH, 0);
			helper.assertTrue(tokens(player) == 1, "push gives the token back");
			helper.assertTrue(Economies.get().balance(player) == 0, "no chips on a push");
			// someone else's token is refused
			ServerPlayer other = player;
			other.setItemInHand(InteractionHand.MAIN_HAND, FreeBets.create(java.util.UUID.randomUUID(), 10, now(player.level().getServer()) + 1000));
			Result<Stake> chips = ExtrasGames.takeStake(player, ExtrasGames.COIN, new CompoundTag(), 0, true);
			helper.assertTrue(!chips.isOk() || chips.value().kind() == Stake.Kind.CHIPS, "a token bound to another player is not a stake");
		});
		helper.succeed();
	}

	@GameTest
	public void rouletteAccruesPointsPvpAndOwnedDoNot(GameTestHelper helper) {
		withPlayer(helper, player -> {
			// Bronze 10 %: 1000 chips at roulette (2.7 %) -> theo 27 -> 2.7 points
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, PlayResult.of("roulette", 1000, 0).withEdge(0.027));
			helper.assertTrue(CompsService.state(player).pointsMilli() == 2700, "roulette accrues: " + CompsService.state(player).pointsMilli());
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, PlayResult.of("pvp", 1000, 0).pvp());
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player,
				PlayResult.of("roulette", 1000, 0).withEdge(0.027).withTable(helper.getLevel(), BlockPos.ZERO, "owner_bank"));
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, PlayResult.of("roulette", 1000, 0).withEdge(0.027).withKind(Stake.Kind.TOKEN));
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, PlayResult.of("roulette", 1000, 0).withEdge(0.027).withKind(Stake.Kind.ITEM));
			helper.assertTrue(CompsService.state(player).pointsMilli() == 2700, "PvP, owned casino, token and pawn rounds accrue 0");
		});
		helper.succeed();
	}

	@GameTest
	public void dayRollSendsTheCashbackToast(GameTestHelper helper) {
		List<String> toasts = new ArrayList<>();
		ServerFx previous = ServerFx.get();
		ServerFx.set(new ServerFx() {
			@Override
			public void celebrate(ServerPlayer player, Celebration c) {
				previous.celebrate(player, c);
			}

			@Override
			public void event(ServerPlayer player, Kind kind, String game, int value) {
				previous.event(player, kind, game, value);
			}

			@Override
			public void toast(ServerPlayer player, String titleKey, Component body, ToastIcon icon) {
				toasts.add(titleKey + "|" + icon);
			}
		});
		try {
			withPlayer(helper, player -> {
				MinecraftServer server = helper.getLevel().getServer();
				VipData.Record rec = VipData.get(server).player(player.getUUID());
				rec.wagered = 25_000; // Gold: 2 %
				rec.tier = 2;
				rec.ledger = new CashbackRules.DayLedger(today(server) - 1, 10_000, 0, 270); // theo 270 -> 5 cashback
				CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, PlayResult.of("roulette", 0, 0));
				helper.assertTrue(Economies.get().balance(player) == 5, "cashback paid");
				helper.assertTrue(toasts.contains("toast.burmaldaholic.cashback.title|CASHBACK"), "cashback toast sent: " + toasts);
			});
		} finally {
			ServerFx.set(previous);
		}
		helper.succeed();
	}

	@GameTest
	public void dailyBonusPaysOnceAndNeedsASession(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			VipService.setWagered(player, 1000);
			CompsRules.State s = CompsService.state(player);
			CompsService.put(player, s.active(CompsService.cfg().daily.minActiveMinutes * 1200L));
			CompsActionPayload claim = new CompsActionPayload(CompsActionPayload.CLAIM_DAILY, "", 1);
			CompsModule.handle(player, claim);
			helper.assertTrue(Economies.get().balance(player) == 0, "no counter session: refused");
			CounterSessions.open(player, CounterSource.command());
			CounterSessions.resetCooldown(player);
			CompsModule.handle(player, claim);
			CompsModule.handle(player, claim); // same tick: dropped by the shared cooldown
			long tierAmount = CompsRules.dailyAmount(CompsRules.BRONZE, CompsService.cfg().daily.amounts());
			helper.assertTrue(Economies.get().balance(player) == tierAmount, "paid once: " + Economies.get().balance(player));
			CounterSessions.resetCooldown(player);
			CompsModule.handle(player, claim);
			helper.assertTrue(Economies.get().balance(player) == tierAmount, "a second claim the same day pays nothing");
			helper.assertTrue(CompsService.state(player).dailyClaimDay() == today(server), "claim day stored");
		});
		helper.succeed();
	}

	@GameTest
	public void redeemTokenSpendsPointsAndRespectsTheLimit(GameTestHelper helper) {
		withPlayer(helper, player -> {
			CompsService.put(player, CompsService.state(player).earn(200_000)); // 200 points
			CounterSessions.open(player, CounterSource.command());
			for (int i = 0; i < 6; i++) {
				CounterSessions.resetCooldown(player);
				CompsModule.handle(player, new CompsActionPayload(CompsActionPayload.REDEEM, "token_10", 1));
			}
			helper.assertTrue(tokens(player) == 5, "L5 tokens a day: " + tokens(player));
			helper.assertTrue(CompsService.state(player).pointsMilli() == 200_000 - 5 * 15_000, "5 × 15 points spent");
			CounterSessions.resetCooldown(player);
			CompsModule.handle(player, new CompsActionPayload(CompsActionPayload.REDEEM, "feast", 1));
			helper.assertTrue(CompsService.state(player).pointsMilli() == 125_000, "feast is Gold+: refused at Bronze");
		});
		helper.succeed();
	}
}
