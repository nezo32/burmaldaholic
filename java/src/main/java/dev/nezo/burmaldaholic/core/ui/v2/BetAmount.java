package dev.nezo.burmaldaholic.core.ui.v2;

import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.wager.BetCap;
import org.jspecify.annotations.Nullable;

/**
 * The pure maths of the bet-entry control (lane HR, owner playtest E4: "pressing 200 times for 100k is hell"): a typed
 * amount, Max, ×2, ½ and the mouse-wheel step, always clamped to {@code min(table max, VIP max, balance)} — and when a
 * clamp happens, WHY (whose cap: {@link BetCap}). Unit-tested; the client's {@code BetEntry} only draws and routes input.
 *
 * <p>Two meanings of the amount: {@link Mode#BET} (single-spot games: it becomes the bet) and {@link Mode#UNIT} (multi-spot
 * games: it becomes the chip a felt click places, so the room under the maximum counts what is already on the table).
 */
public final class BetAmount {
	private BetAmount() {}

	/** Longest typed amount (digits). */
	public static final int MAX_DIGITS = 12;

	public enum Mode {
		/** The amount is the whole bet (blackjack, Plinko, …). */
		BET,
		/** The amount is what one felt click places (roulette, craps, the baccarat spots). */
		UNIT
	}

	/**
	 * An amount after clamping, and why it was changed ({@code reasonKey} null = it was not).
	 *
	 * @param value      the clamped amount (0 = nothing can be bet)
	 * @param reasonKey  the sentence of the bound that clamped it, or null
	 * @param reasonArgs its arguments (numbers / tier names)
	 */
	public record Clamp(long value, @Nullable String reasonKey, Object[] reasonArgs) {
		public boolean clamped() {
			return reasonKey != null;
		}
	}

	/**
	 * What a field amount costs on the table: {@code per × amount + extra}. Most games: the amount IS the stake
	 * ({@link #ONE}). A game may check its maximum and the balance against other multiples of the amount than the stake
	 * it debits: Ultimate Texas Hold'em debits Ante + Blind + Trips ({@code 2 × Ante + Trips}), but its maximum holds the
	 * worst case {@code 6 × Ante + Trips} and the balance must keep the river's Play bet ({@code 3 × Ante + Trips}; T-HR D5,
	 * built by {@code UthFlow.entryCost} from {@code UthLimits}, the server's own rule). The field shows the amount.
	 *
	 * @param per          stake per amount (shown as "Total stake")
	 * @param extra        stake already on the table besides the field
	 * @param maxPer       per amount, as counted against the maximum
	 * @param maxExtra     extra, as counted against the maximum
	 * @param balancePer   per amount, as the balance must hold it
	 * @param balanceExtra extra, as the balance must hold it
	 */
	public record Cost(long per, long extra, long maxPer, long maxExtra, long balancePer, long balanceExtra) {
		public Cost {
			per = Math.max(1, per);
			extra = Math.max(0, extra);
			maxPer = Math.max(1, maxPer);
			maxExtra = Math.max(0, maxExtra);
			balancePer = Math.max(1, balancePer);
			balanceExtra = Math.max(0, balanceExtra);
		}

		/** The stake, the maximum and the balance all count {@code per × amount + extra}. */
		public Cost(long per, long extra) {
			this(per, extra, per, extra, per, extra);
		}

		/** The stake of a field amount (saturating). */
		public long of(long amount) {
			return times(amount, per, extra);
		}

		/** The largest field amount whose stake is at most {@code stake} (0 when even {@code extra} does not fit). */
		public long amountFor(long stake) {
			return fit(stake, per, extra);
		}

		/** The largest field amount the maximum {@code max} allows. */
		public long amountUnderMax(long max) {
			return fit(max, maxPer, maxExtra);
		}

		/** The largest field amount the balance {@code balance} allows. */
		public long amountForBalance(long balance) {
			return fit(balance, balancePer, balanceExtra);
		}

		public boolean identity() {
			return per == 1 && extra == 0 && maxPer == 1 && maxExtra == 0 && balancePer == 1 && balanceExtra == 0;
		}

		private static long times(long amount, long per, long extra) {
			if (amount <= 0) return extra;
			return amount > (Long.MAX_VALUE - extra) / per ? Long.MAX_VALUE : amount * per + extra;
		}

		private static long fit(long budget, long per, long extra) {
			return budget <= extra ? 0 : (budget - extra) / per;
		}
	}

	/** The amount is the stake. */
	public static final Cost ONE = new Cost(1, 0);

	/** The maximum the amount counts against now: the max, or the room under it (UNIT); {@code Long.MAX_VALUE} = none. */
	private static long capOf(BetView v, Mode mode) {
		return v.max() <= 0 ? Long.MAX_VALUE : mode == Mode.UNIT ? Math.max(0, v.roomUnderMax()) : v.max();
	}

	/** The largest amount allowed now: {@code min(max or room under it, balance)}; 0 = nothing. */
	public static long limit(BetView v, Mode mode) {
		return limit(v, mode, ONE);
	}

	/** The largest field amount whose {@link Cost} fits both the maximum and the balance. */
	public static long limit(BetView v, Mode mode, Cost cost) {
		return Math.max(0, Math.min(cost.amountUnderMax(capOf(v, mode)), cost.amountForBalance(Math.max(0, v.balance()))));
	}

	/** Whether the balance (not a maximum) is what {@link #limit} stops at. */
	public static boolean balanceBinds(BetView v, Mode mode) {
		return balanceBinds(v, mode, ONE);
	}

	/** {@link #balanceBinds(BetView, Mode)} for a field whose maximum and balance count different multiples. */
	public static boolean balanceBinds(BetView v, Mode mode, Cost cost) {
		return cost.amountForBalance(Math.max(0, v.balance())) < cost.amountUnderMax(capOf(v, mode));
	}

	/** Clamps {@code value} into {@code [min, limit]} (BET mode) or {@code [1, limit]} (UNIT), saying which bound did it. */
	public static Clamp clamp(long value, BetView v, Mode mode) {
		return clamp(value, v, mode, ONE);
	}

	/** {@link #clamp(long, BetView, Mode)} for a field whose stake is {@code cost.of(value)}. */
	public static Clamp clamp(long value, BetView v, Mode mode, Cost cost) {
		long lim = limit(v, mode, cost);
		long low = mode == Mode.BET ? v.min() : 1;
		if (value > lim) {
			Gate why = balanceBinds(v, mode, cost) ? BetGates.overBalance("", V2Keys.BET, v) : BetGates.aboveMax("", V2Keys.BET, v);
			return new Clamp(lim, why.reasonKey(), why.reasonArgs());
		}
		if (value < low) {
			if (low > lim) return new Clamp(lim, BetGates.overBalance("", V2Keys.BET, v).reasonKey(), new Object[] {Math.max(0, v.balance())});
			Gate why = BetGates.belowMin("", V2Keys.BET, v);
			return new Clamp(low, value <= 0 ? null : why.reasonKey(), why.reasonArgs());
		}
		return new Clamp(value, null, new Object[0]);
	}

	/** Max: the {@link #limit}, and the sentence of whose limit it is (the reason is always given, so Max says why). */
	public static Clamp max(BetView v, Mode mode) {
		return max(v, mode, ONE);
	}

	/** Max for a field whose stake is {@code cost.of(value)}: the largest amount whose whole stake fits. */
	public static Clamp max(BetView v, Mode mode, Cost cost) {
		long lim = limit(v, mode, cost);
		Gate why = balanceBinds(v, mode, cost) ? BetGates.overBalance("", V2Keys.BET, v) : BetGates.aboveMax("", V2Keys.BET, v);
		return new Clamp(lim, why.reasonKey(), why.reasonArgs());
	}

	/** ×2 (saturating), clamped. */
	public static Clamp doubled(long value, BetView v, Mode mode) {
		return doubled(value, v, mode, ONE);
	}

	public static Clamp doubled(long value, BetView v, Mode mode, Cost cost) {
		return clamp(value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : Math.max(1, value) * 2, v, mode, cost);
	}

	/** ½ (rounded down), clamped. */
	public static Clamp halved(long value, BetView v, Mode mode) {
		return halved(value, v, mode, ONE);
	}

	public static Clamp halved(long value, BetView v, Mode mode, Cost cost) {
		return clamp(Math.max(1, value / 2), v, mode, cost);
	}

	/** One mouse-wheel notch up ({@code up}) or down from {@code value}: by {@link #step}, clamped. */
	public static Clamp wheel(long value, boolean up, BetView v, Mode mode) {
		return wheel(value, up, v, mode, ONE);
	}

	public static Clamp wheel(long value, boolean up, BetView v, Mode mode, Cost cost) {
		long s = up ? step(value) : step(Math.max(1, value - 1));
		long next = up ? (value > Long.MAX_VALUE - s ? Long.MAX_VALUE : value + s) : value - s;
		return clamp(Math.max(up ? next : Math.min(next, value - 1), 0), v, mode, cost);
	}

	/**
	 * The wheel step at {@code value}: the largest chip that still fits four times into it (so 100 000 moves by 25 000,
	 * 1 000 by 100, 30 by 5), at least the smallest chip.
	 */
	public static long step(long value) {
		for (int d : ChipMath.DENOMINATIONS) {
			if ((long) d * 4 <= value) return d;
		}
		return ChipMath.DENOMINATIONS[ChipMath.DENOMINATIONS.length - 1];
	}

	/**
	 * The typed text after one more character: digits append (at most {@link #MAX_DIGITS}, no leading zero), {@code k}
	 * multiplies by 1 000 ("25k" = 25 000), {@code m} by 1 000 000; anything else is ignored. Returns the new text.
	 */
	public static String type(String text, char ch) {
		if (ch >= '0' && ch <= '9') {
			String t = text.equals("0") ? "" : text;
			return t.length() >= MAX_DIGITS ? text : t + ch;
		}
		char c = Character.toLowerCase(ch);
		if (text.isEmpty() || text.equals("0")) return text; // "0k" would print 0000 (T-HR D4)
		if ((c == 'k' || c == 'к') && text.length() + 3 <= MAX_DIGITS) return text + "000"; // literal-ok: ×1000
		if ((c == 'm' || c == 'м') && text.length() + 6 <= MAX_DIGITS) return text + "000000"; // literal-ok: ×1 000 000
		return text;
	}

	/** The typed text without its last character. */
	public static String backspace(String text) {
		return text.isEmpty() ? text : text.substring(0, text.length() - 1);
	}

	/** The value of typed text (0 for empty / unparsable). */
	public static long parse(String text) {
		if (text == null || text.isEmpty()) return 0;
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	/** The amount the control starts from: the current bet (BET) or the armed chip (UNIT), else the minimum, clamped. */
	public static long initial(BetView v, Mode mode, long armed) {
		return initial(v, mode, armed, ONE);
	}

	/**
	 * {@link #initial(BetView, Mode, long)} for a field whose stake is {@code cost.of(value)}: the field starts from the
	 * AMOUNT of the current stake ({@code (myTotal - extra) / per}; T-HR D1: UTH reopened with the cost, and Enter doubled it).
	 */
	public static long initial(BetView v, Mode mode, long armed, Cost cost) {
		long start = mode == Mode.BET ? cost.amountFor(v.myTotal()) : armed;
		if (start <= 0) start = Math.max(v.min(), armed);
		return clamp(start, v, mode, cost).value();
	}
}
