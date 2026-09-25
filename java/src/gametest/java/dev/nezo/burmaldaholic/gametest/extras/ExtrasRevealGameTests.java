package dev.nezo.burmaldaholic.gametest.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.logic.anim.CoinTossKeys;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoSync;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelAnim;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelSync;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.CoinToss;
import dev.nezo.burmaldaholic.pvp.PvpMatchView;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

/**
 * Outcome confidentiality of the extras reveals, checked on the real packets' content (extras-pvp.md §0.3 rule 7, §14
 * test 6): the PvP match view a participant — or a spectator — receives is built every tick of a whole Plinko Battle
 * and Scratch Showdown, and no view carries the final ball's last bit, its bin, the final scratch cell or the outcome
 * before that seat's place cue. The solo in-world syncs (wheel, Plinko) are published once per round with the SAME
 * curve as the screen, and the thrown coin shows its face only from its landing tick.
 */
public class ExtrasRevealGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		p.setPos(at.x, at.y, at.z);
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	private static void done(GameTestHelper helper, ServerPlayer... players) {
		MinecraftServer s = helper.getLevel().getServer();
		for (ServerPlayer p : players) {
			Pvp.service().leave(p);
			s.getPlayerList().remove(p);
		}
	}

	/** Every "events" array of a view with the seats its events belong to must only hold events of revealed seats. */
	private static void checkView(GameTestHelper helper, JsonObject view, String finalKind, Set<Integer> finalSeen, String who) {
		boolean settled = "SETTLED".equals(view.get("state").getAsString());
		helper.assertTrue(settled == view.has("outcome"), who + ": the outcome only with the result");
		Set<Integer> placed = new HashSet<>();
		JsonArray placings = view.getAsJsonArray("placings");
		for (JsonElement e : placings) {
			JsonObject pl = e.getAsJsonObject();
			int seat = pl.get("seat").getAsInt();
			placed.add(seat);
			for (JsonElement ev : pl.getAsJsonArray("events")) {
				int evSeat = ev.getAsJsonObject().get("seat").getAsInt();
				helper.assertTrue(evSeat == seat, who + ": a placing only carries its own seat's events");
				if (finalKind.equals(ev.getAsJsonObject().get("kind").getAsString())) finalSeen.add(seat);
			}
		}
		// the steps never carry the final ball's 12th row / the final scratch cell
		for (JsonElement e : view.getAsJsonArray("steps")) {
			JsonObject st = e.getAsJsonObject();
			String kind = st.get("kind").getAsString();
			JsonObject d = st.getAsJsonObject("data");
			if (kind.equals("drop") && d.has("final") && d.get("final").getAsBoolean()) {
				helper.assertTrue(!d.has("bins") && !d.has("points"), who + ": the final drop step has no bins / points");
				for (JsonElement m : d.getAsJsonArray("masks")) {
					helper.assertTrue(m.getAsInt() < 1 << PlinkoBattleMode.FINAL_ROWS_SHOWN, who + ": the final ball's last bit withheld");
				}
			}
			if (kind.equals("cell")) {
				helper.assertTrue(d.get("cell").getAsInt() < 9, who + ": the final scratch cell is never a step");
			}
		}
		if (!settled) {
			for (int seat : finalSeen) helper.assertTrue(placed.contains(seat), who + ": a final result only after its place cue");
		}
	}

	private static void run(GameTestHelper helper, String mode, JsonElement params, String finalKind) {
		PvpService pvp = Pvp.service();
		ServerPlayer a = player(helper, 5000);
		ServerPlayer spectator = player(helper, 0);
		MinecraftServer server = helper.getLevel().getServer();
		PvpMatch m = pvp.openLobby(a, mode, params, 40, new PvpService.Anchor(AnchorKind.NONE, (ServerLevel) a.level(), a.blockPosition()),
			new BotSettings(SeatPolicy.BOTS_ONLY, 3, BotDifficulty.NORMAL, false, true, BotSpeed.INSTANT), false).value();
		helper.assertTrue(m != null && m.state() == MatchState.DRAWN, mode + ": drawn at once");
		Set<Integer> seenByPlayer = new HashSet<>();
		Set<Integer> seenBySpectator = new HashSet<>();
		int[] views = {0};
		helper.onEachTick(() -> {
			if (m.state() == MatchState.SETTLED) return;
			pvp.press(a);
			checkView(helper, PvpMatchView.build(server, a, m), finalKind, seenByPlayer, mode + "/player");
			checkView(helper, PvpMatchView.build(server, spectator, m), finalKind, seenBySpectator, mode + "/spectator");
			checkView(helper, PvpMatchView.build(server, null, m), finalKind, new HashSet<>(), mode + "/anyone");
			views[0]++;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(m.state() == MatchState.SETTLED, mode + " settled");
			helper.assertTrue(views[0] > 100, mode + ": views checked through the whole reveal (" + views[0] + ")");
			// the place cues did reveal the final results of the losers before the result
			helper.assertTrue(seenByPlayer.size() >= 3, mode + ": final results arrived with their place cues: " + seenByPlayer);
			done(helper, a, spectator);
		});
	}

	@GameTest(maxTicks = 3000)
	public void plinkoFinalBallNeverLeaksEarly(GameTestHelper helper) {
		run(helper, "plinko", new PlinkoBattleMode().encodeParams(new PlinkoBattleMode.Params("medium", 2)), "final_ball");
	}

	@GameTest(maxTicks = 3000)
	public void scratchFinalCellNeverLeaksEarly(GameTestHelper helper) {
		ScratchShowdownMode mode = new ScratchShowdownMode();
		run(helper, "scratch", mode.encodeParams(mode.defaults()), "final_cell");
	}

	// ---- solo in-world syncs -------------------------------------------------------------------------------------------

	private static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	@GameTest(maxTicks = 200)
	public void wheelPublishesTheSpinOnce(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ExtrasModule.WHEEL.block());
		WheelBlockEntity wheel = helper.getBlockEntity(pos, WheelBlockEntity.class);
		ServerPlayer p = player(helper, 5000);
		helper.assertTrue(wheel.wheelSync() == null, "nothing before the first spin");
		wheel.onAction(p, "spin", chips(10));
		WheelSync first = wheel.wheelSync();
		helper.assertTrue(first != null && first.prevIndex() < 0, "published at the start");
		CompoundTag update = wheel.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(WheelSync.decode(update.getIntArray(WheelBlockEntity.SYNC_KEY).orElseThrow()).equals(first), "the update tag carries it");
		helper.assertTrue(WheelAnim.segmentAt(first.angle(first.totalMs(), false), first.segmentCount()) == first.index(), "lands on the drawn segment");
		helper.runAfterDelay(WheelBlockEntity.SPIN_TICKS + 2, () -> {
			wheel.onAction(p, "spin", chips(10));
			WheelSync second = wheel.wheelSync();
			helper.assertTrue(second.prevIndex() == first.index() && second.prevSeq() == first.seq(), "the next spin starts from the last rest");
			helper.runAfterDelay(WheelBlockEntity.SPIN_TICKS + 2, () -> {
				helper.getLevel().getServer().getPlayerList().remove(p);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void plinkoPublishesTheDropOnce(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ExtrasModule.PLINKO.block());
		PlinkoBlockEntity machine = helper.getBlockEntity(pos, PlinkoBlockEntity.class);
		ServerPlayer p = player(helper, 5000);
		CompoundTag args = chips(10);
		args.putString("risk", "high");
		machine.onAction(p, "drop", args);
		PlinkoSync s = machine.plinkoSync();
		helper.assertTrue(s != null, "published at the release");
		CompoundTag result = machine.writeClientState(p).getCompoundOrEmpty("result");
		helper.assertTrue(s.path() == result.getIntOr("path", -1) && s.bin() == result.getIntOr("bin", -1), "the screen's path and bin");
		helper.assertTrue(s.edge() == (s.bin() == 0 || s.bin() == 12), "edge = the High jackpot");
		// the chat line / celebration wait for the landing of the screen's timeline
		helper.assertTrue(PlinkoBlockEntity.DROP_TICKS * 50 >= s.landMs(), "settled lines never before the ball lands");
		helper.runAfterDelay(PlinkoBlockEntity.DROP_TICKS + 2, () -> {
			helper.getLevel().getServer().getPlayerList().remove(p);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void coinTossShowsTheFaceOnlyAtTheLanding(GameTestHelper helper) {
		ServerPlayer p = player(helper, 5000);
		CompoundTag args = chips(10);
		args.putString("side", "heads");
		CoinFlipGame.action(p, "flip", args);
		Display.ItemDisplay d = CoinToss.displayOf(p);
		helper.assertTrue(d != null, "a toss display spawned");
		long start = helper.getLevel().getGameTime();
		String[] landed = {null};
		helper.onEachTick(() -> {
			Display.ItemDisplay now = CoinToss.displayOf(p);
			int t = (int) (helper.getLevel().getGameTime() - start);
			if (now == null) return;
			CustomModelData cmd = now.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA);
			String variant = cmd == null || cmd.strings().isEmpty() ? "" : cmd.strings().getFirst();
			if (t < CoinTossKeys.LAND_TICK) {
				helper.assertTrue(variant.equals("spin"), "no face before the landing (tick " + t + "): " + variant);
			} else if (!variant.equals("spin")) {
				landed[0] = variant;
			}
		});
		helper.runAfterDelay(CoinTossKeys.REMOVE_TICK + 3, () -> {
			helper.assertTrue(landed[0] != null && (landed[0].equals("heads") || landed[0].equals("tails")), "the face at the landing: " + landed[0]);
			helper.assertTrue(CoinToss.displayOf(p) == null, "removed after the toss");
			helper.getLevel().getServer().getPlayerList().remove(p);
			helper.succeed();
		});
	}
}
