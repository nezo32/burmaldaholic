package dev.nezo.burmaldaholic.core.pvp.logic;

import java.util.random.RandomGenerator;

/**
 * Fair game randomness for tapes (never odds-adjusted, never the streak re-draw, never the bot rng).
 * Production adapts {@code OddsService.get().fair()}; tests use a seeded generator.
 */
public interface PvpRng {
	int nextInt(int bound);

	/** Uniform in [0, bound), bound ≤ 2⁵³ (Wheel Party pots). */
	long nextLong(long bound);

	boolean nextBoolean();

	/** Uniformly random permutation of 0..n-1 (the tape's seat order). */
	default int[] permutation(int n) {
		int[] p = new int[n];
		for (int i = 0; i < n; i++) {
			p[i] = i;
		}
		for (int i = n - 1; i > 0; i--) {
			int j = nextInt(i + 1);
			int t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		return p;
	}

	static PvpRng of(RandomGenerator r) {
		return new PvpRng() {
			@Override
			public int nextInt(int bound) {
				return r.nextInt(bound);
			}

			@Override
			public long nextLong(long bound) {
				return r.nextLong(bound);
			}

			@Override
			public boolean nextBoolean() {
				return r.nextBoolean();
			}
		};
	}
}
