package dev.nezo.burmaldaholic.chaos.logic;

import java.util.Locale;

/**
 * Outcome of a chaos trigger request (see {@code ChaosApi#trigger}).
 * <ul>
 * <li>{@link #STARTED}: the event runs now.</li>
 * <li>{@link #DEFERRED}: a casino screen is open; it runs when it closes (max {@code chaos.deferMaxTicks}).</li>
 * <li>{@link #SKIPPED}: blocked by a §13.4 safety rule or no safe spot was found.</li>
 * <li>{@link #COOLDOWN}: per-player cooldown, or (golden_hour) Golden Hour active / on cooldown.</li>
 * <li>{@link #DISABLED}: casino mode off, {@code chaos.enabled} off or the event is disabled.</li>
 * </ul>
 */
public enum TriggerResult {
	STARTED, DEFERRED, SKIPPED, COOLDOWN, DISABLED;

	/** Lower-case name, used in lang keys and in the JDK-typed ObjectShare API. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
