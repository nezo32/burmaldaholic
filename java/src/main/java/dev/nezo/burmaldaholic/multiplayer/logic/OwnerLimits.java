package dev.nezo.burmaldaholic.multiplayer.logic;

/**
 * Owner min/max bet settings of an owned table (§18.2, UI.md §11). PURE.
 * 0 means "no owner limit" (the game's own limits apply). The owner can only tighten the game's limits;
 * the max may not exceed the global table max (highest VIP tier max) and the tier max still applies per player.
 */
public final class OwnerLimits {
	private OwnerLimits() {}

	public enum Error {
		INVALID,
		MIN_OVER_MAX,
		OVER_GLOBAL
	}

	/** {@code error == null} = ok. */
	public record Parsed(long min, long max, Error error) {
		public boolean ok() {
			return error == null;
		}

		static Parsed fail(Error e) {
			return new Parsed(0, 0, e);
		}
	}

	/** Validates numeric input (0 or negative = unset). */
	public static Parsed validate(long min, long max, long globalMax) {
		if (min < 0 || max < 0) {
			return Parsed.fail(Error.INVALID);
		}
		if (globalMax > 0 && (max > globalMax || min > globalMax)) {
			return Parsed.fail(Error.OVER_GLOBAL);
		}
		if (min > 0 && max > 0 && min > max) {
			return Parsed.fail(Error.MIN_OVER_MAX);
		}
		return new Parsed(min, max, null);
	}

	/** Parses a text field: empty = 0 (unset); digits with optional spaces / thin spaces as separators. */
	public static long parseField(String text) {
		if (text == null) {
			return 0;
		}
		String v = text.replaceAll("[\\s\\u00A0\\u202F_]", "");
		if (v.isEmpty()) {
			return 0;
		}
		if (!v.chars().allMatch(Character::isDigit) || v.length() > 15) {
			return -1;
		}
		return Long.parseLong(v);
	}

	public static Parsed parse(String minText, String maxText, long globalMax) {
		return validate(parseField(minText), parseField(maxText), globalMax);
	}

	/** Effective minimum bet of an owned table for the insolvency rule. */
	public static long effectiveMin(long gameMin, long ownerMin) {
		return Math.max(1, Math.max(gameMin, ownerMin));
	}
}
