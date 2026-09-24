package dev.nezo.burmaldaholic.gametest.baccarat;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratAdvancements;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratDealer;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Baccarat tables in a real world (GAME_DESIGN §20): full shared coups with stacked cards, exact money
 * conservation (house pays exactly the paytable; chemin de fer moves chips between players minus the
 * rake), leave / disconnect / table break / server stop, and the chemin de fer bank rotation and Banco.
 */
public class BaccaratGameTests {
	private static final Transaction TEST = Transaction.of("baccarat", "gametest");
	private static final int S = Card.SPADES, H = Card.HEARTS, D = Card.DIAMONDS, C = Card.CLUBS;
	/** §20.3 vector 1: Player 8 (natural) beats Banker 6. */
	private static final List<Card> PLAYER_WINS = List.of(Card.of(8, S), Card.of(9, D), Card.of(13, H), Card.of(7, C));
	/** §20.3 vector 4: Banker 9 beats Player 7. */
	private static final List<Card> BANKER_WINS = List.of(Card.of(7, D), Card.of(2, S), Card.of(12, H), Card.of(3, H), Card.of(4, S));
	/** §20.3 vector 2: Tie 3–3. */
	private static final List<Card> TIE = List.of(Card.of(2, C), Card.of(13, D), Card.of(3, H), Card.of(3, S), Card.of(8, D));

	private interface Body {
		void run(ServerPlayer a, ServerPlayer b, ServerPlayer c);
	}

	@SuppressWarnings("removal")
	private static void withPlayers(GameTestHelper helper, Body body) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		ServerPlayer c = helper.makeMockServerPlayerInLevel();
		try {
			for (ServerPlayer p : List.of(a, b, c)) {
				Economies.get().setBalance(server, p.getUUID(), 1000, TEST);
			}
			body.run(a, b, c);
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
			server.getPlayerList().remove(c);
		}
	}

	private static BaccaratTableBlockEntity place(GameTestHelper helper, Block block) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, block);
		return helper.getBlockEntity(pos, BaccaratTableBlockEntity.class);
	}

	private static CompoundTag bet(BetKind box, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("box", box.id());
		t.putLong("amount", amount);
		return t;
	}

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	private static long bal(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	/** Runs the table's timers until {@code phase} (or gives up). */
	private static void runTo(GameTestHelper helper, BaccaratTableBlockEntity table, String phase) {
		for (int i = 0; i < 12 && !phase.equals(table.phase()); i++) {
			table.fastForwardForTests();
		}
		helper.assertTrue(phase.equals(table.phase()), "reached " + phase + " (is " + table.phase() + ")");
	}

	@GameTest
	public void sharedHouseCoupPaysThePaytableExactly(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.stackCardsForTests(BANKER_WINS);
			table.onAction(a, "bet", bet(BetKind.BANKER, 40));
			table.onAction(b, "bet", bet(BetKind.PLAYER, 50));
			table.onAction(b, "bet", bet(BetKind.TIE, 10));
			helper.assertTrue(bal(a) == 960 && bal(b) == 940, "bets debited when placed");
			helper.assertTrue(table.ticksLeft("bet") > 0, "the window starts with the first bet");
			table.onAction(a, "ready", new CompoundTag());
			helper.assertTrue("betting".equals(table.phase()), "still open: b is not ready");
			table.onAction(b, "ready", new CompoundTag());
			helper.assertTrue("no_more_bets".equals(table.phase()), "all bettors ready → no more bets");
			table.onAction(c, "bet", bet(BetKind.PLAYER, 10));
			helper.assertTrue(bal(c) == 1000, "no bets after the window closed");
			runTo(helper, table, "reveal");
			helper.assertTrue(table.openStakes().size() == 2, "the coup is drawn, stakes still open during the reveal");
			runTo(helper, table, "result");
			helper.assertTrue(table.openStakes().isEmpty(), "everybody settled");
			helper.assertTrue(bal(a) == 1038, "Banker 40 wins +38 (5 % commission exact)");
			helper.assertTrue(bal(b) == 940, "Player and Tie lose");
			helper.assertTrue(table.beads().size() == 1, "bead plate");
			runTo(helper, table, "betting");
			table.onAction(a, "rebet", new CompoundTag());
			helper.assertTrue(table.betsOf(a.getUUID()).getOrDefault(BetKind.BANKER, 0L) == 40, "rebet repeats the last coup");
		});
		helper.succeed();
	}

	@GameTest
	public void bankerStepAndLimits(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.onAction(a, "bet", bet(BetKind.BANKER, 30));
			helper.assertTrue(table.betsOf(a.getUUID()).get(BetKind.BANKER) == 20, "Banker snapped down to the step of 20");
			helper.assertTrue(bal(a) == 980, "only the snapped amount is debited");
			table.onAction(a, "bet", bet(BetKind.BANKER, 5));
			helper.assertTrue(table.betsOf(a.getUUID()).get(BetKind.BANKER) == 20, "an off-step add is refused");
			long max = table.limitsFor(a)[1];
			table.onAction(a, "bet", bet(BetKind.TIE, max / 4 + 1));
			helper.assertTrue(!table.betsOf(a.getUUID()).containsKey(BetKind.TIE), "Tie ≤ max × sideMaxFraction");
			table.onAction(a, "unbet", bet(BetKind.BANKER, 0));
			helper.assertTrue(bal(a) == 1000 && table.betsOf(a.getUUID()).isEmpty(), "removing the box returns it");
			table.onAction(a, "bet", bet(BetKind.PLAYER, 25));
			table.onAction(a, "clear", new CompoundTag());
			helper.assertTrue(bal(a) == 1000 && table.openStakes().isEmpty(), "Clear refunds everything");
		});
		helper.succeed();
	}

	@GameTest
	public void leaveRefundsDisconnectPlays(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.stackCardsForTests(PLAYER_WINS);
			table.onAction(a, "bet", bet(BetKind.PLAYER, 50));
			table.onAction(b, "bet", bet(BetKind.PLAYER, 30));
			table.leave(a.getUUID(), LeaveReason.LEFT);
			helper.assertTrue(bal(a) == 1000, "Leave during BETTING = Clear");
			table.leave(b.getUUID(), LeaveReason.DISCONNECT);
			helper.assertTrue(bal(b) == 970 && table.stakeOf(b.getUUID()) == 30, "a disconnected bettor's bets stay");
			runTo(helper, table, "result");
			helper.assertTrue(bal(b) == 1030, "played and credited (Player 1:1)");
			helper.assertTrue(table.openStakes().isEmpty(), "nothing open");
		});
		helper.succeed();
	}

	@GameTest
	public void breakBeforeTheDealRefundsAfterTheDealSettles(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.onAction(a, "bet", bet(BetKind.PLAYER, 50));
			table.onAction(a, "ready", new CompoundTag());
			helper.assertTrue("no_more_bets".equals(table.phase()), "closed");
			table.playOutNow("removed");
			helper.assertTrue(bal(a) == 1000 && table.openStakes().isEmpty(), "undrawn → refunded");

			table.stackCardsForTests(PLAYER_WINS);
			table.onAction(a, "bet", bet(BetKind.PLAYER, 50));
			table.onAction(b, "bet", bet(BetKind.BANKER, 40));
			table.onAction(a, "ready", new CompoundTag());
			table.onAction(b, "ready", new CompoundTag());
			runTo(helper, table, "reveal");
			table.playOutNow(CasinoTableBlockEntity.SERVER_STOPPING);
			helper.assertTrue(bal(a) == 1050 && bal(b) == 960, "drawn → settled at the stored coup, never refunded");
			helper.assertTrue(table.openStakes().isEmpty(), "nothing open");
		});
		helper.succeed();
	}

	@GameTest
	public void highRollerNeedsGoldVip(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.HIGH_ROLLER_TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			helper.assertTrue(table.isHighRoller(), "High-Roller block");
			table.onAction(a, "bet", bet(BetKind.PLAYER, 100));
			helper.assertTrue(bal(a) == 1000 && !table.isSeated(a), "tier 0 cannot play at a High-Roller table");
		});
		helper.succeed();
	}

	@GameTest
	public void chemmyBancoAndRakeConserveChips(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.CHEMMY_TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.sit(a);
			table.sit(b);
			table.sit(c);
			helper.assertTrue("bank_offer".equals(table.phase()), "the bank is offered to seat 1");
			table.onAction(b, "take_bank", amount(100));
			helper.assertTrue(!table.bank().held(), "only the candidate may take the bank");
			table.onAction(a, "take_bank", amount(200));
			helper.assertTrue(table.bank().held() && bal(a) == 800, "bank of 200 escrowed");
			table.onAction(b, "punt", amount(50));
			helper.assertTrue(bal(b) == 950, "punt escrowed");
			table.stackCardsForTests(PLAYER_WINS);
			table.onAction(c, "banco", new CompoundTag());
			helper.assertTrue(bal(b) == 1000, "Banco refunds the other punters");
			// coverage C = min(bank 200, the banker's max 100 at VIP tier 0)
			helper.assertTrue(bal(c) == 900 && "no_more_bets".equals(table.phase()), "Banco matches the whole coverage and closes betting");
			runTo(helper, table, "result");
			helper.assertTrue(bal(c) == 1100, "Player hand wins: the bank pays 1:1");
			helper.assertTrue(bal(a) + bal(b) + bal(c) + table.bank().bank() == 3000, "chips conserved (no rake when the bank loses)");
			runTo(helper, table, "bank_offer");
			helper.assertTrue(!table.bank().held() && bal(a) == 900, "a losing banker gets the rest of the bank back; the bank moves on");

			// seat 2 takes the bank, the bank wins → 5 % rake leaves the economy (house table)
			table.onAction(b, "take_bank", amount(100));
			table.onAction(a, "punt", amount(60));
			table.stackCardsForTests(BANKER_WINS);
			table.onAction(a, "ready", new CompoundTag());
			runTo(helper, table, "result");
			helper.assertTrue(table.bank().bank() == 157, "bank 100 + 60 − rake 3");
			helper.assertTrue(bal(a) == 840, "punter lost 60");
			runTo(helper, table, "bank_offer");
			table.onAction(b, "pass", new CompoundTag());
			helper.assertTrue(bal(b) == 1057, "passing returns the whole bank");
			helper.assertTrue(bal(a) + bal(b) + bal(c) + table.bank().bank() == 3000 - 3, "only the rake left the players");
		});
		helper.succeed();
	}

	@GameTest
	public void chemmyBankerLeavingReturnsEverything(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.CHEMMY_TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "take_bank", amount(300));
			table.onAction(b, "punt", amount(100));
			table.leave(a.getUUID(), LeaveReason.LEFT);
			helper.assertTrue(bal(a) == 1000 && bal(b) == 1000, "banker left before the deal: punts refunded, bank returned");
			helper.assertTrue("bank_offer".equals(table.phase()), "the next seat is offered the bank");

			table.onAction(b, "take_bank", amount(100));
			table.sit(c);
			table.onAction(c, "punt", amount(40));
			table.stackCardsForTests(TIE);
			table.onAction(c, "ready", new CompoundTag());
			runTo(helper, table, "reveal");
			table.playOutNow("removed");
			helper.assertTrue(bal(b) == 1000 && bal(c) == 1000, "a drawn tie is settled (push) and the bank returned on break");
			helper.assertTrue(!table.bank().held(), "no bank left on the broken table");
		});
		helper.succeed();
	}

	@GameTest
	public void chemmyAllPassPlaysAHouseCoup(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.CHEMMY_TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "pass", new CompoundTag());
			table.onAction(b, "pass", new CompoundTag());
			helper.assertTrue("betting".equals(table.phase()), "nobody banks → house coup");
			table.stackCardsForTests(BANKER_WINS);
			table.onAction(a, "bet", bet(BetKind.BANKER, 20));
			table.onAction(a, "ready", new CompoundTag());
			runTo(helper, table, "result");
			helper.assertTrue(bal(a) == 1019, "house coup paid by the house");
		});
		helper.succeed();
	}

	private static boolean has(ServerPlayer p, String id) {
		AdvancementHolder h = p.level().getServer().getAdvancements().get(BaccaratAdvancements.key(id));
		return h != null && p.getAdvancements().getOrStartProgress(h).isDone();
	}

	@GameTest
	public void advancementsLoadWithTheirParents(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<String, String> parents = Map.of("baccarat_natural", "core/beginners_luck", "tie_streak", "baccarat/baccarat_natural",
			"banco", "baccarat/baccarat_natural", "bank_holder", "baccarat/banco");
		parents.forEach((id, parent) -> {
			AdvancementHolder h = server.getAdvancements().get(BaccaratAdvancements.key(id));
			helper.assertTrue(h != null, id + " loaded");
			helper.assertTrue(h.value().parent().map(Identifier::getPath).orElse("").equals(parent), id + " parent " + parent);
		});
		helper.succeed();
	}

	@GameTest
	public void naturalNineAndTieStreak(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		withPlayers(helper, (a, b, c) -> {
			// Player natural 9 (9 + 10) beats Banker 5
			table.stackCardsForTests(List.of(Card.of(9, S), Card.of(2, D), Card.of(10, H), Card.of(3, C)));
			table.onAction(a, "bet", bet(BetKind.PLAYER, 10));
			table.onAction(b, "bet", bet(BetKind.TIE, 5));
			table.onAction(a, "ready", new CompoundTag());
			table.onAction(b, "ready", new CompoundTag());
			runTo(helper, table, "result");
			helper.assertTrue(has(a, "baccarat_natural") && !has(b, "baccarat_natural"), "natural 9 on the winning side");
			for (int i = 0; i < 2; i++) {
				runTo(helper, table, "betting");
				table.stackCardsForTests(TIE);
				table.onAction(b, "bet", bet(BetKind.TIE, 5));
				table.onAction(b, "ready", new CompoundTag());
				runTo(helper, table, "result");
			}
			helper.assertTrue(bal(b) == 1000 - 5 + 40 + 40, "two Tie wins at 8:1");
			helper.assertTrue(has(b, "tie_streak"), "Tie bets won on two consecutive coups");
		});
		helper.succeed();
	}

	@GameTest
	public void dealerFindsTheNearestTable(GameTestHelper helper) {
		BaccaratTableBlockEntity table = place(helper, BaccaratModule.TABLE.block());
		BaccaratDealer dealer = helper.spawn(BaccaratModule.DEALER, new BlockPos(1, 2, 3));
		helper.assertTrue(dealer.nearestTable(helper.getLevel()) == table, "dealer serves the adjacent table");
		helper.succeed();
	}
}
