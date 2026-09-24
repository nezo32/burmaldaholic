package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.List;

/**
 * The drawn "tape" of one spin (SLOTS.md §1.2): every random value of the spin, fixed at CONFIRM, persisted
 * before anything is shown ({@code {v:2, machine, bet, price?, tape, total, jackpotAwards[], startTick}},
 * SLOTS.md §8.1) and replayed by the presentation. Settlement uses only {@link #totalChips()} and
 * {@link #jackpots()}; everything else drives the timeline.
 *
 * @param machine     machine
 * @param bet         total bet (chips; multiple of 5)
 * @param bought      bought feature (no base spin; price = bet × buy price)
 * @param stops       5 base stops (empty when bought)
 * @param freeSpins   free-spin feature or {@code null}
 * @param hunt        Treasure Hunt or {@code null}
 * @param hoard       Piglin's Hoard or {@code null}
 * @param wheel       Dragon Wheel or {@code null}
 * @param jackpots    jackpot awards in tape order (amount fixed at draw, SLOTS.md §5.2)
 * @param totalFifths spin total EXCLUDING progressive awards, capped (fifths of the bet)
 * @param capHit      the max-win cap ended the spin (SLOTS.md §1.3)
 */
public record SpinTape(Machine machine, long bet, boolean bought, int[] stops, FreeSpins freeSpins, Hunt hunt, Hoard hoard,
		Wheel wheel, List<JackpotAward> jackpots, long totalFifths, boolean capHit) {
	/** Tape format version (SLOTS.md §8.1 {@code v:2}). */
	public static final int VERSION = 2;

	/** Chips credited for the spin excluding progressive awards: {@code totalFifths × bet / 5} (exact). */
	public long totalChips() {
		return totalFifths * bet / 5;
	}

	/** One free spin: its stops, the sticky mask AFTER it (End), whether it retriggered. */
	public record FreeSpin(int[] stops, int stickyMaskAfter, boolean retrigger, long payFifths) {}

	/** Free-spin feature: spins awarded at trigger and every spin played (retriggers included). */
	public record FreeSpins(int awarded, List<FreeSpin> spins, long payFifths) {}

	/**
	 * Treasure Hunt: i.i.d. contents in REVEAL order; the i-th opened chest shows entry i whichever chest is
	 * clicked (SLOTS.md §1.2). Entries: 1,2,3,5,10,25 = coins ×bet; -1..-4 = Mini..Grand; 0 = Creeper.
	 */
	public record Hunt(int[] entries, int opened) {}

	/**
	 * Piglin's Hoard: initial coin cells/values, then per respin the new coins (cell, value). Value codes as
	 * {@link Hunt#entries()} (positive = ×bet, negative = jackpot tier), 15 filled adds the Grand.
	 */
	public record Hoard(int[] initialCells, int[] initialValues, List<int[]> respinCells, List<int[]> respinValues) {}

	/** Dragon Wheel: segment index per ring reached (outer, middle, core); length 1–3. */
	public record Wheel(int[] segments) {}

	/**
	 * A jackpot won in this spin (tier 1 Mini … 4 Grand), amount fixed at draw time (pool debited then, so
	 * two players can never win the same money). Owned machines: fixed multiple, inside the cap.
	 */
	public record JackpotAward(int tier, long chips, boolean owned) {}
}
