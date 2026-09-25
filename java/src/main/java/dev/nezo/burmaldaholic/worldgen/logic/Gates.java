package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.function.IntUnaryOperator;

/**
 * Generation decisions (PURE): per-structure chance gates and the village pool weight.
 *
 * <p>Gates are deterministic hashes of the host structure's position (and world seed where
 * available), so the same seed always yields the same casinos, independent of the order chunks are
 * generated in, and the gates themselves never draw from vanilla's random sequence.
 */
public final class Gates {
	/** Salts so the three casino kinds make independent decisions. */
	public static final long SALT_VILLAGE = 0x5EED_CA51_0001L;
	public static final long SALT_PARLOR = 0x5EED_CA51_0002L;
	public static final long SALT_LOUNGE = 0x5EED_CA51_0003L;

	/**
	 * Weight of the casino in a village's street-end pool as a fraction of the pool's total weight
	 * (vanilla terminator pools weigh 4 → casino weight 4, drawn first at half of the street ends).
	 * High on purpose: the per-village chance is applied by the gate, and a gated village should nearly
	 * always get its casino at one of its several street ends.
	 */
	public static final double VILLAGE_WEIGHT_FRACTION = 1.0;

	private Gates() {}

	/** SplitMix64 finaliser. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Uniform double in [0, 1) from the inputs. */
	public static double unit(long seed, int a, int b, long salt) {
		long h = mix(seed ^ mix(salt));
		h = mix(h ^ (a * 0x9E3779B97F4A7C15L));
		h = mix(h ^ (b * 0xC2B2AE3D27D4EB4FL));
		return (h >>> 11) * 0x1.0p-53;
	}

	/** True with probability {@code chance} for this (seed, a, b, salt). */
	public static boolean pass(double chance, long seed, int a, int b, long salt) {
		if (chance <= 0) {
			return false;
		}
		if (chance >= 1) {
			return true;
		}
		return unit(seed, a, b, salt) < chance;
	}

	/** Weight of the casino element in a village pool of total weight {@code poolWeight} (≥ 1). */
	public static int villageWeight(int poolWeight) {
		return Math.max(1, (int) Math.ceil(poolWeight * VILLAGE_WEIGHT_FRACTION));
	}

	/**
	 * Where a casino element with weight {@code weight} lands in a shuffled list of {@code listSize}
	 * elements: the index of the first of {@code weight} copies inserted uniformly at random. Only the
	 * first copy matters (later copies would be retried after a failed fit), so the caller inserts a
	 * single element there — same odds, less work.
	 */
	public static int insertIndex(int listSize, int weight, IntUnaryOperator nextInt) {
		int remaining = listSize;
		int copies = weight;
		int index = 0;
		while (remaining > 0 && copies > 0) {
			if (nextInt.applyAsInt(remaining + copies) < copies) {
				return index;
			}
			remaining--;
			index++;
		}
		return index;
	}
}
