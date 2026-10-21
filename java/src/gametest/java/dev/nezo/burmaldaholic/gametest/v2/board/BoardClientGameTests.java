package dev.nezo.burmaldaholic.gametest.v2.board;

import dev.nezo.burmaldaholic.bots.client.BotTagVisibility;
import dev.nezo.burmaldaholic.client.board.BoardRenderer;
import dev.nezo.burmaldaholic.client.board.BoardStats;
import dev.nezo.burmaldaholic.client.board.PlateFrame;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The in-world board kit on the multi-block tables in a real client (docs/architecture/ui-v2.md §7, MT-DESIGN §2; lane
 * W, {@code BURMALDAHOLIC_CGT=W}). At 1920 × 1080, FOV 70, HUD hidden:
 * <ul>
 *   <li>a full blackjack table with the viewer and atmosphere bots, then the concept's mid-round demo, shot from 3 / 5 /
 *       8 blocks at 0° and 30° and from a raised eye: LOD, board text, plates disjoint, plates never over the dealer
 *       cards; carpet in front of the table keeps the full felt; RU at 5 m;</li>
 *   <li>bot nameplates hidden at 4 and 20 blocks; a plain text display beside the table keeps rendering;</li>
 *   <li>baccarat, Hold'em, UTH, roulette, craps (full tables, two bots each) and the machines (slot, wheel, Plinko,
 *       cashier) at 3 / 5 / 8 blocks;</li>
 *   <li>20 full tables: across tables the shown plates are disjoint ({@link PlateFrame#lastRects}), LOD counts, time;</li>
 *   <li>the TopBar gear of a full Hold'em table opens its Table settings (registerSettings, R-AB M6).</li>
 * </ul>
 * Screenshots {@code p2_w2_*}; report {@code screenshots/board_report.txt} (with each shot's cap height in px from the
 * text px actually used, for the ≥ 18 px @ 5 m / ≥ 13 px @ 8 m check, which the tester also measures on the PNGs).
 */
public class BoardClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("W") && !RealFlow.enabled("board")) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				BoardStats.setEnabled(true);
			});
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runCommand("gamerule doMobSpawning false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			context.runOnClient(mc -> {
				mc.options.fov().set(70);
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			BlockPos[] base = new BlockPos[1];
			world.getServer().runOnServer(server -> base[0] = player(server).blockPosition());
			step("blackjack", () -> blackjack(context, world, base[0].offset(0, 0, 8)));
			List<TableType<?>> games = List.of(BaccaratModule.TABLE, PokerModule.TABLE, UthModule.TABLE, RouletteModule.TABLE, CrapsModule.TABLE);
			for (int i = 0; i < games.size(); i++) {
				TableType<?> t = games.get(i);
				BlockPos at = base[0].offset(30 + 16 * i, 0, 8);
				step(t.name(), () -> game(context, world, t, at));
			}
			step("machines", () -> machines(context, world, base[0].offset(0, 0, 40)));
			step("perf_20", () -> perf(context, world, base[0].offset(-70, 0, 30)));
			step("gear", () -> gear(context, world, base[0].offset(0, 0, 70)));
		} finally {
			context.runOnClient(mc -> {
				BoardRenderer.clearDemos();
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("board failures: " + String.join(" | ", failures));
	}

	private void step(String name, Runnable body) {
		try {
			body.run();
			report.add("PASS " + name);
		} catch (AssertionError | RuntimeException e) {
			report.add("FAIL " + name + ": " + e.getMessage());
			failures.add(name + ": " + e.getMessage());
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.error("[board] {}", name, e);
		}
	}

	// ---- blackjack: live seats, the demo round from every distance, raised eye, carpet, bot tags, RU ------------------

	private void blackjack(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		floor(world, pos, 8, 8);
		world.getServer().runOnServer(server -> TableParts.placeFull(server.overworld(), pos, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.NORTH));
		context.waitTicks(3);
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
		shot(context, world, pos, "p2_w2_bj_live_5m_0", 5, 0, 0, true);
		botTags(context, world, pos);
		context.runOnClient(mc -> BoardRenderer.demo(pos, (be, s) -> BoardDemo.blackjack(be, s, 2)));
		for (double d : new double[] {3, 5, 8}) {
			for (int angle : new int[] {0, 30}) shot(context, world, pos, "p2_w2_bj_" + (int) d + "m_" + angle + "_en", d, angle, 0, true);
		}
		// the concept's camera: a raised eye looking down at the felt — the plates must stay off the dealer cards
		shot(context, world, pos, "p2_w2_bj_5m_0_high", 5, 0, 1.6, true);
		shot(context, world, pos, "p2_w2_bj_3m_30_high", 3, 30, 1.2, true);
		shot(context, world, pos, "p2_w2_bj_12m_0", 12, 0, 0, false);
		// straight down from above: the upright board fades out (visual QA round 1: a huge trapezoid)
		look(context, world, pos, 0.6, 0, 3.2);
		context.waitTicks(10);
		context.takeScreenshot("p2_w2_bj_top");
		// night (outdoor, no lamps): felt, print and cards darken with the furniture; plates and board stay readable
		world.getServer().runCommand("time set midnight");
		shot(context, world, pos, "p2_w2_bj_5m_30_night", 5, 30, 0, true);
		world.getServer().runCommand("time set noon");
		// carpet in the players' cells: the table still draws its full felt (no fallback)
		world.getServer().runOnServer(server -> {
			for (int dx = -1; dx <= 1; dx++) server.overworld().setBlockAndUpdate(pos.offset(dx, 0, -1), Blocks.MOSS_CARPET.defaultBlockState());
		});
		context.waitTicks(10);
		shot(context, world, pos, "p2_w2_bj_carpet_5m", 5, 20, 0, true);
		boolean full = context.computeOnClient(mc -> BoardStats.of(pos).full);
		if (!full) throw new AssertionError("carpet in front of a full table: the board fell back to the compact felt");
		// RU
		language(context, "ru_ru");
		shot(context, world, pos, "p2_w2_bj_5m_0_ru", 5, 0, 0, true);
		language(context, "en_us");
		context.runOnClient(mc -> BoardRenderer.demo(pos, null));
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity t) t.leave(player(server).getUUID(), CasinoTableBlockEntity.LeaveReason.LEFT);
		});
		context.waitTicks(5);
	}

	/** Bot nameplates: hidden at 4 and at 20 blocks (the board's plates are the one name system); plain text untouched. */
	private void botTags(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		double[] c = feltCentre(pos);
		world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:text_display %.2f %d %.2f {text:\"Welcome\",billboard:\"center\"}", c[0] - 2.2,
			pos.getY() + 2, c[1]));
		// bot plates exist only while a non-spectator player is near (BotAvatars skips spectators): watch in creative
		world.getServer().runCommand("gamemode creative @p");
		look(context, world, pos, 4, 0, 0);
		context.waitTicks(10);
		int[] near = tagCounts(context, pos);
		for (int waited = 0; waited < 60 && near[0] == 0; waited += 5) {
			context.waitTicks(5);
			near = tagCounts(context, pos);
		}
		context.takeScreenshot("p2_w2_bj_tags_4m");
		look(context, world, pos, 20, 0, 0);
		context.waitTicks(10);
		int[] far = tagCounts(context, pos);
		report.add(String.format(Locale.ROOT, "bot tags: at 4 m %d of %d bot plates hidden, plain hidden %d; at 20 m %d hidden, plain hidden %d", near[1], near[0],
			near[3], far[1], far[3]));
		if (near[0] == 0) throw new AssertionError("no bot nameplate text displays near the table");
		if (near[1] != near[0]) throw new AssertionError("bot tags not hidden at 4 m: " + near[1] + "/" + near[0]);
		if (far[1] != far[0]) throw new AssertionError("bot tags shown at 20 m (one name system near tables): " + far[1] + "/" + far[0]);
		world.getServer().runCommand("gamemode spectator @p");
		if (near[2] == 0) throw new AssertionError("the plain text display is missing");
		if (near[3] != 0 || far[3] != 0) throw new AssertionError("a plain text display beside the table was hidden");
	}

	/** {bot plates, bot plates hidden, plain displays, plain displays hidden} near the table. */
	private static int[] tagCounts(ClientGameTestContext context, BlockPos pos) {
		return context.computeOnClient(mc -> {
			int[] n = new int[4];
			if (mc.level == null) return n;
			for (Entity e : mc.level.getEntities((Entity) null, new AABB(pos).inflate(5), x -> x instanceof Display.TextDisplay)) {
				Display.TextDisplay t = (Display.TextDisplay) e;
				boolean bot = BotTagVisibility.isBotPlate(t.getText());
				boolean hidden = BotTagVisibility.hidden(t);
				n[bot ? 0 : 2]++;
				if (hidden) n[bot ? 1 : 3]++;
			}
			return n;
		});
	}

	// ---- the other games: full tables, two bots each ----------------------------------------------------------------

	private void game(ClientGameTestContext context, TestSingleplayerContext world, TableType<?> type, BlockPos pos) {
		floor(world, pos, 8, 8);
		world.getServer().runCommand("gamemode creative @p");
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			TableParts.placeFull(level, pos, (MultiBlockTableBlock) type.block(), Direction.NORTH);
			if (!(level.getBlockEntity(pos) instanceof CasinoTableBlockEntity be)) return;
			ServerPlayer p = player(server);
			if (type == PokerModule.TABLE) {
				p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
				CompoundTag args = new CompoundTag();
				args.putString("level", "micro");
				args.putLong("amount", 200);
				be.onAction(p, "buy_in", args);
			}
			if (be instanceof BotTable table) {
				for (int i = 0; i < 2; i++) {
					BotProfile prof = new BotProfile("w2c" + i, i == 0 ? "creeper42" : "axolotl_al", BotDifficulty.NORMAL, Personality.TAG);
					boolean money = table.botRole() == BotRole.MONEY;
					table.seatBot(new SeatOccupant.Bot(prof, table.botRole(), money ? Purse.BANK : Purse.NONE), money ? Math.max(200, table.botBuyIn()) : 0);
				}
				be.setChanged();
				BlockState st = level.getBlockState(pos);
				level.sendBlockUpdated(pos, st, st, 3);
			}
		});
		context.waitTicks(40);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runCommand("gamemode spectator @p");
		String id = type.name().replace("_table", "");
		for (double d : new double[] {3, 5, 8}) {
			for (int angle : new int[] {0, 30}) shot(context, world, pos, "p2_w2_" + id + "_" + (int) d + "m_" + angle + "_en", d, angle, 0, false);
		}
		boolean full = context.computeOnClient(mc -> BoardStats.of(pos).full);
		if (!full) throw new AssertionError(id + ": not drawn as a full table");
	}

	// ---- machines -----------------------------------------------------------------------------------------------------

	private void machines(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin) {
		floor(world, origin.offset(8, 0, 0), 14, 6);
		List<TableType<?>> kinds = List.of(SlotsModule.MACHINES.get(Tier.values()[0]), ExtrasModule.WHEEL, ExtrasModule.PLINKO, CoreContent.CASHIER);
		double[] cy = {1.2, 1.6, 1.6, 1.2};
		world.getServer().runOnServer(server -> {
			for (int i = 0; i < kinds.size(); i++) {
				TableParts.placeFull(server.overworld(), origin.offset(i * 6, 0, 0), (MultiBlockTableBlock) kinds.get(i).block(), Direction.NORTH);
			}
		});
		context.waitTicks(20);
		for (int i = 0; i < kinds.size(); i++) {
			BlockPos at = origin.offset(i * 6, 0, 0);
			String id = kinds.get(i).name();
			for (double d : new double[] {3, 5, 8}) lookAt(context, world, at.getX() + 0.5, at.getY() + cy[i], at.getZ() + 0.5, d, 0, 0, "p2_w2_m_" + id + "_" + (int) d + "m_0");
			lookAt(context, world, at.getX() + 0.5, at.getY() + cy[i], at.getZ() + 0.5, 5, 30, 0, "p2_w2_m_" + id + "_5m_30");
		}
	}

	// ---- perf + cross-table plates: 20 full tables ----------------------------------------------------------------------

	private void perf(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin) {
		List<BlockPos> tables = new ArrayList<>();
		for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) tables.add(origin.offset(col * 5, 0, row * 5));
		floor(world, origin.offset(10, 0, 8), 14, 12);
		world.getServer().runOnServer(server -> {
			for (BlockPos p : tables) TableParts.placeFull(server.overworld(), p, (MultiBlockTableBlock) BlackjackModule.TABLE.block(), Direction.NORTH);
		});
		context.runOnClient(mc -> {
			for (BlockPos p : tables) BoardRenderer.demo(p, (be, s) -> BoardDemo.blackjack(be, s, -1));
		});
		context.waitTicks(10);
		double cx = origin.getX() + 10.5;
		double cz = origin.getZ() - 4.0;
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f facing %.2f %d %.2f", cx, origin.getY() + 2, cz, cx, origin.getY(),
			origin.getZ() + 8.0));
		context.waitTicks(30);
		context.runOnClient(mc -> BoardStats.clear());
		context.waitTicks(2);
		long sum = 0;
		int samples = 0;
		int[] lods = new int[4];
		int maxPlates = 0;
		for (int i = 0; i < 40; i++) {
			context.waitTicks(1);
			long[] r = context.computeOnClient(mc -> {
				long ns = 0;
				long[] o = new long[6];
				for (var e : BoardStats.snapshot().values()) {
					if (e.lod == null) continue;
					ns += e.nanos;
					o[1 + e.lod.ordinal()]++;
					o[5] = Math.max(o[5], e.plates);
				}
				o[0] = ns;
				return o;
			});
			sum += r[0];
			samples++;
			for (int k = 0; k < 4; k++) lods[k] = Math.max(lods[k], (int) r[1 + k]);
			maxPlates = Math.max(maxPlates, (int) r[5]);
		}
		float[] rects = context.computeOnClient(mc -> PlateFrame.lastRects());
		int hidden = context.computeOnClient(mc -> PlateFrame.lastHidden());
		context.takeScreenshot("p2_w2_perf_20");
		// night: the furniture, the felt print and the cards darken with the world; boards and plates stay readable
		world.getServer().runCommand("time set midnight");
		context.waitTicks(20);
		context.takeScreenshot("p2_w2_perf_20_night");
		world.getServer().runCommand("time set noon");
		double ms = sum / (double) samples / 1e6;
		int overlaps = 0;
		for (int a = 0; a < rects.length / 4; a++) {
			for (int b = a + 1; b < rects.length / 4; b++) {
				boolean apart = rects[a * 4 + 2] <= rects[b * 4] + 1e-5 || rects[b * 4 + 2] <= rects[a * 4] + 1e-5 || rects[a * 4 + 3] <= rects[b * 4 + 1] + 1e-5
					|| rects[b * 4 + 3] <= rects[a * 4 + 1] + 1e-5;
				if (!apart) overlaps++;
			}
		}
		report.add(String.format(Locale.ROOT, "perf: LOD full %d / board %d / no-text %d / model %d; extract+submit %.3f ms per frame (all tables); max plates %d;"
			+ " shown plates %d, faded (hidden by a nearer table) %d, overlapping pairs %d", lods[0], lods[1], lods[2], lods[3], ms, maxPlates, rects.length / 4, hidden,
			overlaps));
		if (lods[0] + lods[1] + lods[2] < 15) throw new AssertionError("perf scene: only " + (lods[0] + lods[1] + lods[2]) + " tables drawn");
		if (overlaps > 0) throw new AssertionError("perf scene: " + overlaps + " pairs of shown plates overlap across tables");
		if (maxPlates > 8) throw new AssertionError("perf scene: a table exceeded the plate budget");
		context.runOnClient(mc -> BoardRenderer.clearDemos());
	}

	// ---- the bots gear on a full Hold'em table ------------------------------------------------------------------------------

	private void gear(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
		floor(world, pos, 6, 6);
		world.getServer().runCommand("gamemode creative @p");
		world.getServer().runOnServer(server -> {
			TableParts.placeFull(server.overworld(), pos, (MultiBlockTableBlock) PokerModule.TABLE.block(), Direction.NORTH);
			player(server).connection.teleport(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.6, 0f, 30f);
		});
		context.waitTicks(10);
		dev.nezo.burmaldaholic.gametest.realflow.RealFlow.useBlock(context, pos);
		dev.nezo.burmaldaholic.gametest.realflow.RealFlow.awaitScreen(context, dev.nezo.burmaldaholic.games.poker.client.PokerScreen.class, 60);
		context.waitTicks(20);
		double[] at = context.computeOnClient(mc -> v2Point(mc, "top.settings"));
		if (at == null) throw new AssertionError("no TopBar gear on a bot table: " + dev.nezo.burmaldaholic.gametest.realflow.RealFlow.describe(context));
		context.takeScreenshot("p2_w2_gear_poker");
		dev.nezo.burmaldaholic.gametest.realflow.RealFlow.clickWindow(context, at[0], at[1]);
		dev.nezo.burmaldaholic.gametest.realflow.RealFlow.awaitScreen(context, dev.nezo.burmaldaholic.bots.client.TableSettingsScreen.class, 40);
		report.add("gear: full Hold'em TopBar gear at window " + Math.round(at[0]) + "," + Math.round(at[1]) + " -> Table settings");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
	}

	private static double @org.jspecify.annotations.Nullable [] v2Point(Minecraft mc, String id) {
		if (!(mc.gui.screen() instanceof dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder h)) return null;
		var chrome = h.chrome();
		for (var t : chrome.hits().targets()) {
			if (!t.id().equals(id) || !t.enabled()) continue;
			var vp = chrome.viewport();
			return new double[] {vp.toWindow(t.box().x() + t.box().w() / 2.0), vp.toWindow(t.box().y() + t.box().h() / 2.0)};
		}
		return null;
	}

	// ---- shots ------------------------------------------------------------------------------------------------------

	/** World x, z of a NORTH-facing full table's felt centre (footprint-local (0.5, 0)). */
	private static double[] feltCentre(BlockPos pos) {
		double[] w = TableFootprint.toWorld(0.5, 0.0, Direction.NORTH.getStepX(), Direction.NORTH.getStepZ());
		return new double[] {pos.getX() + w[0], pos.getZ() + w[1]};
	}

	/** Camera {@code d} blocks from the felt centre, {@code angle}° round from the players' side, eye 1.62 (+ raise). */
	private static void look(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, double d, int angle, double raise) {
		double[] c = feltCentre(pos);
		double a = Math.toRadians(angle);
		double cx = c[0] + d * Math.sin(a);
		double cz = c[1] - d * Math.cos(a);
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f facing %.3f %.3f %.3f", cx, pos.getY() + raise, cz, c[0], pos.getY() + 1.05, c[1]));
	}

	private void lookAt(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z, double d, int angle, double raise, String name) {
		double a = Math.toRadians(angle);
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f facing %.3f %.3f %.3f", x + d * Math.sin(a), Math.floor(y) + raise - 0.4,
			z - d * Math.cos(a), x, y, z));
		context.waitTicks(10);
		context.takeScreenshot(name);
		report.add(name);
	}

	private void shot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, String name, double d, int angle, double raise, boolean plates) {
		look(context, world, pos, d, angle, raise);
		context.waitTicks(10);
		context.takeScreenshot(name);
		Snap t = context.computeOnClient(mc -> Snap.of(BoardStats.of(pos)));
		double fov = context.computeOnClient(mc -> HoloScale.effectiveFov(mc.gameRenderer.mainCamera().getFov(), mc.options.fov().get()));
		int h = context.computeOnClient(mc -> mc.getWindow().getHeight());
		double plateCap = HoloScale.capPx(t.platePx(), t.dist(), fov, h), boardCap = HoloScale.capPx(t.boardPx(), t.dist(), fov, h);
		report.add(String.format(Locale.ROOT, "%s: %s; felt dist %.2f, plate cap %.1f px, board cap %.1f px (fov %.1f, %d px)", name, t, t.dist(), plateCap, boardCap, fov, h));
		if (d <= 8) {
			if (t.lod() != HoloScale.Lod.FULL) throw new AssertionError(name + ": LOD " + t.lod() + " at " + d + " blocks");
			if (t.lines() < 1) throw new AssertionError(name + ": no board text at " + d + " blocks");
			if (plates && t.plates() < 1) throw new AssertionError(name + ": no plates at " + d + " blocks");
			float[] r = t.plateRects();
			for (int i = 0; i < t.plateRectCount(); i++) {
				for (int j = i + 1; j < t.plateRectCount(); j++) {
					boolean apart = r[i * 4 + 2] <= r[j * 4] + 1e-4 || r[j * 4 + 2] <= r[i * 4] + 1e-4 || r[i * 4 + 3] <= r[j * 4 + 1] + 1e-4
						|| r[j * 4 + 3] <= r[i * 4 + 1] + 1e-4;
					if (!apart) throw new AssertionError(name + ": plates " + i + " and " + j + " overlap");
				}
				// never over the dealer cards: a plate overlapping the dealer row in u sits above its top
				if (!Float.isNaN(t.dealerTop()) && r[i * 4 + 2] > t.dealerU0() && r[i * 4] < t.dealerU1() && r[i * 4 + 1] < t.dealerTop() - 1e-4) {
					throw new AssertionError(name + ": plate " + i + " covers the dealer cards");
				}
			}
			double want = HoloScale.capTarget(t.dist()); // the real camera → felt distance (a raised eye is farther than d)
			if (plateCap < want - 0.5 || boardCap < want - 0.5) {
				throw new AssertionError(String.format(Locale.ROOT, "%s: cap height plate %.1f / board %.1f px < %.1f", name, plateCap, boardCap, want));
			}
		} else {
			if (t.plates() != 0) throw new AssertionError(name + ": plates beyond 8 blocks");
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private record Snap(HoloScale.Lod lod, int feltQuads, int cardQuads, int chipQuads, int lines, int plates, int leaders, long nanos, float[] plateRects,
			int plateRectCount, float dealerU0, float dealerU1, float dealerTop, float platePx, float boardPx, double dist, boolean full) {
		static Snap of(BoardStats.Table t) {
			return new Snap(t.lod, t.feltQuads, t.cardQuads, t.chipQuads, t.lines, t.plates, t.leaders, t.nanos, t.plateRects.clone(), t.plateRectCount, t.dealerU0,
				t.dealerU1, t.dealerTop, t.platePx, t.boardPx, t.dist, t.full);
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "lod %s, full %b, felt quads %d, card quads %d, chip quads %d, lines %d, plates %d, leaders %d, %.3f ms", lod, full,
				feltQuads, cardQuads, chipQuads, lines, plates, leaders, nanos / 1e6);
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

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.waitTicks(10);
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
