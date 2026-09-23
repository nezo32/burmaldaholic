package dev.nezo.burmaldaholic.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single JSON file {@code config/burmaldaholic.json}; one top-level object per section (dotted keys
 * of docs/design/CONFIG.md become nested objects: {@code economy.ore.diamond} ->
 * {@code {"economy":{"ore":{"diamond":20}}}}). Missing fields fall back to defaults, unknown keys are
 * logged and dropped; the file is rewritten with the complete set of fields after every load so users
 * always see every option. Pure Java (Gson only) — unit-testable.
 *
 * <p>J-core TODO (CONFIG.md): range clamping, per-world override file
 * {@code <world>/data/burmaldaholic_config.json}, {@code /casino config}, client sync.
 *
 * <p>Server-authoritative: values are read on the logical server. If a client needs a value,
 * the owning module syncs it via a payload.
 */
public final class ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger("burmaldaholic/config");
	private static ConfigManager instance = new ConfigManager(null);

	private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
	private final Map<String, ConfigHandle<?>> handles = new LinkedHashMap<>();
	private final Path file;

	public ConfigManager(Path file) {
		this.file = file;
	}

	public static ConfigManager get() {
		return instance;
	}

	/** Called once by core with the real path. */
	public static void init(Path file) {
		ConfigManager previous = instance;
		instance = new ConfigManager(file);
		previous.handles.values().forEach(h -> instance.handles.put(h.section(), h));
	}

	public synchronized <T> ConfigHandle<T> register(String section, Class<T> type, Supplier<T> defaults) {
		if (handles.containsKey(section)) {
			throw new IllegalStateException("Config section '" + section + "' registered twice");
		}
		ConfigHandle<T> handle = new ConfigHandle<>(section, type, defaults);
		handles.put(section, handle);
		return handle;
	}

	/** (Re)loads every registered section from disk and writes the normalised file back. */
	public synchronized void load() {
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed.isJsonObject()) {
					root = parsed.getAsJsonObject();
				}
			} catch (IOException | RuntimeException e) {
				LOG.error("Could not read {}, using defaults", file, e);
			}
		}
		applyJson(root);
		save();
	}

	/** Applies a JSON tree to all handles (visible for tests). */
	public synchronized void applyJson(JsonObject root) {
		for (ConfigHandle<?> handle : handles.values()) {
			apply(handle, root.get(handle.section()));
		}
	}

	private <T> void apply(ConfigHandle<T> handle, JsonElement json) {
		JsonObject merged = gson.toJsonTree(handle.defaults()).getAsJsonObject();
		if (json != null && json.isJsonObject()) {
			deepMerge(merged, json.getAsJsonObject(), handle.section());
		}
		try {
			handle.set(gson.fromJson(merged, handle.type()));
		} catch (RuntimeException e) {
			LOG.error("Invalid config section '{}', using defaults", handle.section(), e);
			handle.set(handle.defaults());
		}
	}

	/** Copies known keys from {@code user} onto {@code defaults}; unknown keys are logged and dropped. */
	private static void deepMerge(JsonObject defaults, JsonObject user, String path) {
		for (var e : user.entrySet()) {
			String key = path + "." + e.getKey();
			JsonElement def = defaults.get(e.getKey());
			if (def == null) {
				LOG.warn("Unknown config key '{}' ignored", key);
			} else if (def.isJsonObject() && e.getValue().isJsonObject()) {
				deepMerge(def.getAsJsonObject(), e.getValue().getAsJsonObject(), key);
			} else {
				defaults.add(e.getKey(), e.getValue());
			}
		}
	}

	public synchronized JsonObject toJson() {
		JsonObject root = new JsonObject();
		handles.values().forEach(h -> root.add(h.section(), gson.toJsonTree(h.get())));
		return root;
	}

	public synchronized void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				gson.toJson(toJson(), writer);
			}
		} catch (IOException e) {
			LOG.error("Could not write {}", file, e);
		}
	}
}
