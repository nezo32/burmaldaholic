package dev.nezo.burmaldaholic.games.slots.pvp;

import dev.nezo.burmaldaholic.core.pvp.PvpModes;

/** Registers Slot Showdown with core (called once from {@code SlotsModule.register}) and its advancement listener. */
public final class SlotsPvp {
	private SlotsPvp() {}

	public static void register() {
		PvpModes.register(new SlotShowdownMode());
		ShowdownAdvancements.register();
	}
}
