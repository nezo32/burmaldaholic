package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Minimal random source for the pure poker logic. The game adapts core's {@code CasinoRng} (fair draws
 * only — poker is never odds-adjusted, GAME_DESIGN.md §14), tests use a seeded {@link RandomGenerator}.
 */
public interface PokerRng {
	/** Uniform int in [0, bound). */
	int nextInt(int bound);

	/** Uniform double in [0, 1). */
	double nextDouble();

	static PokerRng of(RandomGenerator random) {
		return new PokerRng() {
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

	/** Uniform int in [lo, hi] (inclusive). */
	default int between(int lo, int hi) {
		return hi <= lo ? lo : lo + nextInt(hi - lo + 1);
	}

	default <T> T pick(List<T> list) {
		return list.get(nextInt(list.size()));
	}
}
