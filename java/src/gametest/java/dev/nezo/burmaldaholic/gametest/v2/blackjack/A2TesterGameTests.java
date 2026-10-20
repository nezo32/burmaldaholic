package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BlackjackConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.logic.Shoe;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Tester T-A2: extra real-flow checks (everything through {@link TableNetworking#receive}, survival players whose table
 * menu was opened by using the block): the dry-shoe path at table level (1 deck, 0.9 penetration, two bots), mid-round
 * save, the client count never ahead of the felt; blackjack bet-above-balance and insurance money; baccarat
 * {@code unbet_chip} Banker snap / below-minimum remainder / bad args.
 */
public class A2TesterGameTests {
	private static final Transaction TEST = Transaction.of("blackjack", "gametest");

	private static ServerPlayer opened(GameTestHelper helper, BlockPos rel, long balance) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "t-a2"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(rel.getX() + 1.5, 1, rel.getZ() + 0.5)));
		Economies.get().setBalance(server, player.getUUID(), balance, TEST);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu, "using the block opens the table menu");
		return player;
	}

	private static void act(ServerPlayer p, CasinoTableBlockEntity t, String action, CompoundTag args) {
		TableNetworking.receive(p, new TableActionPayload(t.getBlockPos(), action, args));
	}

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	private static long balance(GameTestHelper helper, ServerPlayer p) {
		return Economies.get().balance(helper.getLevel().getServer(), p.getUUID());
	}

	private static List<Card> roundCards(BlackjackRound r) {
		List<Card> out = new ArrayList<>(r.dealerCards());
		for (BlackjackRound.Seat s : r.seats()) for (BlackjackRound.Hand h : s.hands) out.addAll(h.cards);
		return out;
	}

	/** Every card on the felt of the round, with the client's shoe count never ahead of it. */
	private static void checkCount(GameTestHelper helper, BlackjackTableBlockEntity t, ServerPlayer p, String when) {
		CompoundTag s = t.writeClientState(p).getCompoundOrEmpty("shoe");
		Shoe shoe = t.shoeForTests();
		int dealt = s.getIntOr("dealt", -1);
		int left = s.getIntOr("left", -1);
		helper.assertTrue(dealt >= 0 && left >= 0 && dealt + left == shoe.size() && dealt <= shoe.dealt(), when + ": counts sane " + s + " drawn " + shoe.dealt());
		if (t.round() != null && t.beats() != null) {
			int felt = t.beats().publishedCount(helper.getLevel().getGameTime());
			helper.assertTrue(dealt - s.getIntOr("discard", 0) <= felt, when + ": count ahead of the felt " + s + " felt " + felt);
		}
	}

	private static void playOut(GameTestHelper helper, ServerPlayer p, BlackjackTableBlockEntity t, boolean hit) {
		for (int i = 0; i < 60 && t.round() != null && t.round().phase() != BlackjackRound.Phase.DONE; i++) {
			checkCount(helper, t, p, "mid-round " + i);
			t.revealAllForTests();
			BlackjackRound r = t.round();
			if (r.phase() == BlackjackRound.Phase.INSURANCE) {
				act(p, t, "insurance", amount(0));
				CompoundTag no = new CompoundTag();
				no.putBoolean("take", false);
				act(p, t, "even_money", no);
			} else if (r.current() != null && p.getUUID().equals(r.current().seat().player)) {
				act(p, t, hit ? "hit" : "stand", new CompoundTag());
				if (hit && t.round() != null && t.round().current() != null && p.getUUID().equals(t.round().current().seat().player)
					&& !t.round().legal(t.round().current().seat().seat).contains(BlackjackRound.Action.HIT)) {
					act(p, t, "stand", new CompoundTag());
				}
			} else {
				t.fastForwardForTests();
			}
		}
		t.revealAllForTests();
		helper.assertTrue(t.round() != null && t.round().phase() == BlackjackRound.Phase.DONE, "the round finished");
	}

	/**
	 * Dry shoe at table level: 1 deck, penetration 0.9 (cut 47), the shoe burnt to 46 so the round starts before the cut,
	 * two bots + the player deal 8 cards → the shoe runs dry mid-round. No card repeats in the round, the shoe is never
	 * replaced mid-round, the client count is never ahead of the felt, a mid-round save keeps order + refilled flag, and the
	 * collect reshuffles.
	 */
	@GameTest(maxTicks = 200)
	public void dryShoeMidRoundNoRepeatAndSave(GameTestHelper helper) {
		BlackjackConfig c = CasinoConfig.blackjack();
		int decks0 = c.decks;
		double pen0 = c.penetration;
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(rel, BlackjackTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel, 10_000);
		boolean sawDry = false;
		try {
			c.decks = 1;
			c.penetration = 0.9;
			act(p, t, "sit", new CompoundTag());
			t.bots().table().requestChange(p, new dev.nezo.burmaldaholic.core.bots.logic.BotSettings(dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy.MIXED, 2,
				dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL, false, false, dev.nezo.burmaldaholic.core.bots.logic.BotSpeed.FAST), false);
			t.botSafePointForTests();
			for (int attempt = 0; attempt < 6 && !sawDry; attempt++) {
				t.burnShoeForTests(0);
				Shoe shoe = t.shoeForTests();
				helper.assertTrue(shoe.decks() == 1 && shoe.size() == 52, "1-deck shoe");
				if (shoe.dealt() < 46) t.burnShoeForTests(46 - shoe.dealt());
				helper.assertTrue(shoe.dealt() == 46 && !shoe.needsShuffle(0.9), "one short of the cut card");
				act(p, t, "bet", amount(10));
				helper.assertTrue(t.round() != null, "the bet dealt a round");
				helper.assertTrue(t.shoeForTests() == shoe, "the shoe is not replaced at the round start");
				checkCount(helper, t, p, "deal");
				playOut(helper, p, t, true);
				helper.assertTrue(t.shoeForTests() == shoe, "the shoe is never replaced mid-round");
				List<Card> cards = roundCards(t.round());
				Set<Integer> codes = new HashSet<>();
				for (Card k : cards) helper.assertTrue(codes.add(k.code()), "card " + k + " twice in one round (1 deck): " + cards);
				if (shoe.refilled()) {
					sawDry = true;
					helper.assertTrue(t.round().seats().size() >= 2, "bots played");
					// a mid-round save keeps the exact order, position and the refilled flag
					var registries = helper.getLevel().registryAccess();
					CompoundTag saved = t.saveWithoutMetadata(registries);
					BlockPos rel2 = new BlockPos(4, 1, 1);
					helper.setBlock(rel2, BlackjackModule.TABLE.block());
					BlackjackTableBlockEntity copy = helper.getBlockEntity(rel2, BlackjackTableBlockEntity.class);
					copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
					Shoe loaded = copy.shoeForTests();
					helper.assertTrue(loaded != null && loaded.refilled() && loaded.dealt() == shoe.dealt() && loaded.discards() == shoe.discards()
						&& Arrays.equals(loaded.codes(), shoe.codes()), "mid-round save of a refilled shoe");
					helper.setBlock(rel2, net.minecraft.world.level.block.Blocks.AIR);
				}
				t.collectForTests();
				helper.assertTrue(t.round() == null, "collected");
				Shoe after = t.shoeForTests();
				if (sawDry) {
					helper.assertTrue(after.dealt() == 0 && !after.refilled(), "reshuffled at the collect after a dry shoe");
				}
				checkCount(helper, t, p, "betting");
			}
			helper.assertTrue(sawDry, "the shoe never ran dry in 6 attempts (test inconclusive)");
		} finally {
			c.decks = decks0;
			c.penetration = pen0;
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/** Rebet / Deal above the balance through the handler: refused, nothing moves, no round. */
	@GameTest
	public void betAboveBalanceIsRefused(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(rel, BlackjackTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel, 5);
		try {
			act(p, t, "sit", new CompoundTag());
			act(p, t, "bet", amount(10));
			helper.assertTrue(t.round() == null && balance(helper, p) == 5 && t.stakeOf(p.getUUID()) == 0, "bet above balance refused");
			act(p, t, "bet", amount(-10));
			act(p, t, "bet", amount(0));
			helper.assertTrue(t.round() == null && balance(helper, p) == 5 && t.stakeOf(p.getUUID()) == 0, "bad amounts refused");
			act(p, t, "bet", amount(5));
			helper.assertTrue(t.round() != null && balance(helper, p) == 0, "all-in 5 deals");
			act(p, t, "bet", amount(5));
			helper.assertTrue(balance(helper, p) == 0 && t.stakeOf(p.getUUID()) == 5, "a second bet mid-round is refused");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	private void insuranceCase(GameTestHelper helper, Card hole, long expectNet) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(rel, BlackjackTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel, 1000);
		try {
			act(p, t, "sit", new CompoundTag());
			// player 10 + 10, dealer A up
			t.stackCardsForTests(List.of(Card.of(10, Card.SPADES), Card.of(1, Card.HEARTS), Card.of(13, Card.CLUBS), hole, Card.of(5), Card.of(5)));
			act(p, t, "bet", amount(10));
			helper.assertTrue(t.round() != null && t.round().phase() == BlackjackRound.Phase.INSURANCE, "insurance offered on an ace");
			act(p, t, "insurance", amount(6));
			helper.assertTrue(balance(helper, p) == 990, "insurance above half the bet refused (" + balance(helper, p) + ")");
			act(p, t, "insurance", amount(5));
			// dealer blackjack: the peek settles at once (−10 main, +15 insurance) → 1000; else the 5 is staked → 985
			long afterIns = expectNet == 0 ? 1000 : 985;
			helper.assertTrue(balance(helper, p) == afterIns, "insurance 5 taken (" + balance(helper, p) + ")");
			act(p, t, "insurance", amount(5));
			helper.assertTrue(balance(helper, p) == afterIns, "insurance cannot be taken twice (" + balance(helper, p) + ")");
			for (int i = 0; i < 20 && t.round().phase() != BlackjackRound.Phase.DONE; i++) {
				t.revealAllForTests();
				if (t.round().current() != null) act(p, t, "stand", new CompoundTag());
				else t.fastForwardForTests();
			}
			t.revealAllForTests();
			t.collectForTests();
			helper.assertTrue(balance(helper, p) - 1000 == expectNet && t.stakeOf(p.getUUID()) == 0,
				"net " + (balance(helper, p) - 1000) + " expected " + expectNet + ", stake " + t.stakeOf(p.getUUID()));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/** Dealer blackjack: the 10 bet loses, the 5 insurance pays 2:1 → net 0. */
	@GameTest
	public void insurancePaysOnDealerBlackjack(GameTestHelper helper) {
		insuranceCase(helper, Card.of(13, Card.DIAMONDS), 0);
	}

	/** No dealer blackjack: insurance 5 lost, 20 v 20 pushes → net −5. */
	@GameTest
	public void insuranceLostWithoutDealerBlackjack(GameTestHelper helper) {
		insuranceCase(helper, Card.of(9, Card.DIAMONDS), -5);
	}

	// ---- baccarat unbet_chip -------------------------------------------------------------------------------------------

	private static void bac(ServerPlayer p, BaccaratTableBlockEntity t, String action, String box, long amount) {
		CompoundTag args = new CompoundTag();
		if (!box.isEmpty()) args.putString("box", box);
		if (amount != 0) args.putLong("amount", amount);
		TableNetworking.receive(p, new TableActionPayload(t.getBlockPos(), action, args));
	}

	private static long box(BaccaratTableBlockEntity t, ServerPlayer p, String box) {
		return t.writeClientState(p).getCompoundOrEmpty("slip").getLongOr(box, 0);
	}

	/** Banker snapped to the step (20 at 5 %), a remainder below the minimum returns the box, bad args refused. */
	@GameTest
	public void unbetChipBankerSnapAndBelowMinimum(GameTestHelper helper) {
		var cfg = BaccaratModule.config();
		long min0 = cfg.minBet;
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity t = helper.getBlockEntity(rel, BaccaratTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel, 10_000);
		try {
			bac(p, t, "sit", "", 0);
			bac(p, t, "bet", "banker", 60);
			helper.assertTrue(box(t, p, "banker") == 60 && balance(helper, p) == 9_940, "Banker 60 (" + box(t, p, "banker") + ")");
			// 60 − 5 = 55 → snapped down to 40: 20 back
			bac(p, t, "unbet_chip", "banker", 5);
			helper.assertTrue(box(t, p, "banker") == 40 && balance(helper, p) == 9_960 && t.stakeOf(p.getUUID()) == 40,
				"Banker snapped to 40, 20 back (box " + box(t, p, "banker") + ", bal " + balance(helper, p) + ", stake " + t.stakeOf(p.getUUID()) + ")");
			// 40 − 20 = 20 = bankerMin: stays
			bac(p, t, "unbet_chip", "banker", 20);
			helper.assertTrue(box(t, p, "banker") == 20 && balance(helper, p) == 9_980, "Banker 20 stays");
			// 20 − 1 = 19 → snapped to 0: the whole box back
			bac(p, t, "unbet_chip", "banker", 1);
			helper.assertTrue(box(t, p, "banker") == 0 && balance(helper, p) == 10_000 && t.stakeOf(p.getUUID()) == 0, "Banker emptied");
			// bad args: no box, unknown box, zero / negative amount — refused, nothing moves
			bac(p, t, "bet", "player", 30);
			bac(p, t, "unbet_chip", "", 10);
			bac(p, t, "unbet_chip", "nonsense", 10);
			bac(p, t, "unbet_chip", "player", -10);
			bac(p, t, "unbet_chip", "player", Long.MIN_VALUE);
			helper.assertTrue(box(t, p, "player") == 30 && balance(helper, p) == 9_970 && t.stakeOf(p.getUUID()) == 30, "bad unbet_chip args refused");
			bac(p, t, "unbet_chip", "player", 30);
			helper.assertTrue(box(t, p, "player") == 0 && balance(helper, p) == 10_000 && t.stakeOf(p.getUUID()) == 0, "whole box back");
			// a remainder below the minimum: Player 15 − 10 = 5 < min 10 → all 15 back
			cfg.minBet = 10;
			bac(p, t, "bet", "player", 15);
			bac(p, t, "bet", "tie", 10);
			helper.assertTrue(box(t, p, "player") == 15 && balance(helper, p) == 9_975, "Player 15 + Tie 10");
			bac(p, t, "unbet_chip", "player", 10);
			helper.assertTrue(box(t, p, "player") == 0 && box(t, p, "tie") == 10 && balance(helper, p) == 9_990 && t.stakeOf(p.getUUID()) == 10,
				"below-minimum remainder returns the whole box (box " + box(t, p, "player") + ", bal " + balance(helper, p) + ")");
			bac(p, t, "unbet_chip", "tie", 10);
			helper.assertTrue(balance(helper, p) == 10_000 && t.stakeOf(p.getUUID()) == 0, "all back, nothing lost");
		} finally {
			cfg.minBet = (int) min0;
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/** A table removed without bots ever configured (lazy bot table) still plays out with 0 preset world reads. */
	@GameTest
	@SuppressWarnings("removal")
	public void removalWithoutBotsReadsNoPreset(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity bac = helper.getBlockEntity(pos, BaccaratTableBlockEntity.class);
		BlockPos pos2 = new BlockPos(4, 1, 1);
		helper.setBlock(pos2, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity bj = helper.getBlockEntity(pos2, BlackjackTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 1000, TEST);
			helper.assertTrue(bj.sit(a), "seated bj");
			bj.onAction(a, "bet", amount(10));
			helper.assertTrue(bj.round() != null, "bj round in play");
			try (PresetReadCounter counter = new PresetReadCounter(helper.absolutePos(pos2))) {
				counter.reset();
				bj.playOutNow("chunk unloaded");
				helper.assertTrue(counter.reads() == 0, "bj preset reads during a chunk-unload play-out: " + counter.reads());
			}
			helper.assertTrue(bj.stakeOf(a.getUUID()) == 0, "bj played out");
			helper.assertTrue(bac.sit(a), "seated bac");
			CompoundTag b = new CompoundTag();
			b.putString("box", "player");
			b.putLong("amount", 20);
			bac.onAction(a, "bet", b);
			bac.onAction(a, "ready", new CompoundTag());
			for (int i = 0; i < 2 && "betting".equals(bac.phase()); i++) bac.fastForwardForTests();
			try (PresetReadCounter counter = new PresetReadCounter(helper.absolutePos(pos))) {
				counter.reset();
				bac.playOutNow("chunk unloaded");
				helper.assertTrue(counter.reads() == 0, "bac preset reads during a chunk-unload play-out: " + counter.reads());
			}
			helper.assertTrue(bac.stakeOf(a.getUUID()) == 0, "bac played out");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}
}
