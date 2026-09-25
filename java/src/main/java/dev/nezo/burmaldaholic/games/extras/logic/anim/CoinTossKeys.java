package dev.nezo.burmaldaholic.games.extras.logic.anim;

/**
 * Keyframes of the in-world Lucky Coin toss that spectators see (extras-pvp.md §1.3): a transient item display thrown
 * up 1.1 blocks and caught in six 3-tick keyframes while it rolls 0 → 720° about its X axis in 120° steps (slerp takes
 * the shortest path, so no step may exceed 180°), then the true face at tick {@link #LAND_TICK} (the round is settled
 * before the toss starts, and the face model is only switched in at the landing: nobody sees it earlier), a scale pop,
 * the shrink at {@link #SHRINK_TICK} and the removal at {@link #REMOVE_TICK}. PURE; the server applies a key at its
 * {@link #tick} with {@link Key#interpolation} ticks of client-side interpolation.
 */
public final class CoinTossKeys {
	/** Keyframes of the flight (1…6). */
	public static final int FLIGHT_KEYS = 6;
	public static final int KEY_TICKS = 3;
	/** Height of the arc (blocks). */
	public static final float ARC = 1.1f;
	public static final float SCALE = 0.4f;
	public static final float POP_SCALE = 0.5f;
	/** The face model is switched in and the coin lands (sound, crit burst). */
	public static final int LAND_TICK = 21;
	public static final int SETTLE_TICK = 24;
	public static final int SHRINK_TICK = 40;
	public static final int SHRINK_TICKS = 4;
	public static final int REMOVE_TICK = 45;

	private CoinTossKeys() {}

	/** One key: applied at {@code tick}, interpolated over {@code interpolation} ticks. */
	public record Key(int tick, int interpolation, float y, float rollDeg, float scale, boolean face) {}

	/** Flight key {@code k} (1…6): applied at tick {@code 3k − 2}, so key 6 is reached at tick 19. */
	public static Key flight(int k) {
		int kk = Math.max(1, Math.min(FLIGHT_KEYS, k));
		double p = kk / (double) FLIGHT_KEYS;
		float y = (float) (ARC * 4 * p * (1 - p));
		return new Key(KEY_TICKS * kk - 2, KEY_TICKS, y, 120f * kk, SCALE, false);
	}

	/** The key applied at {@code tick}, or {@code null} (nothing changes on this tick). */
	public static Key at(int tick) {
		if (tick >= 1 && tick <= KEY_TICKS * FLIGHT_KEYS - 2 && (tick + 2) % KEY_TICKS == 0) return flight((tick + 2) / KEY_TICKS);
		if (tick == LAND_TICK) return new Key(LAND_TICK, 2, 0f, 0f, POP_SCALE, true);
		if (tick == SETTLE_TICK) return new Key(SETTLE_TICK, KEY_TICKS, 0f, 0f, SCALE, true);
		if (tick == SHRINK_TICK) return new Key(SHRINK_TICK, SHRINK_TICKS, 0f, 0f, 0f, true);
		return null;
	}

	/** True while the face may not be shown (the model stays on the edge-on "spin" variant). */
	public static boolean faceHidden(int tick) {
		return tick < LAND_TICK;
	}
}
