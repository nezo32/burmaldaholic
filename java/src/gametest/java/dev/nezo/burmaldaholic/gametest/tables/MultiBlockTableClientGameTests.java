package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * MT client GameTests (lane MT, {@code BURMALDAHOLIC_CGT=MT}): the multi-block tables in a real client world — every
 * 3×2 game in-world at 3 / 5 / 8 m from the players' side (straight and 30° off-axis), all four facings, the three
 * themes and a compact (legacy) table; right-clicking a far PART with the real use key opens the table screen of the
 * main block; walking into the table is stopped by its collision. Screenshots {@code mt_*}.
 */
public class MultiBlockTableClientGameTests implements FabricClientGameTest {
	/** Lane filter like v2 RealFlow.enabled: {@code BURMALDAHOLIC_CGT=A,MT} runs only those lanes; unset = all. */
	static boolean lane(String lane) {
		String f = System.getenv("BURMALDAHOLIC_CGT");
		return f == null || f.isBlank() || Arrays.stream(f.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(lane));
	}

	private record Show(String id, TableType<?> type) {}

	private static final List<Show> GAMES = List.of(
		new Show("blackjack", BlackjackModule.TABLE),
		new Show("baccarat", BaccaratModule.TABLE),
		new Show("poker", PokerModule.TABLE),
		new Show("uth", UthModule.TABLE),
		new Show("roulette", RouletteModule.TABLE),
		new Show("craps", CrapsModule.TABLE));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!lane("MT")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.getInput().resizeWindow(1280, 720); // owner-facing shots: a real 720p window
			context.waitTicks(40);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			BlockPos base = stage(world);
			context.getInput().pressKey(o -> o.keyToggleGui); // F1: clean in-world shots
			context.waitTicks(40);

			// ---- per game: 3 / 5 / 8 m straight on, 5 m at 30° ---------------------------------------------------------
			for (int i = 0; i < GAMES.size(); i++) {
				BlockPos main = base.offset(i * 7, 0, 0);
				for (int d : new int[] {3, 5, 8}) {
					look(world, main, d, 0);
					context.waitTicks(8);
					context.takeScreenshot("mt_" + GAMES.get(i).id() + "_" + d + "m");
				}
				look(world, main, 5, 30);
				context.waitTicks(8);
				context.takeScreenshot("mt_" + GAMES.get(i).id() + "_5m_30deg");
			}
			// ---- gallery: the whole row from above and behind ------------------------------------------------------
			world.getServer().runOnServer(server -> player(server).connection.teleport(base.getX() + 17.5, base.getY() + 7, base.getZ() + 12.5, 180f, 30f));
			context.waitTicks(15);
			context.takeScreenshot("mt_gallery_row");
			// ---- themes (high roller / player banked) and a compact legacy table -----------------------------------
			BlockPos themes = base.offset(0, 0, -12);
			look(world, themes.offset(4, 0, 0), 9, 0);
			context.waitTicks(10);
			context.takeScreenshot("mt_themes_and_compact");
			// ---- four facings of blackjack ------------------------------------------------------------------------
			BlockPos facings = base.offset(0, 0, 14);
			world.getServer().runOnServer(server -> player(server).connection.teleport(facings.getX() + 6.5, facings.getY() + 8, facings.getZ() - 3.5, 0f, 50f));
			context.waitTicks(15);
			context.takeScreenshot("mt_facings");
			// ---- MT-3 machines: slot bank, wheel, Plinko, cashier, Nether cashier, compact machines -----------------
			BlockPos machines = base.offset(0, 0, -22);
			for (int d : new int[] {5, 9}) {
				world.getServer().runOnServer(server -> player(server).connection.teleport(machines.getX() + 9.5, machines.getY(), machines.getZ() + 1 + d + 0.5,
					180f, d == 5 ? 12f : 8f));
				context.waitTicks(12);
				context.takeScreenshot("mt3_machines_" + d + "m");
			}
			for (int[] m : new int[][] {{1, 0}, {5, 1}, {9, 2}, {13, 3}}) {
				world.getServer().runOnServer(server -> player(server).connection.teleport(machines.getX() + m[0] + 0.5 + 2.5, machines.getY(),
					machines.getZ() + 5.5, 155f, 14f));
				context.waitTicks(10);
				context.takeScreenshot("mt3_machine_" + m[1] + "_angle");
			}
			// ---- night: emissive accents (brass bands, placards, tote board, bulbs, toppers, cashier lamps) ---------
			world.getServer().runCommand("time set midnight");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> player(server).connection.teleport(machines.getX() + 9.5, machines.getY(), machines.getZ() + 7.5, 180f, 10f));
			context.waitTicks(12);
			context.takeScreenshot("mt_night_machines");
			look(world, base, 4, 25);
			context.waitTicks(12);
			context.takeScreenshot("mt_night_blackjack");
			look(world, base.offset(28, 0, 0), 4, -25);
			context.waitTicks(12);
			context.takeScreenshot("mt_night_roulette");
			world.getServer().runOnServer(server -> player(server).connection.teleport(base.getX() + 17.5, base.getY() + 4, base.getZ() + 9.5, 180f, 22f));
			context.waitTicks(12);
			context.takeScreenshot("mt_night_row");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);

			// ---- real input: use key on the far back-left PART opens the main block's screen ------------------------
			context.getInput().pressKey(o -> o.keyToggleGui); // F1 again: crosshair back for the real-input checks
			BlockPos main = base;
			BlockPos far = TableFootprint.partPos(main, Direction.SOUTH, TablePart.BACK_LEFT);
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				// stand south-west of the table, look at the top of the back-left part
				Vec3 eye = new Vec3(far.getX() - 1.5, far.getY() + 1.62, far.getZ() + 3.5);
				Vec3 target = new Vec3(far.getX() + 0.5, far.getY() + 0.85, far.getZ() + 0.5);
				Vec3 dir = target.subtract(eye);
				float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				float pitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
				p.connection.teleport(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
			});
			context.waitTicks(10);
			String looked = context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult h
				? h.getBlockPos().toShortString() + " " + mc.level.getBlockState(h.getBlockPos()) : String.valueOf(mc.hitResult));
			context.takeScreenshot("mt_use_far_part_aim");
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(15);
			BlockPos opened = context.computeOnClient(mc -> mc.gui.screen() instanceof MenuAccess<?> s && s.getMenu() instanceof CasinoTableMenu m ? m.pos() : null);
			context.takeScreenshot("mt_use_far_part_screen");
			if (!main.equals(opened)) {
				throw new AssertionError("use key on the back-left part (looking at " + looked + ") should open the table at " + main.toShortString()
					+ ", opened " + opened);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(10);

			// ---- walking into the felt is stopped -----------------------------------------------------------------
			BlockPos walkMain = base.offset(7, 0, 0);
			world.getServer().runOnServer(server -> player(server).connection.teleport(walkMain.getX() + 0.5, walkMain.getY(), walkMain.getZ() + 3.5, 180f, 0f));
			context.waitTicks(10);
			context.getInput().holdKeyFor(o -> o.keyUp, 40);
			context.waitTicks(5);
			double z = context.computeOnClient(mc -> mc.player.getZ());
			if (z < walkMain.getZ() + 1 - 0.05) {
				throw new AssertionError("walked into the table: player z " + z + " < front edge " + (walkMain.getZ() + 1));
			}
			context.takeScreenshot("mt_walk_stopped");
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Camera on the players' (south) side of a table at {@code main}: {@code d} m from the front edge, {@code yawOff}° off-axis. */
	private static void look(TestSingleplayerContext world, BlockPos main, int d, int yawOff) {
		world.getServer().runOnServer(server -> {
			// felt centre of a south-facing table: x = main + 0.5, z = main (the main / back row boundary)
			double cx = main.getX() + 0.5;
			double cz = main.getZ();
			double a = Math.toRadians(yawOff);
			double dist = d + 1.0; // d metres from the front edge (main z + 1)
			double x = cx - Math.sin(a) * dist;
			double z = cz + Math.cos(a) * dist;
			float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z));
			float pitch = (float) Math.toDegrees(Math.atan2(1.62 - 0.9, dist));
			player(server).connection.teleport(x, main.getY(), z, yaw, pitch);
		});
	}

	/** Flat stage, the six games in a row facing south, theme row, compact table, four facings. */
	private static BlockPos stage(TestSingleplayerContext world) {
		AtomicReference<BlockPos> out = new AtomicReference<>();
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.setGameMode(GameType.CREATIVE);
			p.getAbilities().flying = false;
			ServerLevel level = p.level();
			BlockPos feet = p.blockPosition().offset(0, 0, 0);
			BlockPos base = feet.offset(2, 0, -4);
			for (int x = -8; x <= 46; x++)
				for (int z = -22; z <= 26; z++) {
					level.setBlock(base.offset(x, -1, z), (((x >> 1) + (z >> 1) & 1) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState(), 2);
					for (int y = 0; y <= 8; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			for (int i = 0; i < GAMES.size(); i++) {
				TableParts.placeFull(level, base.offset(i * 7, 0, 0), (MultiBlockTableBlock) GAMES.get(i).type().block(), Direction.SOUTH);
			}
			// themes: high roller + player banked + a compact v0.1 table
			BlockPos themes = base.offset(0, 0, -12);
			TableParts.placeFull(level, themes, (MultiBlockTableBlock) BlackjackModule.HIGH_ROLLER_TABLE.block(), Direction.SOUTH);
			TableParts.placeFull(level, themes.offset(4, 0, 0), (MultiBlockTableBlock) BaccaratModule.CHEMMY_TABLE.block(), Direction.SOUTH);
			TableParts.placeFull(level, themes.offset(8, 0, 0), (MultiBlockTableBlock) RouletteModule.HIGH_ROLLER_TABLE.block(), Direction.SOUTH);
			level.setBlock(themes.offset(11, 0, 0), BlackjackModule.TABLE.block().defaultBlockState().setValue(MultiBlockTableBlock.FACING, Direction.SOUTH), 3);
			// four facings of blackjack
			BlockPos facings = base.offset(0, 0, 14);
			Direction[] ds = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
			for (int i = 0; i < 4; i++) {
				TableParts.placeFull(level, facings.offset(1 + i * 4, 0, 0), (MultiBlockTableBlock) BlackjackModule.TABLE.block(), ds[i]);
			}
			// MT-3 machines row (facing south): a bank of three slots, wheel, Plinko, cashier, Nether cashier, compact v0.1 ones
			BlockPos machines = base.offset(0, 0, -22);
			for (int i = 0; i < 3; i++) {
				TableParts.placeFull(level, machines.offset(i, 0, 0),
					(MultiBlockTableBlock) dev.nezo.burmaldaholic.games.slots.SlotsModule.MACHINES.values().toArray(new TableType<?>[0])[i].block(), Direction.SOUTH);
			}
			TableParts.placeFull(level, machines.offset(5, 0, 0), (MultiBlockTableBlock) dev.nezo.burmaldaholic.games.extras.ExtrasModule.WHEEL.block(), Direction.SOUTH);
			TableParts.placeFull(level, machines.offset(9, 0, 0), (MultiBlockTableBlock) dev.nezo.burmaldaholic.games.extras.ExtrasModule.PLINKO.block(), Direction.SOUTH);
			TableParts.placeFull(level, machines.offset(13, 0, 0), (MultiBlockTableBlock) dev.nezo.burmaldaholic.core.CoreContent.CASHIER.block(), Direction.SOUTH);
			TableParts.placeFull(level, machines.offset(17, 0, 0), (MultiBlockTableBlock) dev.nezo.burmaldaholic.core.CoreContent.NETHER_CASHIER.block(), Direction.SOUTH);
			level.setBlock(machines.offset(20, 0, 0), dev.nezo.burmaldaholic.games.extras.ExtrasModule.WHEEL.block().defaultBlockState()
				.setValue(MultiBlockTableBlock.FACING, Direction.SOUTH), 3);
			level.setBlock(machines.offset(21, 0, 0), dev.nezo.burmaldaholic.games.extras.ExtrasModule.PLINKO.block().defaultBlockState()
				.setValue(MultiBlockTableBlock.FACING, Direction.SOUTH), 3);
			p.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 6.5, 180f, 10f);
			out.set(base);
		});
		return out.get();
	}
}
