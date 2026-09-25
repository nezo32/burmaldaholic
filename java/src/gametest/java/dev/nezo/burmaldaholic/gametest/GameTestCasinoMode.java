package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * Casino mode is OFF by default (GAME_DESIGN.md §2.1), but the server GameTests exercise the casino, so
 * the headless GameTest server turns it on once it has started (before the first test tick) — the same
 * thing a player does with the Create World button or {@code /casino mode on}. Tests that need it off
 * set it themselves and restore it. Client GameTests are unaffected (they create worlds via
 * {@link ClientTestWorlds}).
 */
public class GameTestCasinoMode implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof GameTestServer) {
				CasinoMode.set(server, true);
			}
		});
	}
}
