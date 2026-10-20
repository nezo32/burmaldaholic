package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Locale;
import java.util.UUID;

/**
 * Which skin of its pool a casino NPC wears (PURE; DL-SPEC §3 "Look selection"). Deterministic per casino and stand
 * cell, so a respawned NPC comes back with the same look; spawn eggs (no casino) hash the entity UUID instead.
 */
public final class NpcLook {
	/** Human dealers ({@code dealer/human_0..5}). */
	public static final int HUMAN = 6;
	/** Piglin dealers and the nether cashier ({@code dealer/piglin_0..3}). */
	public static final int PIGLIN = 4;
	/** Ender dealers and croupiers ({@code dealer/ender_0..2}). */
	public static final int ENDER = 3;
	/** Villager-model croupiers and merchants ({@code croupier/villager_0..3}). */
	public static final int VILLAGER = 4;

	private NpcLook() {}

	/** The dimension's dealer species. */
	public enum Species {
		HUMAN, PIGLIN, ENDER;

		public static Species of(String dimension) {
			return switch (dimension) {
				case "minecraft:the_nether" -> PIGLIN;
				case "minecraft:the_end" -> ENDER;
				default -> HUMAN;
			};
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Size of the dealer look pool of this species. */
		public int dealerPool() {
			return switch (this) {
				case HUMAN -> NpcLook.HUMAN;
				case PIGLIN -> NpcLook.PIGLIN;
				case ENDER -> NpcLook.ENDER;
			};
		}
	}

	/** {@code floorMod(mix64(casinoId.hashCode()) ^ mix64(asLong(x, y, z)), size)}; 0 for an empty pool. */
	public static int pick(int size, String casinoId, int x, int y, int z) {
		if (size <= 1) {
			return 0;
		}
		return Math.floorMod(mix64(casinoId.hashCode()) ^ mix64(asLong(x, y, z)), size);
	}

	/** Look of an NPC without a casino (spawn egg, command). */
	public static int pick(int size, UUID uuid) {
		if (size <= 1) {
			return 0;
		}
		return Math.floorMod(mix64(uuid.getMostSignificantBits() ^ mix64(uuid.getLeastSignificantBits())), size);
	}

	/** Same packing as Minecraft's {@code BlockPos.asLong}. */
	public static long asLong(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
	}

	/** SplitMix64 finaliser (Stafford variant 13). */
	public static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
