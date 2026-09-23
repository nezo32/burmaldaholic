package dev.nezo.burmaldaholic.core.rng;

/**
 * Hook for chaos events, golden hour, streak rules, VIP perks, Last Chance, etc.
 * Receives the (possibly already modified) probability of a <em>player-favourable</em> event and
 * returns the new one. Result is clamped to [0,1] by the engine. Must be fast and side-effect free.
 */
@FunctionalInterface
public interface OddsModifier {
	double adjust(OddsContext ctx, double favourableProbability);
}
