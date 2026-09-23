package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.CoreClientModule;
import dev.nezo.burmaldaholic.client.cashier.CashierScreen;
import dev.nezo.burmaldaholic.client.config.CasinoConfigScreen;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.KeyEvent;
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
 * {@code ./gradlew runClientGameTest}. Creating the world goes through CreateWorldScreen, so this
 * also proves the Create World toggle mixin applies.
 */
public class CoreClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Create World screen shows our toggle and clicking it flips the game rule in the UI state.
		context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(null)));
		context.waitForScreen(CreateWorldScreen.class);
		context.runOnClient(mc -> {
			// The toggle's message is "Casino Mode: ON/OFF" (key with an argument), so find it by translation key.
			for (var child : mc.gui.screen().children()) {
				if (child instanceof CycleButton<?> button && button.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().equals("gui.burmaldaholic.core.create_world.casino_mode")) {
					button.onPress(new KeyEvent(0, 0, 0));
					return;
				}
			}
			throw new AssertionError("Casino Mode toggle not found on the Create World screen");
		});
		boolean afterClick = context.computeOnClient(mc ->
			((CreateWorldScreen) mc.gui.screen()).getUiState().getGameRules().get(CasinoMode.rule()));
		if (afterClick == CasinoMode.DEFAULT) {
			throw new AssertionError("Create World casino toggle did not change the game rule");
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));

		// Generated config editor: every page builds its rows (reflection + labels) without errors.
		context.setScreen(() -> new CasinoConfigScreen(null));
		context.waitForScreen(CasinoConfigScreen.class);
		context.takeScreenshot("burmaldaholic_config");
		context.clickScreenButton("config.burmaldaholic.section.slots");
		context.waitTicks(2);
		context.takeScreenshot("burmaldaholic_config_slots");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			boolean serverSide = world.getServer().computeOnServer(CasinoMode::isEnabled);
			if (serverSide != CasinoMode.DEFAULT) {
				throw new AssertionError("casino_mode should default to " + CasinoMode.DEFAULT);
			}
			context.waitFor(mc -> CoreClientModule.casinoEnabled() == CasinoMode.DEFAULT);
			// HUD: status arrives (starting balance 50 from the first-join welcome)
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
}
