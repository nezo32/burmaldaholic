package dev.nezo.burmaldaholic.core.pvp;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of PvP modes. The module owning a mode's solo game registers it in its {@code register}
 * (extras: coin, wheel, plinko, scratch; slots: slots). Common side, both physical sides.
 */
public final class PvpModes {
	private static final Map<String, PvpMode<?, ?>> MODES = new LinkedHashMap<>();

	private PvpModes() {}

	public static void register(PvpMode<?, ?> mode) {
		if (MODES.putIfAbsent(mode.id(), mode) != null) {
			throw new IllegalStateException("PvP mode '" + mode.id() + "' registered twice");
		}
	}

	public static Optional<PvpMode<?, ?>> get(String id) {
		return Optional.ofNullable(MODES.get(id));
	}

	public static Collection<PvpMode<?, ?>> all() {
		return Collections.unmodifiableCollection(MODES.values());
	}
}
