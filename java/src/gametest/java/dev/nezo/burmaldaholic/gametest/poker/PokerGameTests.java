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
}
