package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Role → material table of one style variant (§5 of the architecture doc). Every base {@link Role} must be
 * mapped; {@link #check()} is called by the builder so a forgotten role fails at generation time, not
 * in the world. Optional architectural roles ({@link Role#fallback()} non-null) resolve to their fallback
 * when unmapped. {@link #derive} makes a per-biome swap of an existing palette (CR-1 palette roles).
 */
public record Palette(String id, Map<Role, Material> materials) {
	public Palette {
		materials = Collections.unmodifiableMap(new EnumMap<>(materials));
	}

	public Material get(Role role) {
		Material m = materials.get(role);
		Role r = role;
		while (m == null && r.fallback() != null) {
			r = r.fallback();
			m = materials.get(r);
		}
		if (m == null) {
			throw new IllegalStateException("palette " + id + " has no " + role);
		}
		return m;
	}

	/** True when the role is mapped explicitly (not through a fallback). */
	public boolean maps(Role role) {
		return materials.containsKey(role);
	}

	public BlockSpec block(Role role) {
		return get(role).block();
	}

	public Palette check() {
		for (Role r : Role.values()) {
			get(r);
		}
		return this;
	}

	public static Builder builder(String id) {
		return new Builder(id);
	}

	/** A new palette {@code id} starting from every mapping of this one (a biome swap overrides a few roles). */
	public Builder derive(String newId) {
		Builder b = new Builder(newId);
		b.map.putAll(materials);
		return b;
	}

	public static final class Builder {
		private final String id;
		private final Map<Role, Material> map = new EnumMap<>(Role.class);

		private Builder(String id) {
			this.id = id;
		}

		public Builder put(Role role, Material m) {
			map.put(role, m);
			return this;
		}

		public Builder put(Role role, String fullBlock) {
			return put(role, Material.of(fullBlock));
		}

		public Palette build() {
			return new Palette(id, map).check();
		}
	}
}
