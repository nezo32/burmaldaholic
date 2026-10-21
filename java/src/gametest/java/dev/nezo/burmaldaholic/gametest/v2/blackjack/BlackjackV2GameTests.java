package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackPub;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane A (blackjack): server GameTests of the in-world board's PUBLIC tag (docs/architecture/ui-v2.md §7.2): it reaches
 * the update tag, shows the bets of the betting lobby and the dealt cards, and NEVER the face-down hole card.
 */
public class BlackjackV2GameTests {
	private static final Transaction TEST = Transaction.of("blackjack", "gametest");

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	@GameTest
	@SuppressWarnings("removal")
	public void publicTagHidesTheHoleCardUntilItsFlip(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity table = helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 1000, TEST);
			helper.assertTrue(table.sit(a), "seated");
			BlackjackPub lobby = table.buildPub();
			helper.assertTrue(lobby.phase() == BlackjackPub.BETTING && lobby.dealer().length == 0, "lobby: no cards");
			helper.assertTrue(lobby.seat(table.seats().seatOf(a.getUUID()).orElse(-1)) != null, "lobby: the seated player is public");
			// player 10 + 6 vs dealer 9 up, 8 in the hole
			table.stackCardsForTests(List.of(Card.of(10), Card.of(9, Card.HEARTS), Card.of(6), Card.of(8, Card.DIAMONDS), Card.of(5), Card.of(5)));
			table.onAction(a, "bet", amount(10));
			table.revealAllForTests();
			BlackjackPub turn = table.buildPub();
			helper.assertTrue(turn.dealer().length == 2, "both dealer cards on the felt");
			helper.assertTrue(turn.dealer()[0] == Card.of(9, Card.HEARTS).code(), "the up card is public");
			helper.assertTrue(turn.dealer()[1] == -1, "the hole card is a back during the turns");
			helper.assertTrue(turn.acting() >= 0, "the acting seat is public");
			helper.assertTrue(BlackjackPub.decode(turn.encode()).equals(turn), "round trip");
			CompoundTag update = table.getUpdateTag(helper.getLevel().registryAccess());
			helper.assertTrue(update.contains(BlackjackTableBlockEntity.PUB_KEY), "the update tag carries the board");
			table.onAction(a, "stand", new CompoundTag());
			table.revealAllForTests();
			BlackjackPub done = table.buildPub();
			helper.assertTrue(done.dealer()[1] == Card.of(8, Card.DIAMONDS).code(), "the hole card is public after its flip");
			BlackjackPub.SeatPub mine = done.seat(table.seats().seatOf(a.getUUID()).orElse(-1));
			helper.assertTrue(mine != null && mine.has(BlackjackPub.SETTLED) && mine.net() == -10, "16 vs 17 loses 10 publicly");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}

	/**
	 * T-D2 item 6: a table removed with a round in play (chunk unload, break) is played out WITHOUT a single preset world
	 * read (a lookup there re-enters chunk loading mid-unload and crashed a 26.2 build); the round still pays out.
	 */
	@GameTest
	@SuppressWarnings("removal")
	public void removalPlayOutReadsNoPreset(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity table = helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try (PresetReadCounter counter = new PresetReadCounter(helper.absolutePos(pos))) {
			Economies.get().setBalance(server, a.getUUID(), 1000, TEST);
			helper.assertTrue(table.sit(a), "seated");
			table.bots().table().requestChange(a, new dev.nezo.burmaldaholic.core.bots.logic.BotSettings(dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy.MIXED, 2,
				dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL, false, false, dev.nezo.burmaldaholic.core.bots.logic.BotSpeed.FAST), false);
			table.botSafePointForTests();
			table.onAction(a, "bet", amount(10));
			table.revealAllForTests();
			helper.assertTrue(table.round() != null, "a round in play");
			helper.assertTrue(counter.reads() > 0, "the live table did look its preset up (the counter works)");
			counter.reset();
			helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
			helper.assertTrue(counter.reads() == 0, "preset world reads during the removal play-out: " + counter.reads());
			helper.assertTrue(table.stakeOf(a.getUUID()) == 0, "the round was played out and settled on removal");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}
}
