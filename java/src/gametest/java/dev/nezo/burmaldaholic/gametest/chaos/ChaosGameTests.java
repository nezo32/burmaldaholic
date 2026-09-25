package dev.nezo.burmaldaholic.gametest.chaos;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.chaos.ChaosApi;
import dev.nezo.burmaldaholic.chaos.ChaosData;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.GoldenHourState;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** Chaos layer in a real world: API lookup, chip shower, safety skip, weather, Golden Hour bonus. */
public class ChaosGameTests {
	private static final Transaction TEST = Transaction.of("chaos", "gametest");

	/** Like GameTestHelper#makeMockServerPlayerInLevel, but in Survival (creative players are skipped by §13.4). */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "chaos-test"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
		return player;
	}

	private static void withPlayer(GameTestHelper helper, Consumer<ServerPlayer> body) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			body.accept(player);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	@GameTest
	public void apiIsSharedWithJdkTypes(GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(ChaosApi.KEY_TRIGGER);
		helper.assertTrue(shared instanceof BiFunction<?, ?, ?>, "trigger published in ObjectShare");
		helper.assertTrue(FabricLoader.getInstance().getObjectShare().get(ChaosApi.KEY_JACKPOT) instanceof Consumer<?>, "jackpot published");
		helper.succeed();
	}

	@GameTest
	public void chipShowerDropsChips(GameTestHelper helper) {
		withPlayer(helper, player -> {
			TriggerResult r = ChaosApi.triggerEvent(player, ChaosEvent.CHIP_SHOWER, "admin", true);
			helper.assertTrue(r == TriggerResult.STARTED, "chip shower started, got " + r);
			long chips = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4)).stream()
				.mapToLong(e -> ChipItem.valueOf(e.getItem()))
				.sum();
			helper.assertTrue(chips >= 20 && chips <= 100, "20–100 chips as items, got " + chips);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4)).forEach(e -> e.discard());
		});
		helper.succeed();
	}

	@GameTest
	public void cooldownAndUnknownEvents(GameTestHelper helper) {
		withPlayer(helper, player -> {
			helper.assertTrue(ChaosApi.triggerEvent(player, ChaosEvent.XP_FOUNTAIN, "admin", false) == TriggerResult.STARTED, "first event");
			helper.assertTrue("cooldown".equals(ChaosApi.trigger(player, "curse", "slots")), "per-player cooldown");
			helper.assertTrue("disabled".equals(ChaosApi.trigger(player, "no_such_event", "slots")), "unknown id");
		});
		helper.succeed();
	}

	@GameTest
	public void creativePlayersAreSkipped(GameTestHelper helper) {
		@SuppressWarnings("removal")
		ServerPlayer creative = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue(ChaosApi.triggerEvent(creative, ChaosEvent.CURSE, "admin", true) == TriggerResult.SKIPPED, "creative → skipped");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(creative);
		}
		helper.succeed();
	}

	@GameTest
	public void goldenHourDoublesHouseWinsUpToCap(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		withPlayer(helper, player -> {
			ChaosData data = ChaosData.get(server);
			GoldenHourState gh = data.goldenHour();
			long now = server.overworld().getGameTime();
			gh.start(now, 3600, 0);
			try {
				helper.assertTrue(ChaosApi.goldenHourRemaining(server) == 3600, "provider reports remaining ticks");
				Economies.get().setBalance(server, player.getUUID(), 0, TEST);
				CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, new CasinoEvents.PlayResult("slots", 10, 110));
				helper.assertTrue(Economies.get().balance(player) == 100, "bonus = net win × (2 − 1)");
				CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, CasinoEvents.PlayResult.of("poker", 10, 110).pvp());
				helper.assertTrue(Economies.get().balance(player) == 100, "PvP poker gets no bonus");
				CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, new CasinoEvents.PlayResult("slots", 10, 5));
				helper.assertTrue(Economies.get().balance(player) == 100, "losses get no bonus");
				CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, new CasinoEvents.PlayResult("roulette", 100, 100_000));
				helper.assertTrue(Economies.get().balance(player) == 5000, "capped at chaos.goldenHour.bonusCap");
			} finally {
				gh.stop(now, 0);
				data.setDirty();
			}
			helper.assertTrue(ChaosApi.goldenHourRemaining(server) == 0, "stopped");
		});
		helper.succeed();
	}

	// ---- §13.4 after the presentation delays (lane J-L3: the diamond lands 8 t after its column, the teleport 5 t after
	// its veil, the wave 18 t after its runes) ----------------------------------------------------------------------

	private static long diamondsNear(GameTestHelper helper, net.minecraft.world.phys.Vec3 at) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(8)).stream()
			.filter(e -> e.getItem().is(net.minecraft.world.item.Items.DIAMOND))
			.count();
	}

	@GameTest(maxTicks = 200)
	public void diamondRainGivesNothingToAPlayerWhoLeftDuringTheColumn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = survivalPlayer(helper);
		net.minecraft.world.phys.Vec3 at = player.position();
		TriggerResult r = ChaosApi.triggerEvent(player, ChaosEvent.DIAMOND_RAIN, "admin", true);
		helper.assertTrue(r == TriggerResult.STARTED, "diamond rain started, got " + r);
		// the first glint column starts on tick 1; its diamond is due on tick 1 + COLUMN_TICKS
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(diamondsNear(helper, at) == 0, "no diamond before its column lands");
			server.getPlayerList().remove(player);
		});
		helper.runAfterDelay(130, () -> {
			helper.assertTrue(diamondsNear(helper, at) == 0, "logged out during the fall: no diamonds spawned");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void diamondRainLandsForAPlayerWhoStays(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = survivalPlayer(helper);
		net.minecraft.world.phys.Vec3 at = player.position();
		TriggerResult r = ChaosApi.triggerEvent(player, ChaosEvent.DIAMOND_RAIN, "admin", true);
		helper.assertTrue(r == TriggerResult.STARTED, "diamond rain started, got " + r);
		helper.runAfterDelay(130, () -> {
			long n = diamondsNear(helper, at);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(8)).forEach(e -> e.discard());
			server.getPlayerList().remove(player);
			helper.assertTrue(n >= 2 && n <= 6, "2–6 diamonds landed, got " + n);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void teleportIsCancelledWhenThePlayerStartsFallingInsideTheVeil(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = survivalPlayer(helper);
		net.minecraft.world.phys.Vec3 start = player.position();
		TriggerResult r = ChaosApi.triggerEvent(player, ChaosEvent.RANDOM_TELEPORT, "admin", true);
		if (r != TriggerResult.STARTED) {
			server.getPlayerList().remove(player); // no safe landing in this test world: nothing to re-check
			helper.succeed();
			return;
		}
		player.fallDistance = 10; // §13.4: falling (> 3) → no teleport, even when it happens after the veil
		helper.runAfterDelay(10, () -> {
			double moved = player.position().distanceTo(start);
			server.getPlayerList().remove(player);
			helper.assertTrue(moved < 1, "a player who started falling during the veil stays put, moved " + moved);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void mobWaveIsCancelledForAPlayerWhoLeftDuringTheRunes(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = survivalPlayer(helper);
		net.minecraft.world.phys.Vec3 at = player.position();
		ChaosApi.triggerEvent(player, ChaosEvent.MOB_WAVE, "admin", true);
		server.getPlayerList().remove(player);
		helper.runAfterDelay(30, () -> {
			var mobs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(at, at).inflate(24),
				e -> e.entityTags().contains("burmaldaholic:chaos"));
			mobs.forEach(e -> e.discard());
			helper.assertTrue(mobs.isEmpty(), "no wave for a player who left before the runes finished, got " + mobs.size());
			helper.succeed();
		});
	}
}
