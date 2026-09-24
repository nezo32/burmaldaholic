package dev.nezo.burmaldaholic.games.slots.pvp;

import dev.nezo.burmaldaholic.core.pvp.PvpModes;

/** Registers Slot Showdown with core (called once from {@code SlotsModule.register}). */
public final class SlotsPvp {
	private SlotsPvp() {}

	public static void register() {
		PvpModes.register(new SlotShowdownMode());
	}
}
