package dev.nezo.burmaldaholic.core.pvp.logic;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.List;

/**
 * THE contract of a PvP mode (PVP.md §3.14). Pure, engine-free; identical test vectors in both editions.
 * A mode lives in the module that owns its solo game (coin / wheel / plinko / scratch → extras,
 * slots → slots) and registers itself with {@code PvpModes.register(mode)} in its module's {@code register}.
 * The core engine ({@code core.pvp.PvpService}) owns everything else: invites, lobbies, escrow, rake,
 * persistence, play-out, rivalry, taunts, rematch, reveal choreography, bots and seating.
 *
 * @param <P> set-up parameters chosen by the host (spins, risk, balls, cap, side …)
 * @param <T> the tape: EVERY random value of the match, drawn once at START
 */
public interface PvpMode<P, T> {
	/** {@code coin | slots | wheel | plinko | scratch} (also the config sub-section {@code pvp.<id>}). */
	String id();

	int minPlayers();

	/** Current max (read the mode's {@code pvp.<id>.maxPlayers} config). */
	int maxPlayers();

	AnchorKind anchor();

	/** False only for Wheel Party (stakes differ; §6). */
	boolean equalStakes();

	/** Is the mode switched on ({@code pvp.<id>.enabled})? */
	boolean enabled();

	/** Parameter range check (PVP.md §13); null = ok, else an error translation key. */
	String validate(P params);

	/** Default parameters for the set-up form. */
	P defaults();

	/**
	 * Draws ALL randomness of the match from the FAIR game rng, incl. the seat order. Must not depend
	 * on which seats are bots (BOTS.md §4.1).
	 *
	 * @param players number of participants
	 */
	T draw(PvpRng rng, int players, P params);

	/** Pure, deterministic scoring. {@code stakes[i]} = participant i's escrowed stake. */
	Outcome score(T tape, long[] stakes, P params);

	/** What to reveal at which tick (the engine plays it; the Final Reveal is appended by the engine). */
	List<Step> timeline(T tape, Outcome outcome, P params);

	/**
	 * Bot choices for the decisions of PVP.md §3.15.4 / BOTS.md §4.8 (only coin and wheel have any in
	 * MUST). Uses ONLY the bot rng and the public {@code view}. Default: the mode has no decisions.
	 *
	 * @return the chosen option (see {@link DecisionView#decision()} for the meaning per mode)
	 */
	default long botDecide(DecisionView view, BotDifficulty level, BotRng rng) {
		throw new UnsupportedOperationException("mode " + id() + " has no bot decisions");
	}

	/** Does the mode have bot decisions (difficulty shown in set-up) or is it luck-only? */
	default boolean hasDecisions() {
		return false;
	}

	// ---- persistence: tapes and params are saved as JSON (< 700 chars per PVP.md §3.6) ----

	JsonElement encodeParams(P params);

	P decodeParams(JsonElement json);

	JsonElement encodeTape(T tape);

	T decodeTape(JsonElement json);
}
