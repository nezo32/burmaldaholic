package dev.nezo.burmaldaholic.games.extras.client.pvp;

import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.games.extras.client.pvp.plinko.PlinkoBattleScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.scratch.ScratchShowdownScreen;

/** Registers the Plinko Battle and Scratch Showdown match screens with core's per-mode registry (J-M4, J-M5). */
public final class ExtrasPvpScreens {
	private ExtrasPvpScreens() {}

	public static void register() {
		PvpScreens.register("plinko", PlinkoBattleScreen::new);
		PvpScreens.register("scratch", ScratchShowdownScreen::new);
	}
}
