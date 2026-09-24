package dev.nezo.burmaldaholic.games.extras.pvp;

import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelEntries;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyEntries;
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
<<<<<<< HEAD
		CoinDuelEntries.register(); // J-M1: Lucky Coin on a player, set-up + decision payloads, pvp_all_square
		WheelPartyEntries.register(); // J-M2: wheel party panel payloads, pvp_underdog
=======
		dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownAdvancements.register(); // pvp_lucky_feet (J-M5)
>>>>>>> worktree-agent-a19fec0b1773f637c
	}
}
