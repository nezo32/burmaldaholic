package dev.nezo.burmaldaholic.gametest.loan;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.LoanService;
import dev.nezo.burmaldaholic.loan.entity.CollectorEntity;
import dev.nezo.burmaldaholic.loan.entity.EnforcerEntity;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRules;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Loans end to end on a headless server: take/repay, default + garnishment, Asset Freeze, entities. */
public class LoanGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), 0, TEST);
			body.accept(player);
		} finally {
			LoanService.adminSet(server, player.getUUID(), 0);
			server.getPlayerList().remove(player);
		}
	}

	@GameTest
	public void takeAndRepayOnTime(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			Economy eco = Economies.get();
			long expectedDue = LoanRules.due(500, LoanService.rateFor(server, player.getUUID()));
			helper.assertTrue(LoanService.take(player, 1) == null, "Rent loan is available at Bronze");
			helper.assertTrue(eco.balance(player) == 500, "principal credited");
			LoanRecord rec = LoanService.record(server, player.getUUID());
			helper.assertTrue(rec.status == LoanRecord.Status.ACTIVE && rec.owed == expectedDue, "owes due = ceil(500 × (1 + rate))");
			helper.assertTrue(LoanService.take(player, 0) != null, "one loan at a time");
			helper.assertTrue(CoreServices.debt().owed(server, player.getUUID()) == expectedDue, "core sees the debt");
			helper.assertTrue(CoreServices.debt().ticksToDeadline(server, player.getUUID()) > 0, "deadline countdown");
			helper.assertTrue(LoanService.pay(player, 100, false) == 100, "partial payment");
			eco.setBalance(server, player.getUUID(), 10_000, TEST);
			LoanService.pay(player, 1_000_000, false);
			rec = LoanService.record(server, player.getUUID());
			helper.assertTrue(rec.status == LoanRecord.Status.NONE && rec.goodStanding == 1, "repaid on time: good standing + 1");
			helper.assertTrue(eco.balance(player) == 10_000 - (expectedDue - 100), "paid exactly what was owed");
		});
		helper.succeed();
	}

	@GameTest
	public void defaultGarnishesWinnings(GameTestHelper helper) {
		withPlayer(helper, player -> {
			MinecraftServer server = helper.getLevel().getServer();
			Economy eco = Economies.get();
			LoanService.adminSet(server, player.getUUID(), 700);
			LoanService.adminDefault(server, player.getUUID());
			LoanRecord rec = LoanService.record(server, player.getUUID());
			helper.assertTrue(rec.status == LoanRecord.Status.DEFAULT, "forced default");
			long owedBefore = rec.owed;
			long balanceBefore = eco.balance(player);
			eco.deposit(player, 100, Transaction.payout("slots"));
			long taken = owedBefore - LoanService.record(server, player.getUUID()).owed;
			int pct = LoanService.collectorsMode(server) ? CasinoConfig.loan().garnishPercent : 100;
			helper.assertTrue(taken == LoanRules.garnishAmount(100, pct, owedBefore), "garnished share of a payout");
			helper.assertTrue(eco.balance(player) - balanceBefore == 100 - taken, "the rest reached the balance");
			// refunds are never garnished
			long owed = LoanService.record(server, player.getUUID()).owed;
			eco.deposit(player, 50, Transaction.refund("slots"));
			helper.assertTrue(LoanService.record(server, player.getUUID()).owed == owed, "refund not garnished");
			helper.assertTrue(CoreServices.debt().inDefault(server, player.getUUID()), "core sees the default");
		});
		helper.succeed();
	}

	@GameTest
	public void assetFreezeBlocksWagers(GameTestHelper helper) {
		boolean before = CasinoConfig.loan().collectors.enabled;
		try {
			CasinoConfig.loan().collectors.enabled = false; // Asset Freeze on every difficulty
			withPlayer(helper, player -> {
				MinecraftServer server = helper.getLevel().getServer();
				Economies.get().setBalance(server, player.getUUID(), 1000, TEST);
				helper.assertTrue(BetLimits.validate(player, 10, 1, 0) == null, "can bet before the default");
				Economies.get().setBalance(server, player.getUUID(), 100, TEST);
				LoanService.adminSet(server, player.getUUID(), 400);
				LoanService.adminDefault(server, player.getUUID());
				helper.assertTrue(LoanService.frozen(server, player.getUUID()), "frozen");
				helper.assertTrue(BetLimits.validate(player, 10, 1, 0) != null, "no wagering while frozen");
				long owed = LoanService.record(server, player.getUUID()).owed;
				helper.assertTrue(owed == 350, "seizure at DEFAULT: half the balance went to the debt");
			});
		} finally {
			CasinoConfig.loan().collectors.enabled = before;
		}
		helper.succeed();
	}

	@GameTest
	public void entitiesHaveSpecStats(GameTestHelper helper) {
		LoanSharkEntity shark = helper.spawn(LoanContent.LOAN_SHARK, new BlockPos(1, 2, 1));
		helper.assertTrue(shark.getMaxHealth() == 40, "shark 40 HP");
		helper.assertFalse(shark.canBeLeashed(), "shark can't be leashed");
		CollectorEntity collector = helper.spawn(LoanContent.DEBT_COLLECTOR, new BlockPos(2, 2, 2));
		helper.assertTrue(collector.getMaxHealth() == 24, "collector 24 HP");
		helper.assertFalse(collector.canJoinRaid(), "never a raid member");
		helper.assertFalse(collector.canPickUpLoot(), "never picks anything up");
		EnforcerEntity enforcer = helper.spawn(LoanContent.ENFORCER, new BlockPos(3, 2, 3));
		helper.assertTrue(enforcer.getMaxHealth() == 80, "enforcer 80 HP");
		helper.assertTrue(enforcer.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0.75, "enforcer knockback resistance");
		helper.assertTrue(collector.isAlliedTo(enforcer), "free units don't fight each other");
		helper.assertTrue("burmaldaholic.debt_collection".equals(collector.createDamageSource().type().msgId()), "melee kills read 'was collected'");
		helper.succeed();
	}
}
