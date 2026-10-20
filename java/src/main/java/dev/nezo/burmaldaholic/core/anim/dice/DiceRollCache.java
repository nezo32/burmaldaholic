package dev.nezo.burmaldaholic.core.anim.dice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Solve-once cache of {@link DiceRoll}s (coordinator guardrail for DCa): a roll is solved exactly once per
 * {@code (seq / round, side, seed)} on the server and on each client, then only sampled. Never call
 * {@link DiceSolver#solve} from a render or sample path; go through one of these, held by the screen, the block-entity
 * renderer or the server stage. Small LRU, not thread-safe (one per owner thread).
 */
public final class DiceRollCache {
	private final int capacity;
	private final LinkedHashMap<Long, DiceRoll> map;

	public DiceRollCache(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("capacity " + capacity);
		this.capacity = capacity;
		this.map = new LinkedHashMap<>(capacity * 2, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, DiceRoll> e) {
				return size() > DiceRollCache.this.capacity;
			}
		};
	}

	/**
	 * Cache key of a roll from (round, side, seed) only.
	 *
	 * @deprecated the faces, target and tray are not part of it; use {@link #key(int, int, int, int[], int, DiceTray)}
	 */
	@Deprecated
	public static long key(int seq, int side, int seed) {
		return ((long) seq << 32) ^ ((long) (side & 0xFF) << 24) ^ (seed & 0xFFFFFFFFL) * 0x9E3779B97F4A7C15L;
	}

	/**
	 * Cache key of a roll: everything {@link DiceSolver#solve} reads besides the throw spec — the round sequence (or duel
	 * round), the side / lane, the cosmetic seed, the faces, the target time and the tray — mixed into 64 bits.
	 */
	public static long key(int seq, int side, int seed, int[] faces, int targetMs, DiceTray tray) {
		long h = 0x243F6A8885A308D3L;
		h = mix(h, seq);
		h = mix(h, side);
		h = mix(h, seed);
		h = mix(h, faces.length);
		for (int f : faces) h = mix(h, f);
		h = mix(h, targetMs);
		h = mix(h, trayHash(tray));
		return h;
	}

	/** Structural hash of a tray (the floor polygon's array by content; the record parts are value-based). */
	static int trayHash(DiceTray t) {
		return java.util.Objects.hash(t.g(), java.util.Arrays.hashCode(t.floor().xy()), t.walls(), t.bumpers(), t.restZones(), t.keepOuts(), t.divider(),
			t.restSep());
	}

	private static long mix(long h, int v) {
		h ^= (v & 0xFFFFFFFFL) + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 31);
	}

	/** The cached roll of {@code key}, solving it with {@code solve} only when absent. */
	public DiceRoll get(long key, Supplier<DiceRoll> solve) {
		DiceRoll r = map.get(key);
		if (r == null) {
			r = solve.get();
			map.put(key, r);
		}
		return r;
	}

	/** The cached roll or {@code null} (never solves). */
	public DiceRoll peek(long key) {
		return map.get(key);
	}

	public int size() {
		return map.size();
	}

	public void clear() {
		map.clear();
	}
}
