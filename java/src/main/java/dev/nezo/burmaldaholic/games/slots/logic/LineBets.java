package dev.nezo.burmaldaholic.games.slots.logic;

/**
 * Line-bet limits of a machine (GAME_DESIGN.md §8.2–8.4, CONFIG.md {@code slots.*}) and auto-spin rules
 * (UI.md §6). PURE.
 *
 * @param minLineBet machine minimum line bet (Copper/Gold 1, Netherite {@code slots.netherite.minLineBet})
 * @param maxLineBet machine maximum line bet ({@code slots.<tier>.maxLineBet})
 */
public record LineBets(long minLineBet, long maxLineBet) {
	/** Auto-spin length ("Auto ×10"). */
	public static final int AUTO_SPINS = 10;
	/** Auto-spin stops on a single spin returning ≥ 20 × the spin bet. */
	public static final int AUTO_BIG_WIN_MULTIPLE = 20;

	public LineBets {
		minLineBet = Math.max(1, minLineBet);
		maxLineBet = Math.max(1, maxLineBet);
	}

	/**
	 * Effective range for a player: {@code [min, min(maxLineBet, floor(tierMax / lines))]}. {@code max < min}
	 * means the player's VIP maximum is too low for this machine.
	 */
	public Range range(long tierMax, int lines) {
		long max = Math.min(maxLineBet, Math.max(0, tierMax) / Math.max(1, lines));
		return new Range(minLineBet, max);
	}

	/** Machine maximum spin bet (the jackpot share denominator). */
	public long machineMaxSpinBet(int lines) {
		return maxLineBet * Math.max(1, lines);
	}

	/** @param min smallest line bet, @param max largest line bet (may be &lt; min: nothing playable) */
	public record Range(long min, long max) {
		public boolean playable() {
			return max >= min;
		}

		/** Clamp a requested line bet into the range (min when nothing fits). */
		public long clamp(long v) {
			if (!playable()) {
				return min;
			}
			return Math.max(min, Math.min(max, v));
		}
	}

	/** Whether auto-spin should stop after a spin (big win), before looking at the balance. */
	public static boolean autoStopsOnWin(long totalReturn, long spinBet) {
		return totalReturn >= AUTO_BIG_WIN_MULTIPLE * spinBet;
	}
}
