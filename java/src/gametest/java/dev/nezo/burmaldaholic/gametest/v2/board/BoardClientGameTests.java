package dev.nezo.burmaldaholic.gametest.v2.board;

import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardStats;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The in-world board kit in a real client (docs/architecture/ui-v2.md §11.4, lane W): a blackjack table with the viewer
 * and atmosphere bots seated (public seat header → plates), then the concept's mid-round demo state, shot from 3 / 5 / 8
 * / 12 blocks at 0°, 30° and 60° to the side at 1920 × 1080 (FOV 70); a Hold'em table through the legacy
 * {@code CardTableRenderer} path; and a 20-table perf scene. Asserts, from the renderer's own {@link BoardStats}: the
 * board is drawn at 8 blocks, plates exist ≤ 8 and are disjoint in the camera plane, no text at 12 beyond the board
 * lines, per-table counts within the budget. Screenshots {@code board_*}; report {@code screenshots/board_report.txt}.
 * Run alone with {@code BURMALDAHOLIC_CGT=W}.
 */
public class BoardClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		String only = System.getenv("BURMALDAHOLIC_CGT");
		if (only != null && !only.isEmpty() && !only.equalsIgnoreCase("W") && !only.equalsIgnoreCase("board")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			context.runOnClient(mc -> {
				mc.options.fov().set(70);
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			BlockPos[] base = new BlockPos[1];
			world.getServer().runOnServer(server -> base[0] = player(server).blockPosition().offset(0, 0, 0));
			BlockPos table = base[0].offset(0, 0, 8);
			blackjack(context, world, table);
			BlockPos poker = base[0].offset(24, 0, 8);
			poker(context, world, poker);
			perf(context, world, base[0].offset(-60, 0, 30));
		} finally {
			context.runOnClient(mc -> {
				BoardRenderer.clearDemos();
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			write(context);
		}
	}

	// ---- blackjack: live seat header, then the demo round from every distance / angle --------------------------------

	private void blackjack(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		floor(world, pos, 6, 6);
		world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos,
			BlackjackModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH)));
		context.waitTicks(3);
		// the viewer sits and the table seats three atmosphere bots: the seat header must reach the client
		world.getServer().runCommand("gamemode creative @p");
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) server.overworld().getBlockEntity(pos);
			p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
			t.sit(p);
			t.bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
			t.botSafePointForTests();
		});
		int seated = 0;
		for (int waited = 0; waited < 100 && seated < 2; waited += 5) {
			context.waitTicks(5);
			seated = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos) instanceof CasinoTableBlockEntity be ? count(be.clientSeats())
				: 0);
		}
		report.add("blackjack live: seat header on the client = " + seated + " occupied");
		if (seated < 2) throw new AssertionError("the public seat header did not reach the client (seated " + seated + ")");
		world.getServer().runCommand("gamemode spectator @p");
		shot(context, world, pos, "board_bj_live_5m_0", 5, 0, true);
		// the demo round (the concept's state; names from the header where seated)
		context.runOnClient(mc -> BoardRenderer.demo(pos, (be, s) -> BoardDemo.blackjack(be, s, 2)));
		for (double d : new double[] {3, 5, 8}) {
			for (int angle : new int[] {0, 30, 60}) shot(context, world, pos, "board_bj_" + (int) d + "m_" + angle, d, angle, true);
		}
		shot(context, world, pos, "board_bj_5m_back", 5, 160, true);
		shot(context, world, pos, "board_bj_12m_0", 12, 0, false);
		// a wall beside the table: the overhang falls back to the block top
		world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos.west(), Blocks.STONE_BRICKS.defaultBlockState()));
		context.waitTicks(50);
		shot(context, world, pos, "board_bj_blocked_5m", 5, 20, true);
		world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos.west(), Blocks.AIR.defaultBlockState()));
		context.runOnClient(mc -> BoardRenderer.demo(pos, null));
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity t) t.leave(player(server).getUUID(), CasinoTableBlockEntity.LeaveReason.LEFT);
		});
		context.waitTicks(5);
	}

	/** Hold'em through the legacy {@code CardTableRenderer} path (lane B ports it later). */
	private void poker(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		floor(world, pos, 6, 6);
		world.getServer().runCommand("gamemode creative @p");
		world.getServer().runOnServer(server -> {
			server.overworld().setBlockAndUpdate(pos, PokerModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
			ServerPlayer p = player(server);
			p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity be) {
				p.openMenu(be);
				CompoundTag args = new CompoundTag();
				args.putString("level", "micro");
				args.putLong("amount", 200);
				be.onAction(p, "buy_in", args);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runCommand("gamemode spectator @p");
		context.waitTicks(160);
		shot(context, world, pos, "board_poker_4m_20", 4, 20, false);
		world.getServer().runOnServer(server -> player(server).closeContainer());
	}

	// ---- perf: 20 tables in view ------------------------------------------------------------------------------------

	private void perf(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin) {
		List<BlockPos> tables = new ArrayList<>();
		floor(world, origin.offset(6, 0, 7), 12, 12);
		world.getServer().runOnServer(server -> {
			for (int row = 0; row < 4; row++) {
				for (int col = 0; col < 5; col++) {
					BlockPos p = origin.offset(col * 3, 0, row * 4);
					server.overworld().setBlockAndUpdate(p, BlackjackModule.TABLE.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
				}
			}
		});
		for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) tables.add(origin.offset(col * 3, 0, row * 4));
		context.runOnClient(mc -> {
			for (BlockPos p : tables) BoardRenderer.demo(p, (be, s) -> BoardDemo.blackjack(be, s, -1));
		});
		context.waitTicks(10);
		// stand in front of the first row, looking over the room
		double cx = origin.getX() + 6.5;
		double cz = origin.getZ() - 5.0;
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f facing %.2f %d %.2f", cx, origin.getY() + 2, cz, cx, origin.getY(),
			origin.getZ() + 6.0));
		context.waitTicks(20);
		context.runOnClient(mc -> BoardStats.clear());
		context.waitTicks(2);
		long sum = 0;
		int samples = 0;
		int drawn = 0, maxCards = 0, maxChips = 0, maxLines = 0, maxPlates = 0;
		for (int i = 0; i < 60; i++) {
			context.waitTicks(1);
			long[] r = context.computeOnClient(mc -> {
				long ns = 0;
				int n = 0, c = 0, ch = 0, l = 0, pl = 0;
				for (var e : BoardStats.snapshot().values()) {
					if (e.lod == null) continue;
					n++;
					ns += e.nanos;
					c = Math.max(c, e.cardQuads);
					ch = Math.max(ch, e.chipQuads);
					l = Math.max(l, e.lines);
					pl = Math.max(pl, e.plates);
				}
				return new long[] {ns, n, c, ch, l, pl};
			});
			sum += r[0];
			samples++;
			drawn = Math.max(drawn, (int) r[1]);
			maxCards = Math.max(maxCards, (int) r[2]);
			maxChips = Math.max(maxChips, (int) r[3]);
			maxLines = Math.max(maxLines, (int) r[4]);
			maxPlates = Math.max(maxPlates, (int) r[5]);
		}
		context.takeScreenshot("board_perf_20");
		double ms = sum / (double) samples / 1e6;
		report.add(String.format(Locale.ROOT, "perf: %d tables drawn; extract+submit %.3f ms per sampled frame (all tables, software GL); max per table: card quads %d,"
			+ " chip quads %d, lines %d, plates %d", drawn, ms, maxCards, maxChips, maxLines, maxPlates));
		if (drawn < 15) throw new AssertionError("perf scene: only " + drawn + " tables drawn");
		if (maxCards > 64 || maxChips > 300 || maxLines > 19 || maxPlates > 8) throw new AssertionError("perf scene: a table exceeded the budget");
		context.runOnClient(mc -> BoardRenderer.clearDemos());
	}

	// ---- shots ------------------------------------------------------------------------------------------------------

	/**
	 * Puts the camera {@code d} blocks from the felt centre, {@code angle}° around the table from its front, at eye
	 * height, looking at the felt; shoots; checks the stats of the table at {@code pos}.
	 */
	private void shot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, String name, double d, int angle, boolean plates) {
		double a = Math.toRadians(angle);
		double cx = pos.getX() + 0.5 + d * Math.sin(a);
		double cz = pos.getZ() + 0.5 - d * Math.cos(a);
		double eye = pos.getY() + 0.0; // spectator feet at the floor: eye 1.62 above, like a standing player
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f facing %.3f %.3f %.3f", cx, eye, cz, pos.getX() + 0.5, pos.getY() + 1.15,
			pos.getZ() + 0.5));
		context.waitTicks(8);
		context.takeScreenshot(name);
		Snap t = context.computeOnClient(mc -> Snap.of(BoardStats.of(pos)));
		report.add(name + ": " + t);
		if (d <= 8) {
			if (t.lod() != HoloScale.Lod.FULL) throw new AssertionError(name + ": LOD " + t.lod() + " at " + d + " blocks");
			if (t.lines() < 1) throw new AssertionError(name + ": no board text at " + d + " blocks");
			if (plates && t.plates() < 1) throw new AssertionError(name + ": no plates at " + d + " blocks");
			for (int i = 0; i < t.plateRectCount(); i++) {
				for (int j = i + 1; j < t.plateRectCount(); j++) {
					float[] r = t.plateRects();
					boolean apart = r[i * 4 + 2] <= r[j * 4] + 1e-4 || r[j * 4 + 2] <= r[i * 4] + 1e-4 || r[i * 4 + 3] <= r[j * 4 + 1] + 1e-4
						|| r[j * 4 + 3] <= r[i * 4 + 1] + 1e-4;
					if (!apart) throw new AssertionError(name + ": plates " + i + " and " + j + " overlap");
				}
			}
		} else {
			if (t.plates() != 0) throw new AssertionError(name + ": plates beyond 8 blocks");
			if (d <= 16 && t.lines() < 1) throw new AssertionError(name + ": no board lines at " + d + " blocks");
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** A copy of one table's stats taken on the render thread. */
	private record Snap(HoloScale.Lod lod, int feltQuads, int cardQuads, int chipQuads, int lines, int plates, int leaders, long nanos, float[] plateRects,
			int plateRectCount) {
		static Snap of(BoardStats.Table t) {
			return new Snap(t.lod, t.feltQuads, t.cardQuads, t.chipQuads, t.lines, t.plates, t.leaders, t.nanos, t.plateRects.clone(), t.plateRectCount);
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "lod %s, felt quads %d, card quads %d, chip quads %d, lines %d, plates %d, leaders %d, %.3f ms", lod, feltQuads,
				cardQuads, chipQuads, lines, plates, leaders, nanos / 1e6);
		}
	}

	private static void floor(TestSingleplayerContext world, BlockPos centre, int rx, int rz) {
		world.getServer().runOnServer(server -> {
			var level = server.overworld();
			for (int dx = -rx - 8; dx <= rx + 8; dx++) {
				for (int dz = -rz - 14; dz <= rz + 14; dz++) {
					for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(centre.offset(dx, -1, dz), (dx + dz & 1) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
						: Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
		});
	}

	private static int count(TablePublic.SeatPub[] seats) {
		int n = 0;
		for (TablePublic.SeatPub s : seats) if (s != null) n++;
		return n;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("board_report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
