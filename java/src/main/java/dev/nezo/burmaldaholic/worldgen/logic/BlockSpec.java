package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** A block state by name: {@code minecraft:oak_planks}, {@code burmaldaholic:cashier[facing=west]}. */
public record BlockSpec(String name, Map<String, String> properties) {
	public BlockSpec {
		if (!name.contains(":")) {
			throw new IllegalArgumentException("block name needs a namespace: " + name);
		}
		properties = Collections.unmodifiableMap(new TreeMap<>(properties));
	}

	/** {@code minecraft:<path>} with optional {@code key, value, key, value...} properties. */
	public static BlockSpec mc(String path, String... props) {
		return of("minecraft:" + path, props);
	}

	public static BlockSpec of(String name, String... props) {
		if (props.length % 2 != 0) {
			throw new IllegalArgumentException("properties must be key/value pairs");
		}
		Map<String, String> map = new TreeMap<>();
		for (int i = 0; i < props.length; i += 2) {
			map.put(props[i], props[i + 1]);
		}
		return new BlockSpec(name, map);
	}

	public boolean is(String blockName) {
		return name.equals(blockName);
	}

	@Override
	public String toString() {
		if (properties.isEmpty()) {
			return name;
		}
		StringBuilder sb = new StringBuilder(name).append('[');
		properties.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
		sb.setLength(sb.length() - 1);
		return sb.append(']').toString();
	}
}
