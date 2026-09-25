package dev.nezo.burmaldaholic.core.config;

import java.util.LinkedHashMap;

/** Ordered map literals for config defaults (file and screen keep this order). */
public final class Maps {
	private Maps() {}

	/** {@code Maps.of("a", 1, "b", 2)} → insertion-ordered mutable map. */
	@SuppressWarnings("unchecked")
	public static <V> LinkedHashMap<String, V> of(Object... keyValues) {
		if (keyValues.length % 2 != 0) {
			throw new IllegalArgumentException("key/value pairs expected");
		}
		LinkedHashMap<String, V> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String) keyValues[i], (V) keyValues[i + 1]);
		}
		return map;
	}
}
