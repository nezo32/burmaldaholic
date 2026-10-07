package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pure bet model of a v2 table screen (docs/architecture/ui-v2.md §5.1), built by each game's screen from the state
 * tag the server sent. The kit's BetBar, the shared {@link BetGates} and every game's {@code <G>Flow} read only this, so
 * the bet flow can be enumerated exhaustively in unit tests ({@link FlowCheck}).
 *
 * @param seated      the viewer has a seat
 * @param canSit      a seat is free and the viewer may take it (ignored when seated)
 * @param phase       coarse phase of the round
 * @param bettingOpen bets are accepted now (for the viewer)
 * @param min         table minimum (≥ 1)
 * @param max         table maximum for the viewer (0 = no maximum)
 * @param balance     the viewer's chips
 * @param myTotal     the viewer's total stake on the table this round
 * @param spots       multi-spot games: spot id → amount (insertion order kept); single-spot games: empty
 * @param canUndo     an undoable chip is on the table
 * @param canClear    removable chips are on the table
 * @param canRebet    the last round's bet can be repeated now
 * @param rebetAmount the amount a rebet places (0 = none)
 * @param canDouble   the current bet can be doubled now (×2 tool)
 * @param waitingFor  name of the player the table waits for, or ""
 * @param secondsLeft seconds left on the current timer, or -1
 */
public record BetView(boolean seated, boolean canSit, Phase phase, boolean bettingOpen, long min, long max, long balance, long myTotal,
		Map<String, Long> spots, boolean canUndo, boolean canClear, boolean canRebet, long rebetAmount, boolean canDouble, String waitingFor,
		int secondsLeft) {
	/** The chip denominations of the casino (chip sprites {@code core/v2/chip_<d>}). */
	public static final long[] DENOMS = {1, 5, 25, 100, 500, 1000, 5000};
	/** At most this many chips in the tray. */
	public static final int MAX_CHIPS = 5;

	public enum Phase {
		/** Bets are taken (possibly with a countdown). */
		BETTING,
		/** Bets are closed, the round is about to start (no more bets). */
		LOCKED,
		/** Players act (hit / stand, fold / call, …). */
		ACTING,
		/** The outcome is being shown and paid. */
		SETTLING,
		/** Nothing happens until someone bets / sits. */
		IDLE
	}

	public BetView {
		phase = phase == null ? Phase.IDLE : phase;
		spots = spots == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(spots));
		waitingFor = waitingFor == null ? "" : waitingFor;
		min = Math.max(1, min);
	}

	/** The viewer can afford the minimum. */
	public boolean affordsMin() {
		return balance >= min;
	}

	/** The viewer may put chips down now. */
	public boolean canBet() {
		return seated && bettingOpen && balance > 0;
	}

	/** Room left under the maximum (Long.MAX_VALUE when unlimited). */
	public long roomUnderMax() {
		return max <= 0 ? Long.MAX_VALUE : Math.max(0, max - myTotal);
	}

	/**
	 * The chips offered in the tray (≤ {@link #MAX_CHIPS}, ascending): denominations no larger than the maximum and the
	 * balance; when more fit, the smallest affordable one plus the largest others. The smallest affordable chip is always
	 * offered when the balance covers the minimum (so a bet can always be built up), even if it is above the maximum
	 * left this round. Empty only when the viewer has no chips.
	 */
	public long[] chips(long[] denoms) {
		long cap = Math.min(max <= 0 ? Long.MAX_VALUE : max, balance);
		long[] fit = Arrays.stream(denoms).filter(d -> d > 0 && d <= cap).sorted().toArray();
		if (fit.length == 0) {
			if (balance <= 0) return new long[0];
			long smallest = Arrays.stream(denoms).filter(d -> d > 0).min().orElse(1);
			return smallest <= balance ? new long[] {smallest} : new long[0];
		}
		if (fit.length <= MAX_CHIPS) return fit;
		// five consecutive denominations from the largest one not above the minimum (a 10–1000 table: 5 … 1000)
		int start = 0;
		for (int i = 0; i < fit.length; i++) if (fit[i] <= min) start = i;
		start = Math.min(start, fit.length - MAX_CHIPS);
		return Arrays.copyOfRange(fit, start, start + MAX_CHIPS);
	}

	/** {@link #chips(long[])} of the standard {@link #DENOMS}. */
	public long[] chips() {
		return chips(DENOMS);
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder toBuilder() {
		Builder b = new Builder();
		b.seated = seated;
		b.canSit = canSit;
		b.phase = phase;
		b.bettingOpen = bettingOpen;
		b.min = min;
		b.max = max;
		b.balance = balance;
		b.myTotal = myTotal;
		b.spots = new LinkedHashMap<>(spots);
		b.canUndo = canUndo;
		b.canClear = canClear;
		b.canRebet = canRebet;
		b.rebetAmount = rebetAmount;
		b.canDouble = canDouble;
		b.waitingFor = waitingFor;
		b.secondsLeft = secondsLeft;
		return b;
	}

	/** Fluent construction (the record has many fields; screens set the ones their state carries). */
	public static final class Builder {
		private boolean seated, canSit, bettingOpen, canUndo, canClear, canRebet, canDouble;
		private Phase phase = Phase.IDLE;
		private long min = 1, max, balance, myTotal, rebetAmount;
		private Map<String, Long> spots = new LinkedHashMap<>();
		private String waitingFor = "";
		private int secondsLeft = -1;

		public Builder seated(boolean v) { seated = v; return this; }
		public Builder canSit(boolean v) { canSit = v; return this; }
		public Builder phase(Phase v) { phase = v; return this; }
		public Builder bettingOpen(boolean v) { bettingOpen = v; return this; }
		public Builder limits(long min, long max) { this.min = min; this.max = max; return this; }
		public Builder balance(long v) { balance = v; return this; }
		public Builder myTotal(long v) { myTotal = v; return this; }
		public Builder spot(String id, long amount) { spots.put(id, amount); return this; }
		public Builder spots(Map<String, Long> v) { spots = new LinkedHashMap<>(v); return this; }
		public Builder canUndo(boolean v) { canUndo = v; return this; }
		public Builder canClear(boolean v) { canClear = v; return this; }
		public Builder rebet(boolean can, long amount) { canRebet = can; rebetAmount = amount; return this; }
		public Builder canDouble(boolean v) { canDouble = v; return this; }
		public Builder waitingFor(String v) { waitingFor = v; return this; }
		public Builder secondsLeft(int v) { secondsLeft = v; return this; }

		public BetView build() {
			return new BetView(seated, canSit, phase, bettingOpen, min, max, balance, myTotal, spots, canUndo, canClear, canRebet, rebetAmount,
				canDouble, waitingFor, secondsLeft);
		}
	}
}
