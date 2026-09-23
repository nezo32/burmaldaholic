package dev.nezo.burmaldaholic.core.module;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Which module owns which names. Source: {@code java/config/namespaces.properties} (copied into the
 * jar as {@code burmaldaholic/namespaces.properties}); the Gradle checks use the same file. Pure Java.
 */
public final class Namespaces {
	public static final String RESOURCE = "/burmaldaholic/namespaces.properties";
	private static Namespaces instance;

	private final Map<String, List<String>> byModule;

	public Namespaces(Map<String, List<String>> byModule) {
		this.byModule = Map.copyOf(byModule);
	}

	public static synchronized Namespaces get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Namespaces load() {
		Properties props = new Properties();
		try (InputStream in = Namespaces.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing " + RESOURCE + " (built from java/config/namespaces.properties)");
			}
			props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Map<String, List<String>> map = new LinkedHashMap<>();
		for (String module : props.stringPropertyNames()) {
			List<String> extra = Arrays.stream(props.getProperty(module).split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
			map.put(module, extra);
		}
		return new Namespaces(map);
	}

	public boolean knows(String module) {
		return byModule.containsKey(module);
	}

	/** True if {@code name} (a path / key suffix / section) belongs to {@code module}. */
	public boolean owns(String module, String name) {
		List<String> extra = byModule.get(module);
		if (extra == null) {
			return false;
		}
		if (extra.contains("*")) {
			return true;
		}
		if (matches(module, name)) {
			return true;
		}
		for (String ns : extra) {
			if (matches(ns, name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String ns, String name) {
		return name.equals(ns) || name.startsWith(ns + "_") || name.startsWith(ns + ".") || name.startsWith(ns + "/");
	}
}
