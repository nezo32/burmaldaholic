package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;
import net.minecraft.world.level.gamerules.GameRules;

/** Client GameTest worlds. Casino mode is OFF by default, so tests that play the casino opt in at creation. */
public final class ClientTestWorlds {
	private ClientTestWorlds() {}

	/** A world created with {@code burmaldaholic:casino_mode = true}, like pressing the Create World button. */
	public static TestWorldBuilder casino(ClientGameTestContext context) {
		return context.worldBuilder().adjustSettings(state -> {
			GameRules rules = state.getGameRules();
			rules.set(CasinoMode.rule(), true, null);
			state.setGameRules(rules);
		});
	}
}
