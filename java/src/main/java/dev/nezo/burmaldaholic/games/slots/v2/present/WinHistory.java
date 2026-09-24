package dev.nezo.burmaldaholic.games.slots.v2.present;

/**
 * The win panel's "last wins" list (lane J-L9b; PURE): the settled totals of the latest spins that paid anything,
 * newest first, recorded only when a spin settles (after its reveal, F6) and at most once per spin.
 */
public final class WinHistory {
	public static final int SIZE = 3;

	/** One settled win: the amount and the bet it was won at (amount below the bet = Returned, drawn grey, F9). */
	public record Entry(long amount, long bet) {
		public boolean returned() {
			return amount < bet;
		}
	}

	private final Entry[] entries = new Entry[SIZE];
	private int count;
	private Object last;

	/** Records the settled spin {@code spin} (an identity object, e.g. its script) once, and only when it paid something. */
	public void settle(Object spin, long amount, long bet) {
		if (spin == null || spin == last) return;
		last = spin;
		if (amount <= 0) return;
		System.arraycopy(entries, 0, entries, 1, SIZE - 1);
		entries[0] = new Entry(amount, Math.max(1, bet));
		count = Math.min(SIZE, count + 1);
	}

	public int size() {
		return count;
	}

	/** Entry {@code i} (0 = newest). */
	public Entry get(int i) {
		return entries[i];
	}
}
