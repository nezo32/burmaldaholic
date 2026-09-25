package dev.nezo.burmaldaholic.core.config;

import java.util.function.Supplier;

/** A module's live config section. Always call {@link #get()} (values change on reload). */
public final class ConfigHandle<T> {
	private final String section;
	private final Class<T> type;
	private final Supplier<T> defaults;
	private volatile T value;

	ConfigHandle(String section, Class<T> type, Supplier<T> defaults) {
		this.section = section;
		this.type = type;
		this.defaults = defaults;
		this.value = defaults.get();
	}

	public T get() {
		return value;
	}

	public String section() {
		return section;
	}

	Class<T> type() {
		return type;
	}

	T defaults() {
		return defaults.get();
	}

	void set(T value) {
		this.value = value;
	}
}
