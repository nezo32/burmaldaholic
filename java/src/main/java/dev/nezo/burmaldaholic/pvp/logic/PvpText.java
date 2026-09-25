package dev.nezo.burmaldaholic.pvp.logic;

/** Small language-neutral formatting rules of the PvP UI. Pure. */
public final class PvpText {
	private PvpText() {}

	/**
	 * Basis points as a percent number without trailing zeros, '.' as the decimal separator (the caller
	 * localises it with {@code Texts.decimal}): 300 → "3", 250 → "2.5", 25 → "0.25".
	 */
	public static String percent(int basisPoints) {
		int bp = Math.max(0, basisPoints);
		int whole = bp / 100;
		int frac = bp % 100;
		if (frac == 0) {
			return Integer.toString(whole);
		}
		String f = frac < 10 ? "0" + frac : Integer.toString(frac);
		if (f.endsWith("0")) {
			f = f.substring(0, 1);
		}
		return whole + "." + f;
	}

	/** Whole seconds shown for a countdown of {@code ticks} (rounded up, never negative). */
	public static long seconds(long ticks) {
		return ticks <= 0 ? 0 : (ticks + 19) / 20;
	}

	/** Ticks left of a timer that started at {@code startTick} and lasts {@code duration}, at {@code now}. */
	public static long ticksLeft(long startTick, long duration, long now) {
		return Math.max(0, startTick + duration - now);
	}
}
