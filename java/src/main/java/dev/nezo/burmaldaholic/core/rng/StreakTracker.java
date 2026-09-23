package dev.nezo.burmaldaholic.core.rng;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Win/loss streak per player (across all games). Positive = consecutive wins, negative =
 * consecutive losses, pushes do not change it. Core feeds it from
 * {@code CasinoEvents.PLAY_RESOLVED}; the chaos module registers an {@link OddsModifier} reading it.
 * In-memory only (resets on server restart) — persist later if the design requires.
 */
public final class StreakTracker {
	private static final StreakTracker INSTANCE = new StreakTracker();
	private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

	public static StreakTracker get() {
		return INSTANCE;
	}

	public int streak(UUID player) {
		return streaks.getOrDefault(player, 0);
	}

	/** @param outcome +1 win, -1 loss, 0 push. Returns the new streak. */
	public int record(UUID player, int outcome) {
		return streaks.merge(player, Integer.signum(outcome), (old, delta) -> {
			if (delta == 0) {
				return old;
			}
			if (Integer.signum(old) == delta) {
				return old + delta;
			}
			return delta;
		});
	}

	public void reset(UUID player) {
		streaks.remove(player);
	}
}
