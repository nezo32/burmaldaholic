package dev.nezo.burmaldaholic.gametest.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import dev.nezo.burmaldaholic.games.uth.UthTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.pvp.client.ClientPvp;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Design capture (docs/design/visual/v2/REDESIGN.md §1): every non-slot game screen at the resolutions real players use
 * — 1920 × 1080 and 2560 × 1440 windows at GUI scale 4 (480 × 270 and 640 × 360 GUI px) — with a round in progress,
 * plus the slot machine as the quality reference and each table in the world from 3–4 blocks away (screen closed, HUD
 * hidden). Opt-in: runs only when the environment variable {@code BURMALDAHOLIC_CAPTURE} is set ({@code 1}: everything,
 * {@code world}: the in-world shots only) (it asserts nothing;
 * it exists so the redesign can be compared before / after). Screenshots {@code cap_<w>_<view>}.
 */
public class RedesignCaptureClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private String size = "";

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getenv("BURMALDAHOLIC_CAPTURE");
		if (mode == null || mode.isEmpty()) return;
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			for (int[] wh : mode.equals("world") ? new int[0][] : new int[][] {{1920, 1080}, {2560, 1440}}) {
				context.getInput().resizeWindow(wh[0], wh[1]);
				context.waitTicks(5);
				guiScale(context, 4);
				size = String.valueOf(wh[0]);
				report.add(size + ": gui " + context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()));
				screens(context, world);
			}
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			size = "1920";
			inWorld(context, world);
		} finally {
			guiScale(context, 0);
			write(context);
		}
	}

	// ---- screens ---------------------------------------------------------------------------------------------------

	private void screens(ClientGameTestContext context, TestSingleplayerContext world) {
		step(context, "slots", () -> slots(context, world));
		step(context, "blackjack", () -> blackjack(context, world));
		step(context, "baccarat", () -> baccarat(context, world));
		step(context, "poker", () -> poker(context, world));
		step(context, "uth", () -> uth(context, world));
		step(context, "roulette", () -> roulette(context, world));
		step(context, "craps", () -> craps(context, world));
		step(context, "plinko", () -> plinko(context, world));
		step(context, "wheel", () -> wheel(context, world));
		step(context, "scratch", () -> scratch(context, world));
		step(context, "dice", () -> dice(context, world));
		step(context, "coin", () -> coin(context, world));
		step(context, "pvp", () -> pvp(context, world));
		close(context, world);
	}

	private void step(ClientGameTestContext context, String name, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException | AssertionError e) {
			report.add(size + " " + name + ": FAILED " + e);
		}
		context.runOnClient(mc -> CelebrationOverlay.get().clear());
	}

	private void slots(ClientGameTestContext context, TestSingleplayerContext world) {
		open(context, world, SlotsModule.MACHINES.values().iterator().next(), 0, (be, p) -> {});
		context.waitTicks(20);
		shot(context, "slots_idle");
	}

	private void blackjack(ClientGameTestContext context, TestSingleplayerContext world) {
		BlackjackTableBlockEntity[] t = new BlackjackTableBlockEntity[1];
		open(context, world, BlackjackModule.TABLE, 1, (be, p) -> {
			t[0] = (BlackjackTableBlockEntity) be;
			t[0].sit(p);
			t[0].bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
			t[0].botSafePointForTests();
		});
		shot(context, "blackjack_betting");
		world.getServer().runOnServer(server -> {
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 50);
			t[0].onAction(player(server), "bet", bet);
		});
		context.waitTicks(60);
		world.getServer().runOnServer(server -> t[0].revealAllForTests());
		context.waitTicks(10);
		shot(context, "blackjack_turn");
		world.getServer().runOnServer(server -> t[0].onAction(player(server), "stand", new CompoundTag()));
		for (int i = 0; i < 12; i++) {
			world.getServer().runOnServer(server -> t[0].fastForwardForTests());
			context.waitTicks(2);
		}
		world.getServer().runOnServer(server -> t[0].revealAllForTests());
		context.waitTicks(50);
		shot(context, "blackjack_result");
	}

	private void baccarat(ClientGameTestContext context, TestSingleplayerContext world) {
		BaccaratTableBlockEntity[] t = new BaccaratTableBlockEntity[1];
		open(context, world, BaccaratModule.TABLE, 2, (be, p) -> {
			t[0] = (BaccaratTableBlockEntity) be;
			t[0].bots().setDefaults(new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST));
			t[0].sit(p);
			t[0].onAction(p, "bet", box(BetKind.PLAYER, 100));
			t[0].onAction(p, "bet", box(BetKind.TIE, 5));
		});
		context.waitTicks(50);
		shot(context, "baccarat_betting");
		world.getServer().runOnServer(server -> {
			t[0].onAction(player(server), "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(t[0].phase()); i++) t[0].fastForwardForTests();
			t[0].sendStateTo(player(server));
		});
		context.waitTicks(100);
		shot(context, "baccarat_reveal");
		world.getServer().runOnServer(server -> {
			for (int i = 0; i < 3 && !"result".equals(t[0].phase()); i++) t[0].fastForwardForTests();
			t[0].syncViewers();
		});
		context.waitTicks(40);
		shot(context, "baccarat_result");
	}

	private void poker(ClientGameTestContext context, TestSingleplayerContext world) {
		PokerTableBlockEntity[] t = new PokerTableBlockEntity[1];
		open(context, world, PokerModule.TABLE, 3, (be, p) -> t[0] = (PokerTableBlockEntity) be);
		shot(context, "poker_join");
		world.getServer().runOnServer(server -> {
			CompoundTag args = new CompoundTag();
			args.putString("level", "micro");
			args.putLong("amount", 200);
			t[0].onAction(player(server), "buy_in", args);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() instanceof dev.nezo.burmaldaholic.games.poker.client.PokerScreen s && s.isMyTurn(), 2400);
		} catch (RuntimeException | AssertionError e) {
			report.add(size + " poker: no turn");
		}
		context.waitTicks(5);
		shot(context, "poker_turn");
	}

	private void uth(ClientGameTestContext context, TestSingleplayerContext world) {
		UthTableBlockEntity[] t = new UthTableBlockEntity[1];
		open(context, world, UthModule.TABLE, 4, (be, p) -> {
			t[0] = (UthTableBlockEntity) be;
			t[0].sit(p);
		});
		shot(context, "uth_betting");
		world.getServer().runOnServer(server -> {
			CompoundTag args = new CompoundTag();
			args.putLong("ante", 10);
			args.putLong("trips", 10);
			t[0].onAction(player(server), "bet", args);
		});
		context.waitTicks(80);
		shot(context, "uth_preflop");
		for (String a : List.of("check", "check")) {
			world.getServer().runOnServer(server -> {
				t[0].onAction(player(server), a, new CompoundTag());
				t[0].syncViewers();
			});
			context.waitTicks(40);
		}
		shot(context, "uth_river");
	}

	private void roulette(ClientGameTestContext context, TestSingleplayerContext world) {
		RouletteTableBlockEntity[] t = new RouletteTableBlockEntity[1];
		open(context, world, RouletteModule.TABLE, 5, (be, p) -> {
			t[0] = (RouletteTableBlockEntity) be;
			rbet(t[0], p, "straight", 10, 17);
			rbet(t[0], p, "split", 5, 8, 11);
			rbet(t[0], p, "corner", 5, 20, 21, 23, 24);
			rbet(t[0], p, "red", 10, 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
		});
		shot(context, "roulette_betting");
		world.getServer().runOnServer(server -> t[0].onAction(player(server), "spin", new CompoundTag()));
		context.waitTicks(72);
		shot(context, "roulette_spin");
		context.waitTicks(74);
		shot(context, "roulette_result");
	}

	private void craps(ClientGameTestContext context, TestSingleplayerContext world) {
		CrapsTableBlockEntity[] t = new CrapsTableBlockEntity[1];
		open(context, world, CrapsModule.TABLE, 6, (be, p) -> {
			t[0] = (CrapsTableBlockEntity) be;
			flat(t[0], p, "pass", 50);
			flat(t[0], p, "field", 5);
		});
		shot(context, "craps_comeout");
		world.getServer().runOnServer(server -> {
			t[0].roll(3, 5);
			t[0].sendStateTo(player(server));
		});
		context.waitTicks(80);
		world.getServer().runOnServer(server -> {
			flat(t[0], player(server), "come", 10);
			t[0].sendStateTo(player(server));
		});
		context.waitTicks(10);
		shot(context, "craps_point");
	}

	private void plinko(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos pos = open(context, world, ExtrasModule.PLINKO, 7, (be, p) -> {});
		shot(context, "plinko_idle");
		CompoundTag args = chips(20);
		args.putString("risk", "medium");
		world.getServer().runOnServer(server -> action(server, pos, "drop", args));
		context.waitTicks(30);
		shot(context, "plinko_drop");
	}

	private void wheel(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos pos = open(context, world, ExtrasModule.WHEEL, 8, (be, p) -> {});
		shot(context, "wheel_idle");
		world.getServer().runOnServer(server -> action(server, pos, "spin", chips(50)));
		context.waitTicks(85);
		shot(context, "wheel_result");
	}

	private void scratch(ClientGameTestContext context, TestSingleplayerContext world) {
		openRaw(context, world, server -> {
			ServerPlayer p = player(server);
			ItemStack card = new ItemStack(ExtrasModule.SCRATCH_CARD, 3);
			p.getInventory().add(card);
			ScratchGame.use(p, card, Scratch.Kind.BASIC);
		});
		shot(context, "scratch_fresh");
	}

	private void dice(ClientGameTestContext context, TestSingleplayerContext world) {
		openRaw(context, world, server -> DiceGame.open(player(server), null));
		shot(context, "dice_idle");
		world.getServer().runOnServer(server -> {
			CompoundTag args = chips(50);
			DiceGame.action(player(server), "roll", args);
		});
		context.waitTicks(60);
		shot(context, "dice_result");
	}

	private void coin(ClientGameTestContext context, TestSingleplayerContext world) {
		openRaw(context, world, server -> CoinFlipGame.open(player(server)));
		shot(context, "coin_idle");
		world.getServer().runOnServer(server -> {
			CompoundTag args = chips(100);
			args.putString("side", "heads");
			CoinFlipGame.action(player(server), "flip", args);
		});
		context.waitTicks(37);
		shot(context, "coin_landed");
	}

	private void pvp(ClientGameTestContext context, TestSingleplayerContext world) {
		close(context, world);
		world.getServer().runOnServer(server -> {
			CompoundTag invite = new CompoundTag();
			invite.putInt("id", 1);
			invite.putString("name", "Alex");
			invite.putLong("stake", 200);
			invite.putLong("ticks", 600);
			ExtrasGames.send(player(server), DiceGame.INVITE_SCREEN, true, invite);
		});
		context.waitTicks(20);
		shot(context, "duel_invite");
		close(context, world);
		context.runOnClient(mc -> ClientPvp.receive(new PvpSyncPayload("match", true, coinMatch().toString())));
		context.waitTicks(30);
		shot(context, "pvp_coin_duel");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.runOnClient(mc -> ClientPvp.setStateForTests(null, "clear"));
		context.waitTicks(3);
	}

	// ---- the tables in the world -----------------------------------------------------------------------------------

	private void inWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		close(context, world);
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
		});
		BlockPos[] base = new BlockPos[1];
		world.getServer().runOnServer(server -> base[0] = player(server).blockPosition());
		worldShot(context, world, base[0], "blackjack", BlackjackModule.TABLE, 0, (be, p) -> {
			BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) be;
			t.sit(p);
			t.bots().table().requestChange(p, new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST), false);
			t.botSafePointForTests();
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 50);
			t.onAction(p, "bet", bet);
		}, 70, null);
		worldShot(context, world, base[0], "baccarat", BaccaratModule.TABLE, 1, (be, p) -> {
			BaccaratTableBlockEntity t = (BaccaratTableBlockEntity) be;
			t.sit(p);
			t.onAction(p, "bet", box(BetKind.BANKER, 100));
			t.onAction(p, "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(t.phase()); i++) t.fastForwardForTests();
		}, 100, null);
		worldShot(context, world, base[0], "poker", PokerModule.TABLE, 2, (be, p) -> {
			CompoundTag args = new CompoundTag();
			args.putString("level", "micro");
			args.putLong("amount", 200);
			be.onAction(p, "buy_in", args);
		}, 200, null);
		worldShot(context, world, base[0], "uth", UthModule.TABLE, 3, (be, p) -> {
			UthTableBlockEntity t = (UthTableBlockEntity) be;
			t.sit(p);
			CompoundTag args = new CompoundTag();
			args.putLong("ante", 10);
			args.putLong("trips", 10);
			t.onAction(p, "bet", args);
			t.onAction(p, "check", new CompoundTag());
		}, 120, null);
		worldShot(context, world, base[0], "roulette", RouletteModule.TABLE, 4, (be, p) -> {
			RouletteTableBlockEntity t = (RouletteTableBlockEntity) be;
			rbet(t, p, "straight", 10, 17);
			rbet(t, p, "red", 10, 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
			t.onAction(p, "spin", new CompoundTag());
		}, 60, "roulette_landed");
		worldShot(context, world, base[0], "craps", CrapsModule.TABLE, 5, (be, p) -> {
			CrapsTableBlockEntity t = (CrapsTableBlockEntity) be;
			flat(t, p, "pass", 50);
			flat(t, p, "field", 5);
			t.roll(3, 5);
		}, 40, null);
		worldShot(context, world, base[0], "plinko", ExtrasModule.PLINKO, 6, (be, p) -> {
			CompoundTag args = chips(20);
			args.putString("risk", "high");
			be.onAction(p, "drop", args);
		}, 25, null);
		worldShot(context, world, base[0], "wheel", ExtrasModule.WHEEL, 7, (be, p) -> be.onAction(p, "spin", chips(50)), 30, "wheel_landed");
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
		});
	}

	/**
	 * Places the table 3.5 blocks in front of a fresh spot (facing the player), runs {@code setup} with the screen open on
	 * the server, closes the screen, waits {@code wait} ticks and shoots from eye height looking at the table top; then
	 * a closer, lower view from 2 blocks and (if {@code later} is given) a second shot 80 ticks later.
	 */
	private void worldShot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos base, String name, TableType<?> type, int slot,
			BiConsumer<CasinoTableBlockEntity, ServerPlayer> setup, int wait, String later) {
		close(context, world);
		BlockPos stand = base.offset(slot * 12, 0, 0);
		BlockPos pos = stand.offset(0, 0, 4);
		world.getServer().runOnServer(server -> {
			var level = server.overworld();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -1; dz <= 6; dz++) {
					for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(stand.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(pos, type.block().defaultBlockState().setValue(CasinoTableBlock.FACING, Direction.NORTH));
		});
		world.getServer().runCommand("tp @p " + (stand.getX() + 0.5) + " " + stand.getY() + " " + (stand.getZ() + 0.5) + " facing "
			+ (pos.getX() + 0.5) + " " + (pos.getY() + 0.9) + " " + (pos.getZ() + 0.5));
		look(context, 0f, 12f);
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity be) {
				p.openMenu(be);
				setup.accept(be, p);
				be.sendStateTo(p);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(wait);
		look(context, 0f, 12f);
		context.takeScreenshot("cap_world_" + name + "_3m");
		world.getServer().runCommand("tp @p " + (pos.getX() + 0.5) + " " + stand.getY() + " " + (pos.getZ() - 1.6) + " facing "
			+ (pos.getX() + 0.5) + " " + (pos.getY() + 0.9) + " " + (pos.getZ() + 0.5));
		look(context, 0f, 30f);
		context.takeScreenshot("cap_world_" + name + "_close");
		if (later != null) {
			context.waitTicks(80);
			context.takeScreenshot("cap_world_" + later);
		}
		world.getServer().runOnServer(server -> player(server).closeContainer());
		context.waitTicks(3);
	}

	/** Points the camera (yaw 0 = towards +Z, the table; pitch down in degrees) and lets a few frames render. */
	private static void look(ClientGameTestContext context, float yaw, float pitch) {
		for (int i = 0; i < 3; i++) {
			context.runOnClient(mc -> {
				if (mc.player != null) {
					mc.player.setYRot(yaw);
					mc.player.setXRot(pitch);
					mc.player.setYHeadRot(yaw);
				}
			});
			context.waitTicks(2);
		}
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	private static CompoundTag box(BetKind box, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("box", box.id());
		t.putLong("amount", amount);
		return t;
	}

	private static void rbet(RouletteTableBlockEntity be, ServerPlayer p, String type, long amount, int... nums) {
		CompoundTag args = new CompoundTag();
		args.putString("type", type);
		args.putIntArray("nums", nums);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	private static void flat(CrapsTableBlockEntity be, ServerPlayer p, String kind, long amount) {
		CompoundTag args = new CompoundTag();
		args.putString("kind", kind);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	private static void action(MinecraftServer server, BlockPos pos, String action, CompoundTag args) {
		if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) table.onAction(player(server), action, args);
	}

	private void close(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> player(server).closeContainer());
		context.waitTicks(3);
	}

	/** Places a table two blocks from the player (a fresh lane per game) and opens it as a player would; returns its position. */
	private BlockPos open(ClientGameTestContext context, TestSingleplayerContext world, TableType<?> type, int lane,
			BiConsumer<CasinoTableBlockEntity, ServerPlayer> setup) {
		close(context, world);
		BlockPos[] at = new BlockPos[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			BlockPos pos = p.blockPosition().offset(2, 0, lane - 4);
			at[0] = pos;
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, type.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity be) {
				p.openMenu(be);
				setup.accept(be, p);
				be.sendStateTo(p);
			}
		});
		waitScreen(context, type.toString());
		return at[0];
	}

	private void openRaw(ClientGameTestContext context, TestSingleplayerContext world, java.util.function.Consumer<MinecraftServer> opener) {
		close(context, world);
		world.getServer().runOnServer(opener::accept);
		waitScreen(context, "extras");
	}

	private void waitScreen(ClientGameTestContext context, String what) {
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			report.add(size + " " + what + ": screen did not open");
		}
		context.waitTicks(15);
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(1);
		context.takeScreenshot("cap_" + size + "_" + name);
		report.add(size + " " + name + ": " + context.computeOnClient(mc -> mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getSimpleName()
			+ " " + mc.gui.screen().width + "x" + mc.gui.screen().height));
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("cap_report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---- a Coin Flip Duel mid-spin (the PvP match view; same shape as ExtrasVisualClientGameTests) ---------------------

	private static JsonObject coinMatch() {
		JsonObject o = new JsonObject();
		o.addProperty("id", "cap000c");
		o.addProperty("mode", "coin");
		o.addProperty("state", "DRAWN");
		o.addProperty("you", 0);
		o.addProperty("spectator", false);
		o.addProperty("host", 0);
		o.addProperty("rakePercent", "3");
		o.addProperty("rakeBp", 300);
		o.addProperty("entry", 250);
		o.addProperty("min", 2);
		o.addProperty("max", 2);
		o.addProperty("equalStakes", true);
		o.addProperty("grudge", false);
		o.addProperty("policy", "mixed");
		o.addProperty("balance", 12500);
		o.addProperty("ticksLeft", -1);
		JsonObject params = new JsonObject();
		params.addProperty("heads", true);
		params.addProperty("stake", 250);
		o.add("params", params);
		o.addProperty("final", -1);
		o.add("placings", new JsonArray());
		o.add("taunts", new JsonArray());
		JsonArray ps = new JsonArray();
		ps.add(person(0, "Steve", true));
		ps.add(person(1, "Notch", false));
		o.add("participants", ps);
		o.addProperty("pot", 500);
		o.addProperty("link", 2);
		JsonArray steps = new JsonArray();
		JsonObject cd = new JsonObject();
		cd.addProperty("heads0", true);
		steps.add(step("countdown", 60, cd));
		steps.add(step("spin", 20, new JsonObject()));
		o.add("steps", steps);
		return o;
	}

	private static JsonObject person(int index, String name, boolean you) {
		JsonObject p = new JsonObject();
		p.addProperty("index", index);
		p.addProperty("key", "00000000-0000-0000-0000-00000000000" + index);
		p.addProperty("name", name);
		p.addProperty("bot", false);
		p.addProperty("level", "");
		p.addProperty("tagKey", "");
		p.addProperty("stake", 250);
		p.addProperty("allIn", false);
		p.addProperty("pressed", false);
		p.addProperty("rematch", false);
		p.addProperty("host", index == 0);
		p.addProperty("you", you);
		if (!you) {
			p.addProperty("wins", 3);
			p.addProperty("losses", 5);
		}
		return p;
	}

	private static JsonObject step(String kind, int ticks, JsonObject data) {
		JsonObject s = new JsonObject();
		s.addProperty("kind", kind);
		s.addProperty("ticks", ticks);
		s.addProperty("round", kind.equals("countdown") ? -1 : 0);
		s.addProperty("waitForAll", false);
		s.add("data", data);
		return s;
	}
}
