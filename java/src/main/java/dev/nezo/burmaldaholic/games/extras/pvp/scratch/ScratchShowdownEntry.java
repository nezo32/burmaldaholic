package dev.nezo.burmaldaholic.games.extras.pvp.scratch;

import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.util.Result;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * How a Scratch Showdown is created (PVP.md §8.2, §3.3): from the PvP hub's *New match…* form (owned by
 * the pvp module), either as a duel against one nearby player / a bot, or as an open lobby anchored at the
 * host's position. The hub may call these helpers or the service directly with {@code mode = "scratch"} and
 * {@code params = {}} (the mode has no set-up besides the stake). Server thread only.
 */
public final class ScratchShowdownEntry {
	private ScratchShowdownEntry() {}

	/** Params with the config snapshot (weights, values) of now. */
	static com.google.gson.JsonElement params() {
		ScratchShowdownMode mode = new ScratchShowdownMode();
		return mode.encodeParams(mode.defaults());
	}

	/** Duel: invite one player ({@code PlayerTarget}) or play a house bot ({@code BotTarget}). */
	public static Result<PvpMatch> challenge(ServerPlayer challenger, long stake, PvpService.Opponent opponent) {
		return Pvp.service().challenge(challenger, ScratchShowdownMode.ID, params(), stake, opponent);
	}

	/** Open lobby at the host's position ("anyone nearby" joins from the hub within {@code pvp.joinRadius}). */
	public static Result<PvpMatch> openLobby(ServerPlayer host, long stake, BotSettings seating) {
		return Pvp.service().openLobby(host, ScratchShowdownMode.ID, params(), stake,
			new PvpService.Anchor(AnchorKind.NONE, (ServerLevel) host.level(), host.blockPosition()), seating, false);
	}
}
