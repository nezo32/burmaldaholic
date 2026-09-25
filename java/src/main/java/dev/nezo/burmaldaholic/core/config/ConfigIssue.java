package dev.nezo.burmaldaholic.core.config;

/**
 * A problem found while applying a config file or a {@code /casino config set}.
 *
 * @param key         full dotted key, e.g. {@code economy.ore.diamond}
 * @param kind        what happened
 * @param value       the rejected/raw value as text
 * @param replacement the value actually used, as text
 */
public record ConfigIssue(String key, Kind kind, String value, String replacement) {
	public enum Kind {
		/** Out of range: clamped to {@code replacement}. Lang: config.burmaldaholic.clamped */
		CLAMPED,
		/** Wrong type / unparsable: default used. Lang: config.burmaldaholic.invalid */
		INVALID,
		/** Key does not exist: ignored. Lang: config.burmaldaholic.unknown_key */
		UNKNOWN
	}

	@Override
	public String toString() {
		return switch (kind) {
			case CLAMPED -> key + ": " + value + " out of range, clamped to " + replacement;
			case INVALID -> key + ": invalid value " + value + ", using " + replacement;
			case UNKNOWN -> key + ": unknown key ignored";
		};
	}
}
