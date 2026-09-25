package dev.nezo.burmaldaholic.gametest.roulette;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane J-L6 review: the roulette actions added by the redesign (Undo, Double, the racetrack's call and neighbours bets)
 * against GAME_DESIGN §9 — limits, balance, "no more bets", conservation of chips — and the per-bet returns / the
 * update tag that the screens and the in-world wheel use (the number is never published before SPIN).
 */
public class RouletteGameTests {
	private static final Transaction TEST = Transaction.of("roulette", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	@SuppressWarnings("removal")
	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static RouletteTableBlockEntity table(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, RouletteModule.TABLE.block());
		return helper.getBlockEntity(pos, RouletteTableBlockEntity.class);
	}

	private static CompoundTag bet(BetType type, long amount, int... nums) {
		CompoundTag t = new CompoundTag();
		t.putString("type", type.id());
		t.putIntArray("nums", nums);
		t.putLong("amount", amount);
		return t;
	}

	private static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static long slipTotal(RouletteTableBlockEntity t, ServerPlayer p) {
		return t.writeClientState(p).getLongOr("total", -1);
	}

	@GameTest
	public void undoTakesBackTheNewestChipGroupOnly(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			RouletteTableBlockEntity t = table(helper);
			t.onAction(p, "bet", bet(BetType.STRAIGHT, 10, 17));
			t.onAction(p, "bet", bet(BetType.STRAIGHT, 5, 17));
			t.onAction(p, "bet", bet(BetType.SPLIT, 4, 8, 11));
			helper.assertTrue(balance(p) == 981 && t.stakeOf(p.getUUID()) == 19, "three chips down");
			t.onAction(p, "undo", new CompoundTag());
			helper.assertTrue(balance(p) == 985 && t.stakeOf(p.getUUID()) == 15 && slipTotal(t, p) == 15, "split back");
			t.onAction(p, "undo", new CompoundTag());
			ListTag bets = t.writeClientState(p).getListOrEmpty("bets");
			helper.assertTrue(balance(p) == 990 && bets.size() == 1 && bets.getCompoundOrEmpty(0).getLongOr("amount", 0) == 10,
				"the second chip on 17 back, the first stays: " + bets);
			t.onAction(p, "undo", new CompoundTag());
			helper.assertTrue(balance(p) == 1000 && t.stakeOf(p.getUUID()) == 0 && t.openStakes().isEmpty(), "everything back, nothing escrowed");
			t.onAction(p, "undo", new CompoundTag());
			helper.assertTrue(balance(p) == 1000, "nothing more to undo");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** Undo is gone once the bets are closed (NO_MORE_BETS and later). */
	@GameTest(maxTicks = 100)
	public void undoRefusedAfterNoMoreBets(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		RouletteTableBlockEntity t = table(helper);
		t.onAction(p, "bet", bet(BetType.STRAIGHT, 10, 17));
		t.onAction(p, "spin", new CompoundTag());
		helper.succeedWhen(() -> {
			helper.assertTrue("no_more_bets".equals(t.phase()), "waiting for no more bets: " + t.phase());
			t.onAction(p, "undo", new CompoundTag());
			helper.assertTrue(balance(p) == 990 && t.stakeOf(p.getUUID()) == 10, "undo refused after no more bets");
			remove(helper, p);
		});
	}

	@GameTest
	public void doubleRespectsBalanceAndLimits(GameTestHelper helper) {
		ServerPlayer p = player(helper, 100);
		try {
			RouletteTableBlockEntity t = table(helper);
			t.onAction(p, "bet", bet(BetType.RED, 30, Spot.all(BetType.RED).getFirst().numbers().stream().mapToInt(Integer::intValue).toArray()));
			t.onAction(p, "bet", bet(BetType.STRAIGHT, 10, 0));
			t.onAction(p, "double", new CompoundTag());
			helper.assertTrue(balance(p) == 20 && t.stakeOf(p.getUUID()) == 80, "doubled: 60 red + 20 on zero");
			t.onAction(p, "double", new CompoundTag());
			helper.assertTrue(balance(p) == 20 && t.stakeOf(p.getUUID()) == 80, "a double the balance cannot cover is refused whole");
			t.onAction(p, "undo", new CompoundTag());
			helper.assertTrue(balance(p) == 60 && t.stakeOf(p.getUUID()) == 40, "undo takes the double back");
		} finally {
			remove(helper, p);
		}
		ServerPlayer rich = player(helper, 1_000_000);
		try {
			RouletteTableBlockEntity t = table(helper);
			MinecraftServer server = helper.getLevel().getServer();
			long tierMax = CoreServices.vip().maxBet(server, rich.getUUID());
			long insideMax = Math.max(1, (long) Math.floor(tierMax * CasinoConfig.roulette().insideMaxFraction));
			long chip = insideMax / 2 + 1; // doubling it would pass the inside maximum
			t.onAction(rich, "bet", bet(BetType.STRAIGHT, chip, 7));
			long before = balance(rich);
			t.onAction(rich, "double", new CompoundTag());
			helper.assertTrue(balance(rich) == before && t.stakeOf(rich.getUUID()) == chip, "double over the inside max (" + insideMax + ") refused");
		} finally {
			remove(helper, rich);
		}
		helper.succeed();
	}

	@GameTest
	public void callBetsPlaceTheStandardChips(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			RouletteTableBlockEntity t = table(helper);
			int[] chips = {6, 5, 9, 4};
			long total = 0;
			for (Racetrack.Section s : Racetrack.Section.values()) {
				CompoundTag args = new CompoundTag();
				args.putString("section", s.id());
				args.putLong("amount", 2);
				t.onAction(p, "call", args);
				total += 2L * chips[s.ordinal()];
				helper.assertTrue(t.stakeOf(p.getUUID()) == total && balance(p) == 1000 - total, s.id() + ": " + chips[s.ordinal()] + " chips of 2");
			}
			CompoundTag nb = new CompoundTag();
			nb.putInt("n", 0);
			nb.putLong("amount", 3);
			t.onAction(p, "neighbours", nb);
			total += 15;
			helper.assertTrue(t.stakeOf(p.getUUID()) == total, "neighbours of 0: five straight-ups");
			CompoundTag bad = new CompoundTag();
			bad.putString("section", "nope");
			bad.putLong("amount", 2);
			t.onAction(p, "call", bad);
			CompoundTag bad2 = new CompoundTag();
			bad2.putInt("n", 37);
			bad2.putLong("amount", 2);
			t.onAction(p, "neighbours", bad2);
			helper.assertTrue(t.stakeOf(p.getUUID()) == total, "invalid call bets refused");
			// the slip holds only standard bets (merged spots, e.g. zero spiel's 0-3 split beside Voisins' chips)
			ListTag bets = t.writeClientState(p).getListOrEmpty("bets");
			long sum = 0;
			for (int i = 0; i < bets.size(); i++) {
				String key = bets.getCompoundOrEmpty(i).getStringOr("spot", "");
				helper.assertTrue(Spot.parseKey(key).map(Spot::isValid).orElse(false), "standard spot: " + key);
				sum += bets.getCompoundOrEmpty(i).getLongOr("amount", 0);
			}
			helper.assertTrue(sum == total, "slip = what was paid");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** The per-bet returns shown by the screen add up to what the server paid; the number is not public before SPIN. */
	@GameTest(maxTicks = 400)
	public void perBetReturnsAndNoEarlyNumber(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		RouletteTableBlockEntity t = table(helper);
		t.onAction(p, "bet", bet(BetType.STRAIGHT, 10, 17));
		t.onAction(p, "bet", bet(BetType.RED, 20, Spot.all(BetType.RED).getFirst().numbers().stream().mapToInt(Integer::intValue).toArray()));
		t.onAction(p, "bet", bet(BetType.DOZEN, 5, Spot.all(BetType.DOZEN).getFirst().numbers().stream().mapToInt(Integer::intValue).toArray()));
		helper.assertTrue(balance(p) == 965, "35 down");
		t.onAction(p, "spin", new CompoundTag());
		boolean[] sawClosed = new boolean[1];
		helper.succeedWhen(() -> {
			if ("no_more_bets".equals(t.phase())) {
				RouletteSync nmb = t.sync();
				if (nmb.result() != -1 || t.writeClientState(p).getIntOr("result", -1) != -1) {
					helper.fail("the number is public before SPIN: " + nmb);
				}
				sawClosed[0] = true;
			}
			helper.assertTrue("result".equals(t.phase()) && sawClosed[0], "waiting for the result: " + t.phase());
			CompoundTag s = t.writeClientState(p);
			long[] rets = s.getLongArray("bet_returns").orElseThrow();
			long sum = 0;
			for (long r : rets) {
				sum += r;
			}
			int n = s.getIntOr("result", -1);
			ListTag bets = s.getListOrEmpty("bets");
			helper.assertTrue(rets.length == bets.size() && bets.size() == 3, "one return per bet");
			long paid = balance(p) - 965;
			helper.assertTrue(sum == paid, "per-bet returns " + sum + " = paid " + paid + " (number " + n + ")");
			List<Bets.Bet> slip = List.of(new Bets.Bet(Spot.of(BetType.STRAIGHT, 17), 10),
				new Bets.Bet(Spot.all(BetType.RED).getFirst(), 20), new Bets.Bet(Spot.all(BetType.DOZEN).getFirst(), 5));
			helper.assertTrue(Bets.totalReturn(slip, n, CasinoConfig.roulette().laPartage) == paid, "GAME_DESIGN §9 payouts");
			helper.assertTrue(t.sync().result() == n, "the in-world wheel lands on the same number");
			remove(helper, p);
		});
	}
}
