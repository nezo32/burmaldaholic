package dev.nezo.burmaldaholic.gametest.extras;

import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.extras.server.DuelStage;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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

	// ---- DCb (DI §3.4, §6): side / start, chat at COMPARE, modded viewers get no entities or sound packets ------------

	@GameTest(maxTicks = 40)
	public void pvpResultCarriesSideAndStartAndChatWaitsForTheCompare(GameTestHelper helper) {
		ServerPlayer a = player(helper, 100, 0);
		ServerPlayer b = player(helper, 100, 3);
		List<CompoundTag> results = new ArrayList<>();
		List<ServerPlayer> to = new ArrayList<>();
		var old = DiceGame.PVP_RESULT_SINK;
		DiceGame.PVP_RESULT_SINK = (p, r) -> {
			to.add(p);
			results.add(r.copy());
		};
		// SY-SPEC §4: the duel starts SyncLead.PVP_DICE ticks after the decision
		long start = dev.nezo.burmaldaholic.core.anim.sync.SyncLead.startTick(helper.getLevel().getServer().overworld().getGameTime(),
			dev.nezo.burmaldaholic.core.anim.sync.SyncLead.PVP_DICE);
		try {
			DiceGame.resolvePvpForTesting(a, b, 10);
		} finally {
			DiceGame.PVP_RESULT_SINK = old;
		}
		helper.assertTrue(results.size() == 2, "one result per duellist: " + results.size());
		CompoundTag ra = results.get(to.indexOf(a));
		CompoundTag rb = results.get(to.indexOf(b));
		helper.assertTrue("a".equals(ra.getStringOr("side", "")) && "b".equals(rb.getStringOr("side", "")), "side a / b");
		helper.assertTrue(ra.getLongOr("start", -1) == start && rb.getLongOr("start", -1) == start, "start = the duel's game tick " + start);
		helper.assertTrue(ra.getIntOr("seed", 0) == rb.getIntOr("seed", 1), "one cosmetic seed for both screens");
		int rounds = ra.getListOrEmpty("rounds").size();
		// each round's two lines per player at start + revealTick(round) (the COMPARE beat), the result at the last one
		List<Long> ticks = DiceGame.pendingTicks(a.getUUID());
		helper.assertTrue(ticks.size() == 2 * rounds + 1, "lines " + ticks);
		for (int i = 0; i < rounds; i++) {
			long want = start + DuelTimeline.revealTick(i);
			helper.assertTrue(ticks.get(2 * i) == want && ticks.get(2 * i + 1) == want, "round " + i + " at " + want + ": " + ticks);
			helper.assertTrue(DuelTimeline.revealTick(i) * 50L >= DuelTimeline.roundStart(i) + DuelTimeline.COMPARE, "never before the compare");
		}
		helper.assertTrue(ticks.get(2 * rounds) == start + DuelTimeline.revealTick(rounds - 1), "result line at the last compare");
		DiceGame.flushPending(helper.getLevel().getServer(), true);
		CasinoWorldData.get(helper.getLevel().getServer()).player(a.getUUID()).pendingMail.clear();
		CasinoWorldData.get(helper.getLevel().getServer()).player(b.getUUID()).pendingMail.clear();
		remove(helper, a);
		remove(helper, b);
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void moddedViewersGetTheSyncButNoEntitiesOrSoundPackets(GameTestHelper helper) {
		ServerPlayer a = player(helper, 100, 0);
		ServerPlayer b = player(helper, 100, 3);
		ServerPlayer modded = player(helper, 0, 1.5);
		ServerPlayer vanilla = player(helper, 0, 1.6);
		Set<UUID> moddedSet = Set.of(modded.getUUID(), a.getUUID(), b.getUUID());
		var oldModded = DuelStage.modded;
		List<String> soundsModded = new ArrayList<>();
		List<String> soundsVanilla = new ArrayList<>();
		Set<UUID> synced = new HashSet<>();
		DuelStage.modded = p -> moddedSet.contains(p.getUUID());
		DuelStage.soundSink = (p, id) -> (p == modded ? soundsModded : p == vanilla ? soundsVanilla : new ArrayList<String>()).add(id);
		DuelStage.syncSink = (p, sync) -> synced.add(p.getUUID());
		long start = helper.getLevel().getServer().overworld().getGameTime();
		Vec3 ca = new Vec3(a.getX(), a.getY() + 1.1, a.getZ());
		Vec3 cb = new Vec3(b.getX(), b.getY() + 1.1, b.getZ());
		boolean staged = DuelStage.stage(helper.getLevel(), ca, cb, a, List.<int[][]>of(new int[][] {{3, 4}, {5, 1}}), 0, 4321, start,
			Set.of(a.getUUID(), b.getUUID()));
		helper.assertTrue(staged, "staged");
		int[] checked = new int[1];
		helper.onEachTick(() -> {
			for (Entity e : DuelStage.entities()) {
				if (!(e instanceof Display.ItemDisplay)) continue;
				helper.assertTrue(!e.broadcastToPlayer(modded), "a modded viewer is never sent a die entity");
				helper.assertTrue(e.broadcastToPlayer(vanilla), "a vanilla viewer sees the die entities");
				checked[0]++;
			}
		});
		helper.runAfterDelay(DuelTimeline.revealTick(0) + 10, () -> {
			try {
				helper.assertTrue(checked[0] > 0, "dice entities were checked");
				helper.assertTrue(synced.contains(modded.getUUID()) && !synced.contains(vanilla.getUUID()), "the sync goes to modded viewers only: " + synced);
				helper.assertTrue(soundsModded.isEmpty(), "no sound packets to a modded viewer: " + soundsModded);
				helper.assertTrue(soundsVanilla.contains("dice_throw") && soundsVanilla.contains("dice_settle"), "the vanilla bystander hears the pairs: "
					+ soundsVanilla);
			} finally {
				DuelStage.modded = oldModded;
				DuelStage.soundSink = null;
				DuelStage.syncSink = null;
				DuelStage.stopAll(helper.getLevel().getServer());
				remove(helper, a);
				remove(helper, b);
				remove(helper, modded);
				remove(helper, vanilla);
			}
			helper.succeed();
		});
	}
}
