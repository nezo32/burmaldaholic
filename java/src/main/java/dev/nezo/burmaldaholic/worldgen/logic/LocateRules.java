package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code /casino locate} decisions (PURE): which gate a predicted host must pass, how far and in which rings the
 * structure grid is searched, the nearest candidate, the 8-point compass direction and the per-player cooldown.
 *
 * <p>Gate parity with generation (must stay in sync with {@code VillageCasinos} / {@code CasinoStructures}):
 * <ul>
 *   <li>village: {@code Gates.pass(chance, 0, startPiece.x, startPiece.z, SALT_VILLAGE)} where the start piece of a
 *       vanilla village sits at the MIN corner of the structure's start chunk (JigsawStructure.findGenerationPoint
 *       uses {@code chunkPos.getMinBlockX/Z()}; villages have no {@code start_jigsaw_name} and the heightmap
 *       projection only moves the piece in Y);</li>
 *   <li>bastion / End City: {@code Gates.pass(chance, levelSeed, startChunk.x, startChunk.z, SALT_*)};</li>
 *   <li>v0.2 standalone casino ({@link CasinoKind#CASINO}, structure set {@code burmaldaholic:casinos}): no hash gate
 *       — every cell of its own grid may hold one (biome, terrain fit and the village exclusion zone decide), so the
 *       pre-filter always passes. The v0.2 annex / Parlor v2 / Lounge v2 reuse the v0.1 gates above unchanged.</li>
 * </ul>
 * A passed gate is only a pre-filter (the room may not fit); the locator always confirms the casino piece in the
 * real structure start.
 */
public final class LocateRules {
	/** Default search caps in chunks per casino kind (villages are dense, End Cities sparse). */
	public static final int CAP_VILLAGE_CHUNKS = 64;
	public static final int CAP_PARLOR_CHUNKS = 48;
	public static final int CAP_LOUNGE_CHUNKS = 96;
	/** Standalone casinos: one per ~830 blocks (~52 chunks) on a 28-chunk grid. */
	public static final int CAP_CASINO_CHUNKS = 96;

	/** Compass ids, clockwise from north ({@code gui.burmaldaholic.worldgen.dir.<id>}). */
	public static final List<String> DIRECTIONS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");

	private LocateRules() {}

	/** Search cap in chunks for casinos of this kind. */
	public static int capChunks(CasinoKind kind) {
		return switch (kind) {
			case VILLAGE_CASINO -> CAP_VILLAGE_CHUNKS;
			case PIGLIN_PARLOR -> CAP_PARLOR_CHUNKS;
			case HIGH_ROLLER -> CAP_LOUNGE_CHUNKS;
			case CASINO -> CAP_CASINO_CHUNKS;
		};
	}

	/**
	 * Whether a host structure starting in chunk ({@code chunkX}, {@code chunkZ}) passed the casino gate of
	 * {@code kind} at generation time (see the class comment for the exact inputs).
	 */
	public static boolean gate(CasinoKind kind, double chance, long levelSeed, int chunkX, int chunkZ) {
		return switch (kind) {
			case VILLAGE_CASINO -> Gates.pass(chance, 0L, chunkX << 4, chunkZ << 4, Gates.SALT_VILLAGE);
			case PIGLIN_PARLOR -> Gates.pass(chance, levelSeed, chunkX, chunkZ, Gates.SALT_PARLOR);
			case HIGH_ROLLER -> Gates.pass(chance, levelSeed, chunkX, chunkZ, Gates.SALT_LOUNGE);
			case CASINO -> chance > 0;
		};
	}

	/** Grid cells (dx, dz) of ring {@code r} around the centre cell: ring 0 is the centre, ring r has 8r cells. */
	public static List<int[]> ringCells(int r) {
		List<int[]> out = new ArrayList<>(Math.max(1, 8 * r));
		if (r == 0) {
			out.add(new int[] {0, 0});
			return out;
		}
		for (int dx = -r; dx <= r; dx++) {
			out.add(new int[] {dx, -r});
			out.add(new int[] {dx, r});
		}
		for (int dz = -r + 1; dz <= r - 1; dz++) {
			out.add(new int[] {-r, dz});
			out.add(new int[] {r, dz});
		}
		return out;
	}

	/** Rings needed to cover {@code radiusChunks} on a grid of {@code spacing} chunks (one extra for the cell offset). */
	public static int maxRing(int radiusChunks, int spacing) {
		if (radiusChunks <= 0 || spacing <= 0) {
			return 0;
		}
		return (radiusChunks + spacing - 1) / spacing + 1;
	}

	/**
	 * How far (chunks) a casino may lie from its host's start chunk: jigsaw villages reach 80 blocks from their
	 * centre, appended rooms stay within 7 chunks ({@code CasinoStructures.MAX_CHUNK_REACH}), a standalone casino
	 * within {@code max_distance_from_center} (48) plus its terrain-fit shift (16) of the start.
	 */
	public static final int HOST_REACH_CHUNKS = 8;

	/**
	 * Whether ring {@code r} can still hold a casino closer than {@code bestChunks}: every start chunk of a ring-r cell
	 * is more than {@code (r - 1) * spacing} chunks (Chebyshev, hence also Euclidean) away from the searcher's chunk,
	 * and the casino lies at most {@link #HOST_REACH_CHUNKS} from that start chunk.
	 */
	public static boolean ringMayBeat(int r, int spacing, double bestChunks) {
		return r <= 1 || (double) (r - 1) * spacing - HOST_REACH_CHUNKS <= bestChunks;
	}

	/** Whether a host starting {@code startChunks} away may hold a casino within {@code radiusChunks}. */
	public static boolean withinRadius(double startChunks, int radiusChunks) {
		return startChunks <= radiusChunks + HOST_REACH_CHUNKS;
	}

	/** Horizontal distance in blocks. */
	public static double distance(double fromX, double fromZ, double toX, double toZ) {
		return Math.hypot(toX - fromX, toZ - fromZ);
	}

	/** A located casino (block coordinates of its standing point). */
	public record Candidate(CasinoKind kind, int x, int y, int z, boolean predicted) {}

	/** The candidate closest (horizontally) to ({@code fromX}, {@code fromZ}); ties keep the earlier one. */
	public static Optional<Candidate> nearest(Collection<Candidate> candidates, double fromX, double fromZ) {
		Candidate best = null;
		double bestDist = Double.MAX_VALUE;
		for (Candidate c : candidates) {
			double d = distance(fromX, fromZ, c.x() + 0.5, c.z() + 0.5);
			if (d < bestDist) {
				best = c;
				bestDist = d;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * 8-point compass direction from the searcher to the target ({@code dx} = east, {@code dz} = south, Minecraft
	 * axes). Zero offset counts as north.
	 */
	public static String direction(double dx, double dz) {
		if (dx == 0 && dz == 0) {
			return DIRECTIONS.getFirst();
		}
		double degrees = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
		int index = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
		return DIRECTIONS.get(index);
	}

	/**
	 * Seconds the player still has to wait (0 = may search now). Times are MONOTONIC nanoseconds
	 * ({@link System#nanoTime()}), so wall-clock jumps neither extend nor skip the wait; {@code lastNanos}
	 * {@code null} = never searched.
	 */
	public static long cooldownLeftSeconds(Long lastNanos, long nowNanos, int cooldownSeconds) {
		if (lastNanos == null || cooldownSeconds <= 0) {
			return 0;
		}
		long leftNanos = cooldownSeconds * 1_000_000_000L - (nowNanos - lastNanos);
		return leftNanos <= 0 ? 0 : (leftNanos + 999_999_999L) / 1_000_000_000L;
	}

	/** Minimum gap between two structure-grid searches server-wide (they block the server thread). */
	public static final long GRID_SEARCH_GAP_NANOS = 1_000_000_000L;

	/** Whether a new structure-grid search may start ({@code lastNanos} {@code null} = none yet; monotonic time). */
	public static boolean gridSearchAllowed(Long lastNanos, long nowNanos) {
		return lastNanos == null || nowNanos - lastNanos >= GRID_SEARCH_GAP_NANOS;
	}
}
