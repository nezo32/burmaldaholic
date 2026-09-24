package dev.nezo.burmaldaholic.core.pvp.logic;

/**
 * Record of one ordered pair A→B (PVP.md §3.8). {@code run}: +n = A won the last n against B, −n = lost
 * the last n. Bots never have records (BOTS.md §5.3).
 */
public record HeadToHead(int wins, int losses, long net, int run) {
	public static final HeadToHead EMPTY = new HeadToHead(0, 0, 0, 0);

	public HeadToHead win(long chips) {
		return new HeadToHead(wins + 1, losses, net + chips, run > 0 ? run + 1 : 1);
	}

	public HeadToHead loss(long chips) {
		return new HeadToHead(wins, losses + 1, net - chips, run < 0 ? run - 1 : -1);
	}

	/** Next 2-player meeting is a grudge match for the side whose run is ≤ −grudgeLosses. */
	public boolean grudge(int grudgeLosses) {
		return run <= -grudgeLosses;
	}
}
