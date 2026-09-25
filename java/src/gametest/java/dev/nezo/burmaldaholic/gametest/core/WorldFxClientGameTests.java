package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.chaos.ChaosApi;
import dev.nezo.burmaldaholic.chaos.client.ChaosFx;
import dev.nezo.burmaldaholic.chaos.client.GoldenHourFx;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.lastchance.client.LastChanceClientModule;
import dev.nezo.burmaldaholic.lastchance.logic.CoinFlipTimeline;
import dev.nezo.burmaldaholic.lastchance.net.CoinFlipPayload;
import dev.nezo.burmaldaholic.loan.client.LoanClientModule;
import dev.nezo.burmaldaholic.loan.net.LoanFxPayload;
import dev.nezo.burmaldaholic.vip.client.VipClientModule;
import dev.nezo.burmaldaholic.worldgen.client.WorldgenClientModule;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.net.CasinoViewPayload;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Lane J-L3 client check (real client, Xvfb): world & meta FX reach the client and end on the server's state —
 * Golden Hour (vignette, countdown plaque), a chaos event intro (card + glint columns), a curse (vignette + spiral),
 * the Last Chance flip (spinning, then exactly the payload face for heads and tails), a multi-tier VIP tier-up (the
 * badge that stays is the server's tier), the Debt Collectors' arrival, and a casino scene with bot nameplates, the
 * animated cashier / wheel / Plinko fronts and a roofline marquee. Screenshots {@code jtest_jl3_*}.
 */
public class WorldFxClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			context.waitTicks(40);
			BlockPos base = stage(world);
			context.waitTicks(20);

			// ---- Golden Hour -----------------------------------------------------------------------------------------
			AtomicReference<TriggerResult> r = new AtomicReference<>();
			world.getServer().runOnServer(server -> r.set(ChaosApi.triggerEvent(player(server), ChaosEvent.GOLDEN_HOUR, "admin", true)));
			if (r.get() != TriggerResult.STARTED) throw new AssertionError("golden hour did not start: " + r.get());
			context.waitFor(mc -> GoldenHourFx.active(), 100);
			context.waitTicks(14);
			context.takeScreenshot("jtest_jl3_golden_hour");
			context.waitTicks(60);
			context.takeScreenshot("jtest_jl3_golden_hour_steady");
			world.getServer().runCommand("casino chaos golden_hour stop");
			context.waitFor(mc -> !GoldenHourFx.active(), 100);
			context.waitTicks(30);

			// ---- chaos: diamond rain (good) and curse (bad) ----------------------------------------------------------
			world.getServer().runOnServer(server -> r.set(ChaosApi.triggerEvent(player(server), ChaosEvent.DIAMOND_RAIN, "admin", true)));
			if (r.get() != TriggerResult.STARTED) throw new AssertionError("diamond rain did not start: " + r.get());
			context.waitFor(mc -> ChaosFx.card() == ChaosEvent.DIAMOND_RAIN, 60);
			context.waitTicks(9);
			context.takeScreenshot("jtest_jl3_chaos_diamond_rain");
			context.waitTicks(80);
			world.getServer().runOnServer(server -> r.set(ChaosApi.triggerEvent(player(server), ChaosEvent.CURSE, "admin", true)));
			if (r.get() != TriggerResult.STARTED) throw new AssertionError("curse did not start: " + r.get());
			context.waitFor(mc -> ChaosFx.card() == ChaosEvent.CURSE, 60);
			context.waitTicks(4);
			context.takeScreenshot("jtest_jl3_chaos_curse");
			world.getServer().runCommand("effect clear @a");
			context.waitTicks(70);

			// ---- Last Chance: heads, then tails ----------------------------------------------------------------------
			flip(world, true);
			context.waitFor(mc -> LastChanceClientModule.flipFrame() != null, 60);
			context.waitTicks(12);
			context.takeScreenshot("jtest_jl3_lastchance_spin");
			context.waitFor(mc -> {
				CoinFlipTimeline.Frame f = LastChanceClientModule.flipFrame();
				return f != null && f.landed();
			}, 60);
			context.waitTicks(8);
			CoinFlipTimeline.Frame heads = context.computeOnClient(mc -> LastChanceClientModule.flipFrame());
			if (heads == null || heads.coinFrame() != CoinFlipTimeline.HEADS_FRAME) throw new AssertionError("heads must land on the heads face: " + heads);
			context.takeScreenshot("jtest_jl3_lastchance_heads");
			context.waitFor(mc -> LastChanceClientModule.flipFrame() == null, 100);
			flip(world, false);
			context.waitFor(mc -> {
				CoinFlipTimeline.Frame f = LastChanceClientModule.flipFrame();
				return f != null && f.landed();
			}, 100);
			context.waitTicks(8);
			CoinFlipTimeline.Frame tails = context.computeOnClient(mc -> LastChanceClientModule.flipFrame());
			if (tails == null || tails.coinFrame() != CoinFlipTimeline.TAILS_FRAME) throw new AssertionError("tails must land on the tails face: " + tails);
			context.takeScreenshot("jtest_jl3_lastchance_tails");
			context.waitFor(mc -> LastChanceClientModule.flipFrame() == null, 100);

			// ---- VIP tier-up (Silver → Netherite: flicks, then the server's tier) ------------------------------------
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				ServerPlayNetworking.send(p, new FxPayload(ServerFx.Kind.VIP_UP, 1, 0, 0, "vip", 5, 0, 0, p.getId(), Optional.of(p.position()),
					Optional.of(p.getUUID()), Optional.of(p.getDisplayName()), Optional.empty()));
			});
			context.waitFor(mc -> VipClientModule.tierUpShown() >= 0, 60);
			context.waitTicks(22);
			int shown = context.computeOnClient(mc -> VipClientModule.tierUpShown());
			if (shown != 5) throw new AssertionError("the tier-up must show the server's tier, got " + shown);
			context.takeScreenshot("jtest_jl3_vip_tier_up");
			context.waitFor(mc -> VipClientModule.tierUpShown() < 0, 120);

			// ---- Debt Collectors' arrival ----------------------------------------------------------------------------
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				Vec3 at = p.position();
				ServerPlayNetworking.send(p, new LoanFxPayload(p.getUUID(), at, List.of(at.add(3, 0, -2), at.add(4, 0, 0), at.add(3, 0, 2))));
			});
			context.waitFor(mc -> LoanClientModule.arrivalActive(), 60);
			context.waitTicks(10);
			context.takeScreenshot("jtest_jl3_collectors");
			context.waitTicks(60);

			// ---- casino scene: bot nameplates, attract fronts, roofline marquee ---------------------------------------
			world.getServer().runOnServer(server -> seatBots(server, base));
			int casinos = context.computeOnClient(mc -> WorldgenClientModule.previewAttract(new CasinoViewPayload(List.of(
				new CasinoViewPayload.Casino(CasinoKind.VILLAGE_CASINO.ordinal(), base.getX() + 2, base.getY(), base.getZ() - 5, base.getX() + 9,
					base.getY() + 2, base.getZ() + 5)), 0)));
			if (casinos != 1) throw new AssertionError("attract view not applied");
			context.waitTicks(50);
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				BlockEntity be = p.level().getBlockEntity(base.offset(5, 0, 0));
				long plates = p.level().getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class, new net.minecraft.world.phys.AABB(base).inflate(12)).size();
				String occ = be instanceof BotTable t ? t.occupants().toString() : "none";
				if (plates == 0) throw new AssertionError("no bot nameplates at the stage table; occupants " + occ);
			});
			context.takeScreenshot("jtest_jl3_bot_plates_attract");
			// harden the world close (a loaded close can deadlock Test / Render / Server threads): no screen open, bots
			// gone, and a few ticks for the server to drain before the singleplayer context closes
			context.runOnClient(mc -> mc.gui.setScreen(null));
			// breaking the table takes its plates with it (no leaked text displays)
			world.getServer().runOnServer(server -> player(server).level().removeBlock(base.offset(5, 0, 0), false));
			context.waitTicks(50);
			world.getServer().runOnServer(server -> {
				long left = player(server).level().getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class,
					new net.minecraft.world.phys.AABB(base).inflate(12)).size();
				if (left != 0) throw new AssertionError("bot nameplates outlived their table: " + left);
			});
			context.waitTicks(20);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void flip(TestSingleplayerContext world, boolean heads) {
		world.getServer().runOnServer(server -> ServerPlayNetworking.send(player(server), new CoinFlipPayload(heads, false)));
	}

	/** A small stage in front of the player (facing east): stone floor, cashier, wheel, Plinko, a blackjack table. */
	private static BlockPos stage(TestSingleplayerContext world) {
		AtomicReference<BlockPos> out = new AtomicReference<>();
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.setGameMode(GameType.SURVIVAL);
			ServerLevel level = p.level();
			BlockPos feet = p.blockPosition();
			for (int x = -2; x <= 12; x++)
				for (int z = -7; z <= 7; z++) {
					level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
					for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(feet.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			face(level, feet.offset(6, 0, -3), CoreContent.CASHIER.block().defaultBlockState());
			face(level, feet.offset(6, 0, 3), ExtrasModule.WHEEL.block().defaultBlockState());
			face(level, feet.offset(6, 0, 4), ExtrasModule.PLINKO.block().defaultBlockState());
			face(level, feet.offset(5, 0, 0), dev.nezo.burmaldaholic.games.blackjack.BlackjackModule.TABLE.block().defaultBlockState());
			p.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90f, 12f);
			out.set(feet);
		});
		return out.get();
	}

	private static void face(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		level.setBlockAndUpdate(pos, state);
	}

	/** Seats three bots at the blackjack table (MIXED, as a table owner would) so their nameplates appear. */
	private static void seatBots(MinecraftServer server, BlockPos base) {
		ServerPlayer p = player(server);
		BlockEntity be = p.level().getBlockEntity(base.offset(5, 0, 0));
		if (!(be instanceof BotTable table) || table.tableBots() == null) throw new AssertionError("no bot table at the stage");
		TableBots bots = table.tableBots();
		bots.setKeeper(p.getUUID());
		if (be instanceof dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity t) t.sit(p); // a human at the table starts a session
		var change = bots.requestChange(p, new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.MIXED, true, false, BotSpeed.NORMAL), true);
		bots.safePoint(p.level());
		System.out.println("[jl3] bots seated at the stage table: " + bots.bots().size() + " (" + change + ")");
	}
}
