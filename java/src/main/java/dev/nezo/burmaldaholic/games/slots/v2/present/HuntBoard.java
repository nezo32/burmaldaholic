package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Treasure Hunt board state (slots.md §4.8, SLOTS.md §1.2 pick-me honesty; PURE). The client learns entry i only
 * when the server confirms the i-th pick (F7, D6): until then the pressed chest RATTLES. Whichever chest is clicked,
 * the i-th opened chest shows entry i. When the hunt ends (Creeper, or all opened) the remaining chests open DIMMED
 * with the remaining real entries (80 ms stagger); dimmed prizes do not add to the total.
 */
public final class HuntBoard {
	public static final int CHESTS = 15;
	public static final int OPEN_MS = 400;
	public static final int PRIZE_MS = 300;
	public static final int DIM_STAGGER_MS = 80;
	public static final int DROP_MS = 300;
	public static final int DROP_STAGGER_MS = 40;

	public enum State {
		CLOSED,
		/** pressed, waiting for the server's reveal: rattles */
		PENDING,
		OPEN,
		/** opened at the end with a remaining entry (not paid) */
		DIMMED
	}

	private final State[] state = new State[CHESTS];
	private final int[] entry = new int[CHESTS];
	private final double[] stamp = new double[CHESTS];
	private final int[] pickOrder = new int[CHESTS];
	private int pending = -1;
	private int opened;
	private boolean ended;
	private long totalTimesBet;
	private int lastOpened = -1;

	public HuntBoard() {
		for (int i = 0; i < CHESTS; i++) {
			state[i] = State.CLOSED;
			pickOrder[i] = -1;
		}
	}

	public State state(int chest) {
		return state[chest];
	}

	public int entry(int chest) {
		return entry[chest];
	}

	/** ms timestamp of the chest's last change (press / reveal / dim). */
	public double stamp(int chest) {
		return stamp[chest];
	}

	public int pending() {
		return pending;
	}

	public int opened() {
		return opened;
	}

	public boolean ended() {
		return ended;
	}

	public int lastOpened() {
		return lastOpened;
	}

	/** Sum of the coin prizes opened so far, in × bet (jackpot gems and the Creeper add 0). */
	public long totalTimesBet() {
		return totalTimesBet;
	}

	public boolean canPick(int chest) {
		return !ended && pending < 0 && chest >= 0 && chest < CHESTS && state[chest] == State.CLOSED;
	}

	/** The player pressed a chest: it rattles until {@link #reveal}. Returns false when not allowed. */
	public boolean press(int chest, double nowMs) {
		if (!canPick(chest)) return false;
		state[chest] = State.PENDING;
		stamp[chest] = nowMs;
		pending = chest;
		return true;
	}

	/** First closed chest in reading order (autoplay / "Open all" / the spectator timeline). */
	public int nextClosed() {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 5; col++) {
				int c = row * 5 + col;
				if (state[c] == State.CLOSED) return c;
			}
		}
		return -1;
	}

	/**
	 * The server revealed the next entry: it goes to the pending chest (or, without one, to the next closed chest in
	 * reading order). A Creeper ({@code 0}) ends the hunt.
	 */
	public int reveal(int value, double nowMs) {
		int chest = pending >= 0 ? pending : nextClosed();
		if (chest < 0 || ended) return -1;
		state[chest] = State.OPEN;
		entry[chest] = value;
		stamp[chest] = nowMs;
		pickOrder[chest] = opened;
		opened++;
		pending = -1;
		lastOpened = chest;
		if (value > 0) totalTimesBet += value;
		if (value == 0 || opened >= CHESTS) ended = true;
		return chest;
	}

	/** End of the hunt: open the remaining chests dimmed with the remaining entries, 80 ms apart in reading order. */
	public void revealRest(int[] remaining, double nowMs) {
		ended = true;
		int i = 0;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 5; col++) {
				int c = row * 5 + col;
				if (state[c] != State.CLOSED && state[c] != State.PENDING) continue;
				state[c] = State.DIMMED;
				entry[c] = i < remaining.length ? remaining[i] : 0;
				stamp[c] = nowMs + DIM_STAGGER_MS * (double) i;
				i++;
			}
		}
		pending = -1;
	}

	// ---- visual helpers --------------------------------------------------------------------------------------

	/** Rattle x offset (px) while pending: ±1 px at 20 Hz for 1 s, then slower (5 Hz). */
	public static int rattle(double msSincePress) {
		if (msSincePress < 0) return 0;
		double hz = msSincePress < 1000 ? 20 : 5;
		return Math.sin(2 * Math.PI * hz * msSincePress / 1000.0) >= 0 ? 1 : -1;
	}

	/** Lid flipbook frame 0 (closed) … 5 (open) over 400 ms after the reveal. */
	public static int openFrame(double msSinceReveal) {
		if (msSinceReveal <= 0) return 0;
		return Math.min(5, 1 + (int) (msSinceReveal / 80));
	}

	/** Prize pop scale (after the lid, 300 ms outBack). */
	public static double prizePop(double msSinceReveal) {
		double t = msSinceReveal - OPEN_MS;
		if (t <= 0) return 0;
		if (t >= PRIZE_MS) return 1;
		return Ease.OUT_BACK.apply(t / PRIZE_MS);
	}

	/** Coin pile size 0 small (1–3×), 1 medium (5–10×), 2 large (25×). */
	public static int pileSize(int value) {
		if (value >= 25) return 2;
		if (value >= 5) return 1;
		return 0;
	}

	/** Intro drop: y offset above the slot (px) of chest i at {@code ms} since the intro start (unbounded intro). */
	public static double dropOffset(int chest, double ms) {
		return dropOffset(chest, ms, Double.POSITIVE_INFINITY);
	}

	/**
	 * Intro drop inside an intro of {@code introMs}: the 40 ms stagger and 300 ms drop are compressed so the LAST chest
	 * has landed when the intro ends (the timeline holds at the intro's end while it waits for the picks, so a chest
	 * still in the air there would hang misaligned over the grid for the whole hunt).
	 */
	public static double dropOffset(int chest, double ms, double introMs) {
		double drop = Math.min(DROP_MS, Math.max(1, introMs * 0.5));
		double stagger = Math.min(DROP_STAGGER_MS, Math.max(0, (introMs - drop) / (CHESTS - 1)));
		double t = ms - stagger * chest;
		if (t <= 0) return 30;
		if (t >= drop || ms >= introMs) return 0;
		return 30 * (1 - Ease.OUT_BOUNCE.apply(t / drop));
	}

	/** Hover wobble angle (degrees) at 2 Hz, ±3°. */
	public static double wobble(double ms) {
		return 3 * Math.sin(2 * Math.PI * 2 * ms / 1000.0);
	}

	/** Creeper swell scale 1 → 1.35 over 600 ms (inCubic). */
	public static double creeperSwell(double msSinceReveal) {
		double t = msSinceReveal - OPEN_MS;
		if (t <= 0) return 1;
		return 1 + 0.35 * Ease.IN_CUBIC.apply(Math.min(1, t / 600.0));
	}
}
