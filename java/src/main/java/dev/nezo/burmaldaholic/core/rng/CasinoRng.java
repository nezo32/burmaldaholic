package dev.nezo.burmaldaholic.core.rng;

import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Per-play random source. See {@link OddsService}. */
public final class CasinoRng {
	private final RandomGenerator random;
	private final DoubleUnaryOperator adjust;

	public CasinoRng(RandomGenerator random, DoubleUnaryOperator adjust) {
		this.random = random;
		this.adjust = adjust;
	}

	/** Fair uniform int in [0, bound). Never influenced by modifiers. */
	public int nextInt(int bound) {
		return random.nextInt(bound);
	}

	/** Fair uniform double in [0, 1). */
	public double nextDouble() {
		return random.nextDouble();
	}

	/** Fisher-Yates shuffle (fair). */
	public <T> void shuffle(List<T> list) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T tmp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, tmp);
		}
	}

	/** True with the MODIFIED probability of a player-favourable event whose base chance is {@code p}. */
	public boolean chance(double baseProbability) {
		return random.nextDouble() < adjust.applyAsDouble(baseProbability);
	}

	/**
	 * Weighted pick where the total weight of {@code favourable} outcomes is rescaled so that its
	 * share equals the modified probability. Relative weights within each group are preserved.
	 */
	public <T> T weighted(List<Weighted<T>> outcomes, Predicate<T> favourable) {
		double fav = 0;
		double total = 0;
		for (Weighted<T> w : outcomes) {
			total += w.weight();
			if (favourable.test(w.value())) {
				fav += w.weight();
			}
		}
		if (total <= 0) {
			throw new IllegalArgumentException("weights must be positive");
		}
		double baseP = fav / total;
		double p = (fav == 0 || fav == total) ? baseP : adjust.applyAsDouble(baseP);
		boolean pickFav = fav > 0 && random.nextDouble() < p;
		if (fav == total) {
			pickFav = true;
		}
		double groupTotal = pickFav ? fav : total - fav;
		double roll = random.nextDouble() * groupTotal;
		T last = null;
		for (Weighted<T> w : outcomes) {
			if (favourable.test(w.value()) != pickFav) {
				continue;
			}
			last = w.value();
			roll -= w.weight();
			if (roll < 0) {
				return w.value();
			}
		}
		return last;
	}

	public record Weighted<T>(T value, double weight) {}
}
