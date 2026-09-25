package dev.nezo.burmaldaholic.gametest.baccarat;

import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Card;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The baccarat screen in English and Russian (UI.md §14): a house coup with bets and a finished coup, and
 * the chemin de fer bank offer / banker view. Screenshots {@code jtest_baccarat_<lang>_<state>} and a
 * layout report ({@code jtest_baccarat_layout.txt}: labels wider than their button, widgets off screen).
 */
public class BaccaratClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			world.getServer().runCommand("casino balance set @p 12500");
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				shots(context, world, lang);
			}
		} finally {
			write(context);
			language(context, "en_us");
		}
	}

	private void shots(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		open(context, world, lang + "_betting", BaccaratModule.TABLE, (table, player) -> {
			table.onAction(player, "bet", bet(BetKind.PLAYER, 50));
			table.onAction(player, "bet", bet(BetKind.BANKER, 40));
			table.onAction(player, "bet", bet(BetKind.TIE, 5));
		});
		open(context, world, lang + "_result", BaccaratModule.TABLE, (table, player) -> {
			table.stackCardsForTests(List.of(Card.of(12, Card.SPADES), Card.of(6, Card.DIAMONDS), Card.of(12, Card.DIAMONDS),
				Card.of(1, Card.CLUBS), Card.of(5, Card.SPADES)));
			table.onAction(player, "bet", bet(BetKind.PLAYER_PAIR, 10));
			table.onAction(player, "bet", bet(BetKind.BANKER, 20));
			table.onAction(player, "ready", new CompoundTag());
			for (int i = 0; i < 6 && !"result".equals(table.phase()); i++) {
				table.fastForwardForTests();
			}
			table.sendStateTo(player);
		});
		open(context, world, lang + "_chemmy_offer", BaccaratModule.CHEMMY_TABLE, (table, player) -> table.sit(player));
		open(context, world, lang + "_chemmy_bank", BaccaratModule.CHEMMY_TABLE, (table, player) -> {
			table.sit(player);
			CompoundTag args = new CompoundTag();
			args.putLong("amount", 200);
			table.onAction(player, "take_bank", args);
		});
	}

	private static CompoundTag bet(BetKind box, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("box", box.id());
		t.putLong("amount", amount);
		return t;
	}

	private interface Setup {
		void run(BaccaratTableBlockEntity table, ServerPlayer player);
	}

	private void open(ClientGameTestContext context, TestSingleplayerContext world, String name, TableType<?> type, Setup setup) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		Consumer<MinecraftServer> opener = server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			server.overworld().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, type.block().defaultBlockState());
			if (server.overworld().getBlockEntity(pos) instanceof BaccaratTableBlockEntity table) {
				player.openMenu(table);
				setup.run(table, player);
				table.sendStateTo(player);
			}
		};
		world.getServer().runOnServer(server -> {
			server.getPlayerList().getPlayers().getFirst().closeContainer();
			opener.accept(server);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			report.add(name + ": screen did not open");
			return;
		}
		context.waitTicks(10);
		context.takeScreenshot("jtest_baccarat_" + name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) {
			report.add(name + ": " + p);
		}
	}

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
				out.add("label overflows button: \"" + label + "\"");
			}
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the screen: \"" + label + "\"");
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_baccarat_layout.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
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
