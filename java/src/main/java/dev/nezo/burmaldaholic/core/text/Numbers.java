package dev.nezo.burmaldaholic.core.text;

/** Language-neutral number/time formatting (LOCALIZATION.md §4). Pure Java, shared rules with Bedrock. */
public final class Numbers {
	private Numbers() {}

	/**
	 * Integer with a regular space every 3 digits from 10 000 up ({@code 12 500}, {@code 1 000 000});
	 * below 10 000 no grouping ({@code 2500}). Negative numbers keep a leading {@code -}.
	 */
	public static String format(long n) {
		String sign = n < 0 ? "-" : "";
		String digits = n < 0 ? Long.toString(n).substring(1) : Long.toString(n);
		if (digits.length() <= 4) {
			return sign + digits;
		}
		StringBuilder sb = new StringBuilder();
		int lead = digits.length() % 3;
		if (lead > 0) {
			sb.append(digits, 0, lead);
		}
		for (int i = lead; i < digits.length(); i += 3) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(digits, i, i + 3);
		}
		return sign + sb;
	}

	/** {@code MM:SS} for a tick count (20 ticks = 1 s), rounded up to whole seconds. */
	public static String minutesSeconds(long ticks) {
		long seconds = (Math.max(0, ticks) + 19) / 20;
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}

	/** {@code HH:MM} part of the HUD loan timer: remaining world ticks as real-time hours:minutes within the day. */
	public static String hoursMinutes(long ticks) {
		long minutes = (Math.max(0, ticks) + 1199) / 1200;
		return String.format("%02d:%02d", minutes / 60, minutes % 60);
	}
}
