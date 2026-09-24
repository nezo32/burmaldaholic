package dev.nezo.burmaldaholic.gametest.uth;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity.LeaveReason;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.UthConfig;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.games.uth.logic.BankRules;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Ultimate Texas Hold'em tables in a real world (GAME_DESIGN §21): full rounds with stacked decks, money
 * conservation, timeouts and safe defaults, leave / disconnect / table break / server stop, limits, and the
 * player-banked dealer seat with its rake.
 */
public class UthGameTests {
	private static final Transaction TEST = Transaction.of("uth", "gametest");
	private static final long START = 1000;

	@SuppressWarnings("removal")
	private static void withPlayers(GameTestHelper helper, BiConsumer<ServerPlayer, ServerPlayer> body) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		ServerPlayer b = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), START, TEST);
			Economies.get().setBalance(server, b.getUUID(), START, TEST);
			body.accept(a, b);
		} finally {
			server.getPlayerList().remove(a);
			server.getPlayerList().remove(b);
		}
	}

	private static UthTableBlockEntity place(GameTestHelper helper, Block block) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, block);
		return helper.getBlockEntity(pos, UthTableBlockEntity.class);
	}

	private static CompoundTag bet(long ante, long trips) {
		CompoundTag t = new CompoundTag();
		t.putLong("ante", ante);
		t.putLong("trips", trips);
		return t;
	}

	private static long bal(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	/** Deck whose deal gives the seats (table order) these holes, then the dealer and the board. */
	private static int[] deck(List<String> holes, String dealer, String board) {
		int n = holes.size();
		int[][] h = new int[n][];
		for (int i = 0; i < n; i++) {
			h[i] = UthCards.parseAll(holes.get(i));
		}
		int[] d = UthCards.parseAll(dealer);
		int[] bd = UthCards.parseAll(board);
		int[] out = new int[52];
		boolean[] used = new boolean[52];
		int k = 0;
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < n; i++) {
				out[k++] = h[i][pass];
			}
			out[k++] = d[pass];
		}
		for (int c : bd) {
			out[k++] = c;
		}
		for (int i = 0; i < k; i++) {
			used[out[i]] = true;
		}
		for (int c = 0; c < 52; c++) {
			if (!used[c]) {
				out[k++] = c;
			}
		}
		return out;
	}

	/** Runs the table's timers until it is back in BETTING (the round is settled and shown). */
	private static void finish(UthTableBlockEntity table) {
		for (int i = 0; i < 20 && table.round() != null; i++) {
			table.fastForwardForTests(1);
		}
	}

	@GameTest
	public void singleSeatRoundSettlesExactly(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			helper.assertTrue(table.sit(a), "seated");
			// §21.1 vector 2: 9h9c vs KdKc, board 9d 5s 2h Jc 3d; Ante 10, Trips 10, Bet ×4 → +80
			table.stackDeckForTests(deck(List.of("9h 9c"), "Kd Kc", "9d 5s 2h Jc 3d"));
			table.onAction(a, "bet", bet(10, 10));
			helper.assertTrue(table.round() != null, "single player: dealt at once");
			helper.assertTrue(bal(a) == START - 30, "Ante + Blind + Trips debited");
			table.onAction(a, "bet_4x", new CompoundTag());
			helper.assertTrue(bal(a) == START - 70, "Play ×4 debited");
			finish(table);
			helper.assertTrue(table.openStakes().isEmpty(), "settled");
			helper.assertTrue(bal(a) == START + 80, "vector 2 pays +80, got " + (bal(a) - START));
			helper.assertTrue(table.escrowTotal() == 0, "nothing held");
		});
		helper.succeed();
	}

	@GameTest
	public void timeoutsCheckAndRiverAutoPlaysAStraight(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			// a: 7c 2d makes a straight 3-7 on the river; b: Qh Jh nothing. Dealer Kd Kc qualifies.
			table.stackDeckForTests(deck(List.of("7c 2d", "Qh Jh"), "Kd Kc", "3s 4h 5d 6c 8s"));
			table.onAction(a, "bet", bet(10, 0));
			helper.assertTrue(table.round() == null && table.ticksLeft("bet") > 0, "waits for b");
			table.onAction(b, "bet", bet(10, 0));
			UthRound r = table.round();
			helper.assertTrue(r != null && r.seats().size() == 2, "both dealt");
			// nobody acts: preflop timeout → check (no chips put at risk)
			table.fastForwardForTests(1);
			helper.assertTrue(r.seat(a.getUUID()).last != null && r.seat(a.getUUID()).playMultiple == 0, "preflop timeout checks");
			helper.assertTrue(bal(a) == START - 20 && bal(b) == START - 20, "no Play bet on a timeout check");
			finish(table);
			UthRound.Seat sa = r.seat(a.getUUID());
			UthRound.Seat sb = r.seat(b.getUUID());
			helper.assertTrue(sa.playMultiple == 1, "straight at the river auto-plays ×1");
			helper.assertTrue(sb.folded, "no made hand: river timeout folds");
			// a: straight beats kings → Play +10, Ante +10, Blind 1:1 +10 = +30; b: −20
			helper.assertTrue(bal(a) == START + 30, "a +30, got " + (bal(a) - START));
			helper.assertTrue(bal(b) == START - 20, "b −20, got " + (bal(b) - START));
			helper.assertTrue(table.openStakes().isEmpty(), "all settled");
		});
		helper.succeed();
	}

	@GameTest
	public void leaveAfterDealAppliesDefaultsAndSettles(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			table.stackDeckForTests(deck(List.of("Ah Kh", "2c 7d"), "Qc Qd", "As 9h 4h 3c Ts"));
			table.onAction(a, "bet", bet(10, 0));
			table.onAction(b, "bet", bet(10, 5));
			UthRound r = table.round();
			helper.assertTrue(r != null, "dealt");
			table.onAction(a, "bet_4x", new CompoundTag());
			table.leave(b.getUUID(), LeaveReason.LEFT); // b walks away: check, check, river fold (no straight)
			helper.assertTrue(!table.isSeated(b), "b left");
			finish(table);
			UthRound.Seat sb = r.seat(b.getUUID());
			helper.assertTrue(sb.folded, "b folded by default at the river");
			helper.assertTrue(bal(b) == START - 25, "b loses Ante, Blind and Trips, got " + (bal(b) - START));
			// a: pair of aces beats queens → Play +40, Ante +10, Blind push = +50
			helper.assertTrue(bal(a) == START + 50, "a +50, got " + (bal(a) - START));
			helper.assertTrue(table.openStakes().isEmpty(), "nothing open, nothing refunded");
		});
		helper.succeed();
	}

	@GameTest
	public void leavingBeforeTheDealRefundsDisconnectKeepsTheBet(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "bet", bet(10, 5));
			helper.assertTrue(bal(a) == START - 25, "debited");
			table.leave(a.getUUID(), LeaveReason.LEFT);
			helper.assertTrue(bal(a) == START, "Leave before the deal refunds");
			helper.assertTrue(table.ticksLeft("bet") < 0, "bet timer cancelled");
			table.sit(a);
			table.onAction(a, "bet", bet(10, 0));
			table.leave(a.getUUID(), LeaveReason.DISCONNECT);
			helper.assertTrue(bal(a) == START - 20, "a disconnect keeps the bet in play (§21.5)");
			helper.assertTrue(table.confirmedBet(a.getUUID()).isPresent(), "still confirmed");
			// b confirms → deal; a plays with default actions
			table.onAction(b, "bet", bet(10, 0));
			helper.assertTrue(table.round() != null, "dealt with the absent player");
			finish(table);
			helper.assertTrue(table.openStakes().isEmpty(), "both settled");
			helper.assertTrue(table.escrowTotal() == 0, "no direct Play bet left behind");
		});
		helper.succeed();
	}

	@GameTest
	public void brokenTablePlaysADealtRoundOut(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			// a: royal flush on the river; bet ×4 preflop, Trips 5: dealer does not qualify → +40 + 0 + 5 000 + 250
			table.stackDeckForTests(deck(List.of("As Ks"), "7d 2c", "Qs Js Ts 3h 4d"));
			table.onAction(a, "bet", bet(10, 5));
			table.onAction(a, "bet_4x", new CompoundTag());
			helper.assertTrue(table.playOutNow("removed"), "something was in play");
			helper.assertTrue(table.round() == null && table.openStakes().isEmpty(), "played out, nothing open");
			helper.assertTrue(bal(a) == START + 5290, "vector 1: +5 290, got " + (bal(a) - START));
		});
		helper.succeed();
	}

	@GameTest
	public void serverStopRefundsUndrawnBets(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			table.onAction(a, "bet", bet(10, 5));
			helper.assertTrue(table.round() == null, "not dealt (b has not bet)");
			table.playOutNow(UthTableBlockEntity.SERVER_STOPPING);
			helper.assertTrue(bal(a) == START && bal(b) == START, "undrawn bets are returned");
			helper.assertTrue(table.openStakes().isEmpty(), "nothing open");
		});
		helper.succeed();
	}

	@GameTest
	public void limitsApplyToSixAntesPlusTrips(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.TABLE.block());
		withPlayers(helper, (a, b) -> {
			table.sit(a);
			table.sit(b);
			long maxW = table.limitsFor(a)[1];
			long ante = maxW / 6 + 1;
			table.onAction(a, "bet", bet(ante, 0));
			helper.assertTrue(table.confirmedBet(a.getUUID()).isEmpty() && bal(a) == START, "6 × Ante over the max is refused");
			table.onAction(a, "bet", bet(maxW / 6, maxW % 6 + 1));
			helper.assertTrue(table.confirmedBet(a.getUUID()).isEmpty(), "Trips counts towards W");
			Economies.get().setBalance(a.level().getServer(), a.getUUID(), 29, TEST);
			table.onAction(a, "bet", bet(10, 0));
			helper.assertTrue(table.confirmedBet(a.getUUID()).isEmpty(), "must keep 1 × Ante for the river");
			Economies.get().setBalance(a.level().getServer(), a.getUUID(), START, TEST);
			table.onAction(a, "bet", bet(10, 0));
			helper.assertTrue(table.confirmedBet(a.getUUID()).isPresent(), "a valid bet is accepted");
			table.onAction(a, "clear", new CompoundTag());
			helper.assertTrue(bal(a) == START, "Clear returns the bet before the deal");
		});
		helper.succeed();
	}

	@GameTest
	public void playerBankedRoundPaysTheBankAndRakes(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.PLAYER_BANKED_TABLE.block());
		UthConfig cfg = CasinoConfig.uth();
		int vip = cfg.pvp.minBankerVip;
		cfg.pvp.minBankerVip = 0;
		withPlayers(helper, (a, b) -> {
			MinecraftServer server = a.level().getServer();
			Economies.get().setBalance(server, b.getUUID(), 20_000, TEST);
			try {
				table.sit(b);
				CompoundTag take = new CompoundTag();
				take.putLong("amount", 5_000);
				table.onAction(b, "take_bank", take);
				helper.assertTrue(b.getUUID().equals(table.bankerId()), "b holds the dealer seat");
				helper.assertTrue(!table.isSeated(b), "the banker gave up their player seat");
				helper.assertTrue(bal(b) == 15_000 && table.bank() == 5_000, "bank escrowed");
				table.sit(a);
				// the bank covers 505 × Ante + 50 × Trips per seat: Ante 10 needs 5 050 > 5 000
				table.onAction(a, "bet", bet(10, 0));
				helper.assertTrue(table.confirmedBet(a.getUUID()).isEmpty() && bal(a) == START, "bank_cover refuses the bet");
				// a: 2c 7d vs Qc Qd (board As 9h 4h 3c Ts) checks through and folds at the river timeout → bank +18
				table.stackDeckForTests(deck(List.of("2c 7d"), "Qc Qd", "As 9h 4h 3c Ts"));
				table.onAction(a, "bet", bet(9, 0));
				helper.assertTrue(table.round() != null, "dealt against the bank");
				table.onAction(a, "check", new CompoundTag());
				finish(table);
				helper.assertTrue(bal(a) == START - 18, "a folded: −18, got " + (bal(a) - START));
				long rake = BankRules.rake(18, cfg.pvp.rakePercent);
				helper.assertTrue(table.bank() == 5_000 + 18 - rake, "bank +18 − rake, got " + table.bank());
				table.onAction(b, "leave_bank", new CompoundTag());
				helper.assertTrue(table.bankerId() == null, "dealer seat free again");
				helper.assertTrue(bal(b) == 20_000 + 18 - rake, "bank returned, got " + bal(b));
				helper.assertTrue(bal(a) + bal(b) == START + 20_000 - rake, "chips conserved except the rake");
			} finally {
				cfg.pvp.minBankerVip = vip;
			}
		});
		helper.succeed();
	}

	@GameTest
	public void brokenPlayerBankedTableReturnsBetsAndBank(GameTestHelper helper) {
		UthTableBlockEntity table = place(helper, UthModule.PLAYER_BANKED_TABLE.block());
		UthConfig cfg = CasinoConfig.uth();
		int vip = cfg.pvp.minBankerVip;
		cfg.pvp.minBankerVip = 0;
		withPlayers(helper, (a, b) -> {
			MinecraftServer server = a.level().getServer();
			Economies.get().setBalance(server, b.getUUID(), 5_000, TEST);
			try {
				table.sit(b);
				CompoundTag take = new CompoundTag();
				take.putLong("amount", 2_000);
				table.onAction(b, "take_bank", take);
				table.sit(a);
				ServerPlayer c = helper.makeMockServerPlayerInLevel();
				try {
					Economies.get().setBalance(server, c.getUUID(), START, TEST);
					table.sit(c);
					table.onAction(a, "bet", bet(2, 0)); // c has not bet → betting stays open
					helper.assertTrue(table.confirmedBet(a.getUUID()).isPresent() && bal(a) == START - 4, "bet against the bank");
					table.playOutNow("removed"); // table broken before the deal: bet refunded, bank returned
					helper.assertTrue(bal(a) == START, "a refunded");
					helper.assertTrue(bal(b) == 5_000, "bank returned in full");
					helper.assertTrue(table.bankerId() == null && table.escrowTotal() == 0, "nothing held");
				} finally {
					server.getPlayerList().remove(c);
				}
			} finally {
				cfg.pvp.minBankerVip = vip;
			}
		});
		helper.succeed();
	}
}
