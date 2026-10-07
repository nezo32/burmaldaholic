package dev.nezo.burmaldaholic.gametest.v2.baccarat;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.present.BaccaratPub;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane A (baccarat): server GameTests of the in-world board's PUBLIC tag (docs/architecture/ui-v2.md §7.2): the boxes
 * show the bets, the coup's cards follow the reveal (no face before its beat, no winner before the result).
 */
public class BaccaratV2GameTests {
	private static final Transaction TEST = Transaction.of("baccarat", "gametest");

	@GameTest
	@SuppressWarnings("removal")
	public void publicTagFollowsTheReveal(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, BaccaratModule.TABLE.block());
		BaccaratTableBlockEntity table = helper.getBlockEntity(pos, BaccaratTableBlockEntity.class);
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = helper.makeMockServerPlayerInLevel();
		try {
			Economies.get().setBalance(server, a.getUUID(), 10_000, TEST);
			helper.assertTrue(table.sit(a), "seated");
			CompoundTag bet = new CompoundTag();
			bet.putString("box", "player");
			bet.putLong("amount", 100);
			table.onAction(a, "bet", bet);
			BaccaratPub betting = table.buildPub();
			helper.assertTrue(betting.boxes()[BaccaratPub.P] >= 100, "the Player box shows the bet");
			BaccaratPub.SeatPub mine = betting.seat(table.seats().seatOf(a.getUUID()).orElse(-1));
			helper.assertTrue(mine != null && mine.mainBox() == BaccaratPub.P, "the seat's main box is Player");
			table.onAction(a, "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(table.phase()); i++) table.fastForwardForTests();
			helper.assertTrue("reveal".equals(table.phase()), "the reveal runs");
			BaccaratPub reveal = table.buildPub();
			helper.assertTrue(reveal.winner() == 0, "no winner during the reveal");
			long now = helper.getLevel().getGameTime();
			for (int i = 0; i < reveal.player().length; i++) {
				boolean shown = reveal.playerRev()[i] >= 0 && reveal.revStart() + reveal.playerRev()[i] <= now;
				helper.assertTrue(reveal.player()[i] < 0 || shown, "no Player face before its reveal beat");
			}
			helper.assertTrue(table.getUpdateTag(helper.getLevel().registryAccess()).contains(BaccaratTableBlockEntity.PUB_KEY), "update tag");
			for (int i = 0; i < 6 && !"result".equals(table.phase()); i++) table.fastForwardForTests();
			BaccaratPub result = table.buildPub();
			helper.assertTrue("result".equals(table.phase()) && result.winner() > 0, "the winner is public on the result");
			helper.assertTrue(BaccaratPub.allShown(result.player()) && BaccaratPub.allShown(result.banker()), "every card face up on the result");
		} finally {
			server.getPlayerList().remove(a);
		}
		helper.succeed();
	}
}
