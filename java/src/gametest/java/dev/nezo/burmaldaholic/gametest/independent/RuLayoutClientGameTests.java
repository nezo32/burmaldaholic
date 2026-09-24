package dev.nezo.burmaldaholic.gametest.independent;

import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tester pass: every game screen in Russian at the default window (GUI scale auto) — screenshots
 * {@code jtest_ru_*} plus an automatic layout report (buttons whose label is wider than the button, widgets
 * outside the screen) written to {@code jtest_layout_report.txt} next to the screenshots. The report is
 * informational; clear overflows found by it are fixed in the screens.
 */
public class RuLayoutClientGameTests implements FabricClientGameTest {
	private static final List<String> REPORT = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		language(context, "ru_ru");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("casino balance set @p 12500");
			Map<String, TableType<?>> tables = new LinkedHashMap<>();
			tables.put("blackjack", BlackjackModule.TABLE);
			tables.put("roulette", RouletteModule.TABLE);
			tables.put("craps", CrapsModule.TABLE);
			tables.put("poker", PokerModule.TABLE);
			tables.put("slots_copper", SlotsModule.MACHINES.get(Tier.COPPER));
			tables.put("slots_gold", SlotsModule.MACHINES.get(Tier.GOLD));
			tables.put("slots_netherite", SlotsModule.MACHINES.get(Tier.NETHERITE));
			tables.put("wheel", ExtrasModule.WHEEL);
			tables.put("plinko", ExtrasModule.PLINKO);
			tables.put("cashier", CoreContent.CASHIER);
			for (Map.Entry<String, TableType<?>> e : tables.entrySet()) {
				final int offset = 2; // same spot every time: within reach, the previous block is replaced
				TableType<?> type = e.getValue();
				open(context, world, e.getKey(), server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					BlockPos pos = player.blockPosition().offset(offset, 0, 0);
					server.overworld().setBlockAndUpdate(pos, type.block().defaultBlockState());
					if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
						player.openMenu(table);
						table.sendStateTo(player);
					}
				});
			}
			for (String page : List.of("wallet", "contracts", "loan", "achievements", "challenges", "my_casino", "rules")) {
				open(context, world, "menu_" + page, server -> CasinoMenu.open(server.getPlayerList().getPlayers().getFirst(), page));
			}
			open(context, world, "coin_flip", server -> CoinFlipGame.open(server.getPlayerList().getPlayers().getFirst()));
			open(context, world, "dice", server -> DiceGame.open(server.getPlayerList().getPlayers().getFirst(), null));
		} finally {
			writeReport(context);
			language(context, "en_us");
		}
	}

	private static void open(ClientGameTestContext context, TestSingleplayerContext world, String name, Consumer<MinecraftServer> opener) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			server.getPlayerList().getPlayers().getFirst().closeContainer();
			opener.accept(server);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			REPORT.add(name + ": screen did not open");
			return;
		}
		context.waitTicks(10);
		context.takeScreenshot("jtest_ru_" + name);
		List<String> problems = context.computeOnClient(mc -> inspect(mc, mc.gui.screen()));
		for (String p : problems) {
			REPORT.add(name + " [" + context.computeOnClient(mc -> mc.gui.screen().getClass().getSimpleName()) + "]: " + p);
		}
	}

	/** Buttons whose label does not fit and widgets outside the screen. */
	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) {
			return out;
		}
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) {
				continue;
			}
			String label = w.getMessage().getString();
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > w.getWidth() - 4) {
				out.add("label overflows button (" + mc.font.width(w.getMessage()) + " > " + (w.getWidth() - 4) + " px): \"" + label + "\"");
			}
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the " + screen.width + "×" + screen.height + " screen at " + w.getX() + "," + w.getY() + " "
					+ w.getWidth() + "×" + w.getHeight() + ": \"" + label + "\"");
			}
		}
		return out;
	}

	private static void writeReport(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_layout_report.txt"), String.join("\n", REPORT) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
