package dev.nezo.burmaldaholic.gametest.comps;

import dev.nezo.burmaldaholic.comps.CompsService;
import dev.nezo.burmaldaholic.comps.FreeBets;
import dev.nezo.burmaldaholic.comps.logic.CompsRules;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRng;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * T-EC2 (tester): free-bet token money safety at the TABLE games, through the real receivers
 * ({@link TableNetworking#receive} for tables, the player's packet listener for coin flip). Every round checks:
 * win = winnings only, push = token back, loss = nothing; the PlayResult is TOKEN; no VIP wager, comp points or
 * streak; a foreign / expired token is never taken; a same-tick double click takes one token.
 *
 * <p>Blackjack split / insurance tests assert the PER-HAND rule (the token belongs to the original hand only; the
 * chip hands and insurance settle as chips). They fail on the aggregate approximation in
 * {@code CasinoTableBlockEntity.payToken} (T-EC2 D1).
 */
public class CompsTokenTableGameTests {
	private static final Transaction TEST = Transaction.of("comps", "gametest");
	private static final BlockPos AT = new BlockPos(1, 1, 1);
	private static final long T = 50;
	private static final Map<UUID, List<PlayResult>> SEEN = new ConcurrentHashMap<>();
	private static volatile boolean hooked;

	private static synchronized void hook() {
		if (!hooked) {
			hooked = true;
			CasinoEvents.PLAY_RESOLVED.register((p, r) -> SEEN.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(r));
		}
	}

	/** Only the round moves chips: no contract rewards, no chaos (Golden Hour) bonuses. */
	private static void quiet(Runnable body) {
		hook();
		boolean contracts = CasinoConfig.contracts().enabled;
		double reward = CasinoConfig.contracts().rewardMultiplier;
		boolean chaos = CasinoConfig.chaos().enabled;
		CasinoConfig.contracts().enabled = false;
		CasinoConfig.contracts().rewardMultiplier = 0;
		CasinoConfig.chaos().enabled = false;
		try {
			body.run();
		} finally {
			CasinoConfig.contracts().enabled = contracts;
			CasinoConfig.contracts().rewardMultiplier = reward;
			CasinoConfig.chaos().enabled = chaos;
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 3.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		p.closeContainer();
		helper.getLevel().getServer().getPlayerList().remove(p);
		SEEN.remove(p.getUUID());
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static ItemStack token(UUID owner, long value, long expires, int count) {
		ItemStack s = FreeBets.create(owner, value, expires);
		s.setCount(count);
		return s;
	}

	private static void hold(ServerPlayer p, long value, int count) {
		p.setItemInHand(InteractionHand.MAIN_HAND, token(p.getUUID(), value, now(p.level().getServer()) + CompsRules.TOKEN_LIFETIME_TICKS, count));
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

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	private static void act(ServerPlayer p, CasinoTableBlockEntity table, String action, CompoundTag args) {
		TableNetworking.receive(p, new TableActionPayload(table.getBlockPos(), action, args));
	}

	private static PlayResult last(ServerPlayer p) {
		List<PlayResult> l = SEEN.get(p.getUUID());
		return l == null || l.isEmpty() ? null : l.getLast();
	}

	/** No VIP wager, no comp points, no streak, and the reported round is a TOKEN round. */
	private static void noCredit(GameTestHelper helper, ServerPlayer p, String what) {
		MinecraftServer server = helper.getLevel().getServer();
		PlayResult r = last(p);
		helper.assertTrue(r != null && r.stakeKind() == Stake.Kind.TOKEN, what + ": the round is reported as TOKEN: " + r);
		helper.assertTrue(dev.nezo.burmaldaholic.vip.VipService.wagered(server, p.getUUID()) == 0, what + ": no VIP wager credit");
		helper.assertTrue(CompsService.state(p).pointsMilli() == 0, what + ": no comp points");
		helper.assertTrue(StreakTracker.get(server, p.getUUID()) == 0, what + ": no streak move");
	}

	// ---- blackjack ------------------------------------------------------------------------------

	/** One blackjack round with a 50-chip token, start balance 1000; {@code script} = actions after the bet. */
	private static void blackjack(GameTestHelper helper, String what, List<Card> cards, List<String> script, long balance, int tokensLeft) {
		quiet(() -> {
			helper.setBlock(AT, BlackjackModule.TABLE.block());
			BlackjackTableBlockEntity table = helper.getBlockEntity(AT, BlackjackTableBlockEntity.class);
			ServerPlayer p = player(helper, 1000);
			try {
				p.openMenu(table);
				hold(p, T, 1);
				List<Card> deck = new ArrayList<>(cards);
				for (int i = 0; i < 8; i++) {
					deck.add(Card.of(2)); // spare low cards, never reached in these scripts
				}
				table.stackCardsForTests(deck);
				act(p, table, "bet", amount(999)); // the bet field is ignored with a token in hand
				helper.assertTrue(tokens(p) == 0, what + ": the token was taken");
				helper.assertTrue(Economies.get().balance(p) == 1000, what + ": no chips debited for the token bet");
				for (String s : script) {
					if (s.startsWith("insurance:")) {
						act(p, table, "insurance", amount(Long.parseLong(s.substring(10))));
					} else {
						act(p, table, s, new CompoundTag());
					}
				}
				BlackjackRound r = table.round();
				helper.assertTrue(r != null && r.phase() == BlackjackRound.Phase.DONE, what + ": round finished (" + (r == null ? null : r.phase()) + ")");
				helper.assertTrue(table.openStakes().isEmpty(), what + ": every stake settled");
				helper.assertTrue(Economies.get().balance(p) == balance, what + ": balance " + Economies.get().balance(p) + ", expected " + balance);
				helper.assertTrue(tokens(p) == tokensLeft, what + ": tokens " + tokens(p) + ", expected " + tokensLeft);
				noCredit(helper, p, what);
			} finally {
				remove(helper, p);
			}
		});
		helper.succeed();
	}

	private static Card c(int rank) {
		return Card.of(rank);
	}

	@GameTest
	public void bjTokenWinPaysWinningsOnly(GameTestHelper helper) {
		// 20 v 18
		blackjack(helper, "bj win", List.of(c(10), c(9), c(10), c(9)), List.of("stand"), 1000 + T, 0);
	}

	@GameTest
	public void bjTokenPushReturnsToken(GameTestHelper helper) {
		// 20 v 20
		blackjack(helper, "bj push", List.of(c(10), c(10), c(10), c(10)), List.of("stand"), 1000, 1);
	}

	@GameTest
	public void bjTokenLossPaysNothing(GameTestHelper helper) {
		// 18 v 20
		blackjack(helper, "bj loss", List.of(c(10), c(10), c(8), c(10)), List.of("stand"), 1000, 0);
	}

	@GameTest
	public void bjTokenDoubleWin(GameTestHelper helper) {
		// 6+5 doubles (50 chips) and draws 10: 21 v 18 -> return 200, token part 50 not paid: +150 on 950
		blackjack(helper, "bj double win", List.of(c(6), c(9), c(5), c(9), c(10)), List.of("double"), 1000 - T + 3 * T, 0);
	}

	@GameTest
	public void bjTokenDoublePushReturnsTokenAndChips(GameTestHelper helper) {
		// 5+5 doubles and draws K: 20 v 20 -> the doubled chips come back and the token too
		blackjack(helper, "bj double push", List.of(c(5), c(10), c(5), c(10), c(13)), List.of("double"), 1000, 1);
	}

	@GameTest
	public void bjTokenSplitTokenHandWinsChipHandLoses(GameTestHelper helper) {
		// 10,10 split v 18: hand 1 (token) Q = 20 wins +50 winnings, hand 2 (50 chips) 6 = 16 loses.
		// Correct: 1000 - 50 + 50 = 1000 and the token is USED (it won). Aggregate rule: "push" -> token back (overpay).
		blackjack(helper, "bj split token-win/chip-loss", List.of(c(10), c(9), c(10), c(9), c(12), c(6)), List.of("split", "stand", "stand"), 1000, 0);
	}

	@GameTest
	public void bjTokenSplitTokenHandLosesChipHandWins(GameTestHelper helper) {
		// hand 1 (token) 6 = 16 loses, hand 2 (50 chips) Q = 20 wins 100. Correct: 1000 - 50 + 100 = 1050, token gone.
		blackjack(helper, "bj split token-loss/chip-win", List.of(c(10), c(9), c(10), c(9), c(6), c(12)), List.of("split", "stand", "stand"), 1050, 0);
	}

	@GameTest
	public void bjTokenSplitTokenHandPushChipHandWins(GameTestHelper helper) {
		// dealer 20; hand 1 (token) Q = 20 push -> token back; hand 2 (50 chips) A = 21 wins 100. Correct: 1050 + token.
		blackjack(helper, "bj split token-push/chip-win", List.of(c(10), c(10), c(10), c(10), c(12), c(1)), List.of("split", "stand", "stand"), 1050, 1);
	}

	@GameTest
	public void bjTokenInsuranceDealerBlackjack(GameTestHelper helper) {
		// 20 v A up, K hole (dealer BJ); insurance 25 pays 2:1 -> 75. Main (token) loses. Correct: 1000 - 25 + 75 = 1050, token gone.
		blackjack(helper, "bj insurance dealer BJ", List.of(c(10), c(1), c(10), c(13)), List.of("insurance:25"), 1050, 0);
	}

	@GameTest
	public void bjTokenInsuranceLostMainPush(GameTestHelper helper) {
		// 20 v A up, 9 hole (20, no BJ); insurance 25 lost; main pushes -> token back. Correct: 975 + token.
		blackjack(helper, "bj insurance lost, main push", List.of(c(10), c(1), c(10), c(9)), List.of("insurance:25", "stand"), 975, 1);
	}

	/** Someone else's token and an expired token are never taken; a same-tick double bet takes one token. */
	@GameTest
	public void bjTokenGuards(GameTestHelper helper) {
		quiet(() -> {
			MinecraftServer server = helper.getLevel().getServer();
			helper.setBlock(AT, BlackjackModule.TABLE.block());
			BlackjackTableBlockEntity table = helper.getBlockEntity(AT, BlackjackTableBlockEntity.class);
			ServerPlayer a = player(helper, 0);
			ServerPlayer b = player(helper, 0);
			ServerPlayer c = player(helper, 1000);
			try {
				a.openMenu(table);
				b.openMenu(table);
				c.openMenu(table);
				// A holds B's token; B holds an expired token of their own
				a.setItemInHand(InteractionHand.MAIN_HAND, token(b.getUUID(), T, now(server) + 10_000, 1));
				b.setItemInHand(InteractionHand.MAIN_HAND, token(b.getUUID(), T, now(server) - 1, 1));
				act(a, table, "bet", amount(T));
				act(b, table, "bet", amount(T));
				helper.assertTrue(table.stakeOf(a.getUUID()) == 0 && tokens(a) == 1, "a foreign token is not a stake");
				helper.assertTrue(table.stakeOf(b.getUUID()) == 0 && tokens(b) == 1, "an expired token is not a stake");
				// C: two tokens in hand, two bets in the same tick -> one token, one stake
				hold(c, T, 2);
				act(c, table, "bet", amount(T));
				act(c, table, "bet", amount(T));
				helper.assertTrue(tokens(c) == 1, "same-tick double bet takes one token: " + tokens(c));
				helper.assertTrue(table.stakeOf(c.getUUID()) == T && Economies.get().balance(c) == 1000, "one token stake, no chips: "
					+ table.stakeOf(c.getUUID()) + " / " + Economies.get().balance(c));
				// leaving before the deal gives the token back (no chips)
				if (table.round() == null) {
					act(c, table, "leave", new CompoundTag());
					helper.assertTrue(tokens(c) == 2 && Economies.get().balance(c) == 1000, "leave before the deal returns the token: "
						+ tokens(c) + " / " + Economies.get().balance(c));
				}
			} finally {
				remove(helper, a);
				remove(helper, b);
				remove(helper, c);
			}
		});
		helper.succeed();
	}

	// ---- wheel / plinko / coin flip (random draw: invariants per round) -------------------------

	/** The settlement invariant of a one-hand token round with total return {@code ret} (from the reported result). */
	private static void checkOneHand(GameTestHelper helper, ServerPlayer p, String what) {
		PlayResult r = last(p);
		helper.assertTrue(r != null, what + ": a round was reported");
		long ret = r.payout();
		long stake = r.bet();
		long bal = Economies.get().balance(p);
		helper.assertTrue(stake == T, what + ": stake = token value: " + stake);
		if (ret == stake) {
			helper.assertTrue(bal == 0 && tokens(p) == 1, what + ": push (" + ret + ") -> token back, no chips: " + bal + " / " + tokens(p));
		} else {
			helper.assertTrue(bal == Math.max(0, ret - stake) && tokens(p) == 0, what + ": return " + ret + " -> pays " + Math.max(0, ret - stake)
				+ " and uses the token: " + bal + " / " + tokens(p));
		}
		noCredit(helper, p, what);
	}

	@GameTest(maxTicks = 200)
	public void wheelTokenRounds(GameTestHelper helper) {
		quiet(() -> {
			helper.setBlock(AT, ExtrasModule.WHEEL.block());
			WheelBlockEntity wheel = helper.getBlockEntity(AT, WheelBlockEntity.class);
			for (int i = 0; i < 24; i++) {
				ServerPlayer p = player(helper, 0);
				try {
					p.openMenu(wheel);
					hold(p, T, 2);
					CompoundTag args = amount(999);
					args.putString("stake", "chips");
					act(p, wheel, "spin", args);
					act(p, wheel, "spin", args); // same tick: busy, the 2nd token stays
					int left = tokens(p);
					p.getInventory().removeItem(p.getInventory().getSelectedSlot(), 1); // drop the untouched spare from the count
					helper.assertTrue(left >= 1, "wheel " + i + ": the same-tick 2nd spin kept a token");
					checkOneHand(helper, p, "wheel " + i);
				} finally {
					remove(helper, p);
				}
			}
		});
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void plinkoTokenRounds(GameTestHelper helper) {
		quiet(() -> {
			helper.setBlock(AT, ExtrasModule.PLINKO.block());
			PlinkoBlockEntity plinko = helper.getBlockEntity(AT, PlinkoBlockEntity.class);
			var pcfg = dev.nezo.burmaldaholic.core.config.CasinoConfig.extras().plinko;
			double fraction = pcfg.maxBetFraction;
			// T-EC2 L2: a token above the player's plinko max (Bronze: 100 / 5 = 20) is refused and kept
			ServerPlayer low = player(helper, 0);
			try {
				low.openMenu(plinko);
				hold(low, T, 1);
				CompoundTag a = amount(T);
				a.putString("risk", "low");
				act(low, plinko, "drop", a);
				helper.assertTrue(tokens(low) == 1 && plinko.stakeOf(low.getUUID()) == 0 && last(low) == null,
					"a token above the plinko max is refused and kept");
			} finally {
				remove(helper, low);
			}
			pcfg.maxBetFraction = 1.0; // the rounds below: max = tier max (100) so the 50 token fits
			try {
			for (int i = 0; i < 24; i++) {
				ServerPlayer p = player(helper, 0);
				try {
					p.openMenu(plinko);
					hold(p, T, 1);
					CompoundTag args = amount(999);
					args.putString("risk", i % 2 == 0 ? "low" : "high");
					act(p, plinko, "drop", args);
					act(p, plinko, "drop", args); // same tick: busy (and no token left)
					checkOneHand(helper, p, "plinko " + i);
					helper.assertTrue(SEEN.get(p.getUUID()).size() == 1, "plinko " + i + ": one round only");
				} finally {
					remove(helper, p);
				}
			}
			} finally {
				pcfg.maxBetFraction = fraction;
			}
		});
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void coinFlipTokenRoundsViaPacket(GameTestHelper helper) {
		quiet(() -> {
			for (int i = 0; i < 24; i++) {
				ServerPlayer p = player(helper, 0);
				try {
					hold(p, T, 1);
					p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ExtrasModule.LUCKY_COIN));
					CompoundTag t = amount(999);
					t.putString("side", "heads");
					t.putString("stake", "chips");
					p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new ExtrasActionPayload(CoinFlipGame.SCREEN, "flip", t)));
					p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new ExtrasActionPayload(CoinFlipGame.SCREEN, "flip", t)));
					helper.assertTrue(SEEN.getOrDefault(p.getUUID(), List.of()).size() == 1, "coin " + i + ": the 2nd flip (no token, 0 chips) is refused");
					checkOneHand(helper, p, "coin " + i);
				} finally {
					remove(helper, p);
				}
			}
		});
		helper.succeed();
	}

	// ---- slots ---------------------------------------------------------------------------------

	@GameTest(maxTicks = 400)
	public void slotsTokenFullWinAndLoss(GameTestHelper helper) {
		quiet(() -> {
			helper.setBlock(AT, SlotsModule.MACHINES.get(Tier.COPPER).block());
			SlotMachineBlockEntity be = helper.getBlockEntity(AT, SlotMachineBlockEntity.class);
			var cfg = dev.nezo.burmaldaholic.games.slots.SlotMachinesV2.cfg(be.machineV2());
			int vipBefore = cfg.minVipTier;
			cfg.minVipTier = 0;
			ServerPlayer p = player(helper, 0);
			try {
				p.openMenu(be);
				spin(helper, be, p, "slots win", t -> t.totalFifths() > 0 && !t.featureTriggered() && t.jackpots().isEmpty() && t.hunt() == null);
				long afterWin = Economies.get().balance(p);
				spin(helper, be, p, "slots loss", t -> t.totalFifths() == 0 && !t.featureTriggered() && t.jackpots().isEmpty() && t.hunt() == null);
				helper.assertTrue(Economies.get().balance(p) == afterWin, "slots loss pays nothing");
				// a token below the smallest ladder bet is refused and kept
				long smallest = be.writeClientState(p).getCompoundOrEmpty("v2").getLongArray("bets").orElse(new long[] {1})[0];
				if (smallest > 1) {
					hold(p, smallest - 1, 1);
					CompoundTag a = new CompoundTag();
					a.putLong("bet", smallest);
					act(p, be, "spin", a);
					helper.assertTrue(tokens(p) == 1 && be.roundTape() == null, "a token below the smallest bet is refused and kept");
				}
			} finally {
				be.drawForTesting(null);
				cfg.minVipTier = vipBefore;
				be.leave(p.getUUID(), CasinoTableBlockEntity.LeaveReason.LEFT);
				remove(helper, p);
			}
		});
		helper.succeed();
	}

	private static void spin(GameTestHelper helper, SlotMachineBlockEntity be, ServerPlayer p, String what, Predicate<SpinTape> want) {
		int[] seed = {1};
		be.drawForTesting(req -> {
			for (int k = seed[0]; k < seed[0] + 3_000_000; k++) {
				SpinTape t = SlotDraw.draw(req, SlotRng.seeded(k));
				if (want.test(t)) {
					seed[0] = k + 1;
					return t;
				}
			}
			throw new IllegalStateException("no tape for " + what);
		});
		long before = Economies.get().balance(p);
		hold(p, T, 1);
		CompoundTag args = new CompoundTag();
		args.putLong("bet", -1);
		act(p, be, "spin", args);
		helper.assertTrue(be.roundTape() != null, what + ": the token spin started");
		helper.assertTrue(tokens(p) == 0 && Economies.get().balance(p) == before, what + ": token taken, no chips debited");
		be.fireRoundTimerForTesting();
		helper.assertTrue(be.roundTape() == null && be.stakeOf(p.getUUID()) == 0, what + ": settled");
		SpinTape settled = TapeCodec.decode(be.writeClientState(p).getCompoundOrEmpty("v2").getCompoundOrEmpty("result").getStringOr("tape", ""));
		long paid = Economies.get().balance(p) - before;
		helper.assertTrue(paid == settled.payoutChips(), what + ": the full spin win is credited (paid " + paid + ", tape " + settled.payoutChips() + ")");
		helper.assertTrue(tokens(p) == 0, what + ": no token back on a slot spin");
		noCredit(helper, p, what);
	}
}
