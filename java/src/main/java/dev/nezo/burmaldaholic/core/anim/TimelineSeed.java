package dev.nezo.burmaldaholic.core.anim;

/**
 * What the server publishes so that every client can rebuild the same {@link Timeline}
 * (docs/architecture/animation.md §3.3, "sync protocol"). Travels in a block entity's update tag (tables,
 * cabinets, wheels: {@code getUpdatePacket} → spectators and the BER) or in a game payload (screens). The
 * game-specific OUTCOME (pocket, dice, stops/tape section, card beats) travels next to it; the client calls
 * the game's pure builder with {@code (outcome, seed, TimingProfile.SHARED)} and samples it at
 * {@code (clientGameTime − startTick + partialTick) × 50} ms.
 *
 * @param game      builder id ({@code slots.nether}, {@code roulette}, {@code blackjack} …)
 * @param seq       round / spin counter (monotonic per block); a change restarts the client player
 * @param startTick server game time of beat 0
 * @param seed      cosmetic seed ({@link SeedMix#mix} of public values)
 * @param speedPct  shared-time speed chosen by the acting player where the game allows it (slots turbo);
 *                  100 otherwise. Spectators use the same value so the cabinet lands with the screen.
 */
public record TimelineSeed(String game, int seq, long startTick, int seed, int speedPct) {
	/** Milliseconds since beat 0 for a client whose level time is {@code gameTime} + {@code partialTick}. */
	public double elapsedMs(long gameTime, float partialTick) {
		return ((gameTime - startTick) + (double) partialTick) * 50.0;
	}

	/**
	 * Catch-up rule (tables.md §0.1): a viewer who arrives after 85 % of the shared part sees the settled
	 * state directly.
	 */
	public static boolean showSettled(double elapsedMs, int sharedEndMs) {
		return sharedEndMs <= 0 || elapsedMs >= 0.85 * sharedEndMs;
	}
}
