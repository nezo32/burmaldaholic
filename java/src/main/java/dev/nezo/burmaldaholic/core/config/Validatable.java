package dev.nezo.burmaldaholic.core.config;

/**
 * Optional cross-field validation of a config object (e.g. "sell rate must be ≥ buy rate").
 * Called after all fields were bound and range-clamped. Fix the fields in place and report
 * each change through {@code issues}.
 */
public interface Validatable {
	void validate(Issues issues);

	/** Sink for issues; keys are relative to the object being validated. */
	interface Issues {
		void clamped(String relativeKey, Object oldValue, Object newValue);

		void invalid(String relativeKey, Object oldValue, Object newValue);
	}
}
