package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * THE decision interface every game implements for its bots (one per game, in the game's own
 * {@code logic} package, pure): Texas Hold'em, chemin de fer, UTH, blackjack, the atmosphere bettors of
 * roulette / craps / baccarat, and every PvP mode with decisions.
 *
 * <ul>
 *   <li>{@code V} = what a human in that seat would see (never hidden cards, the shoe order or a tape);</li>
 *   <li>{@code A} = the game's action type (the same type the human input produces);</li>
 *   <li>{@link #decide} is pure and deterministic for a given {@link BotRng} state;</li>
 *   <li>{@link #legalize} is the mandatory last step (clamp sizes, raise → call when closed, fold → check
 *       when free, bet → table limits). Callers use {@link #act}, which applies it.</li>
 * </ul>
 * Heavy precomputation (Monte-Carlo, UTH river enumeration) goes in {@link #work}, which the driver runs
 * within the server-wide budget ({@code bots.mcBudgetPerTick}) before calling decide.
 */
public interface BotPolicy<V, A> {
	/** @param work the finished {@link #work} result, or null when the policy needs none / it timed out */
	A decide(BotProfile bot, V view, Object work, BotRng rng);

	A legalize(V view, A action);

	/** Optional heavy job for this decision; null = none. */
	default BotWork work(BotProfile bot, V view, BotRng rng) {
		return null;
	}

	/** decide + legalize; the only entry point drivers call. */
	default A act(BotProfile bot, V view, Object work, BotRng rng) {
		return legalize(view, decide(bot, view, work, rng));
	}
}
