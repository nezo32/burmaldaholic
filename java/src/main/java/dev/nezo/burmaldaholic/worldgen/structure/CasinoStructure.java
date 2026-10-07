package dev.nezo.burmaldaholic.worldgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.WorldgenRuntime;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * Structure type {@code burmaldaholic:casino} (architecture doc casino-buildings.md §6.1): a vanilla jigsaw
 * structure (every jigsaw field inlined, parsed by vanilla's own codec) plus
 * <ul>
 *   <li>the casino-mode gate ({@link WorldgenRuntime#active()}, like the annexes);</li>
 *   <li>D5 "complete or absent": the assembly must contain a piece of every {@code required_pieces} pool;</li>
 *   <li>a flatness / water check on the assembled footprint: the WORLD_SURFACE_WG heights at the footprint
 *       corners and every piece centre span at most {@code max_height_range}, and no sample is under water.</li>
 * </ul>
 * The jigsaw assembles <b>once</b> (inside the vanilla stub's consumer, with the context's random); the built
 * pieces are re-wrapped as {@code Either.right} so {@code Structure.generate} never runs it a second time with
 * a different layout.
 */
public final class CasinoStructure extends Structure {
	public static final MapCodec<CasinoStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			JigsawStructure.CODEC.forGetter(s -> s.jigsaw),
			Codec.intRange(0, 64).optionalFieldOf("max_height_range", PackFiles.MAX_HEIGHT_RANGE).forGetter(s -> s.maxHeightRange),
			Codec.STRING.listOf().optionalFieldOf("required_pieces", List.of()).forGetter(s -> s.required))
		.apply(i, CasinoStructure::new));

	private final JigsawStructure jigsaw;
	private final int maxHeightRange;
	private final List<String> required;

	public CasinoStructure(JigsawStructure jigsaw, int maxHeightRange, List<String> required) {
		super(new Structure.StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
		this.jigsaw = jigsaw;
		this.maxHeightRange = maxHeightRange;
		this.required = List.copyOf(required);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		STATS.attempts.incrementAndGet();
		if (!WorldgenRuntime.active()) {
			STATS.gated.incrementAndGet();
			return Optional.empty();
		}
		long t0 = System.nanoTime();
		boolean evaluated = false;
		try {
			Optional<GenerationStub> stub = jigsaw.findValidGenerationPoint(ctx); // cheap: start piece + biome check
			if (stub.isEmpty()) {
				STATS.noStart.incrementAndGet();
				return stub;
			}
			evaluated = true;
			long t1 = System.nanoTime();
			StructurePiecesBuilder built = stub.get().getPiecesBuilder(); // the jigsaw assembles here, ONCE
			List<StructurePiece> pieces = built.build().pieces();
			long t2 = System.nanoTime();
			STATS.stubNanos.addAndGet(t1 - t0);
			STATS.assembleNanos.addAndGet(t2 - t1);
			if (!hasRequired(pieces)) {
				STATS.incomplete.incrementAndGet();
				return Optional.empty();
			}
			Fit fit = terrain(ctx, pieces);
			STATS.terrainNanos.addAndGet(System.nanoTime() - t2);
			if (fit.terrain() == Terrain.FLOODED) {
				STATS.flooded.incrementAndGet();
				return Optional.empty();
			}
			if (fit.terrain() == Terrain.STEEP) {
				STATS.steep.incrementAndGet();
				return Optional.empty();
			}
			if (fit.dy() != 0) {
				built.offsetPiecesVertically(fit.dy());
			}
			STATS.accepted.incrementAndGet();
			return Optional.of(new GenerationStub(stub.get().position(), Either.right(built)));
		} finally {
			if (evaluated) {
				STATS.record(System.nanoTime() - t0);
			}
		}
	}

	/** D5: every required pool contributed a piece. */
	boolean hasRequired(List<StructurePiece> pieces) {
		if (required.isEmpty()) {
			return true;
		}
		Set<String> pools = new TreeSet<>();
		for (StructurePiece piece : pieces) {
			if (piece instanceof PoolElementStructurePiece p) {
				Optional<Layout> l = CasinoElements.layoutOf(p.getElement());
				l.ifPresent(layout -> pools.add(PackFiles.poolName(PackFiles.pieceName(layout.id()))));
			}
		}
		return pools.containsAll(required);
	}

	enum Terrain { OK, STEEP, FLOODED }

	/** Grid step of the (survey-only) dense terrain samples inside every piece box. */
	static final int SAMPLE_STEP = 10;
	/** Water deeper than this at any probe = lake / river / ocean. */
	static final int MAX_WATER_DEPTH = 2;
	/** More shallow-water probes than this = marsh / lake shore. */
	static final int MAX_WET_PROBES = 2;
	/** Corner probes sit this far inside the footprint. */
	static final int CORNER_INSET = 2;

	/**
	 * The probes the structure can afford (each is one noise column, ~1–4 ms): every piece centre (start first)
	 * and the four corners of the footprint (union box, inset {@link #CORNER_INSET}).
	 */
	static List<int[]> probes(List<StructurePiece> pieces) {
		List<int[]> out = new ArrayList<>();
		BoundingBox union = pieces.getFirst().getBoundingBox();
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			out.add(new int[] {b.getCenter().getX(), b.getCenter().getZ()});
			union = new BoundingBox(Math.min(union.minX(), b.minX()), Math.min(union.minY(), b.minY()), Math.min(union.minZ(), b.minZ()),
				Math.max(union.maxX(), b.maxX()), Math.max(union.maxY(), b.maxY()), Math.max(union.maxZ(), b.maxZ()));
		}
		int i = CORNER_INSET;
		out.add(new int[] {union.minX() + i, union.minZ() + i});
		out.add(new int[] {union.maxX() - i, union.minZ() + i});
		out.add(new int[] {union.minX() + i, union.maxZ() - i});
		out.add(new int[] {union.maxX() - i, union.maxZ() - i});
		return out;
	}

	/** Dense samples (survey validation of {@link #probes}): a {@link #SAMPLE_STEP} grid over every piece box, start centre first. */
	static List<int[]> samples(List<StructurePiece> pieces) {
		List<int[]> out = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		BlockPos c = pieces.getFirst().getBoundingBox().getCenter();
		out.add(new int[] {c.getX(), c.getZ()});
		seen.add(BlockPos.asLong(c.getX(), 0, c.getZ()));
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			for (int x : axis(b.minX() + 1, b.maxX() - 1)) {
				for (int z : axis(b.minZ() + 1, b.maxZ() - 1)) {
					if (seen.add(BlockPos.asLong(x, 0, z))) {
						out.add(new int[] {x, z});
					}
				}
			}
		}
		return out;
	}

	private static List<Integer> axis(int lo, int hi) {
		List<Integer> v = new ArrayList<>();
		if (hi <= lo) {
			v.add((lo + hi) / 2);
			return v;
		}
		int n = Math.max(1, (int) Math.ceil((hi - lo) / (double) SAMPLE_STEP));
		for (int i = 0; i <= n; i++) {
			v.add(lo + (int) Math.round((hi - lo) * (i / (double) n)));
		}
		return v;
	}

	/** Terrain verdict + the vertical shift that puts the floor at the middle of the measured ground. */
	record Fit(Terrain terrain, int dy) {}

	/**
	 * Terrain under the assembled footprint from the noise columns of the {@link #probes} (no chunk is generated):
	 * water deeper than {@link #MAX_WATER_DEPTH} at any probe, or shallow water at more than {@link #MAX_WET_PROBES},
	 * rejects the spot; otherwise the dry ground heights may span at most {@code max_height_range}, and the whole
	 * assembly moves vertically so its floor sits at the middle of that span (vanilla projected it to the start
	 * centre): at most half the range is then filled under the low side (2-row foundation + {@code beard_thin}) or
	 * cut on the high side.
	 */
	private Fit terrain(GenerationContext ctx, List<StructurePiece> pieces) {
		List<int[]> probes = probes(pieces);
		int ref = Integer.MIN_VALUE;
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		int wet = 0;
		boolean deep = false;
		int used = 0;
		for (int[] s : probes) {
			int[] h = heights(ctx, s[0], s[1]);
			used++;
			if (ref == Integer.MIN_VALUE) {
				ref = h[0];
			}
			int depth = h[0] - h[1];
			if (depth > 0) {
				wet++;
				deep |= depth > MAX_WATER_DEPTH;
			} else {
				lo = Math.min(lo, h[0]);
				hi = Math.max(hi, h[0]);
			}
			if (!STATS.detailed && (deep || wet > MAX_WET_PROBES || (hi != Integer.MIN_VALUE && hi - lo > maxHeightRange))) {
				break; // early exit outside surveys
			}
		}
		boolean flooded = deep || wet > MAX_WET_PROBES || hi == Integer.MIN_VALUE;
		int range = flooded ? 0 : hi - lo;
		if (STATS.detailed) {
			STATS.observeDense(denseRange(ctx, pieces));
		}
		STATS.observe(range, flooded, used);
		if (flooded) {
			return new Fit(Terrain.FLOODED, 0);
		}
		if (range > maxHeightRange) {
			return new Fit(Terrain.STEEP, 0);
		}
		return new Fit(Terrain.OK, Math.floorDiv(lo + hi, 2) - ref);
	}

	/** Survey only: dry-ground range over the dense grid (validates that {@link #probes} see the slope). */
	private static int denseRange(GenerationContext ctx, List<StructurePiece> pieces) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int[] s : samples(pieces)) {
			int[] h = heights(ctx, s[0], s[1]);
			if (h[0] == h[1]) {
				lo = Math.min(lo, h[0]);
				hi = Math.max(hi, h[0]);
			}
		}
		return hi == Integer.MIN_VALUE ? 0 : hi - lo;
	}

	/** {surface, floor}: first free y above any non-air block, and above any motion-blocking block (WG heightmaps). */
	private static int[] heights(GenerationContext ctx, int x, int z) {
		NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
		int min = ctx.heightAccessor().getMinY();
		int surface = min;
		int floor = min;
		for (int y = ctx.heightAccessor().getMaxY(); y >= min; y--) {
			BlockState s = column.getBlock(y);
			if (surface == min && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(s)) {
				surface = y + 1;
			}
			if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(s)) {
				floor = y + 1;
				break;
			}
		}
		return new int[] {Math.max(surface, floor), floor};
	}

	@Override
	public StructureType<?> type() {
		return CasinoStructureTypes.CASINO;
	}

	public int maxHeightRange() {
		return maxHeightRange;
	}

	public List<String> requiredPieces() {
		return required;
	}

	// ---- diagnostics (read by WorldgenSpawnSurvey) --------------------------------------------------------

	public static final Stats STATS = new Stats();

	/** Outcome counters + generation timings of {@link #findGenerationPoint} since the last {@link #reset}. */
	public static final class Stats {
		public final AtomicLong attempts = new AtomicLong();
		public final AtomicLong gated = new AtomicLong();
		public final AtomicLong noStart = new AtomicLong();
		public final AtomicLong incomplete = new AtomicLong();
		public final AtomicLong steep = new AtomicLong();
		public final AtomicLong flooded = new AtomicLong();
		public final AtomicLong accepted = new AtomicLong();
		public final AtomicLong stubNanos = new AtomicLong();
		public final AtomicLong assembleNanos = new AtomicLong();
		public final AtomicLong terrainNanos = new AtomicLong();
		public final AtomicLong sampleColumns = new AtomicLong();
		/** Surveys set this: measure every sample (no early exit) so the deviation histogram is complete. */
		public volatile boolean detailed;
		/** Dry ground range seen by the probes (0..31, 31 = more), of the non-flooded evaluated spots. */
		public final java.util.concurrent.atomic.AtomicLongArray deviation = new java.util.concurrent.atomic.AtomicLongArray(32);
		/** Same over the dense grid (surveys only), to validate the probes. */
		public final java.util.concurrent.atomic.AtomicLongArray dense = new java.util.concurrent.atomic.AtomicLongArray(32);
		private final long[] nanos = new long[8192];
		private int count;

		synchronized void record(long ns) {
			nanos[count % nanos.length] = ns;
			count++;
		}

		void observe(int maxDev, boolean flooded, int samples) {
			sampleColumns.addAndGet(samples);
			if (!flooded) {
				deviation.incrementAndGet(Math.min(31, maxDev));
			}
		}


		synchronized void observeDense(int range) {
			dense.incrementAndGet(Math.min(31, range));
		}

		public synchronized void reset() {
			for (int i = 0; i < dense.length(); i++) {
				dense.set(i, 0);
			}
			for (AtomicLong a : List.of(attempts, gated, noStart, incomplete, steep, flooded, accepted, stubNanos, assembleNanos, terrainNanos,
					sampleColumns)) {
				a.set(0);
			}
			for (int i = 0; i < deviation.length(); i++) {
				deviation.set(i, 0);
			}
			count = 0;
		}

		/** Share of non-flooded evaluated spots whose probe range is ≤ {@code range}. */
		public double passShare(int range) {
			return share(deviation, range);
		}

		private static double share(java.util.concurrent.atomic.AtomicLongArray h, int range) {
			long ok = 0;
			long all = 0;
			for (int i = 0; i < h.length(); i++) {
				all += h.get(i);
				ok += i <= range ? h.get(i) : 0;
			}
			return all == 0 ? 0 : ok / (double) all;
		}

		/** Recorded durations (ns) of the evaluated (non-gated) attempts, sorted. */
		public synchronized long[] sortedNanos() {
			long[] out = java.util.Arrays.copyOf(nanos, Math.min(count, nanos.length));
			java.util.Arrays.sort(out);
			return out;
		}

		@Override
		public String toString() {
			long evaluated = Math.max(1, accepted.get() + steep.get() + flooded.get() + incomplete.get());
			StringBuilder pass = new StringBuilder();
			for (int r : new int[] {6, 8, 10, 12, 14, 16}) {
				pass.append(" <=").append(r).append(": probes ").append(Math.round(passShare(r) * 100)).append("% dense ")
					.append(Math.round(share(dense, r) * 100)).append('%');
			}
			return "attempts=" + attempts + " gated=" + gated + " noStart(biome/jigsaw)=" + noStart + " incomplete=" + incomplete
				+ " steep=" + steep + " flooded=" + flooded + " accepted=" + accepted
				+ " | avg ms/evaluated: stub " + String.format(java.util.Locale.ROOT, "%.2f", stubNanos.get() / 1e6 / evaluated)
				+ " assemble " + String.format(java.util.Locale.ROOT, "%.2f", assembleNanos.get() / 1e6 / evaluated)
				+ " terrain " + String.format(java.util.Locale.ROOT, "%.2f", terrainNanos.get() / 1e6 / evaluated)
				+ " (" + sampleColumns.get() / evaluated + " columns)"
				+ " | dry ground range pass" + pass;
		}
	}
}
