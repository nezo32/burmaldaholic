package dev.nezo.burmaldaholic.core.pvp.logic;

import java.util.Map;

/**
 * A mode event: {@code kind} is a stable id ({@code kaboom}, {@code swap}, {@code time_warp},
 * {@code underdog}, {@code edge}, {@code creeper}, {@code foot}, {@code by_a_hair}, …), {@code seat} the
 * participant index (-1 = none), {@code round} the round/step (0-based, -1 = match), {@code data} small
 * extra values (other seat, points, symbol id).
 */
public record PvpEvent(String kind, int seat, int round, Map<String, Long> data) {
	public PvpEvent {
		data = Map.copyOf(data);
	}

	public static PvpEvent of(String kind, int seat, int round) {
		return new PvpEvent(kind, seat, round, Map.of());
	}
}
