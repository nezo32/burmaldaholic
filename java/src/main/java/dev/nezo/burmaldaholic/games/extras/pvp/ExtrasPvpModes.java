package dev.nezo.burmaldaholic.games.extras.pvp;

import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyMode;

/**
 * Registers the extras-owned PvP modes with core (called once from {@code ExtrasModule.register}), so the
 * two mode developers of extras never edit ExtrasModule. Machine-UI entries ("Start a Wheel Party",
 * "Join the battle", Lucky Coin on a player) are added by each mode's owner in their own files and call
 * {@code Pvp.service()}.
 */
public final class ExtrasPvpModes {
	private ExtrasPvpModes() {}

	public static void register() {
		PvpModes.register(new CoinDuelMode());
		PvpModes.register(new WheelPartyMode());
		PvpModes.register(new PlinkoBattleMode());
		PvpModes.register(new ScratchShowdownMode());
		dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownAdvancements.register(); // pvp_lucky_feet (J-M5)
	}
}
