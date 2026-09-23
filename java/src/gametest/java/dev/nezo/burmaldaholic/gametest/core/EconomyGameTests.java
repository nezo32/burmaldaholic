package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.earnings.Earnings;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.HeartPenalties;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Economy, cashier, stakes and earnings basics in a real world (headless server). */
public class EconomyGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), 0, TEST);
			player.getInventory().clearContent();
			body.accept(player);
		} finally {
			server.getPlayerList().remove(player);
		}
	}

	@GameTest
	public void debitCreditAndCap(GameTestHelper helper) {
		withPlayer(helper, player -> {
			Economy eco = Economies.get();
			MinecraftServer server = helper.getLevel().getServer();
			eco.setBalance(server, player.getUUID(), 100, TEST);
			helper.assertFalse(eco.tryWithdraw(player, 150, TEST), "cannot overdraw");
			helper.assertTrue(eco.balance(player) == 100, "failed debit changes nothing");
			helper.assertTrue(eco.tryWithdraw(player, 60, TEST), "debit");
			helper.assertTrue(eco.balance(player) == 40, "balance after debit");
			long max = CasinoConfig.economy().maxBalance;
			eco.setBalance(server, player.getUUID(), max - 10, TEST);
			long credited = eco.deposit(player, 50, TEST);
			helper.assertTrue(credited == 10 && eco.balance(player) == max, "credit capped at economy.maxBalance");
		});
		helper.succeed();
	}

	@GameTest
	public void batchIsAtomic(GameTestHelper helper) {
		withPlayer(helper, a -> withPlayer(helper, b -> {
			Economy eco = Economies.get();
			MinecraftServer server = helper.getLevel().getServer();
			eco.setBalance(server, a.getUUID(), 100, TEST);
			Economy.TxResult tx = eco.batch(server).credit(AccountId.player(b.getUUID()), 500).debit(AccountId.player(a.getUUID()), 500).commit(TEST);
			helper.assertFalse(tx.ok(), "batch must fail");
			helper.assertTrue(eco.balance(a) == 100 && eco.balance(b) == 0, "no leg applied");
			helper.assertTrue(eco.transfer(server, AccountId.player(a.getUUID()), AccountId.player(b.getUUID()), 70, TEST).ok(), "transfer");
			helper.assertTrue(eco.balance(a) == 30 && eco.balance(b) == 70, "both legs applied");
		}));
		helper.succeed();
	}

	@GameTest
	public void cashierDepositWithdrawAndExchange(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CoreContent.CASHIER.block());
		CashierBlockEntity cashier = helper.getBlockEntity(pos, CashierBlockEntity.class);
		withPlayer(helper, player -> {
			player.getInventory().add(new ItemStack(Chips.item(100), 2));
			player.getInventory().add(new ItemStack(Chips.item(5), 3));
			helper.assertTrue(cashier.depositAll(player) == 215, "deposited all chip items");
			helper.assertTrue(Economies.get().balance(player) == 215, "balance after deposit");
			helper.assertTrue(player.getInventory().countItem(Chips.item(100)) == 0, "chips taken");

			helper.assertTrue(cashier.withdraw(player, 128, 0), "withdraw");
			helper.assertTrue(Economies.get().balance(player) == 87, "balance after withdraw");
			helper.assertTrue(player.getInventory().countItem(Chips.item(100)) == 1
				&& player.getInventory().countItem(Chips.item(25)) == 1
				&& player.getInventory().countItem(Chips.item(1)) == 3, "greedy denominations 100+25+1+1+1");
			helper.assertFalse(cashier.withdraw(player, 1000, 0), "cannot withdraw more than the balance");

			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			cashier.onAction(player, "buy", countTag(2));
			helper.assertTrue(Economies.get().balance(player) == 87 + 2 * 8, "bought 16 chips for 2 emeralds");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "emeralds taken");
			cashier.onAction(player, "sell", countTag(1));
			helper.assertTrue(Economies.get().balance(player) == 103 - 10, "sold 10 chips");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1, "got 1 emerald");
		});
		helper.succeed();
	}

	private static net.minecraft.nbt.CompoundTag countTag(int count) {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt("count", count);
		return tag;
	}

	@GameTest
	public void chipStakeSettlesAndFeedsStreak(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			Economies.get().setBalance(server, player.getUUID(), 100, TEST);
			StreakTracker.set(server, player.getUUID(), 0);
			Result<Stake> r = Stakes.chips(player, "gametest", 50, 1, 0);
			helper.assertTrue(r.isOk(), "stake accepted");
			helper.assertTrue(Economies.get().balance(player) == 50, "stake debited");
			Stakes.settle(player, r.value(), Stakes.Outcome.WIN, 50);
			helper.assertTrue(Economies.get().balance(player) == 150, "stake + winnings credited");
			helper.assertTrue(StreakTracker.get(server, player.getUUID()) == 1, "win streak +1");
			helper.assertFalse(Stakes.chips(player, "gametest", 5000, 1, 0).isOk(), "above tier-0 max bet (100)");
		});
		helper.succeed();
	}

	@GameTest
	public void heartWagerLossLowersMaxHealth(GameTestHelper helper) {
		withPlayer(helper, player -> {
			float before = player.getMaxHealth();
			helper.assertFalse(Stakes.hearts(player, "gametest", 2).isOk(), "2 hearts = 200 chips > tier-0 max bet");
			Result<Stake> r = Stakes.hearts(player, "gametest", 1);
			helper.assertTrue(r.isOk(), "heart stake accepted");
			Stakes.settle(player, r.value(), Stakes.Outcome.LOSS, 0);
			helper.assertTrue(player.getMaxHealth() == before - 2, "max health −1 heart");
			helper.assertTrue(HeartPenalties.activeHearts(player) == 1, "penalty stored");
			HeartPenalties.clear(player);
			helper.assertTrue(player.getMaxHealth() == before, "cleared");
		});
		helper.succeed();
	}

	@GameTest
	public void villagerTradePaysChips(GameTestHelper helper) {
		withPlayer(helper, player -> {
			Earnings.onTrade(player, new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BREAD), 10, 1, 0.05f));
			helper.assertTrue(Economies.get().balance(player) == 4, "1 + 3 emeralds");
		});
		helper.succeed();
	}
}
