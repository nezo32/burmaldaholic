package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Role → material table of one style variant (§5 of the architecture doc). Every {@link Role} must be
 * mapped; {@link #check()} is called by the builder so a forgotten role fails at generation time, not
 * in the world.
 */
public record Palette(String id, Map<Role, Material> materials) {
	public Palette {
		materials = Collections.unmodifiableMap(new EnumMap<>(materials));
	}

	public Material get(Role role) {
		Material m = materials.get(role);
		if (m == null) {
			throw new IllegalStateException("palette " + id + " has no " + role);
		}
		return m;
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
