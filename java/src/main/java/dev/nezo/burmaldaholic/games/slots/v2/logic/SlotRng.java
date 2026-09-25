package dev.nezo.burmaldaholic.games.slots.v2.logic;

import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * Randomness for the draw. The module adapts {@code OddsService} (fair draws + the §14 streak re-draw of the
 * WHOLE spin, SLOTS.md §8.2); tests and the cross-edition vectors use {@link #seeded}. Never used by presentation.
 */
public interface SlotRng {
	/** Uniform in [0, bound). */
	int nextInt(int bound);

	/** Integer-weighted pick (weights ≥ 0, 0 &lt; Σ ≤ 2^31 − 1; the largest table is 102 073). */
	default int weighted(int[] weights) {
		int total = 0;
		for (int w : weights) total = Math.addExact(total, w);
		int r = nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			r -= weights[i];
			if (r < 0) return i;
		}
		return weights.length - 1;
	}

	/**
	 * Reference RNG of the shared vectors ({@code slots_engine.json}): mulberry32 ({@link SeedMix.FxRng}), identical
	 * in TypeScript. Bounds ≤ 2^20 (the largest used is 1 000 000).
	 */
	static SlotRng seeded(int seed) {
		SeedMix.FxRng r = new SeedMix.FxRng(seed);
		return r::nextInt;
	}
}
