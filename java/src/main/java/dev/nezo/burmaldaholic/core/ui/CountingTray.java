package dev.nezo.burmaldaholic.core.ui;

/**
 * The cashier's counting tray (extras.md §8.3, global.md §4.3; lane J-L2): one chip column per denomination
 * ({@link ChipColumns#DENOMS} order, largest first) filled by chips flying in one after another — largest
 * denomination first, bottom disc first — then the stacks stay with their counts until the next move. Pure timing and
 * layout; the screen draws it. A column shows at most {@link #MAX_DISCS} discs (the count label says the rest).
 */
public final class CountingTray {
	public static final int MAX_DISCS = 7;
	public static final int FLIGHT_MS = 280;
	/** Stagger between two chips; shorter for big moves so the whole count stays near one second. */
	public static final int STAGGER_MS = 70;
	public static final int MIN_STAGGER_MS = 25;
	/** Budget for all launches of a big move (the stagger shrinks to fit, down to {@link #MIN_STAGGER_MS}). */
	public static final int LAUNCH_BUDGET_MS = 840;

	private final long[] counts;
	private final int[] discs;
	private final int[] firstFlight;
	private final int total;
	private final int stagger;

	/** {@code counts} per {@link ChipColumns#DENOMS} (copied; missing entries = 0, negatives = 0). */
	public CountingTray(long[] counts) {
		int n = ChipColumns.DENOMS.length;
		this.counts = new long[n];
		this.discs = new int[n];
		this.firstFlight = new int[n];
		int k = 0;
		for (int i = 0; i < n; i++) {
			long c = counts != null && i < counts.length ? Math.max(0, counts[i]) : 0;
			this.counts[i] = c;
			this.discs[i] = (int) Math.min(MAX_DISCS, c);
			this.firstFlight[i] = k;
			k += discs[i];
		}
		this.total = k;
		this.stagger = total <= 1 ? STAGGER_MS : Math.max(MIN_STAGGER_MS, Math.min(STAGGER_MS, LAUNCH_BUDGET_MS / (total - 1)));
	}

	/** Chips of column {@code col} (the real count, not capped). */
	public long count(int col) {
		return counts[col];
	}

	/** Discs drawn in column {@code col} once every chip has landed. */
	public int discs(int col) {
		return discs[col];
	}

	/** Chip items in the move (all columns). */
	public long chips() {
		long sum = 0;
		for (long c : counts) sum += c;
		return sum;
	}

	/** Flights (drawn discs) of the whole move. */
	public int flights() {
		return total;
	}

	public int staggerMs() {
		return stagger;
	}

	/** When the last chip has landed (ms after the start); 0 for an empty move. */
	public long flightsDoneMs() {
		return total == 0 ? 0 : (long) (total - 1) * stagger + FLIGHT_MS;
	}

	/**
	 * Flight progress of disc {@code j} (0 = bottom) of column {@code col} at {@code t} ms after the start: {@code < 0}
	 * not launched yet, {@code 0..1} in the air, {@code >= 1} landed.
	 */
	public double flight(int col, int j, long t) {
		long start = (long) (firstFlight[col] + j) * stagger;
		return (t - start) / (double) FLIGHT_MS;
	}

	/** Discs of column {@code col} that have landed at {@code t}. */
	public int landed(int col, long t) {
		int n = 0;
		for (int j = 0; j < discs[col]; j++) if (flight(col, j, t) >= 1) n++;
		return n;
	}

	/** Discs landed over all columns at {@code t} (drives the chip-stack clicks). */
	public int landed(long t) {
		int n = 0;
		for (int i = 0; i < discs.length; i++) n += landed(i, t);
		return n;
	}

	/** Y of disc {@code j} (0 = bottom) of a stack whose lowest disc ends at {@code baseY} (discs are 3 px high). */
	public static int discY(int baseY, int j) {
		return baseY - 3 - j * ChipColumns.PITCH;
	}
}
