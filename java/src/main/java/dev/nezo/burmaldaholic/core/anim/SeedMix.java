package dev.nezo.burmaldaholic.core.anim;

/**
 * Cosmetic seeds and a tiny deterministic RNG shared bit-for-bit with Bedrock
 * ({@code bedrock/src/core/logic/anim/seed.ts}). Cosmetic randomness (card jitter, wheel rest offset,
 * particle scatter, idle flourish choice) comes ONLY from here, seeded by public data such as
 * {@code (tablePosHash, roundSeq, slot)} — never from the game RNG, the bot RNG or client {@code Random}
 * (global.md §6, tables.md §0.6.6, cards.md §0.7.8).
 */
public final class SeedMix {
	private SeedMix() {}

	/** murmur3 finaliser. */
	public static int fmix(int h) {
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h;
	}

	/** Order-sensitive mix of 32-bit parts (FNV-style fold through {@link #fmix}). */
	public static int mix(int... parts) {
		int h = 0x811C9DC5;
		for (int p : parts) h = fmix((h ^ p) * 0x01000193 + 0x7F4A7C15);
		return h;
	}

	/** Splits a long (e.g. a round id or a packed BlockPos) into two parts. */
	public static int mixLong(long v) {
		return mix((int) (v >>> 32), (int) v);
	}

	/** Stable 32-bit hash of an ASCII/UTF-16 id (game ids, table keys). */
	public static int hash(String s) {
		int h = 0x811C9DC5;
		for (int i = 0; i < s.length(); i++) h = (h ^ s.charAt(i)) * 0x01000193;
		return fmix(h);
	}

	/** mulberry32: 32-bit state, identical sequence in Java and TypeScript. */
	public static final class FxRng {
		private int state;

		public FxRng(int seed) {
			this.state = seed;
		}

		/** Next value as an unsigned 32-bit number in a long. */
		public long nextU32() {
			state += 0x6D2B79F5;
			int t = state;
			t = (t ^ (t >>> 15)) * (t | 1);
			t ^= t + (t ^ (t >>> 7)) * (t | 61);
			return Integer.toUnsignedLong(t ^ (t >>> 14));
		}

		/** Uniform in [0, bound), bound ≤ 2^20 (exact in both editions). */
		public int nextInt(int bound) {
			if (bound <= 0 || bound > (1 << 20)) throw new IllegalArgumentException("bound " + bound);
			return (int) ((nextU32() * bound) >>> 32);
		}

		/** Uniform in [0, 1). */
		public double nextDouble() {
			return nextU32() / 4294967296.0;
		}
	}
}
