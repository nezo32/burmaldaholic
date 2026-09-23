package dev.nezo.burmaldaholic.gametest.blackjack;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackDealer;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Blackjack table + dealer in a real world: full rounds, money conservation, disconnect auto-stand. */
public class BlackjackGameTests {
	private static final Transaction TEST = Transaction.of("blackjack", "gametest");

	@SuppressWarnings("removal")
	private static void withPlayers(GameTestHelper helper, BiConsumer<ServerPlayer, ServerPlayer> body) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 1000, TEST);
			Economies.get().setBalance(server, b.getUUID(), 1000, TEST);
			body.accept(a, b);
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
	}

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	/** Plays every pending decision of {@code player}: no insurance, keep even money off, stand. */
	private static void standOut(BlackjackTableBlockEntity table, ServerPlayer player) {
		for (int guard = 0; guard < 20; guard++) {
			BlackjackRound r = table.round();
			if (r == null || r.phase() == BlackjackRound.Phase.DONE) {
				return;
			}
			if (r.phase() == BlackjackRound.Phase.INSURANCE) {
				CompoundTag even = new CompoundTag();
				even.putBoolean("take", false);
				table.onAction(player, "insurance", amount(0));
				table.onAction(player, "even_money", even);
			} else {
				table.onAction(player, "stand", new CompoundTag());
			}
		}
	}

	private static BlackjackTableBlockEntity place(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BlackjackModule.TABLE.block());
		return helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
	}

	@GameTest
	public void singlePlayerRoundSettlesAndConservesChips(GameTestHelper helper) {
		BlackjackTableBlockEntity table = place(helper);
		withPlayers(helper, (a, b) -> {
			helper.assertTrue(table.sit(a), "seated");
			table.onAction(a, "bet", amount(10));
			BlackjackRound r = table.round();
			helper.assertTrue(r != null, "single player: the deal starts at once");
			helper.assertTrue(Economies.get().balance(a) <= 990, "bet debited");
			standOut(table, a);
			helper.assertTrue(r.phase() == BlackjackRound.Phase.DONE, "round finished");
			helper.assertTrue("result".equals(table.phase()), "result phase");
			helper.assertTrue(table.openStakes().isEmpty(), "every stake settled");
			long expected = 1000 - r.stakedOf(r.seats().getFirst().seat) + r.returnOf(r.seats().getFirst().seat);
			helper.assertTrue(Economies.get().balance(a) == expected, "balance = start − staked + return");
			table.onAction(a, "bet", amount(10));
			helper.assertTrue(table.stakeOf(a.getUUID()) == 0, "no betting during the result phase");
		});
		helper.succeed();
	}

	@GameTest
	public void betLimitsAreEnforced(GameTestHelper helper) {
		BlackjackTableBlockEntity table = place(helper);
		withPlayers(helper, (a, b) -> {
			table.onAction(a, "bet", amount(0));
			table.onAction(a, "bet", amount(5000));
			helper.assertTrue(table.stakeOf(a.getUUID()) == 0 && table.round() == null, "invalid bets rejected");
			table.onAction(a, "hit", new CompoundTag());
			helper.assertTrue(table.round() == null, "no action without a round");
		});
		helper.succeed();
	}

	@GameTest
	public void secondPlayerWaitsThenDisconnectAutoStands(GameTestHelper helper) {
		BlackjackTableBlockEntity table = place(helper);
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "bet", amount(20));
			helper.assertTrue(table.round() == null, "waits for the other seated player");
			helper.assertTrue(table.ticksLeft("bet") > 0, "bet timer started by the first bet");
			table.onAction(b, "bet", amount(30));
			BlackjackRound r = table.round();
			helper.assertTrue(r != null && r.seats().size() == 2, "everyone bet: dealt");
			// a disconnects mid-round: auto-stand, still settled
			table.leave(a.getUUID(), LeaveReason.DISCONNECT);
			standOut(table, b);
			helper.assertTrue(r.phase() == BlackjackRound.Phase.DONE, "round finished without a");
			helper.assertTrue(table.openStakes().isEmpty(), "both stakes settled, none refunded");
			for (ServerPlayer p : List.of(a, b)) {
				BlackjackRound.Seat s = r.seats().stream().filter(x -> x.player.equals(p.getUUID())).findFirst().orElseThrow();
				helper.assertTrue(Economies.get().balance(p) == 1000 - r.stakedOf(s.seat) + r.returnOf(s.seat), "settled correctly");
			}
		});
		helper.succeed();
	}

	@GameTest
	public void leavingBeforeTheDealRefunds(GameTestHelper helper) {
		BlackjackTableBlockEntity table = place(helper);
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "bet", amount(40));
			helper.assertTrue(Economies.get().balance(a) == 960, "debited");
			table.leave(a.getUUID(), LeaveReason.LEFT);
			helper.assertTrue(Economies.get().balance(a) == 1000, "refunded");
			helper.assertTrue(table.ticksLeft("bet") < 0, "bet timer cancelled");
		});
		helper.succeed();
	}

	@GameTest
	public void dealerFindsNearestTable(GameTestHelper helper) {
		BlackjackTableBlockEntity table = place(helper);
		BlackjackDealer dealer = helper.spawn(BlackjackModule.DEALER, new BlockPos(1, 2, 3));
		helper.assertTrue(dealer.nearestTable(helper.getLevel()) == table, "dealer serves the adjacent table");
		helper.succeed();
	}
}
