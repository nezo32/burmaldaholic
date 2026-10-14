package dev.nezo.burmaldaholic.core.anim.motion;

import dev.nezo.burmaldaholic.core.anim.SeedMix;

/**
 * Deterministic COSMETIC randomness of a motion (PHASE2B §0.4): a {@link SeedMix.FxRng} (mulberry32, bit-identical
 * with the Bedrock port) seeded by {@code SeedMix.mix(seed, SeedMix.hash(stream), idx)}. The seed is public data
 * (table position, round sequence) sent by the server; this class must never be fed from, or feed, {@code CasinoRng} /
 * {@code OddsService}. Separate streams ({@code "PEG"}, {@code "DSIM"}, {@code "RATTLE"}…) and indices (row, die,
 * attempt) keep decisions independent, so adding a draw to one stream never shifts another.
 *
 * <p>Mutable and not thread-safe; create one per build (allocation at build time only).
 */
public final class MotionRng {
	private final SeedMix.FxRng rng;
	private final int mixed;

	private MotionRng(int mixed) {
		this.mixed = mixed;
		this.rng = new SeedMix.FxRng(mixed);
	}

	/**
	 * The generator of stream {@code stream}, index {@code idx} of cosmetic seed {@code seed}.
	 *
	 * @param seed   cosmetic seed (e.g. {@code SeedMix.mix(pos, seq)})
	 * @param stream stream name, hashed with {@link SeedMix#hash}
	 * @param idx    index inside the stream (row, die, attempt…)
	 */
	public static MotionRng of(int seed, String stream, int idx) {
		return new MotionRng(SeedMix.mix(seed, SeedMix.hash(stream), idx));
	}

	/** Two-index variant ({@code (seed, stream, idx, sub)}, e.g. attempt and die). */
	public static MotionRng of(int seed, String stream, int idx, int sub) {
		return new MotionRng(SeedMix.mix(seed, SeedMix.hash(stream), idx, sub));
	}

	/** The mixed 32-bit seed of this generator (for logs and golden vectors). */
	public int seed() {
		return mixed;
	}

	/** Next unsigned 32-bit value. */
	public long nextU32() {
		return rng.nextU32();
	}

	/** Uniform in [0, bound), {@code 0 < bound ≤ 2^20}. */
	public int nextInt(int bound) {
		return rng.nextInt(bound);
	}

	/** Uniform in [0, 1). */
	public double nextDouble() {
		return rng.nextDouble();
	}

	/** Uniform in [lo, hi). */
	public double range(double lo, double hi) {
		return lo + (hi - lo) * rng.nextDouble();
	}

	/** Uniform integer in [lo, hi] (both inclusive). */
	public int rangeInt(int lo, int hi) {
		if (hi < lo) throw new IllegalArgumentException(lo + ".." + hi);
		return lo + rng.nextInt(hi - lo + 1);
	}

	/** True with probability {@code p}. */
	public boolean chance(double p) {
		return rng.nextDouble() < p;
	}

	/** −1 or +1 with equal probability. */
	public int sign() {
		return rng.nextInt(2) == 0 ? -1 : 1;
	}

	/** {@code center · (1 ± frac)}, uniform. */
	public double jitter(double center, double frac) {
		return center * (1 + range(-frac, frac));
	}

	/** Index picked with integer weights (e.g. tips 1:2:1 → {@code weighted(1, 2, 1)}). */
	public int weighted(int... weights) {
		int total = 0;
		for (int w : weights) {
			if (w < 0) throw new IllegalArgumentException("weight " + w);
			total += w;
		}
		if (total <= 0) throw new IllegalArgumentException("no weight");
		int r = rng.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			r -= weights[i];
			if (r < 0) return i;
		}
		return weights.length - 1;
	}
}
