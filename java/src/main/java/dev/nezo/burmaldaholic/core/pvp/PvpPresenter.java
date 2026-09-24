package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Edition UI hook of the engine (PVP.md §3.11, §3.14 "revealStep / sound / particles"). The engine
 * decides WHAT and WHEN; the presenter decides HOW. Java: the {@code pvp} module installs a presenter
 * that sends {@code PvpMatchSyncPayload}s to the per-mode screens (registered client-side with
 * {@code client.pvp.PvpScreens}) and falls back to titles / chat / the HUD ticker when a screen is
 * closed. Default: chat lines only (so the engine works headless in GameTests).
 *
 * <p>{@code viewers} = participants (online) + spectators within {@code pvp.announceRadius} where the
 * PVP.md tables say so.
 */
public interface PvpPresenter {
	/** Lobby created / joined / left / seating changed / countdown tick. */
	default void lobbyChanged(PvpMatch match) {}

	/** One timeline step revealed (never unrevealed tape data). */
	default void revealStep(PvpMatch match, Step step, List<ServerPlayer> viewers) {}

	/** Final Reveal choreography (§3.11.4) — engine calls this once per place / at the winner. */
	default void finalReveal(PvpMatch match, int place, List<ServerPlayer> viewers) {}

	/** Result window (ranking, pot, rake, payouts, [Rematch] [Taunt] [Close]). */
	default void result(PvpMatch match, List<ServerPlayer> participants) {}

	/** Vanilla sound id of PVP.md §12 at the match's players ({@code who} = participant index or -1 = all). */
	default void sound(PvpMatch match, String soundId, int who) {}

	/** Particle id of PVP.md §12 at a participant (-1 = anchor). */
	default void particles(PvpMatch match, String particleId, int who) {}

	PvpPresenter NONE = new PvpPresenter() {};
}
