package dev.nezo.burmaldaholic.core.rng;

/**
 * Lucky/Unlucky streak math (GAME_DESIGN.md §14). Pure Java, unit-tested; shared vectors with Bedrock.
 */
public final class StreakRules {
	private StreakRules() {}

	/** Streak settings (config {@code streak.*}). */
	public record Settings(boolean enabled, int max, double luckyPerStep, double pityPerStep, double minHouseEdge, int decayTicks) {
		public static final Settings DEFAULTS = new Settings(true, 10, 0.005, 0.003, 0.01, 12000);
	}

	/**
	 * New streak after a settled wager with stake ≥ 1: win (net &gt; 0) → {@code max(S,0)+1},
	 * loss (net &lt; 0) → {@code min(S,0)−1}, push → unchanged; clamped to ±max.
	 */
	public static int update(int s, long net, int max) {
		if (net > 0) {
			return Math.min(max, Math.max(s, 0) + 1);
		}
		if (net < 0) {
			return Math.max(-max, Math.min(s, 0) - 1);
		}
		return s;
	}

	/** Streak and decay anchor after lazy decay. */
	public record Decayed(int streak, long anchor) {}

	/**
	 * For every {@code decayTicks} of world time since {@code anchor} without a settled wager, S moves
	 * one step toward 0. {@code decayTicks == 0} disables decay.
	 */
	public static Decayed decay(int s, long anchor, long now, int decayTicks) {
		if (decayTicks <= 0 || s == 0 || now <= anchor) {
			return new Decayed(s, s == 0 ? now : anchor);
		}
		long steps = (now - anchor) / decayTicks;
		if (steps <= 0) {
			return new Decayed(s, anchor);
		}
		int abs = Math.abs(s);
		int left = (int) Math.max(0, abs - steps);
		int result = Integer.signum(s) * left;
		return new Decayed(result, left == 0 ? now : anchor + steps * decayTicks);
	}

	/**
	 * Probability of re-drawing a losing outcome once:
	 * {@code r = min(r_raw, r_cap)}, {@code r_raw = 0.005·S} (S &gt; 0) or {@code 0.003·|S|} (S &lt; 0),
	 * {@code r_cap = max(0, (1 − minHouseEdge) / rtp − 1)}. Guarantees RTP' ≤ 1 − minHouseEdge.
	 */
	public static double redrawProbability(int s, double rtp, Settings settings) {
		if (!settings.enabled() || s == 0 || rtp <= 0) {
			return 0;
		}
		double raw = s > 0 ? settings.luckyPerStep() * s : settings.pityPerStep() * -s;
		double cap = Math.max(0, (1 - settings.minHouseEdge()) / rtp - 1);
		return Math.max(0, Math.min(raw, cap));
	}
}
