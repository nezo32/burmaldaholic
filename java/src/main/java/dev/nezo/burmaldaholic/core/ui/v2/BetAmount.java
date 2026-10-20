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

	/** The largest amount allowed now: {@code min(max or room under it, balance)}; 0 = nothing. */
	public static long limit(BetView v, Mode mode) {
		long cap = v.max() <= 0 ? Long.MAX_VALUE : mode == Mode.UNIT ? v.roomUnderMax() : v.max();
		return Math.max(0, Math.min(cap, v.balance()));
	}

	/** Whether the balance (not a maximum) is what {@link #limit} stops at. */
	public static boolean balanceBinds(BetView v, Mode mode) {
		long cap = v.max() <= 0 ? Long.MAX_VALUE : mode == Mode.UNIT ? v.roomUnderMax() : v.max();
		return v.balance() < cap;
	}

	/** Clamps {@code value} into {@code [min, limit]} (BET mode) or {@code [1, limit]} (UNIT), saying which bound did it. */
	public static Clamp clamp(long value, BetView v, Mode mode) {
		long lim = limit(v, mode);
		long low = mode == Mode.BET ? v.min() : 1;
		if (value > lim) {
			Gate why = balanceBinds(v, mode) ? BetGates.overBalance("", V2Keys.BET, v) : BetGates.aboveMax("", V2Keys.BET, v);
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
		long lim = limit(v, mode);
		Gate why = balanceBinds(v, mode) ? BetGates.overBalance("", V2Keys.BET, v) : BetGates.aboveMax("", V2Keys.BET, v);
		return new Clamp(lim, why.reasonKey(), why.reasonArgs());
	}

	/** ×2 (saturating), clamped. */
	public static Clamp doubled(long value, BetView v, Mode mode) {
		return clamp(value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : Math.max(1, value) * 2, v, mode);
	}

	/** ½ (rounded down), clamped. */
	public static Clamp halved(long value, BetView v, Mode mode) {
		return clamp(Math.max(1, value / 2), v, mode);
	}

	/** One mouse-wheel notch up ({@code up}) or down from {@code value}: by {@link #step}, clamped. */
	public static Clamp wheel(long value, boolean up, BetView v, Mode mode) {
		long s = up ? step(value) : step(Math.max(1, value - 1));
		long next = up ? (value > Long.MAX_VALUE - s ? Long.MAX_VALUE : value + s) : value - s;
		return clamp(Math.max(up ? next : Math.min(next, value - 1), 0), v, mode);
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
		if ((c == 'k' || c == 'к') && !text.isEmpty() && text.length() + 3 <= MAX_DIGITS) return text + "000"; // literal-ok: ×1000
		if ((c == 'm' || c == 'м') && !text.isEmpty() && text.length() + 6 <= MAX_DIGITS) return text + "000000"; // literal-ok: ×1 000 000
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
		long start = mode == Mode.BET ? v.myTotal() : armed;
		if (start <= 0) start = Math.max(v.min(), armed);
		return clamp(start, v, mode).value();
	}
}
