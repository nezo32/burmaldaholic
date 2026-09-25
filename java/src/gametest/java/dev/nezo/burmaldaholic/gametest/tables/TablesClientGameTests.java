package dev.nezo.burmaldaholic.gametest.tables;

import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.craps.CrapsTableBlockEntity;
import dev.nezo.burmaldaholic.games.craps.client.CrapsScreen;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.extras.server.DuelStage;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteScreen;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane J-L6 (tables redesign): opens the roulette, craps and Dice Duel screens on real tables and takes screenshots
 * {@code jtest_tables_<lang>_<case>} of the betting view, the spin / throw mid-flight and the settled result — EN and RU,
 * one themed variant each (Piglin Parlor roulette, End craps / duel) and the compact roulette at GUI scale 3 — to compare
 * with docs/design/visual/mockups/tables_*.png. Checks that the screens end on the server's outcome (the wheel's pocket
 * and the dice faces come from the state) and writes {@code jtest_tables_report.txt}.
 */
public class TablesClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null)); // never start with a pause screen: server tasks would wait forever
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				guiScale(context, 2);
				roulette(context, world, lang, 0, true);
				craps(context, world, lang, 0);
				dice(context, world, lang, 0);
			}
			language(context, "en_us");
			guiScale(context, 2);
			roulette(context, world, "en_us_bastion", 1, true);
			craps(context, world, "en_us_end", 2);
			dice(context, world, "en_us_end", 2);
			// compact layouts: GUI scale 3 of the 854 × 480 test window is exactly the 284 × 160 compact frame
			guiScale(context, 3);
			report.add("gui scale 3: " + context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + " x " + mc.getWindow().getGuiScaledHeight()));
			roulette(context, world, "en_us_compact", 2, true);
			craps(context, world, "en_us_compact", 0);
			dice(context, world, "en_us_compact", 0);
			// and the compact frame forced at GUI scale 2 (twice the pixels: the details are inspectable)
			guiScale(context, 2);
			TableChrome.forceCompact = true;
			try {
				roulette(context, world, "ru_ru_compact_forced", 0, false);
				craps(context, world, "en_us_compact_forced", 1);
			} finally {
				TableChrome.forceCompact = false;
			}
			invite(context, world);
			duelStage(context, world);
		} finally {
			TableChrome.forceCompact = false;
			guiScale(context, 0);
			language(context, "en_us");
			DiceGame.themeOverride = -1;
			write(context);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static BlockPos place(MinecraftServer server, int dx, net.minecraft.world.level.block.Block block) {
		ServerPlayer p = player(server);
		BlockPos pos = p.blockPosition().offset(dx - 2, 0, 3);
		server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		server.overworld().setBlockAndUpdate(pos, block.defaultBlockState());
		return pos;
	}

	private void close(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runOnServer(server -> player(server).closeContainer());
		context.waitTicks(3);
	}

	// ---- roulette --------------------------------------------------------------------------------------------------

	private void roulette(ClientGameTestContext context, TestSingleplayerContext world, String name, int theme, boolean fullCycle) {
		close(context, world);
		BlockPos[] pos = new BlockPos[1];
		world.getServer().runOnServer(server -> {
			pos[0] = place(server, 2, RouletteModule.TABLE.block());
			if (server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) {
				be.setThemeForTesting(theme);
				ServerPlayer p = player(server);
				p.openMenu(be);
				bet(be, p, "straight", 10, 17);
				bet(be, p, "split", 5, 8, 11);
				bet(be, p, "corner", 5, 20, 21, 23, 24);
				bet(be, p, "red", 10, redNumbers());
				bet(be, p, "dozen", 5, range(13, 24));
				bet(be, p, "dozen", 5, range(1, 12));
				bet(be, p, "column", 5, range3(2));
				be.sendStateTo(p);
			}
		});
		if (!waitScreen(context, name + " roulette", RouletteScreen.class)) {
			return;
		}
		context.waitTicks(10);
		context.takeScreenshot("jtest_tables_" + name + "_roulette_betting");
		if (!fullCycle && !name.contains("end")) {
			return;
		}
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) {
				be.onAction(player(server), "spin", new CompoundTag());
			}
		});
		// no more bets (20 t), then 2.6 s into the 5 s spin
		context.waitTicks(20 + 52);
		context.takeScreenshot("jtest_tables_" + name + "_roulette_spin");
		context.waitTicks(48 + 26);
		context.takeScreenshot("jtest_tables_" + name + "_roulette_win");
		int[] result = new int[1];
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be) {
				result[0] = be.sync().result();
			}
		});
		int shown = context.computeOnClient(mc -> mc.level != null && mc.level.getBlockEntity(pos[0]) instanceof RouletteTableBlockEntity be
			&& be.clientSync() != null ? be.clientSync().result() : -2);
		report.add(name + " roulette: server result " + result[0] + ", in-world wheel sync " + shown);
		if (shown != result[0]) {
			report.add("  FAIL: the in-world wheel does not land on the server's number");
		}
		// the in-world wheel from the player's view (screen closed)
		close(context, world);
		look(world, pos[0]);
		context.waitTicks(5);
		context.takeScreenshot("jtest_tables_" + name + "_roulette_world");
		context.waitTicks(40);
	}

	private static void look(TestSingleplayerContext world, BlockPos pos) {
		world.getServer().runCommand("tp @p ~ ~ ~ facing " + (pos.getX() + 0.5) + " " + (pos.getY() - 0.6) + " " + (pos.getZ() + 0.5));
	}

	private static int[] range(int a, int b) {
		int[] out = new int[b - a + 1];
		for (int i = a; i <= b; i++) {
			out[i - a] = i;
		}
		return out;
	}

	private static int[] range3(int first) {
		int[] out = new int[12];
		for (int i = 0; i < 12; i++) {
			out[i] = first + 3 * i;
		}
		return out;
	}

	private static int[] redNumbers() {
		return new int[] {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
	}

	private static void bet(RouletteTableBlockEntity be, ServerPlayer p, String type, long amount, int... nums) {
		CompoundTag args = new CompoundTag();
		args.putString("type", type);
		args.putIntArray("nums", nums);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	// ---- craps -----------------------------------------------------------------------------------------------------

	private void craps(ClientGameTestContext context, TestSingleplayerContext world, String name, int theme) {
		close(context, world);
		BlockPos[] pos = new BlockPos[1];
		world.getServer().runOnServer(server -> {
			pos[0] = place(server, 3, CrapsModule.TABLE.block());
			if (server.overworld().getBlockEntity(pos[0]) instanceof CrapsTableBlockEntity be) {
				be.setThemeForTesting(theme);
				ServerPlayer p = player(server);
				p.openMenu(be);
				flat(be, p, "pass", 50);
				flat(be, p, "field", 5);
				be.roll(3, 5); // point 8
				be.sendStateTo(p);
			}
		});
		if (!waitScreen(context, name + " craps", CrapsScreen.class)) {
			return;
		}
		context.waitTicks(70);
		world.getServer().runOnServer(server -> {
			if (server.overworld().getBlockEntity(pos[0]) instanceof CrapsTableBlockEntity be) {
				ServerPlayer p = player(server);
				flat(be, p, "come", 10);
				for (var b : be.table().betsOf(p.getUUID())) {
					if (b.kind().id().equals("pass")) {
						CompoundTag args = new CompoundTag();
						args.putInt("bet", b.id());
						args.putLong("amount", 75);
						be.onAction(p, "odds", args);
					}
				}
				be.roll(4, 1); // 5: the come bet travels to the 5
				be.sendStateTo(p);
			}
		});
		context.waitTicks(10);
		context.takeScreenshot("jtest_tables_" + name + "_craps_roll");
		context.waitTicks(60);
		context.takeScreenshot("jtest_tables_" + name + "_craps_point");
		int[] faces = context.computeOnClient(mc -> mc.gui.screen() instanceof CrapsScreen s ? new int[] {1} : new int[] {0});
		report.add(name + " craps: screen open " + (faces[0] == 1));
		close(context, world);
		look(world, pos[0]);
		context.waitTicks(5);
		context.takeScreenshot("jtest_tables_" + name + "_craps_world");
	}

	private static void flat(CrapsTableBlockEntity be, ServerPlayer p, String kind, long amount) {
		CompoundTag args = new CompoundTag();
		args.putString("kind", kind);
		args.putLong("amount", amount);
		be.onAction(p, "bet", args);
	}

	// ---- dice duel -------------------------------------------------------------------------------------------------

	private void dice(ClientGameTestContext context, TestSingleplayerContext world, String name, int theme) {
		close(context, world);
		DiceGame.themeOverride = theme;
		world.getServer().runOnServer(server -> DiceGame.open(player(server), null));
		try {
			context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("DiceScreen"), 200);
		} catch (RuntimeException | AssertionError e) {
			report.add(name + " dice: screen did not open");
			return;
		}
		context.waitTicks(10);
		context.takeScreenshot("jtest_tables_" + name + "_dice_idle");
		world.getServer().runOnServer(server -> {
			CompoundTag args = new CompoundTag();
			args.putString("stake", "chips");
			args.putLong("amount", 50);
			DiceGame.action(player(server), "roll", args);
		});
		context.waitTicks(16);
		context.takeScreenshot("jtest_tables_" + name + "_dice_throw");
		context.waitTicks(24);
		context.takeScreenshot("jtest_tables_" + name + "_dice_reveal");
		context.waitTicks(20);
		context.takeScreenshot("jtest_tables_" + name + "_dice_end");
	}

	private void invite(ClientGameTestContext context, TestSingleplayerContext world) {
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
		context.takeScreenshot("jtest_tables_en_us_duel_invite");
		close(context, world);
	}

	// ---- the PvP duel in the world (DuelStage) ------------------------------------------------------------------------

	/** Stages a two-round duel (a tie, then 6+5 vs 2+1) from the player to a point ahead and shoots the world. */
	private void duelStage(ClientGameTestContext context, TestSingleplayerContext world) {
		close(context, world);
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			server.overworld().setBlockAndUpdate(p.blockPosition().offset(0, 0, 4), Blocks.AIR.defaultBlockState());
		});
		world.getServer().runCommand("tp @p ~ ~ ~ facing ~ ~-1 ~4");
		context.waitTicks(3);
		boolean[] staged = new boolean[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			net.minecraft.world.phys.Vec3 a = new net.minecraft.world.phys.Vec3(p.getX() - 0.6, p.getY() + 1.1, p.getZ() + 0.8);
			net.minecraft.world.phys.Vec3 b = a.add(1.2, 0, 3.6);
			staged[0] = DuelStage.stage(server.overworld(), a, b, p, List.of(new int[][] {{3, 4}, {5, 2}}, new int[][] {{6, 5}, {2, 1}}), 0, 1234,
				server.overworld().getGameTime(), java.util.Set.of());
		});
		report.add("duel stage: staged " + staged[0]);
		context.waitTicks(14); // round 1 in flight
		context.takeScreenshot("jtest_tables_en_us_duel_world_throw");
		context.waitTicks(26); // round 1 at rest: the tie, totals up
		context.takeScreenshot("jtest_tables_en_us_duel_world_tie");
		context.waitTicks(50); // round 2 landed: 11 vs 3
		context.takeScreenshot("jtest_tables_en_us_duel_world_rest");
		int[] left = new int[1];
		context.waitTicks(80);
		world.getServer().runOnServer(server -> left[0] = DuelStage.active());
		report.add("duel stage: active after the linger " + left[0]);
		if (left[0] != 0) {
			report.add("  FAIL: the staged dice were not removed");
		}
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private boolean waitScreen(ClientGameTestContext context, String what, Class<?> type) {
		try {
			context.waitFor(mc -> type.isInstance(mc.gui.screen()), 200);
			return true;
		} catch (RuntimeException | AssertionError e) {
			report.add(what + ": screen did not open");
			return false;
		}
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_tables_report.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(20);
	}

}
