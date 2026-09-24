package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The BOT random stream (BOTS.md §4.1): think delays, mixed strategies, Monte-Carlo samples, names,
 * personalities, quips. NEVER the game RNG (shuffles, dice, wheel, PvP tapes are drawn by the game's
 * fair RNG, which bot code must not call). One instance per table session / PvP match.
 */
public interface BotRng {
	/** Uniform int in [0, bound). */
	int nextInt(int bound);

	/** Uniform double in [0, 1). */
	double nextDouble();

	/** Uniform int in [lo, hi] (inclusive). */
	default int between(int lo, int hi) {
		return hi <= lo ? lo : lo + nextInt(hi - lo + 1);
	}

	default boolean chance(double p) {
		return nextDouble() < p;
	}

	default <T> T pick(List<T> list) {
		return list.get(nextInt(list.size()));
	}

	/** Seeded L64X128MixRandom (tests: {@code debug.fixedSeed ^ 0xB07}). */
	static BotRng seeded(long seed) {
		return of(java.util.random.RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(seed));
	}

	/** Fresh stream for a new session, seeded from {@link java.security.SecureRandom}. */
	static BotRng fresh() {
		return seeded(new java.security.SecureRandom().nextLong());
	}

	static BotRng of(RandomGenerator random) {
		return new BotRng() {
			@Override
			public int nextInt(int bound) {
				return random.nextInt(bound);
			}

			@Override
			public double nextDouble() {
				return random.nextDouble();
			}
		};
	}
}
