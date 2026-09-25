package dev.nezo.burmaldaholic.multiplayer.logic;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Casino claims (GAME_DESIGN.md §18.2). PURE (no Minecraft imports).
 *
 * <p>A claim is a full-height cylinder of radius {@code radius} around the Casino Charter block.
 * Claims never overlap (horizontal distance between charters ≥ r1 + r2) and never reach into the
 * spawn protection area; each player owns at most {@code ownership.maxPerPlayer} casinos.
 */
public final class Claims {
	/** Extra spawn buffer used when the server has no spawn-protection radius (singleplayer). */
	public static final int DEFAULT_SPAWN_BUFFER = 16;

	private Claims() {}

	/** The geometric part of a casino. */
	public interface Claim {
		String dimension();

		int x();

		int z();

		int radius();

		UUID owner();
	}

	public enum Check {
		OK,
		OVERLAP,
		LIMIT,
		DISABLED
	}

	/**
	 * @param spawnDimension dimension of the world spawn (null = no spawn rule)
	 * @param spawnBuffer    spawn protection radius kept free of claims
	 */
	public record Rules(boolean enabled, int radius, int maxPerPlayer, String spawnDimension, int spawnX, int spawnZ, int spawnBuffer) {}

	private static long dist2(long ax, long az, long bx, long bz) {
		long dx = ax - bx;
		long dz = az - bz;
		return dx * dx + dz * dz;
	}

	/** Whether block column (x, z) of {@code dimension} lies inside the claim (distance ≤ radius, any height). */
	public static boolean inClaim(Claim c, String dimension, int x, int z) {
		if (!c.dimension().equals(dimension)) {
			return false;
		}
		long r = c.radius();
		return dist2(x, z, c.x(), c.z()) <= r * r;
	}

	/** The claim containing the position (claims never overlap, so at most one). */
	public static <C extends Claim> Optional<C> claimAt(Collection<C> claims, String dimension, int x, int z) {
		for (C c : claims) {
			if (inClaim(c, dimension, x, z)) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	/** May {@code owner} put a charter at (x, z)? */
	public static Check check(Collection<? extends Claim> existing, UUID owner, String dimension, int x, int z, Rules rules) {
		if (!rules.enabled() || rules.maxPerPlayer() <= 0) {
			return Check.DISABLED;
		}
		long mine = existing.stream().filter(c -> c.owner().equals(owner)).count();
		if (mine >= rules.maxPerPlayer()) {
			return Check.LIMIT;
		}
		for (Claim c : existing) {
			if (!c.dimension().equals(dimension)) {
				continue;
			}
			long r = (long) c.radius() + rules.radius();
			if (dist2(x, z, c.x(), c.z()) < r * r) {
				return Check.OVERLAP;
			}
		}
		if (rules.spawnDimension() != null && rules.spawnDimension().equals(dimension)) {
			long r = (long) rules.radius() + Math.max(0, rules.spawnBuffer());
			if (dist2(x, z, rules.spawnX(), rules.spawnZ()) < r * r) {
				return Check.OVERLAP;
			}
		}
		return Check.OK;
	}

	/**
	 * Break protection of charters and linked tables: owners and operators may always break;
	 * everybody else only an unprotected table ({@code ownership.protectTables} off). The charter
	 * itself is always protected.
	 */
	public static boolean mayBreak(boolean isOwner, boolean isOperator, boolean protectTables, boolean isCharter) {
		if (isOwner || isOperator) {
			return true;
		}
		return !protectTables && !isCharter;
	}

	/** Next casino id ("c1", "c2", …) for a persisted counter. */
	public static String nextId(int counter) {
		return "c" + (counter + 1);
	}

	/** Bankroll account id of a casino (core {@code Economy.bankrolls()}). */
	public static String bankrollId(String casinoId) {
		return "multiplayer:charter/" + casinoId;
	}
}
