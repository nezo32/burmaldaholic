package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Randomness for the draw. The module adapts {@code OddsService.play(...)} (fair draws + the §14 streak
 * re-draw of the WHOLE spin, SLOTS.md §8.2); tests use a seeded implementation. Never used by presentation.
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
}
