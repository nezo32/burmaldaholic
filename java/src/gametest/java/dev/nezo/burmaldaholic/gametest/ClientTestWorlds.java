package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;

/** Client GameTest worlds. Casino mode is OFF by default, so tests that play the casino opt in at creation. */
public final class ClientTestWorlds {
	private ClientTestWorlds() {}

	/** A world created with Casino Mode ON, exactly like pressing the Create World button. */
	public static TestWorldBuilder casino(ClientGameTestContext context) {
		return context.worldBuilder().adjustSettings(state -> ((CasinoModeCreationState) state).burmaldaholic$setCasinoMode(true));
	}
}
