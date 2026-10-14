package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The bet of a single-spot extras game (Plinko, Wheel of Fortune, Coin Flip, Dice Duel; docs/architecture/ui-v2.md §5,
 * REDESIGN.md §7.7) as the shared BetBar builds it. These games take the amount WITH the play action ({@code drop},
 * {@code spin}, {@code flip}, {@code roll}: the server validates and debits it then), so the chips do not travel one by
 * one: a chip click adds to this local amount, Undo removes the last chip, Clear empties it, Rebet restores the amount
 * last played, ×2 doubles it — every step clamped to the table maximum and the balance. The amount stays after a round
 * (press the primary again to repeat), like the old screens' bet well.
 *
 * <p>PURE and unit-tested ({@code LocalBetTest}); {@link #view} feeds the kit's {@code BetGates} so the chip / tool /
 * primary reasons are the same as at every table.
 */
public final class LocalBet {
	private long amount;
	private long last;
	/** The amount of a play sent and not yet answered by the server (0 = none). */
	private long pending;
	private final Deque<Long> chips = new ArrayDeque<>();

	public LocalBet() {}

	public LocalBet(long initial) {
		this.amount = Math.max(0, initial);
	}

	public long amount() {
		return amount;
	}

	/** The amount last played (the Rebet target), 0 = none. */
	public long last() {
		return last;
	}

	/** Cap of the bet: the table maximum for the viewer (0 = none) and the balance. */
	static long cap(long max, long balance) {
		long c = max <= 0 ? Long.MAX_VALUE : max;
		return Math.max(0, Math.min(c, balance));
	}

	/** Adds a chip, clamped to the cap; false when nothing was added. */
	public boolean add(long denom, long max, long balance) {
		long cap = cap(max, balance);
		if (denom <= 0 || amount >= cap) return false;
		long added = Math.min(denom, cap - amount);
		amount += added;
		chips.push(added);
		return true;
	}

	/** Removes the last chip added (or one chip of {@code denom} worth when the history is empty). */
	public boolean undo() {
		if (!chips.isEmpty()) {
			amount = Math.max(0, amount - chips.pop());
			return true;
		}
		return false;
	}

	public boolean clear() {
		boolean had = amount > 0;
		amount = 0;
		chips.clear();
		return had;
	}

	/** The last played amount again (clamped); false when there is none. */
	public boolean rebet(long max, long balance) {
		if (last <= 0) return false;
		chips.clear();
		amount = Math.min(last, cap(max, balance));
		if (amount > 0) chips.push(amount);
		return amount > 0;
	}

	/** Doubles the amount (clamped). */
	public boolean dbl(long max, long balance) {
		if (amount <= 0) return false;
		long before = amount;
		amount = Math.min(amount * 2, cap(max, balance));
		if (amount > before) chips.push(amount - before);
		return amount > before;
	}

	/** Clamps after the balance or the limits changed (a loss, a new machine): never above the cap. */
	public void clamp(long max, long balance) {
		long cap = cap(max, balance);
		if (amount > cap) {
			amount = cap;
			chips.clear();
			if (amount > 0) chips.push(amount);
		}
	}

	/** The amount was played: it becomes the Rebet target (the amount itself stays for the next round). */
	public void played() {
		last = amount;
	}

	/**
	 * The play action was sent with the current amount; it becomes the Rebet target only when the server answers with
	 * its result ({@link #ack}), so a refused play (limits, balance, a busy machine) never changes Rebet (R-CDE m5).
	 */
	public void sent() {
		pending = amount;
	}

	/** The server's result for the play {@link #sent} arrived: that amount is now the Rebet target. */
	public void ack() {
		if (pending > 0) last = pending;
		pending = 0;
	}

	/** The amount of a play sent and not yet answered (0 = none). */
	public long pending() {
		return pending;
	}

	/**
	 * The kit's bet model: always "seated" (a machine has no seats), betting open unless a round runs ({@code busy} →
	 * {@link BetView.Phase#SETTLING}, reason "round in progress" unless the game's flow says better).
	 */
	public BetView view(long min, long max, long balance, boolean busy) {
		long cap = cap(max, balance);
		return BetView.builder()
			.seated(true)
			.canSit(false)
			.phase(busy ? BetView.Phase.SETTLING : BetView.Phase.BETTING)
			.bettingOpen(!busy)
			.limits(Math.max(1, min), max)
			.balance(balance)
			.myTotal(amount)
			.canUndo(!chips.isEmpty())
			.canClear(amount > 0)
			.rebet(last > 0 && last != amount, last)
			.canDouble(amount > 0 && amount < cap)
			.build();
	}
}
