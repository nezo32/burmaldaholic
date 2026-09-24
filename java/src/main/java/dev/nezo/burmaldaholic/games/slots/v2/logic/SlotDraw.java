package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * CONFIRM → DRAW TAPE → (streak re-draw) → reserve/award jackpots → PERSIST (SLOTS.md §1.2). Pure: the
 * module passes the jackpot view and receives the tape; it then debits pools, persists and presents.
 * SKELETON — lane S-J2 / S-B2.
 */
public final class SlotDraw {
	private SlotDraw() {}

	/**
	 * @param def    machine definition
	 * @param bet    total bet (on the ladder, multiple of 5)
	 * @param buy    buy feature instead of a base spin
	 * @param owned  owned-casino machine (fixed jackpots, no pools, SLOTS.md §5.2)
	 * @param pools  current pool values per tier 1..4 (index 0 unused); ignored when owned
	 */
	public record Request(MachineDef def, long bet, boolean buy, boolean owned, long[] pools) {}

	public static SpinTape draw(Request request, SlotRng rng) {
		throw new UnsupportedOperationException("slots v2 draw: lane S-J2");
	}

	/** Owned-casino reservation per spin (SLOTS.md §8.6): {@code cap × bet}, also for a bought feature. */
	public static long reservation(MachineDef def, long bet) {
		return (long) def.capMultiple() * bet;
	}
}
