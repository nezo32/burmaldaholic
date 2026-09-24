package dev.nezo.burmaldaholic.gametest.craps;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.logic.Bet;
import dev.nezo.burmaldaholic.games.craps.logic.BetKind;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Craps table money flow in a real world: bets, odds, point made, leaving mid-point, table broken. */
public class CrapsGameTests {
	private static final Transaction TEST = Transaction.of("craps", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayer(GameTestHelper helper, long balance, Consumer<ServerPlayer> body) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Economies.get().setBalance(server, player.getUUID(), balance, TEST);
			body.accept(player);
		} finally {
			server.getPlayerList().remove(player);
		}
	}

	private static CrapsTableBlockEntity table(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CrapsModule.TABLE.block());
		return helper.getBlockEntity(pos, CrapsTableBlockEntity.class);
	}

	private static CompoundTag bet(BetKind kind, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("kind", kind.id());
		t.putLong("amount", amount);
		return t;
	}

	private static CompoundTag odds(int betId, long amount) {
		CompoundTag t = new CompoundTag();
		t.putInt("bet", betId);
		t.putLong("amount", amount);
		return t;
	}

	/**
	 * Lane J-L6 (J-T, tables.md §2.7): the update tag carries the roll for the in-world dice, the state carries the
	 * throw seed / time and {@code last_res} with the server's returns, the money settles at once, and the next betting
	 * window only opens after the reveal delay.
	 */
	@GameTest
	public void rollSyncAndRevealDelay(GameTestHelper helper) {
		CrapsTableBlockEntity table = table(helper);
		withPlayer(helper, 100, player -> {
			table.onAction(player, "bet", bet(BetKind.PASS, 10));
			table.roll(5, 6); // natural: pass wins
			helper.assertTrue(Economies.get().balance(player) == 110, "money settles at the roll, not after the animation");
			var sync = dev.nezo.burmaldaholic.games.craps.logic.CrapsSync.decode(
				table.getUpdateTag(helper.getLevel().registryAccess()).getIntArray("craps_sync").orElseThrow());
			helper.assertTrue(sync.d1() == 5 && sync.d2() == 6 && sync.rolls() == 1, "update tag has the dice: " + sync);
			CompoundTag state = table.writeClientState(player);
			helper.assertTrue(state.getLongOr("roll_time", -1) >= 0 && state.contains("seed"), "throw time + seed");
			var res = state.getListOrEmpty("last_res");
			helper.assertTrue(res.size() == 1 && "win".equals(res.getCompoundOrEmpty(0).getStringOr("outcome", ""))
				&& res.getCompoundOrEmpty(0).getLongOr("ret", 0) == 20, "last_res with the server return: " + res);
			helper.assertTrue(table.windowEnd() >= state.getLongOr("roll_time", 0) + dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats.REVEAL_DELAY_TICKS,
				"the next window opens after the reveal");
		});
		helper.succeed();
	}

	@GameTest
	public void passLineWithOddsPaysTrueOdds(GameTestHelper helper) {
		CrapsTableBlockEntity table = table(helper);
		withPlayer(helper, 100, player -> {
			table.onAction(player, "bet", bet(BetKind.PASS, 10));
			helper.assertTrue(Economies.get().balance(player) == 90, "flat bet debited");
			helper.assertTrue(table.isSeated(player), "betting seats the player");
			helper.assertTrue(player.getUUID().equals(table.table().shooter()), "lone player shoots");
			table.onAction(player, "bet", bet(BetKind.COME, 10));
			helper.assertTrue(Economies.get().balance(player) == 90, "no Come bet on the come-out");
			table.roll(3, 3);
			helper.assertTrue(table.table().point() == 6, "point 6");
			Bet pass = table.table().betsOf(player.getUUID()).get(0);
			table.onAction(player, "odds", odds(pass.id(), 7));
			helper.assertTrue(Economies.get().balance(player) == 90, "odds on 6 must be a multiple of 5");
			table.onAction(player, "odds", odds(pass.id(), 55));
			helper.assertTrue(Economies.get().balance(player) == 90, "odds over 5x refused");
			table.onAction(player, "odds", odds(pass.id(), 20));
			helper.assertTrue(Economies.get().balance(player) == 70 && table.escrowed() == 30, "odds debited and escrowed");
			table.onAction(player, "bet", bet(BetKind.PASS, 10));
			helper.assertTrue(Economies.get().balance(player) == 70, "no Pass bet while the point is on");
			table.roll(4, 2);
			helper.assertTrue(Economies.get().balance(player) == 70 + 20 + 20 + 24, "flat 1:1 + odds 6:5");
			helper.assertTrue(table.escrowed() == 0 && table.table().comeOut(), "settled, puck off");
		});
		helper.succeed();
	}

	@GameTest
	public void fieldAndDontPassBar12(GameTestHelper helper) {
		CrapsTableBlockEntity table = table(helper);
		withPlayer(helper, 100, player -> {
			table.onAction(player, "bet", bet(BetKind.DONT_PASS, 10));
			table.onAction(player, "bet", bet(BetKind.FIELD, 10));
			helper.assertTrue(Economies.get().balance(player) == 80, "two bets");
			table.roll(6, 6);
			helper.assertTrue(Economies.get().balance(player) == 80 + 10 + 40, "bar 12 push + field 3:1");
		});
		helper.succeed();
	}

	@GameTest
	public void leavingMidPointPlaysTheBetsOut(GameTestHelper helper) {
		CrapsTableBlockEntity table = table(helper);
		withPlayer(helper, 100, player -> {
			table.onAction(player, "bet", bet(BetKind.PASS, 10));
			table.roll(2, 2);
			table.leave(player.getUUID(), LeaveReason.LEFT);
			long balance = Economies.get().balance(player);
			helper.assertTrue(table.escrowed() == 0, "nothing left on the table");
			helper.assertTrue(balance == 90 || balance == 110, "pass bet resolved as a win or a loss");
			helper.assertTrue(!table.isSeated(player), "left the seat");
		});
		helper.succeed();
	}

	@GameTest
	public void breakingTheTablePlaysTheBetsOut(GameTestHelper helper) {
		CrapsTableBlockEntity table = table(helper);
		withPlayer(helper, 100, player -> {
			table.onAction(player, "bet", bet(BetKind.PASS, 10));
			table.roll(5, 5);
			table.onAction(player, "odds", odds(table.table().betsOf(player.getUUID()).get(0).id(), 30));
			helper.assertTrue(Economies.get().balance(player) == 60, "flat + odds debited");
			helper.destroyBlock(new BlockPos(1, 1, 1));
			long balance = Economies.get().balance(player);
			// review B1: a Pass bet on 10 is never refunded by breaking the table; it is rolled out (win: 20 + 90)
			helper.assertTrue(balance == 60 || balance == 170, "pass + odds resolved as a loss or a win, got " + balance);
		});
		helper.succeed();
	}
}
