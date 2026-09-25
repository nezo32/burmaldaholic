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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config storage and validation (docs/design/CONFIG.md "Storage and editing"). Pure Java (Gson):
 *
 * <ul>
 *   <li><b>Global file</b> {@code config/burmaldaholic.json}: one top-level object per section, nested
 *       objects follow the dotted keys ({@code economy.ore.diamond} -> {@code {"economy":{"ore":{"diamond":20}}}}).
 *       Rewritten complete (every key, current values) after each load so users see every option.</li>
 *   <li><b>Per-world override</b> {@code <world>/data/burmaldaholic_config.json}: same format, only the keys
 *       that differ; wins over the global file. {@code /casino config set} writes here.</li>
 *   <li>Validation per field ({@link ConfigBinder}): wrong type → default, out of range → clamped,
 *       unknown key → ignored; every problem is logged and returned as {@link ConfigIssue}.</li>
 * </ul>
 *
 * Values are read through {@link ConfigHandle#get()} (or {@link CasinoConfig}); on a remote client
 * the handles hold the values synced from the server.
 */
public final class ConfigManager {
	private static final Logger LOG = LoggerFactory.getLogger("burmaldaholic/config");
	private static ConfigManager instance = new ConfigManager(null);

	private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final ConfigBinder binder = new ConfigBinder(gson);
	private final Map<String, ConfigHandle<?>> handles = new java.util.LinkedHashMap<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final @Nullable Path file;
	private @Nullable Path worldFile;
	private JsonObject worldOverrides = new JsonObject();
	private List<ConfigIssue> lastIssues = List.of();

	public ConfigManager(@Nullable Path file) {
		this.file = file;
	}

	public static ConfigManager get() {
		return instance;
	}

	/** Called once by core with the real path; keeps sections registered before. */
	public static void init(Path file) {
		ConfigManager previous = instance;
		instance = new ConfigManager(file);
		previous.handles.values().forEach(h -> instance.handles.put(h.section(), h));
	}

	public Gson gson() {
		return gson;
	}

	public synchronized <T> ConfigHandle<T> register(String section, Class<T> type, Supplier<T> defaults) {
		if (handles.containsKey(section)) {
			throw new IllegalStateException("Config section '" + section + "' registered twice");
		}
		ConfigHandle<T> handle = new ConfigHandle<>(section, type, defaults);
		handles.put(section, handle);
		return handle;
	}

	public synchronized Collection<ConfigHandle<?>> handles() {
		return List.copyOf(handles.values());
	}

	public synchronized Optional<ConfigHandle<?>> handle(String section) {
		return Optional.ofNullable(handles.get(section));
	}

	/** Runs after every (re)load / change of effective values (server uses it to sync clients). */
	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	/** The per-world override file; {@code null} when no world is loaded. Call {@link #load()} after. */
	public synchronized void setWorldFile(@Nullable Path worldFile) {
		this.worldFile = worldFile;
		this.worldOverrides = worldFile == null ? new JsonObject() : read(worldFile);
	}

	public synchronized @Nullable Path worldFile() {
		return worldFile;
	}

	/** Problems found by the last load/apply. */
	public synchronized List<ConfigIssue> lastIssues() {
		return lastIssues;
	}

	/** (Re)loads global + per-world files, applies them and rewrites the normalised global file. */
	public synchronized List<ConfigIssue> load() {
		JsonObject global = file == null ? new JsonObject() : read(file);
		if (worldFile != null) {
			worldOverrides = read(worldFile);
		}
		List<ConfigIssue> globalIssues = new ArrayList<>();
		JsonObject normalisedGlobal = new JsonObject();
		for (ConfigHandle<?> h : handles.values()) {
			normalisedGlobal.add(h.section(), gson.toJsonTree(bindSection(h, global, globalIssues)));
		}
		for (String key : global.keySet()) {
			if (!handles.containsKey(key)) {
				globalIssues.add(new ConfigIssue(key, ConfigIssue.Kind.UNKNOWN, "", ""));
			}
		}
		List<ConfigIssue> issues = applyEffective(merge(global.deepCopy(), worldOverrides));
		if (file != null) {
			write(file, normalisedGlobal);
		}
		List<ConfigIssue> all = new ArrayList<>(globalIssues);
		issues.stream().filter(i -> !all.contains(i)).forEach(all::add);
		all.forEach(i -> LOG.warn("Config: {}", i));
		lastIssues = List.copyOf(all);
		return lastIssues;
	}

	/** Applies a JSON tree as the effective config (tests, and the global-only path). */
	public synchronized List<ConfigIssue> applyJson(JsonObject root) {
		List<ConfigIssue> issues = applyEffective(root);
		lastIssues = List.copyOf(issues);
		return lastIssues;
	}

	private List<ConfigIssue> applyEffective(JsonObject root) {
		List<ConfigIssue> issues = new ArrayList<>();
		for (ConfigHandle<?> h : handles.values()) {
			setUnchecked(h, bindSection(h, root, issues));
		}
		listeners.forEach(Runnable::run);
		return issues;
	}

	@SuppressWarnings("unchecked")
	private static <T> void setUnchecked(ConfigHandle<T> h, Object value) {
		h.set((T) value);
	}

	private <T> T bindSection(ConfigHandle<T> h, JsonObject root, List<ConfigIssue> issues) {
		T value = h.defaults();
		JsonElement json = root.get(h.section());
		if (json == null) {
			return value;
		}
		if (!json.isJsonObject()) {
			issues.add(new ConfigIssue(h.section(), ConfigIssue.Kind.INVALID, json.toString(), "{…}"));
			return value;
		}
		return binder.bind(value, json.getAsJsonObject(), h.section(), issues);
	}

	/** Effective values of every section (what the game uses). */
	public synchronized JsonObject toJson() {
		JsonObject root = new JsonObject();
		handles.values().forEach(h -> root.add(h.section(), gson.toJsonTree(h.get())));
		return root;
	}

	/** Default values of every section. */
	public synchronized JsonObject defaultsJson() {
		JsonObject root = new JsonObject();
		handles.values().forEach(h -> root.add(h.section(), gson.toJsonTree(h.defaults())));
		return root;
	}

	/** The global file's content as the game would normalise it (for the config screen). */
	public synchronized JsonObject globalJson() {
		JsonObject global = file == null ? new JsonObject() : read(file);
		JsonObject result = new JsonObject();
		List<ConfigIssue> ignored = new ArrayList<>();
		handles.values().forEach(h -> result.add(h.section(), gson.toJsonTree(bindSection(h, global, ignored))));
		return result;
	}

	/** Replaces the global file (config screen) and reloads. */
	public synchronized List<ConfigIssue> saveGlobal(JsonObject root) {
		if (file != null) {
			write(file, root);
		}
		return load();
	}

	/** All leaf keys ({@code economy.ore.diamond}), in file order. Arrays are leaves. */
	public synchronized List<String> keys() {
		List<String> keys = new ArrayList<>();
		collectKeys(toJson(), "", keys);
		return keys;
	}

	private static void collectKeys(JsonObject obj, String prefix, List<String> out) {
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
			if (e.getValue().isJsonObject()) {
				collectKeys(e.getValue().getAsJsonObject(), key, out);
			} else {
				out.add(key);
			}
		}
	}

	/** Effective value at a dotted key (leaf or object). */
	public synchronized Optional<JsonElement> value(String key) {
		return Optional.ofNullable(at(toJson(), key));
	}

	public synchronized Optional<JsonElement> defaultValue(String key) {
		return Optional.ofNullable(at(defaultsJson(), key));
	}

	/** Result of {@link #setWorldValue}. {@code issues} mention the key if it was clamped. */
	public record SetResult(boolean ok, List<ConfigIssue> issues, JsonElement effective) {}

	/**
	 * Sets one key in the per-world override (or the global file when no world is loaded) and
	 * re-applies. Unknown keys and invalid values are rejected and nothing is written.
	 */
	public synchronized SetResult setWorldValue(String key, String rawValue) {
		JsonElement current = at(toJson(), key);
		return setWorldValue(key, ConfigBinder.parseRaw(rawValue, current));
	}

	public synchronized SetResult setWorldValue(String key, JsonElement value) {
		JsonObject target = worldFile != null ? worldOverrides.deepCopy() : (file == null ? new JsonObject() : read(file));
		put(target, key, value);
		JsonObject global = file == null ? new JsonObject() : read(file);
		JsonObject candidate = worldFile != null ? merge(global, target) : target;
		List<ConfigIssue> issues = new ArrayList<>();
		for (ConfigHandle<?> h : handles.values()) {
			bindSection(h, candidate, issues);
		}
		List<ConfigIssue> mine = issues.stream().filter(i -> i.key().equals(key) || i.key().startsWith(key + ".") || i.key().startsWith(key + "[")).toList();
		boolean rejected = mine.stream().anyMatch(i -> i.kind() != ConfigIssue.Kind.CLAMPED) || !isKnownPrefix(key);
		if (rejected) {
			return new SetResult(false, mine.isEmpty() ? List.of(new ConfigIssue(key, ConfigIssue.Kind.UNKNOWN, value.toString(), "")) : mine, value);
		}
		if (worldFile != null) {
			worldOverrides = target;
			write(worldFile, worldOverrides);
			load();
		} else if (file != null) {
			write(file, target);
			load();
		} else {
			applyJson(candidate);
		}
		return new SetResult(true, mine, value(key).orElse(value));
	}

	/** Resets a key to its default (writes the default into the override so it wins over the global file). */
	public synchronized SetResult resetWorldValue(String key) {
		JsonElement def = at(defaultsJson(), key);
		if (def == null) {
			return new SetResult(false, List.of(new ConfigIssue(key, ConfigIssue.Kind.UNKNOWN, "", "")), new JsonObject());
		}
		return setWorldValue(key, def.deepCopy());
	}

	/** Client side: values received from the server (remote servers only). */
	public synchronized void applySynced(JsonObject effective) {
		applyEffective(effective);
	}

	private boolean isKnownPrefix(String key) {
		int dot = key.indexOf('.');
		return handles.containsKey(dot < 0 ? key : key.substring(0, dot));
	}

	// ---- JSON helpers -----------------------------------------------------------------------

	public static @Nullable JsonElement at(JsonObject root, String key) {
		JsonElement cur = root;
		for (String part : key.split("\\.")) {
			if (cur == null || !cur.isJsonObject()) {
				return null;
			}
			cur = cur.getAsJsonObject().get(part);
		}
		return cur;
	}

	public static void put(JsonObject root, String key, JsonElement value) {
		String[] parts = key.split("\\.");
		JsonObject cur = root;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement next = cur.get(parts[i]);
			if (next == null || !next.isJsonObject()) {
				next = new JsonObject();
				cur.add(parts[i], next);
			}
			cur = next.getAsJsonObject();
		}
		cur.add(parts[parts.length - 1], value);
	}

	/** Deep-merges {@code over} into {@code base} (objects merge, everything else replaces). */
	static JsonObject merge(JsonObject base, JsonObject over) {
		for (Map.Entry<String, JsonElement> e : over.entrySet()) {
			JsonElement b = base.get(e.getKey());
			if (b != null && b.isJsonObject() && e.getValue().isJsonObject()) {
				merge(b.getAsJsonObject(), e.getValue().getAsJsonObject());
			} else {
				base.add(e.getKey(), e.getValue().deepCopy());
			}
		}
		return base;
	}

	private JsonObject read(Path path) {
		if (!Files.isRegularFile(path)) {
			return new JsonObject();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
			LOG.error("{} must contain a JSON object, using defaults", path);
		} catch (IOException | RuntimeException e) {
			LOG.error("Could not read {}, using defaults", path, e);
		}
		return new JsonObject();
	}

	private void write(Path path, JsonObject root) {
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				gson.toJson(root, writer);
			}
		} catch (IOException e) {
			LOG.error("Could not write {}", path, e);
		}
	}
}
