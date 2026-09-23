package dev.nezo.burmaldaholic.core.config;

/**
 * Core config section. Pattern for every module: public mutable fields with defaults, no-arg ctor.
 * Field names are also translation keys for the config screen:
 * {@code config.burmaldaholic.<module>.<field>}.
 */
public final class CoreConfig {
	/** Chips every player starts with when casino mode is on. J-core: final value from docs/design. */
	public long startingBalance = 1000;
	/** Max distance (blocks) between a player and a table they interact with. */
	public double maxTableDistance = 8.0;
}
