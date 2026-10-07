package dev.nezo.burmaldaholic.gametest.v2.extras;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The in-world Plinko and Wheel of Fortune machines (lane E, ui-v2.md §7.6; REDESIGN.md §9.1 "Plinko gets an upright
 * board"): a Plinko machine and a wheel side by side facing the player, captured idle, mid-drop / mid-spin and after the
 * landing, at 3, 5 and 8 blocks (screenshots {@code v2e_world_<view>}). Asserts that the public face reached the client
 * (the plate's name, the dropped risk's bins). Opt-in with {@code BURMALDAHOLIC_CGT=E} (or unset = run).
 */
public class ExtrasBoardClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!LaneFilter.on("E")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 5000");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			world.getServer().runCommand("time set noon");
			BlockPos[] at = new BlockPos[2];
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				BlockPos base = p.blockPosition();
				at[0] = base.offset(-1, 0, -3);
				at[1] = base.offset(1, 0, -3);
				// the casino layout: machines two blocks apart, facing the room (south = towards the player)
				server.overworld().setBlockAndUpdate(at[0], ExtrasModule.PLINKO.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.SOUTH));
				server.overworld().setBlockAndUpdate(at[1].east(), ExtrasModule.WHEEL.block().defaultBlockState()
					.setValue(CasinoTableBlock.FACING, Direction.SOUTH));
				at[1] = at[1].east();
				p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
			});
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				if (mc.player != null) {
					mc.player.setYRot(180f);
					mc.player.setXRot(-8f);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("v2e_world_idle_3");
			world.getServer().runOnServer(server -> {
				CompoundTag drop = chips(20);
				drop.putString("risk", "high");
				action(server, at[0], "drop", drop);
				action(server, at[1], "spin", chips(50));
			});
			context.waitTicks(30);
			context.takeScreenshot("v2e_world_running_3");
			context.runOnClient(mc -> CelebrationOverlay.get().clear());
			context.waitTicks(60);
			context.runOnClient(mc -> CelebrationOverlay.get().clear());
			context.waitTicks(5);
			context.takeScreenshot("v2e_world_landed_3");
			String[] names = context.computeOnClient(mc -> new String[] {
				mc.level.getBlockEntity(at[0]) instanceof PlinkoBlockEntity pl ? pl.machinePub().name() + "/" + pl.machinePub().centi().length : "-",
				mc.level.getBlockEntity(at[1]) instanceof WheelBlockEntity w ? w.lastPlayerName() : "-"});
			String me = context.computeOnClient(mc -> mc.player.getGameProfile().name());
			if (!names[0].equals(me + "/13")) throw new AssertionError("plinko public face on the client: " + names[0]);
			if (!names[1].equals(me)) throw new AssertionError("wheel plate name on the client: " + names[1]);
			for (int d : new int[] {5, 8}) {
				int dz = d - 3;
				world.getServer().runOnServer(server -> {
					ServerPlayer p = player(server);
					p.teleportTo(p.getX(), p.getY(), p.getZ() + dz);
				});
				context.waitTicks(10);
				context.runOnClient(mc -> {
					if (mc.player != null) {
						mc.player.setYRot(180f);
						mc.player.setXRot(-6f);
					}
				});
				context.waitTicks(5);
				context.takeScreenshot("v2e_world_landed_" + d);
				world.getServer().runOnServer(server -> {
					ServerPlayer p = player(server);
					p.teleportTo(p.getX(), p.getY(), p.getZ() - dz);
				});
			}
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(10);
		}
	}

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	static void action(MinecraftServer server, BlockPos pos, String action, CompoundTag args) {
		if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) table.onAction(player(server), action, args);
	}
}
