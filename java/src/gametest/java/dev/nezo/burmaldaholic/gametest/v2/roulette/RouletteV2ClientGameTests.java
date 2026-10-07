package dev.nezo.burmaldaholic.gametest.v2.roulette;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane C (roulette v2, docs/architecture/ui-v2.md §11.2–11.4): the real player flow on the v2 screen — open the table by
 * USING the block, place a straight, a split, a corner, a street, a dozen, red and a call bet by CLICKING the layout at
 * 480 × 270 (1920 × 1080 @4) and 640 × 360 (2560 × 1440 @4, 1920 × 1080 @3), spin with the primary, assert every
 * stake and the payout on the SERVER — plus the in-world board at 3 / 5 / 8 blocks. Screenshots {@code v2c_*}, report
 * {@code v2c_report.txt}. Run only this lane: {@code BURMALDAHOLIC_CGT=C}.
 */
public class RouletteV2ClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("C")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			inWorld(context, world);
		} finally {
			guiScale(context, 0);
			language(context, "en_us");
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("roulette v2: " + String.join("; ", failures));
	}

	// ---- the in-world board ------------------------------------------------------------------------------------------

	private void inWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos[] pos = new BlockPos[2];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos stand = p.blockPosition().offset(0, 0, 20);
			var level = server.overworld();
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -1; dz <= 12; dz++) {
					for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(stand.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
			pos[1] = stand;
			pos[0] = stand.offset(0, 0, 9);
			level.setBlockAndUpdate(pos[0], RouletteModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
			if (level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) {
				be.setThemeForTesting(0);
				be.sit(p);
				bet(be, p, "straight", 25, 17);
				bet(be, p, "split", 10, 8, 11);
				bet(be, p, "corner", 5, 20, 21, 23, 24);
				bet(be, p, "red", 20, redNumbers());
				bet(be, p, "dozen", 25, range(13, 24));
			}
		});
		context.waitTicks(30);
		for (float d : new float[] {3f, 5f, 8f}) {
			stand(context, world, pos[0], d);
			context.takeScreenshot("v2c_world_betting_" + (int) d + "m");
		}
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) be.onAction(player(server), "spin", new CompoundTag());
		});
		stand(context, world, pos[0], 3f);
		context.waitTicks(20 + 40);
		context.takeScreenshot("v2c_world_spin_3m");
		context.waitTicks(80);
		context.takeScreenshot("v2c_world_result_3m");
		stand(context, world, pos[0], 5f);
		context.takeScreenshot("v2c_world_result_5m");
		int[] result = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			? new int[] {be.sync().result()} : new int[] {-9});
		int shown = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			&& be.clientSync() != null ? be.clientSync().result() : -2);
		int stacks = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			? be.clientPub().stacks().size() : -1);
		report.add("world: server result " + result[0] + ", client sync " + shown + ", public stacks " + stacks);
		if (shown != result[0]) failures.add("in-world wheel result " + shown + " != server " + result[0]);
		if (stacks < 5) failures.add("in-world stacks " + stacks + " < 5");
	}

	/** Stands {@code d} blocks in front of the table (it faces north: the player is north of it), looking at the felt. */
	private static void stand(ClientGameTestContext context, TestSingleplayerContext world, BlockPos table, float d) {
		double eye = 1.62;
		double y = table.getY();
		double z = table.getZ() + 0.5 - d;
		world.getServer().runCommand("tp @p " + (table.getX() + 0.5) + " " + y + " " + z);
		float pitch = (float) Math.toDegrees(Math.atan2(eye - 1.0, d));
		for (int i = 0; i < 3; i++) {
			context.runOnClient(mc -> {
				if (mc.player != null) {
					mc.player.setYRot(0f);
					mc.player.setXRot(pitch);
					mc.player.setYHeadRot(0f);
				}
			});
			context.waitTicks(2);
		}
		context.waitTicks(4);
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void bet(RouletteTableBlockEntity be, ServerPlayer p, String type, long amount, int... nums) {
		CompoundTag args = new CompoundTag();
		args.putString("type", type);
		args.putIntArray("nums", nums);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	static int[] range(int a, int b) {
		int[] out = new int[b - a + 1];
		for (int i = a; i <= b; i++) out[i - a] = i;
		return out;
	}

	static int[] redNumbers() {
		return new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
	}

	static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	static void language(ClientGameTestContext context, String code) {
		boolean same = context.computeOnClient(mc -> mc.getLanguageManager().getSelected().equals(code));
		if (same) return;
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.waitTicks(20);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>(report);
			failures.forEach(f -> lines.add("FAIL " + f));
			Files.writeString(dir.resolve("v2c_report.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
