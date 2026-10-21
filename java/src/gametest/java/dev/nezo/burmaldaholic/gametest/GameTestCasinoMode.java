package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * Casino mode is ON by default (GAME_DESIGN.md §2.1, v0.1.2), but the GameTest world is reused between
 * runs and a test that was interrupted while it had the mode off would leave it off; so the headless
 * GameTest server makes sure it is on once it has started (before the first test tick). Tests that need it off
 * set it themselves and restore it. Client GameTests are unaffected (they create worlds via
 * {@link ClientTestWorlds}).
 */
public class GameTestCasinoMode implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof GameTestServer) {
				CasinoMode.set(server, true);
				// INT-X: with 416 tests the run crosses the world's first sunset (~10 min); a sunset-rolled Golden Hour
				// (x2 winnings for 3 min) then broke every payout assert that ran during it. Chaos tests start events
				// themselves ("admin"), so the random sunset and ambient rolls are off on the headless GameTest server.
				dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().goldenHour.sunsetChance = 0;
				dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().ambientChance = 0; // same for ambient rolls
			}
		});
	}
}
