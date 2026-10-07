package dev.nezo.burmaldaholic.worldgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.WorldgenRuntime;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * Structure type {@code burmaldaholic:casino} (architecture doc casino-buildings.md §6.1): a vanilla jigsaw
 * structure (every jigsaw field inlined, parsed by vanilla's own codec) plus
 * <ul>
 *   <li>the casino-mode gate ({@link WorldgenRuntime#active()}, like the annexes);</li>
 *   <li>a cheap biome pre-check before vanilla's jigsaw (which projects the start to the heightmap — one noise
 *       column — BEFORE its own biome check, for every style of the set in every cell);</li>
 *   <li>D5 "complete or absent": the assembly must contain a piece of every {@code required_pieces} pool;</li>
 *   <li>a terrain fit ({@link #fit}): a few noise-column probes on the footprint, trying a handful of horizontal
 *       offsets within the cell; the accepted footprint is moved there and vertically to the middle of its
 *       ground span.</li>
 * </ul>
 * The jigsaw assembles <b>once</b> (inside the vanilla stub's consumer, with the context's random); the built
 * pieces are re-wrapped as {@code Either.right} so {@code Structure.generate} never runs it a second time with
 * a different layout. Verdicts are cached per (world, chunk, structure): {@code /locate}, structure checks and the
 * real chunk generation never repeat the terrain work.
 */
public final class CasinoStructure extends Structure {
	public static final MapCodec<CasinoStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			JigsawStructure.CODEC.forGetter(s -> s.jigsaw),
			Codec.intRange(0, 64).optionalFieldOf("max_height_range", PackFiles.MAX_HEIGHT_RANGE).forGetter(s -> s.maxHeightRange),
			Codec.STRING.listOf().optionalFieldOf("required_pieces", List.of()).forGetter(s -> s.required))
		.apply(i, CasinoStructure::new));

	/** Water deeper than this at any probe = lake / river / ocean. */
	static final int MAX_WATER_DEPTH = 2;
	/** More shallow-water probes than this = marsh / lake shore. */
	static final int MAX_WET_PROBES = 2;
	/** Corner probes sit this far inside the footprint. */
	static final int CORNER_INSET = 2;
	/** Probes snap to this lattice, so the candidate offsets below share most of their columns. */
	static final int PROBE_GRID = 8;
	/** Horizontal offsets tried in order (the first that fits wins); multiples of {@link #PROBE_GRID}. */
	static final int[][] OFFSETS = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, -16}, {16, -16}, {-16, 16}};
	/** Diagnostic knobs (surveys compare configurations); production uses the defaults: the vanilla spot + 4 axis shifts (the diagonals add ~25 % of the extra fits for ~2× the probes). */
	public static volatile int candidates = 5;
	public static volatile boolean biomePrecheck = true;

	private final JigsawStructure jigsaw;
	private final int maxHeightRange;
	private final List<String> required;
	/** Rotated start-piece centres relative to the chunk's min corner (lazy; for the biome pre-check). */
	private volatile int[][] startCentres;

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
			if (biomePrecheck && !couldBeValidBiome(ctx)) {
				STATS.precheckRejected.incrementAndGet();
				return Optional.empty();
			}
			Key key = new Key(System.identityHashCode(ctx.randomState()), ctx.seed(), ctx.chunkPos().pack(), System.identityHashCode(this));
			Fit cached = VERDICTS.get(key);
			if (cached != null && cached.terrain() != Terrain.OK) {
				STATS.cacheHits.incrementAndGet();
				count(cached.terrain());
				return Optional.empty();
			}
			Optional<GenerationStub> stub = jigsaw.findValidGenerationPoint(ctx); // start piece projection + biome check
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
			Fit fit;
			if (cached != null) {
				fit = cached;
				STATS.cacheHits.incrementAndGet();
			} else {
				fit = fit(ctx, pieces);
				remember(key, fit);
			}
			STATS.terrainNanos.addAndGet(System.nanoTime() - t2);
			count(fit.terrain());
			if (fit.terrain() != Terrain.OK) {
				return Optional.empty();
			}
			if (fit.dx() != 0 || fit.dy() != 0 || fit.dz() != 0) {
				for (StructurePiece p : pieces) {
					p.move(fit.dx(), fit.dy(), fit.dz());
				}
			}
			return Optional.of(new GenerationStub(stub.get().position().offset(fit.dx(), fit.dy(), fit.dz()), Either.right(built)));
		} finally {
			long ns = System.nanoTime() - t0;
			STATS.recordAll(ns);
			if (evaluated) {
				STATS.record(ns);
			}
		}
	}

	private static void count(Terrain t) {
		(t == Terrain.OK ? STATS.accepted : t == Terrain.STEEP ? STATS.steep : STATS.flooded).incrementAndGet();
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

	// ---- biome pre-check ----------------------------------------------------------------------------------

	/** Surface heights the pre-check samples the biome at (vanilla checks one quart at the projected start). */
	private static final int[] PRECHECK_Y = {64, 76, 92, 112, 140};

	/**
	 * Could vanilla's biome check (at the start piece's centre, any rotation) pass here? Samples the noise biome at
	 * the four possible start centres over a few surface heights — climate noise only, no terrain. A false "no" is
	 * only possible where the surface biome differs from every sampled height (high peaks), which the terrain fit
	 * would reject anyway; surveys compare both ways.
	 */
	private boolean couldBeValidBiome(GenerationContext ctx) {
		if (!BiomeLookup.AVAILABLE) {
			return true;
		}
		int[][] centres = startCentres(ctx);
		int x0 = ctx.chunkPos().getMinBlockX();
		int z0 = ctx.chunkPos().getMinBlockZ();
		for (int y : PRECHECK_Y) {
			for (int[] c : centres) {
				Holder<Biome> b = BiomeLookup.at(ctx, QuartPos.fromBlock(x0 + c[0]), QuartPos.fromBlock(y), QuartPos.fromBlock(z0 + c[1]));
				if (b == null || ctx.validBiome().test(b)) {
					return true;
				}
			}
		}
		return false;
	}

	private int[][] startCentres(GenerationContext ctx) {
		int[][] c = startCentres;
		if (c == null) {
			int sx = 1;
			int sz = 1;
			for (var e : jigsaw.getStartPool().value().getTemplates()) {
				StructurePoolElement element = e.getFirst();
				var size = element.getSize(ctx.structureTemplateManager(), Rotation.NONE);
				sx = Math.max(sx, size.getX());
				sz = Math.max(sz, size.getZ());
			}
			int hx = (sx - 1) / 2;
			int hz = (sz - 1) / 2;
			// template origin at the chunk's min corner, clockwise quarter turns: (x, z) -> (-z, x)
			c = new int[][] {{hx, hz}, {-hz, hx}, {-hx, -hz}, {hz, -hx}};
			startCentres = c;
		}
		return c;
	}

	/**
	 * Noise-biome lookup that links on 26.2 ({@code BiomeSource.getNoiseBiome(x, y, z, randomState.sampler())}) and
	 * 26.3 ({@code context.biomeResolver().getNoiseBiome(x, y, z)}); resolved once by reflection, the release jar is
	 * compiled against 26.2 only.
	 */
	static final class BiomeLookup {
		private static final MethodHandle SAMPLER; // RandomState -> Climate.Sampler (26.2)
		private static final MethodHandle NOISE_BIOME_26_2; // BiomeSource, x, y, z, Sampler -> Holder
		private static final MethodHandle RESOLVER; // GenerationContext -> BiomeResolver (26.3)
		private static final MethodHandle NOISE_BIOME_26_3; // BiomeResolver, x, y, z -> Holder
		static final boolean AVAILABLE;

		static {
			MethodHandles.Lookup l = MethodHandles.publicLookup();
			MethodHandle sampler = null;
			MethodHandle nb2 = null;
			MethodHandle resolver = null;
			MethodHandle nb3 = null;
			try {
				sampler = l.findVirtual(RandomState.class, "sampler", MethodType.methodType(Climate.Sampler.class));
				nb2 = l.findVirtual(BiomeSource.class, "getNoiseBiome",
					MethodType.methodType(Holder.class, int.class, int.class, int.class, Climate.Sampler.class));
			} catch (ReflectiveOperationException e) {
				sampler = null;
				nb2 = null;
			}
			if (sampler == null) {
				try {
					resolver = l.findVirtual(GenerationContext.class, "biomeResolver", MethodType.methodType(BiomeResolver.class));
					nb3 = l.findVirtual(BiomeResolver.class, "getNoiseBiome", MethodType.methodType(Holder.class, int.class, int.class, int.class));
				} catch (ReflectiveOperationException e) {
					resolver = null;
					nb3 = null;
				}
			}
			SAMPLER = sampler;
			NOISE_BIOME_26_2 = nb2;
			RESOLVER = resolver;
			NOISE_BIOME_26_3 = nb3;
			AVAILABLE = (sampler != null && nb2 != null) || (resolver != null && nb3 != null);
		}

		private BiomeLookup() {}

		/** The noise biome, or null when it cannot be looked up (callers then assume "valid"). */
		@SuppressWarnings("unchecked")
		static Holder<Biome> at(GenerationContext ctx, int qx, int qy, int qz) {
			try {
				if (SAMPLER != null) {
					Object s = SAMPLER.invoke(ctx.randomState());
					return (Holder<Biome>) NOISE_BIOME_26_2.invoke(ctx.biomeSource(), qx, qy, qz, s);
				}
				Object r = RESOLVER.invoke(ctx);
				return (Holder<Biome>) NOISE_BIOME_26_3.invoke(r, qx, qy, qz);
			} catch (Throwable t) {
				return null;
			}
		}
	}

	// ---- terrain fit ----------------------------------------------------------------------------------------

	enum Terrain { OK, STEEP, FLOODED }

	/** Verdict + the move that fits the footprint: horizontal candidate offset, vertical mid-span shift. */
	record Fit(Terrain terrain, int dx, int dy, int dz) {}

	private record Key(int randomState, long seed, long chunk, int structure) {}

	private static final Map<Key, Fit> VERDICTS = new ConcurrentHashMap<>();
	private static final int MAX_VERDICTS = 4096;

	private static void remember(Key key, Fit fit) {
		if (VERDICTS.size() >= MAX_VERDICTS) {
			VERDICTS.clear();
		}
		VERDICTS.put(key, fit);
	}

	/** Forget cached verdicts (surveys switching configurations). */
	public static void clearCaches() {
		VERDICTS.clear();
	}

	/** Probe columns of a footprint shifted by {@code dx, dz}: the four corners (inset) first, then every piece centre. */
	static List<int[]> probes(List<StructurePiece> pieces, int dx, int dz) {
		List<int[]> out = new ArrayList<>();
		BoundingBox union = pieces.getFirst().getBoundingBox();
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			union = new BoundingBox(Math.min(union.minX(), b.minX()), Math.min(union.minY(), b.minY()), Math.min(union.minZ(), b.minZ()),
				Math.max(union.maxX(), b.maxX()), Math.max(union.maxY(), b.maxY()), Math.max(union.maxZ(), b.maxZ()));
		}
		int i = CORNER_INSET;
		out.add(new int[] {union.minX() + i + dx, union.minZ() + i + dz});
		out.add(new int[] {union.maxX() - i + dx, union.maxZ() - i + dz});
		out.add(new int[] {union.maxX() - i + dx, union.minZ() + i + dz});
		out.add(new int[] {union.minX() + i + dx, union.maxZ() - i + dz});
		for (StructurePiece p : pieces) {
			BlockPos c = p.getBoundingBox().getCenter();
			out.add(new int[] {c.getX() + dx, c.getZ() + dz});
		}
		for (int[] s : out) {
			s[0] = Math.round(s[0] / (float) PROBE_GRID) * PROBE_GRID;
			s[1] = Math.round(s[1] / (float) PROBE_GRID) * PROBE_GRID;
		}
		return out;
	}

	/**
	 * Fits the footprint to the terrain (noise columns only, no chunk is generated): for each candidate offset, water
	 * deeper than {@link #MAX_WATER_DEPTH} at any probe or shallow water at more than {@link #MAX_WET_PROBES} rejects
	 * it, so does dry ground spanning more than {@code max_height_range}; the first fitting candidate wins and the
	 * floor moves to the middle of its span (at most half the range is filled under the low side by the foundation +
	 * {@code beard_thin}, or cut on the high side). Columns are shared between candidates (probe lattice).
	 */
	Fit fit(GenerationContext ctx, List<StructurePiece> pieces) {
		// vanilla projected the start's ground level (min y + ground delta 1) to the start centre's surface + start_height
		int ref = pieces.getFirst().getBoundingBox().minY() + 1 - PackFiles.START_HEIGHT;
		Map<Long, int[]> columns = new HashMap<>();
		Fit worst = null;
		int n = Math.max(1, Math.min(candidates, OFFSETS.length));
		for (int k = 0; k < n; k++) {
			int dx = OFFSETS[k][0];
			int dz = OFFSETS[k][1];
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			int wet = 0;
			Terrain verdict = Terrain.OK;
			for (int[] s : probes(pieces, dx, dz)) {
				int[] h = columns.computeIfAbsent(BlockPos.asLong(s[0], 0, s[1]), key -> {
					STATS.sampleColumns.incrementAndGet();
					return heights(ctx, s[0], s[1]);
				});
				int depth = h[0] - h[1];
				if (depth > 0) {
					wet++;
					if (depth > MAX_WATER_DEPTH || wet > MAX_WET_PROBES) {
						verdict = Terrain.FLOODED;
						break;
					}
				} else {
					lo = Math.min(lo, h[0]);
					hi = Math.max(hi, h[0]);
					if (hi - lo > maxHeightRange) {
						verdict = Terrain.STEEP;
						break;
					}
				}
			}
			if (verdict == Terrain.OK && hi == Integer.MIN_VALUE) {
				verdict = Terrain.FLOODED;
			}
			if (verdict == Terrain.OK) {
				STATS.candidateUsed.incrementAndGet(Math.min(k, STATS.candidateUsed.length() - 1));
				return new Fit(Terrain.OK, dx, Math.floorDiv(lo + hi, 2) - ref, dz);
			}
			if (worst == null) {
				worst = new Fit(verdict, 0, 0, 0); // report the base spot's reason
			}
		}
		return worst;
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
		public final AtomicLong precheckRejected = new AtomicLong();
		public final AtomicLong cacheHits = new AtomicLong();
		public final AtomicLong noStart = new AtomicLong();
		public final AtomicLong incomplete = new AtomicLong();
		public final AtomicLong steep = new AtomicLong();
		public final AtomicLong flooded = new AtomicLong();
		public final AtomicLong accepted = new AtomicLong();
		public final AtomicLong stubNanos = new AtomicLong();
		public final AtomicLong assembleNanos = new AtomicLong();
		public final AtomicLong terrainNanos = new AtomicLong();
		public final AtomicLong sampleColumns = new AtomicLong();
		public final AtomicLong allNanos = new AtomicLong();
		/** Index of the candidate offset that fitted (0 = the vanilla spot). */
		public final java.util.concurrent.atomic.AtomicLongArray candidateUsed = new java.util.concurrent.atomic.AtomicLongArray(OFFSETS.length);
		private final long[] nanos = new long[16384];
		private int count;
		private final long[] allSamples = new long[65536];
		private int allCount;

		synchronized void record(long ns) {
			nanos[count % nanos.length] = ns;
			count++;
		}

		synchronized void recordAll(long ns) {
			allNanos.addAndGet(ns);
			allSamples[allCount % allSamples.length] = ns;
			allCount++;
		}

		public synchronized void reset() {
			for (AtomicLong a : List.of(attempts, gated, precheckRejected, cacheHits, noStart, incomplete, steep, flooded, accepted, stubNanos,
					assembleNanos, terrainNanos, sampleColumns, allNanos)) {
				a.set(0);
			}
			for (int i = 0; i < candidateUsed.length(); i++) {
				candidateUsed.set(i, 0);
			}
			count = 0;
			allCount = 0;
		}

		/** Durations (ns) of the attempts that assembled a casino (passed the biome checks), sorted. */
		public synchronized long[] sortedNanos() {
			long[] out = java.util.Arrays.copyOf(nanos, Math.min(count, nanos.length));
			java.util.Arrays.sort(out);
			return out;
		}

		/** Durations (ns) of every non-gated attempt (one per structure of the set tried in a cell), sorted. */
		public synchronized long[] sortedAllNanos() {
			long[] out = java.util.Arrays.copyOf(allSamples, Math.min(allCount, allSamples.length));
			java.util.Arrays.sort(out);
			return out;
		}

		@Override
		public String toString() {
			long evaluated = Math.max(1, accepted.get() + steep.get() + flooded.get() + incomplete.get());
			StringBuilder used = new StringBuilder();
			for (int i = 0; i < candidateUsed.length(); i++) {
				used.append(i == 0 ? "" : "/").append(candidateUsed.get(i));
			}
			return "attempts=" + attempts + " gated=" + gated + " precheckRejected=" + precheckRejected + " noStart(biome/jigsaw)=" + noStart
				+ " cacheHits=" + cacheHits + " incomplete=" + incomplete + " steep=" + steep + " flooded=" + flooded + " accepted=" + accepted
				+ " (by candidate " + used + ")"
				+ " | avg ms/evaluated: stub " + String.format(java.util.Locale.ROOT, "%.2f", stubNanos.get() / 1e6 / evaluated)
				+ " assemble " + String.format(java.util.Locale.ROOT, "%.2f", assembleNanos.get() / 1e6 / evaluated)
				+ " terrain " + String.format(java.util.Locale.ROOT, "%.2f", terrainNanos.get() / 1e6 / evaluated)
				+ " (" + sampleColumns.get() / evaluated + " columns)";
		}
	}
}
