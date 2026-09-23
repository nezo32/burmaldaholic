package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.client.CoreClientModule;
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
		context.clickScreenButton("gamerule.burmaldaholic.casino_mode");
		boolean afterClick = context.computeOnClient(mc ->
			((CreateWorldScreen) mc.gui.screen()).getUiState().getGameRules().get(CasinoMode.rule()));
		if (afterClick == CasinoMode.DEFAULT) {
			throw new AssertionError("Create World casino toggle did not change the game rule");
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			boolean serverSide = world.getServer().computeOnServer(CasinoMode::isEnabled);
			if (serverSide != CasinoMode.DEFAULT) {
				throw new AssertionError("casino_mode should default to " + CasinoMode.DEFAULT);
			}
			context.waitFor(mc -> CoreClientModule.casinoEnabled() == CasinoMode.DEFAULT);
			world.getServer().runCommand("gamerule burmaldaholic:casino_mode false");
			context.waitFor(mc -> !CoreClientModule.casinoEnabled());
		}
	}
}
