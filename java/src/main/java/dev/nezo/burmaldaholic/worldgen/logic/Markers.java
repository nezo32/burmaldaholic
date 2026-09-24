package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Optional;

/**
 * Data-marker metadata strings (structure blocks in DATA mode inside the templates). The template
 * shell is vanilla blocks + game tables; everything that needs code at placement time is a marker:
 * <pre>
 *   burmaldaholic:worldgen casino &lt;layoutId&gt;          registers the casino (bounds, kind)
 *   burmaldaholic:worldgen npc &lt;role&gt; &lt;facing&gt;         spawns an NPC (skipped if its entity type is absent)
 *   burmaldaholic:worldgen chest &lt;lootTable&gt; &lt;facing&gt;   places a chest filled from {@link CasinoLoot}
 * </pre>
 * Facings are template-local and get rotated with the piece.
 */
public final class Markers {
	public static final String PREFIX = "burmaldaholic:worldgen ";

	private Markers() {}

	public sealed interface Marker permits Anchor, Npc, Chest {
		String metadata();
	}

	public record Anchor(String layoutId) implements Marker {
		@Override
		public String metadata() {
			return PREFIX + "casino " + layoutId;
		}
	}

	public record Npc(NpcRole role, Facing facing) implements Marker {
		@Override
		public String metadata() {
			return PREFIX + "npc " + role.id() + " " + facing.id();
		}
	}

	public record Chest(CasinoLoot.Table table, Facing facing) implements Marker {
		@Override
		public String metadata() {
			return PREFIX + "chest " + table.id() + " " + facing.id();
		}
	}

	/** Parses a marker; empty for foreign / malformed metadata (never throws). */
	public static Optional<Marker> parse(String metadata) {
		if (metadata == null || !metadata.startsWith(PREFIX)) {
			return Optional.empty();
		}
		String[] parts = metadata.substring(PREFIX.length()).trim().split("\\s+");
		try {
			return switch (parts[0]) {
				case "casino" -> parts.length == 2 && Layouts.byId(parts[1]).isPresent()
					? Optional.of(new Anchor(parts[1])) : Optional.empty();
				case "npc" -> parts.length == 3
					? Optional.of(new Npc(NpcRole.byId(parts[1]), Facing.byId(parts[2]))) : Optional.empty();
				case "chest" -> parts.length == 3
					? Optional.of(new Chest(CasinoLoot.Table.byId(parts[1]), Facing.byId(parts[2]))) : Optional.empty();
				default -> Optional.empty();
			};
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
