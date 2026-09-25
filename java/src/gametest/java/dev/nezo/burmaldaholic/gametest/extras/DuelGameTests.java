package dev.nezo.burmaldaholic.gametest.extras;

import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.extras.server.DuelStage;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Lane J-L6 review: the PvP Dice Duel in the world ({@link DuelStage}, tables.md §3.4) and its held-back chat lines
 * (§3.6): four dice appear between the players, the stage cleans up after the last reveal, the money settles at once,
 * and a line never gets lost when a duellist leaves before it is due.
 */
public class DuelGameTests {
	private static final Transaction TEST = Transaction.of("dice", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance, double dx) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5 + dx, 1, 1.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	@SuppressWarnings("removal")
	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	@GameTest(maxTicks = 400)
	public void duelStageThrowsFourDiceAndCleansUp(GameTestHelper helper) {
		ServerPlayer a = player(helper, 100, 0);
		ServerPlayer b = player(helper, 100, 3);
		DiceGame.resolvePvpForTesting(a, b, 10);
		long ab = Economies.get().balance(a) + Economies.get().balance(b);
		helper.assertTrue(ab <= 200 && ab >= 190, "settled at once (winner paid minus rake, or both refunded): " + ab);
		helper.assertTrue(DuelStage.active() >= 1, "staged");
		helper.assertTrue(DiceGame.pendingLines() > 0, "the round lines wait for the dice");
		int[] seenDice = new int[1];
		boolean[] seenTotal = new boolean[1];
		helper.onEachTick(() -> {
			List<Entity> es = DuelStage.entities();
			long dice = es.stream().filter(e -> e instanceof Display.ItemDisplay && !e.isRemoved()).count();
			seenDice[0] = (int) Math.max(seenDice[0], dice);
			seenTotal[0] |= es.stream().anyMatch(e -> e instanceof Display.TextDisplay);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(seenDice[0] >= 4 && seenTotal[0], "four dice and a total were shown (" + seenDice[0] + ")");
			helper.assertTrue(DuelStage.active() == 0 && DuelStage.entities().isEmpty(), "stage over: everything removed");
			helper.assertTrue(DiceGame.pendingLines() == 0, "every line posted");
			remove(helper, a);
			remove(helper, b);
		});
	}

	@GameTest(maxTicks = 40)
	public void duelLinesReachALeaverByMail(GameTestHelper helper) {
		ServerPlayer a = player(helper, 100, 0);
		ServerPlayer b = player(helper, 100, 2);
		MinecraftServer server = helper.getLevel().getServer();
		var rec = CasinoWorldData.get(server).player(b.getUUID());
		int before = rec.pendingMail.size();
		DiceGame.resolvePvpForTesting(a, b, 10);
		remove(helper, b); // b leaves before the dice land
		DiceGame.flushPending(server, true); // e.g. the server stops
		helper.assertTrue(DiceGame.pendingLines() == 0, "nothing dropped");
		helper.assertTrue(rec.pendingMail.size() >= before + 3, "b's roll lines and the result wait for the next join: " + rec.pendingMail.size());
		rec.pendingMail.clear();
		remove(helper, a);
		helper.succeed();
	}
}
