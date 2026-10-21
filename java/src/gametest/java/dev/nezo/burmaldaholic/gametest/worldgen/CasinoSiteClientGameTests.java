package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.Level;

/**
 * The Nether and End casino-site checks of {@link CasinoSiteGameTests} in a REAL default-preset world (lane WG): on
 * 26.3 the GameTest server's Nether is flat and its End has no noise generator, so the real-terrain walks run here,
 * on the integrated server of a fresh world. No screenshots: everything is asserted on server blocks.
 */
public class CasinoSiteClientGameTests implements FabricClientGameTest {
	private static final long SEED = 12345L;

	@Override
	public void runTest(ClientGameTestContext context) {
		String lanes = System.getenv("BURMALDAHOLIC_CGT");
		if (lanes != null && !lanes.isBlank() && !List.of(lanes.split(",")).contains("WG")) {
			return; // lane filter (BURMALDAHOLIC_CGT=WG runs this one)
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
				((CasinoModeCreationState) state).burmaldaholic$setCasinoMode(true);
				state.setSeed(Long.toString(SEED));
				state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			}).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(null));
			for (String dim : new String[] {"nether", "end"}) {
				String result = world.getServer().computeOnServer(server -> {
					try {
						return dim.equals("nether") ? "OK " + CasinoSiteGameTests.checkNether(server.getLevel(Level.NETHER))
							: "OK " + CasinoSiteGameTests.checkEnd(server.getLevel(Level.END));
					} catch (AssertionError | RuntimeException e) {
						return "FAIL " + e;
					}
				});
				CasinoQa.log("site {}: {}", dim, result);
				if (result.startsWith("FAIL")) {
					failures.add(dim + ": " + result);
				}
			}
			context.waitTicks(5);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("casino sites in a real world:\n  " + String.join("\n  ", failures));
		}
	}
}
