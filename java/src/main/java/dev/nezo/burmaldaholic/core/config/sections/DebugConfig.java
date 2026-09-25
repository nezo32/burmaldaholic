package dev.nezo.burmaldaholic.core.config.sections;


/** Config section `debug` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class DebugConfig {
	/** Log every settled round (for testers). */
	public boolean logRounds = false;
	/** Non-zero → deterministic RNG (tests only). */
	public long fixedSeed = 0L;
	/** Show computed RTP on machine screens (ops). */
	public boolean showOdds = false;
}
