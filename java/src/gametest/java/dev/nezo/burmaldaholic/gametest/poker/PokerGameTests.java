package dev.nezo.burmaldaholic.gametest.poker;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Poker table in a real world: buy-in escrow, stand-up refund, full hands against house bots. */
public class PokerGameTests {
	private static final Transaction TEST = Transaction.of("poker", "gametest");
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		player.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), player.getUUID(), balance, TEST);
		return player;
	}

	private static CompoundTag buyIn(String level, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("level", level);
		t.putLong("amount", amount);
		return t;
	}

	@GameTest
	public void buyInAndStandUpReturnsTheStack(GameTestHelper helper) {
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer p = player(helper, 1000);
		MinecraftServer server = helper.getLevel().getServer();
		try {
			long bb = CasinoConfig.poker().stakes.micro.bb;
			table.onAction(p, "buy_in", buyIn("micro", 10 * bb));
			helper.assertTrue(Economies.get().balance(p) == 1000, "buy-in below the minimum is refused");
			long amount = (long) CasinoConfig.poker().minBuyInBb * bb;
			table.onAction(p, "buy_in", buyIn("micro", amount));
			helper.assertTrue(Economies.get().balance(p) == 1000 - amount, "buy-in escrowed");
			CompoundTag state = table.writeClientState(p);
			helper.assertTrue(state.getBooleanOr("seated", false), "seated");
			helper.assertTrue("micro".equals(state.getStringOr("stake", "")), "stake level locked by the first player");
			table.onAction(p, "stand_up", new CompoundTag());
			helper.assertTrue(Economies.get().balance(p) == 1000, "stack returned on stand-up");
			helper.assertTrue(!table.writeClientState(p).getBooleanOr("seated", true), "no longer seated");
			table.onAction(p, "buy_in", buyIn("high", 20_000));
			helper.assertTrue(Economies.get().balance(p) == 1000, "High stakes need Diamond VIP");
		} finally {
			server.getPlayerList().remove(p);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 2400)
	public void handsRunAgainstBots(GameTestHelper helper) {
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer p = player(helper, 1000);
		MinecraftServer server = helper.getLevel().getServer();
		int thinkMin = CasinoConfig.poker().botThinkMinTicks;
		int thinkMax = CasinoConfig.poker().botThinkMaxTicks;
		CasinoConfig.poker().botThinkMinTicks = 0;
		CasinoConfig.poker().botThinkMaxTicks = 2;
		long bb = CasinoConfig.poker().stakes.micro.bb;
		long amount = (long) CasinoConfig.poker().maxBuyInBb * bb;
		table.onAction(p, "buy_in", buyIn("micro", amount));
		helper.assertTrue(Economies.get().balance(p) == 1000 - amount, "buy-in escrowed");
		helper.succeedWhen(() -> {
			// J-L3 thinking dots: only ever a seated bot (the one whose turn is being prepared)
			String thinking = table.botThinking();
			helper.assertTrue(thinking == null || table.occupants().stream().anyMatch(o -> o != null && o.isBot() && o.key().equals(thinking)),
				"botThinking names a seated bot: " + thinking);
			CompoundTag state = table.writeClientState(p);
			CompoundTag legal = state.getCompoundOrEmpty("legal");
			if (!legal.isEmpty()) {
				CompoundTag act = new CompoundTag();
				act.putString("kind", legal.getBooleanOr("can_check", false) ? "check" : "call");
				act.putInt("seq", legal.getIntOr("seq", 0));
				table.onAction(p, "act", act);
			}
			boolean busted = !state.getBooleanOr("seated", false);
			helper.assertTrue(busted || state.getListOrEmpty("table").size() >= 2, "bots joined");
			helper.assertTrue(busted || state.getIntOr("hand_no", 0) >= 4 && !state.getBooleanOr("live", true), "played a few hands");
			table.onAction(p, "stand_up", new CompoundTag());
			long balance = Economies.get().balance(p);
			CasinoConfig.poker().botThinkMinTicks = thinkMin;
			CasinoConfig.poker().botThinkMaxTicks = thinkMax;
			server.getPlayerList().remove(p);
			helper.assertTrue(balance >= 1000 - amount, "stack paid back: " + balance);
			helper.assertTrue(!table.writeClientState(p).getBooleanOr("seated", true), "stood up");
		});
	}
	private static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	/**
	 * Review B1: fold, stand up, buy in again, stand up — while the hand keeps running (a second human and bots
	 * are still in it). The re-buy is refused while the folded entry is still dealt in, and no cycle ever pays
	 * back more than was bought in: the player never ends above the starting total.
	 */
	@GameTest(maxTicks = 2400)
	public void foldStandRebuyCyclesConserveChips(GameTestHelper helper) {
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		MinecraftServer server = helper.getLevel().getServer();
		long bb = CasinoConfig.poker().stakes.micro.bb;
		long max = (long) CasinoConfig.poker().maxBuyInBb * bb;
		long min = (long) CasinoConfig.poker().minBuyInBb * bb;
		table.onAction(a, "buy_in", buyIn("micro", max));
		table.onAction(b, "buy_in", buyIn("micro", max));
		helper.assertTrue(balance(a) == 1000 - max && balance(b) == 1000 - max, "both bought in");
		helper.succeedWhen(() -> {
			CompoundTag legal = table.writeClientState(a).getCompoundOrEmpty("legal");
			helper.assertTrue(!legal.isEmpty(), "waiting for A's decision");
			CompoundTag fold = new CompoundTag();
			fold.putString("kind", "fold");
			fold.putInt("seq", legal.getIntOr("seq", 0));
			table.onAction(a, "act", fold);
			boolean live = table.writeClientState(a).getBooleanOr("live", false);
			table.onAction(a, "stand_up", new CompoundTag());
			long afterStand = balance(a);
			helper.assertTrue(afterStand <= 1000, "stand-up pays at most the hand stack: " + afterStand);
			for (int cycle = 0; cycle < 3; cycle++) {
				table.onAction(a, "buy_in", buyIn("micro", min));
				if (live) {
					helper.assertTrue(balance(a) == afterStand, "re-buy refused while still dealt into the running hand");
					helper.assertTrue(!table.writeClientState(a).getBooleanOr("seated", true), "not seated again");
				}
				table.onAction(a, "stand_up", new CompoundTag());
				helper.assertTrue(balance(a) <= afterStand, "cycle " + cycle + " minted chips: " + balance(a) + " > " + afterStand);
			}
			table.onAction(b, "stand_up", new CompoundTag());
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		});
	}

	/**
	 * Review M1: a table that stops mid-hand (chunk unload / server stop → {@code playOutNow}) plays the hand out
	 * and cashes everybody out; nothing is left in the block entity to be refunded on the next load.
	 */
	@GameTest(maxTicks = 1200)
	public void stoppedTableSettlesTheHandInsteadOfSavingARefund(GameTestHelper helper) {
		helper.setBlock(TABLE, PokerModule.TABLE.block());
		PokerTableBlockEntity table = helper.getBlockEntity(TABLE, PokerTableBlockEntity.class);
		ServerPlayer p = player(helper, 1000);
		MinecraftServer server = helper.getLevel().getServer();
		long amount = (long) CasinoConfig.poker().maxBuyInBb * CasinoConfig.poker().stakes.micro.bb;
		table.onAction(p, "buy_in", buyIn("micro", amount));
		helper.succeedWhen(() -> {
			helper.assertTrue(table.writeClientState(p).getBooleanOr("live", false), "waiting for a live hand");
			helper.assertTrue(table.playOutNow("gametest"), "something was in play");
			helper.assertTrue(!table.writeClientState(p).getBooleanOr("seated", true), "cashed out");
			CompoundTag saved = table.saveWithoutMetadata(helper.getLevel().registryAccess());
			helper.assertFalse(saved.contains("burmaldaholic_poker_refunds"), "no start-of-hand refund saved: " + saved);
			helper.assertTrue(balance(p) <= 2 * 1000 && balance(p) >= 1000 - amount, "stack paid: " + balance(p));
			server.getPlayerList().remove(p);
		});
	}
}
