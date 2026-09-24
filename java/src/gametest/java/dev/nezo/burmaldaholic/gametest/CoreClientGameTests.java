package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.CoreClientModule;
import dev.nezo.burmaldaholic.client.cashier.CashierScreen;
import dev.nezo.burmaldaholic.client.config.CasinoConfigScreen;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Client game tests (real client, needs a display: run under Xvfb on Linux CI).
 * {@code ./gradlew runClientGameTest}. Screenshots land in {@code build/run/clientGameTest/screenshots}
 * ({@code jmode_create_world_en_us/ru_ru} show the Casino Mode button below Difficulty).
 */
public class CoreClientGameTests implements FabricClientGameTest {
	private static final String TOGGLE = "gui.burmaldaholic.core.create_world.casino_mode";

	@Override
	public void runTest(ClientGameTestContext context) {
		createWorldButton(context);

		// Generated config editor: every page builds its rows (reflection + labels) without errors.
		context.setScreen(() -> new CasinoConfigScreen(null));
		context.waitForScreen(CasinoConfigScreen.class);
		context.takeScreenshot("burmaldaholic_config");
		context.clickScreenButton("config.burmaldaholic.section.slots");
		context.waitTicks(2);
		context.takeScreenshot("burmaldaholic_config_slots");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			// A world created without touching the button: casino mode OFF (the default).
			boolean serverSide = world.getServer().computeOnServer(CasinoMode::isEnabled);
			if (serverSide || CasinoMode.DEFAULT) {
				throw new AssertionError("casino_mode should default to OFF");
			}
			context.waitTicks(20);
			if (context.computeOnClient(mc -> CoreClientModule.casinoEnabled())) {
				throw new AssertionError("client thinks casino mode is on in a default world");
			}
			// Turning it on later (existing world): client sync + first-join welcome (starting balance, card).
			world.getServer().runCommand("gamerule burmaldaholic:casino_mode true");
			context.waitFor(mc -> CoreClientModule.casinoEnabled());
			context.waitFor(mc -> ClientCasinoState.hasStatus() && ClientCasinoState.balance() > 0);
			world.getServer().runCommand("casino balance set @p 12500");
			context.waitFor(mc -> ClientCasinoState.balance() == 12500);
			context.waitTicks(5);
			context.takeScreenshot("burmaldaholic_hud");

			// Cashier screen opens through the generic table menu
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(2, 0, 0);
				server.overworld().setBlockAndUpdate(pos, CoreContent.CASHIER.block().defaultBlockState());
				if (server.overworld().getBlockEntity(pos) instanceof CashierBlockEntity cashier) {
					player.openMenu(cashier);
					cashier.sendStateTo(player);
				}
			});
			context.waitForScreen(CashierScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("burmaldaholic_cashier");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			world.getServer().runCommand("gamerule burmaldaholic:casino_mode false");
			context.waitFor(mc -> !CoreClientModule.casinoEnabled());
		}
	}

	/**
	 * Create World -> Game tab: "Casino Mode" sits directly below "Difficulty" (same column and width, one
	 * grid row lower, above "Allow Commands"), defaults OFF, toggles the game rule, fits in English and
	 * Russian, and a world created with it ON has the rule on and hands out the starting balance.
	 */
	private static void createWorldButton(ClientGameTestContext context) {
		context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(null)));
		context.waitForScreen(CreateWorldScreen.class);
		context.waitTicks(2);
		checkPlacement(context, "en_us");
		if (uiRule(context) || context.computeOnClient(mc -> (Boolean) casinoButton(mc).getValue())) {
			throw new AssertionError("Casino Mode should default to OFF on the Create World screen");
		}
		screenshotWithTooltip(context, "jmode_create_world_en_us");

		clickCasinoButton(context);
		boolean first = uiRule(context);
		clickCasinoButton(context);
		boolean second = uiRule(context);
		clickCasinoButton(context);
		boolean third = uiRule(context);
		if (!first || second || !third) {
			throw new AssertionError("toggle failed: first=" + first + " second=" + second + " third=" + third);
		}

		language(context, "ru_ru");
		context.waitForScreen(CreateWorldScreen.class);
		checkPlacement(context, "ru_ru");
		if (!uiRule(context)) {
			throw new AssertionError("Casino Mode choice lost after the screen was rebuilt");
		}
		screenshotWithTooltip(context, "jmode_create_world_ru_ru");
		language(context, "en_us");
		context.waitForScreen(CreateWorldScreen.class);

		context.clickScreenButton("selectWorld.create");
		context.waitFor(mc -> mc.getSingleplayerServer() != null && mc.player != null, 20 * 60);
		boolean onServer = context.computeOnClient(mc -> CasinoMode.isEnabled(mc.getSingleplayerServer()));
		if (!onServer) {
			throw new AssertionError("world created with Casino Mode: ON does not have casino_mode=true");
		}
		context.waitFor(mc -> CoreClientModule.casinoEnabled(), 20 * 30);
		context.waitFor(mc -> ClientCasinoState.hasStatus() && ClientCasinoState.balance() > 0, 20 * 30);
		// Leave the world, otherwise the framework fails ("finished while a server is still running").
		context.runOnClient(mc -> {
			mc.level.disconnect(Component.translatable("menu.savingLevel"));
			mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 20 * 60);
		context.waitTicks(20);
		context.setScreen(TitleScreen::new);
	}

	private static void checkPlacement(ClientGameTestContext context, String lang) {
		String problem = context.computeOnClient(mc -> {
			AbstractWidget difficulty = null;
			AbstractWidget allowCommands = null;
			String difficultyName = Component.translatable("options.difficulty").getString();
			String commandsName = Component.translatable("selectWorld.allowCommands").getString();
			for (var child : mc.gui.screen().children()) {
				if (child instanceof CycleButton<?> b && b != casinoButton(mc)) {
					String text = b.getMessage().getString();
					if (text.startsWith(difficultyName)) {
						difficulty = b;
					} else if (text.startsWith(commandsName)) {
						allowCommands = b;
					}
				}
			}
			CycleButton<?> casino = casinoButton(mc);
			if (difficulty == null || allowCommands == null) {
				return "vanilla Difficulty / Allow Commands buttons not found";
			}
			int rowSpacing = 8; // GameTab: layout.rowSpacing(8)
			if (casino.getX() != difficulty.getX() || casino.getWidth() != difficulty.getWidth()) {
				return "not aligned with Difficulty: x=" + casino.getX() + "/" + difficulty.getX()
					+ " w=" + casino.getWidth() + "/" + difficulty.getWidth();
			}
			if (casino.getY() != difficulty.getBottom() + rowSpacing) {
				return "not directly below Difficulty: y=" + casino.getY() + " difficulty bottom=" + difficulty.getBottom();
			}
			if (allowCommands.getY() != casino.getBottom() + rowSpacing) {
				return "Allow Commands is not the next row: y=" + allowCommands.getY() + " casino bottom=" + casino.getBottom();
			}
			int textWidth = mc.font.width(casino.getMessage());
			if (textWidth > casino.getWidth() - 8) {
				return "label does not fit: " + textWidth + " > " + (casino.getWidth() - 8);
			}
			System.out.println("BURMALDAHOLIC_CREATE_WORLD_LAYOUT " + lang + " difficulty.y=" + difficulty.getY()
				+ " casino.y=" + casino.getY() + " allowCommands.y=" + allowCommands.getY() + " label=\""
				+ casino.getMessage().getString() + "\" width=" + textWidth + "/" + casino.getWidth());
			return null;
		});
		if (problem != null) {
			throw new AssertionError("Create World Casino Mode button (" + lang + "): " + problem);
		}
	}

	/** A real left click in the middle of the button (the message has an argument, so clickScreenButton can't match it). */
	private static void clickCasinoButton(ClientGameTestContext context) {
		hoverCasinoButton(context);
		context.getInput().pressMouse(0); // GLFW_MOUSE_BUTTON_LEFT
		context.waitTicks(1);
	}

	private static void hoverCasinoButton(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			CycleButton<?> b = casinoButton(mc);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void screenshotWithTooltip(ClientGameTestContext context, String name) {
		hoverCasinoButton(context);
		context.waitTicks(10);
		context.takeScreenshot(name);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	private static CycleButton<?> casinoButton(Minecraft mc) {
		for (var child : mc.gui.screen().children()) {
			if (child instanceof CycleButton<?> button && button.getMessage().getContents() instanceof TranslatableContents t
				&& t.getKey().equals(TOGGLE)) {
				return button;
			}
		}
		throw new AssertionError("Casino Mode toggle not found on the Create World screen");
	}

	private static boolean uiRule(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ((CreateWorldScreen) mc.gui.screen()).getUiState().getGameRules().get(CasinoMode.rule()));
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200); // the Mojang reload overlay fades out
		context.waitTicks(20);
	}
}
