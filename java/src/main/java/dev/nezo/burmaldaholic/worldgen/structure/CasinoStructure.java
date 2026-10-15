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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
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
 * structure (every jigsaw field inlined, parsed by vanilla's own codec) plus, in this order (cheapest first):
 * <ol>
 *   <li>the gate: {@link WorldgenRuntime#standaloneActive()} — casino mode, {@code worldgen.enabled},
 *       {@code worldgen.standalone.enabled}; a volatile snapshot, never the server's saved data (R-CB M5);</li>
 *   <li>a biome pre-check on climate noise only (the four possible start centres × a few heights);</li>
 *   <li><b>prefit</b> (R-CB M6b): the start piece's rotation and element are the first two draws of the context's
 *       random ({@code JigsawPlacement.addPieces}), so they are predicted from {@code (seed, chunk)} and the start
 *       piece's footprint + door throat are fitted to the terrain BEFORE the jigsaw runs. (A rotation-independent
 *       prefit is useless: the four rotations of a template about the chunk corner only share that corner.)</li>
 *   <li>the jigsaw, once (inside the stub's consumer, with the context's random); the built pieces are re-wrapped
 *       as {@code Either.right} so {@code Structure.generate} never runs it again with another layout;</li>
 *   <li>D5 "complete or absent": a piece of every {@code required_pieces} pool;</li>
 *   <li>the exact fit on the real piece boxes ({@link FitSite}: corners, door throat, outline ≤ 4 blocks, piece
 *       centres), trying a few horizontal shifts; the accepted footprint is moved there and vertically to the
 *       middle of its ground span;</li>
 *   <li>vanilla's biome check at the final start position (also re-run by {@code findValidGenerationPoint}).</li>
 * </ol>
 * Heights come from {@link TerrainProbe} (density samples, ~0.1 ms a column instead of a 2–4 ms noise column),
 * cached per placement cell and shared by every style of the set tried there. The pack writes no
 * {@code project_start_to_heightmap}, so the jigsaw itself samples no column; the fit sets the height.
 * Rejections are cached per (structure, world noise, chunk) in a weak map ({@code /locate} and structure checks
 * repeat the same cells), cleared when the server stops.
 */
public final class CasinoStructure extends Structure {
	public static final MapCodec<CasinoStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			JigsawStructure.CODEC.forGetter(s -> s.jigsaw),
			Codec.intRange(0, 64).optionalFieldOf("max_height_range", PackFiles.MAX_HEIGHT_RANGE).forGetter(s -> s.maxHeightRange),
			Codec.STRING.listOf().optionalFieldOf("required_pieces", List.of()).forGetter(s -> s.required))
		.apply(i, CasinoStructure::new));

	/** Water deeper than this at a probe counts as deep (lake, river, ocean). */
	static final int MAX_WATER_DEPTH = 2;
	/** Wet probes allowed: this many, or one in {@link #WET_SHARE} of the probes (lake shore, river bank). */
	static final int MAX_WET_PROBES = 2;
	static final int WET_SHARE = 8;
	/** Probes with water deeper than {@link #MAX_WATER_DEPTH} allowed: one in this many. */
	static final int DEEP_SHARE = 16;
	/** A water bed may lie this much deeper than the height range below the highest dry ground. */
	static final int BED_SLACK = 4;
	/** The FLAT test ignores one in this many probes at each end of the height span. */
	static final int TRIM_SHARE = 24;
	/** Ground above the floor (feet level at the door) the carve piece may cut out of the rooms. */
	static final int CUT_MAX = 8;
	/** Outline spacing of the coarse pass that screens each candidate shift. */
	static final int COARSE_SPACING = 16;
	/** Horizontal shifts of the whole casino tried in order (the first that fits wins); multiples of {@link FitSite#SPACING}. */
	static final int[][] OFFSETS = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, -16}, {16, -16}, {-16, 16},
		{32, 0}, {-32, 0}, {0, 32}, {0, -32}};
	/** Diagnostic knobs (surveys compare configurations); production uses the defaults. */
	public static volatile int candidates = OFFSETS.length;
	public static volatile boolean biomePrecheck = true;
	public static volatile boolean prefit = false;
	/** Outline spacing of the exact pass (contract: ≤ 4); surveys compare coarser ones. */
	public static volatile int fineSpacing = FitSite.SPACING;
	/** Overrides {@code max_height_range} when ≥ 0 (surveys). */
	public static volatile int heightRangeOverride = Integer.getInteger("burmaldaholic.casinoHeightRange", -1);

	/** Why a cell has no casino (survey reasons, R-CB M13). */
	public enum Reason { OK, FLAT, FLOOD, INCOMPLETE, BIOME }

	private final JigsawStructure jigsaw;
	private final int maxHeightRange;
	private final List<String> required;
	/** Rotated start-piece centres relative to the chunk's min corner (lazy; for the biome pre-check). */
	private volatile int[][] startCentres;
	/** Cached rejections: world noise → chunk → reason. Weak keys: a stopped world's noise is collected. */
	private final Map<RandomState, Map<Long, Reason>> verdicts = Collections.synchronizedMap(new WeakHashMap<>());

	private static final Set<CasinoStructure> INSTANCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	public CasinoStructure(JigsawStructure jigsaw, int maxHeightRange, List<String> required) {
		super(new Structure.StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
		this.jigsaw = jigsaw;
		this.maxHeightRange = maxHeightRange;
		this.required = List.copyOf(required);
		INSTANCES.add(this);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		boolean stats = STATS.enabled;
		if (stats) {
			STATS.attempts.incrementAndGet();
		}
		if (!WorldgenRuntime.standaloneActive()) {
			if (stats) {
				STATS.gated.incrementAndGet();
			}
			return Optional.empty();
		}
		long t0 = stats ? System.nanoTime() : 0;
		boolean evaluated = false;
		try {
			if (biomePrecheck && !couldBeValidBiome(ctx)) {
				if (stats) {
					STATS.precheckRejected.incrementAndGet();
				}
				return Optional.empty();
			}
			long chunk = ctx.chunkPos().pack();
			Map<Long, Reason> cache = verdicts.computeIfAbsent(ctx.randomState(), k -> new ConcurrentHashMap<>());
			Reason cached = cache.get(chunk);
			if (cached != null) {
				if (stats) {
					STATS.cacheHits.incrementAndGet();
					STATS.count(cached, null);
				}
				return Optional.empty();
			}
			evaluated = true;
			Cell cell = Cell.of(ctx);
			int range = heightRangeOverride >= 0 ? heightRangeOverride : maxHeightRange;
			// 1. prefit: the predicted start piece + door throat
			if (prefit) {
				Fit pre = prefit(ctx, cell, range);
				if (pre != null && pre.reason() != Reason.OK) {
					remember(cache, chunk, pre.reason());
					if (stats) {
						STATS.prefitRejected.incrementAndGet();
						STATS.count(pre.reason(), biomeName(ctx, cell));
					}
					return Optional.empty();
				}
			}
			// 2. the jigsaw, once
			long t1 = stats ? System.nanoTime() : 0;
			Optional<GenerationStub> stub = jigsaw.findGenerationPoint(ctx);
			if (stub.isEmpty()) {
				if (stats) {
					STATS.noStart.incrementAndGet();
				}
				return stub;
			}
			StructurePiecesBuilder built = stub.get().getPiecesBuilder(); // the jigsaw assembles here, ONCE
			List<StructurePiece> pieces = built.build().pieces();
			long t2 = stats ? System.nanoTime() : 0;
			if (stats) {
				STATS.stubNanos.addAndGet(t1 - t0);
				STATS.assembleNanos.addAndGet(t2 - t1);
			}
			if (!hasRequired(pieces)) {
				remember(cache, chunk, Reason.INCOMPLETE);
				if (stats) {
					STATS.count(Reason.INCOMPLETE, biomeName(ctx, cell));
				}
				return Optional.empty();
			}
			// 3. exact fit on the piece boxes
			Fit fit = fit(ctx, cell, pieces, range);
			if (stats) {
				STATS.terrainNanos.addAndGet(System.nanoTime() - t2);
			}
			if (fit.reason() != Reason.OK) {
				remember(cache, chunk, fit.reason());
				if (stats) {
					STATS.count(fit.reason(), biomeName(ctx, cell));
				}
				return Optional.empty();
			}
			if (fit.dx() != 0 || fit.dy() != 0 || fit.dz() != 0) {
				for (StructurePiece p : pieces) {
					p.move(fit.dx(), fit.dy(), fit.dz());
				}
			}
			BlockPos at = stub.get().position().offset(fit.dx(), fit.dy(), fit.dz());
			StructurePiecesBuilder out = withGround(pieces);
			// 4. vanilla's biome check at the final start (findValidGenerationPoint repeats it)
			Holder<Biome> biome = BiomeLookup.at(ctx, QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
			if (biome != null && !ctx.validBiome().test(biome)) {
				remember(cache, chunk, Reason.BIOME);
				if (stats) {
					STATS.count(Reason.BIOME, name(biome));
				}
				return Optional.empty();
			}
			if (stats) {
				STATS.count(Reason.OK, biome == null ? "?" : name(biome));
				STATS.candidateUsed.incrementAndGet(Math.min(fit.candidate(), STATS.candidateUsed.length() - 1));
			}
			return Optional.of(new GenerationStub(at, Either.right(out)));
		} finally {
			if (stats) {
				long ns = System.nanoTime() - t0;
				STATS.recordAll(ns);
				if (evaluated) {
					STATS.record(ns);
				}
			}
		}
	}

	/**
	 * The start's final piece list: {@link CasinoGroundPiece} CARVE first (clears the rooms' volume above the floor
	 * before any template is placed), the template pieces, then the SKIRT (foundation continued down to the ground).
	 */
	static StructurePiecesBuilder withGround(List<StructurePiece> pieces) {
		List<FitSite.Box> boxes = new ArrayList<>(pieces.size());
		int top = Integer.MIN_VALUE;
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			boxes.add(new FitSite.Box(b.minX(), b.minZ(), b.maxX(), b.maxZ()));
			top = Math.max(top, b.maxY());
		}
		// floor block row = template y 3 of the start piece (every piece shares it)
		int floorY = pieces.getFirst().getBoundingBox().minY() + CasinoGroundPiece.FOUNDATION_ROWS;
		StructurePiecesBuilder out = new StructurePiecesBuilder();
		out.addPiece(new CasinoGroundPiece(CasinoGroundPiece.Mode.CARVE, boxes, floorY, top));
		pieces.forEach(out::addPiece);
		out.addPiece(new CasinoGroundPiece(CasinoGroundPiece.Mode.SKIRT, boxes, floorY, top));
		return out;
	}

	private static void remember(Map<Long, Reason> cache, long chunk, Reason reason) {
		if (cache.size() >= MAX_VERDICTS) {
			cache.clear();
		}
		cache.put(chunk, reason);
	}

	private static final int MAX_VERDICTS = 4096;

	/** Forget cached verdicts and cells (server stop; surveys switching configurations). */
	public static void clearCaches() {
		synchronized (INSTANCES) {
			for (CasinoStructure s : INSTANCES) {
				s.verdicts.clear();
			}
		}
		Cell.clear();
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

	/** Surface heights the pre-check samples the biome at (vanilla checks one quart at the start). */
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

	/** Survey label: the biome at the start chunk's centre, on the surface. */
	private static String biomeName(GenerationContext ctx, Cell cell) {
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		int y = cell.heights(x, z)[0];
		Holder<Biome> b = BiomeLookup.at(ctx, QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		return b == null ? "?" : name(b);
	}

	private static String name(Holder<Biome> b) {
		return b.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
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

	/** Verdict + the move that fits the footprint: horizontal candidate offset, vertical mid-span shift. */
	record Fit(Reason reason, int dx, int dy, int dz, int candidate) {}

	/**
	 * Per placement cell (thread, world noise, chunk): the height probe and its column cache, shared by every style
	 * of the set tried in this cell (vanilla tries them one after another on the same thread) and by both fit stages.
	 */
	static final class Cell {
		private static final ThreadLocal<Cell> CURRENT = new ThreadLocal<>();
		private static volatile int epoch;

		final RandomState randomState;
		final long seed;
		final long chunk;
		final int epochAt;
		final TerrainProbe probe;
		final Map<Long, int[]> columns = new HashMap<>();
		int lastSurface = TerrainProbe.NO_GUESS;

		private Cell(GenerationContext ctx) {
			randomState = ctx.randomState();
			seed = ctx.seed();
			chunk = ctx.chunkPos().pack();
			epochAt = epoch;
			probe = TerrainProbe.of(ctx.chunkGenerator(), ctx.randomState(), ctx.heightAccessor());
		}

		static Cell of(GenerationContext ctx) {
			Cell c = CURRENT.get();
			if (c == null || c.randomState != ctx.randomState() || c.seed != ctx.seed() || c.chunk != ctx.chunkPos().pack() || c.epochAt != epoch) {
				c = new Cell(ctx);
				CURRENT.set(c);
			}
			return c;
		}

		/** Invalidates every thread's cell (their next use rebuilds it). */
		static void clear() {
			epoch++;
			CURRENT.remove();
		}

		/** {surface, floor} of a column ({@link TerrainProbe#heights}), cached. */
		int[] heights(int x, int z) {
			long key = BlockPos.asLong(x, 0, z);
			int[] h = columns.get(key);
			if (h == null) {
				int d0 = probe.densityCalls;
				int e0 = probe.exactCalls;
				h = probe.heights(x, z, lastSurface);
				columns.put(key, h);
				if (STATS.enabled) {
					STATS.sampleColumns.incrementAndGet();
					STATS.densitySamples.addAndGet(probe.densityCalls - d0);
					STATS.exactColumns.addAndGet(probe.exactCalls - e0);
				}
			}
			lastSurface = h[0];
			return h;
		}
	}

	/**
	 * Prefit: the start piece's footprint + door throat, with the rotation and element the jigsaw will draw first
	 * from the context random ({@code Rotation.getRandom}, then {@code startPool.getRandomTemplate}); null when it
	 * cannot be predicted. The start piece is part of every assembly, so a site it does not fit is rejected for all.
	 */
	Fit prefit(GenerationContext ctx, Cell cell, int range) {
		ChunkPos cp = ctx.chunkPos();
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(ctx.seed(), cp.x(), cp.z());
		Rotation rotation = Rotation.getRandom(random);
		StructurePoolElement start = jigsaw.getStartPool().value().getRandomTemplate(random);
		Vec3i size = start.getSize(ctx.structureTemplateManager(), Rotation.NONE);
		if (size.getX() <= 0 || size.getZ() <= 0) {
			return null;
		}
		int turns = rotation.ordinal();
		int ox = cp.getMinBlockX();
		int oz = cp.getMinBlockZ();
		FitSite.Box box = FitSite.templateBox(ox, oz, size.getX(), size.getZ(), turns);
		return site(cell, List.of(box), FitSite.throat(ox, oz, size.getX(), size.getZ(), turns), range, 0);
	}

	/** Exact fit on the assembled pieces (+ the start piece's door throat). */
	Fit fit(GenerationContext ctx, Cell cell, List<StructurePiece> pieces, int range) {
		// the start piece's ground level (min y + ground delta 1) sits at the fit's floor height + start_height
		int ref = pieces.getFirst().getBoundingBox().minY() + 1 - PackFiles.START_HEIGHT;
		List<FitSite.Box> boxes = new ArrayList<>(pieces.size());
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			boxes.add(new FitSite.Box(b.minX(), b.minZ(), b.maxX(), b.maxZ()));
		}
		List<int[]> throat = List.of();
		if (pieces.getFirst() instanceof PoolElementStructurePiece s) {
			Vec3i size = s.getElement().getSize(ctx.structureTemplateManager(), Rotation.NONE);
			throat = FitSite.throat(s.getPosition().getX(), s.getPosition().getZ(), size.getX(), size.getZ(), s.getRotation().ordinal());
		}
		return site(cell, boxes, throat, range, ref);
	}

	/**
	 * Fits a site to the terrain. For each candidate shift (the first that fits wins):
	 * <ul>
	 *   <li>FLOOD: water at any door-throat column; water deeper than {@link #MAX_WATER_DEPTH} at more than one in
	 *       {@link #DEEP_SHARE} probes; any water at more than one in {@link #WET_SHARE}; or a wet column whose bed
	 *       lies more than {@code range + }{@link #BED_SLACK} below the highest dry ground (the foundation +
	 *       {@code beard_thin} fill under the boxes cannot reach it);</li>
	 *   <li>FLAT: the dry ground spans more than {@code range}, ignoring the {@code n / }{@link #TRIM_SHARE} highest and
	 *       lowest probes (a lone bump or dip at the outline is cut / filled by the beard; this also absorbs the
	 *       rare few-block error of {@link TerrainProbe}).</li>
	 * </ul>
	 * Both tests only grow as probes are added, so a candidate is dropped as soon as it fails. The floor moves to
	 * the middle of the (trimmed) span. The base spot's reason is reported when no candidate fits.
	 */
	static Fit site(Cell cell, List<FitSite.Box> boxes, List<int[]> throat, int range, int ref) {
		List<int[]> fine = FitSite.points(boxes, throat, fineSpacing);
		List<int[]> coarse = FitSite.points(boxes, throat, COARSE_SPACING);
		Set<Long> throatKeys = new java.util.HashSet<>();
		for (int[] t : throat) {
			throatKeys.add(BlockPos.asLong(t[0], 0, t[1]));
		}
		int[] span = new int[2];
		Reason first = null;
		int tries = Math.max(1, Math.min(candidates, OFFSETS.length));
		for (int k = 0; k < tries; k++) {
			int dx = OFFSETS[k][0];
			int dz = OFFSETS[k][1];
			// coarse pass first (cheap rejection of a bad shift), then every outline column of the survivor
			Reason verdict = check(cell, coarse, throatKeys, dx, dz, range, span);
			if (verdict == Reason.OK) {
				verdict = check(cell, fine, throatKeys, dx, dz, range, span);
			}
			if (verdict == Reason.OK) {
				// the floor goes to the door's ground (the throat median), inside the trimmed span; terrain higher than
				// CUT_MAX above it would be a hillside carved out of the rooms: try the next shift
				int floor = throatLevel(cell, throat, dx, dz, Math.floorDiv(span[0] + span[1], 2));
				floor = Math.clamp(floor, span[0], span[1]);
				if (span[1] - floor <= CUT_MAX) {
					return new Fit(Reason.OK, dx, floor - 1 - ref, dz, k);
				}
				verdict = Reason.FLAT;
			}
			if (first == null) {
				first = verdict;
			}
		}
		return new Fit(first, 0, 0, 0, -1);
	}

	/** Median ground (feet) height of the door throat, {@code fallback} without a dry throat column. */
	private static int throatLevel(Cell cell, List<int[]> throat, int dx, int dz, int fallback) {
		int[] h = new int[throat.size()];
		int n = 0;
		for (int[] t : throat) {
			int[] c = cell.heights(t[0] + dx, t[1] + dz);
			if (c[0] == c[1]) {
				h[n++] = c[0];
			}
		}
		if (n == 0) {
			return fallback;
		}
		java.util.Arrays.sort(h, 0, n);
		return h[n / 2];
	}

	/** One candidate shift against one probe set (see {@link #site}); {@code span} receives the trimmed {lo, hi}. */
	private static Reason check(Cell cell, List<int[]> points, Set<Long> throatKeys, int dx, int dz, int range, int[] span) {
		int n = points.size();
		int trim = n / TRIM_SHARE;
		int maxDeep = n / DEEP_SHARE;
		int maxWet = Math.max(MAX_WET_PROBES, n / WET_SHARE);
		int[] dry = new int[n];
		int dryN = 0;
		int wet = 0;
		int deep = 0;
		int bed = Integer.MAX_VALUE;
		for (int[] s : points) {
			int[] h = cell.heights(s[0] + dx, s[1] + dz);
			int depth = h[0] - h[1];
			if (depth > 0) {
				bed = Math.min(bed, h[1]);
				if (throatKeys.contains(BlockPos.asLong(s[0], 0, s[1])) || ++wet > maxWet || depth > MAX_WATER_DEPTH && ++deep > maxDeep) {
					return Reason.FLOOD;
				}
				continue;
			}
			int i = dryN++; // insertion into the sorted dry heights
			while (i > 0 && dry[i - 1] > h[0]) {
				dry[i] = dry[i - 1];
				i--;
			}
			dry[i] = h[0];
			if (dryN > 2 * trim && dry[dryN - 1 - trim] - dry[trim] > range) {
				return Reason.FLAT;
			}
		}
		if (dryN <= 2 * trim) {
			return Reason.FLOOD;
		}
		span[0] = dry[trim];
		span[1] = dry[dryN - 1 - trim];
		if (bed != Integer.MAX_VALUE && span[1] - bed > range + BED_SLACK) {
			return Reason.FLOOD;
		}
		return Reason.OK;
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

	// ---- diagnostics (WorldgenSpawnSurvey; -Dburmaldaholic.worldgenStats=true) -----------------------------------

	public static final Stats STATS = new Stats();

	/**
	 * Outcome counters + generation timings of {@link #findGenerationPoint} since the last {@link #reset}. Off unless
	 * {@code -Dburmaldaholic.worldgenStats=true} or a survey sets {@link #enabled} (R-CB M6d: no shared counters or
	 * locks on worldgen threads in production).
	 */
	public static final class Stats {
		public volatile boolean enabled = Boolean.getBoolean("burmaldaholic.worldgenStats");
		public final AtomicLong attempts = new AtomicLong();
		public final AtomicLong gated = new AtomicLong();
		public final AtomicLong precheckRejected = new AtomicLong();
		public final AtomicLong prefitRejected = new AtomicLong();
		public final AtomicLong cacheHits = new AtomicLong();
		public final AtomicLong noStart = new AtomicLong();
		public final AtomicLong incomplete = new AtomicLong();
		public final AtomicLong steep = new AtomicLong();
		public final AtomicLong flooded = new AtomicLong();
		public final AtomicLong biome = new AtomicLong();
		public final AtomicLong accepted = new AtomicLong();
		public final AtomicLong stubNanos = new AtomicLong();
		public final AtomicLong assembleNanos = new AtomicLong();
		public final AtomicLong terrainNanos = new AtomicLong();
		public final AtomicLong sampleColumns = new AtomicLong();
		public final AtomicLong densitySamples = new AtomicLong();
		public final AtomicLong exactColumns = new AtomicLong();
		public final AtomicLong allNanos = new AtomicLong();
		/** Index of the candidate offset that fitted (0 = the vanilla spot). */
		public final AtomicLongArray candidateUsed = new AtomicLongArray(OFFSETS.length);
		/** Biome (at the start chunk centre) → outcome counts by {@link Reason} ordinal. */
		private final Map<String, long[]> perBiome = new TreeMap<>();
		private final long[] nanos = new long[16384];
		private int count;
		private final long[] allSamples = new long[65536];
		private int allCount;

		void count(Reason r, String biomeName) {
			(switch (r) {
				case OK -> accepted;
				case FLAT -> steep;
				case FLOOD -> flooded;
				case INCOMPLETE -> incomplete;
				case BIOME -> biome;
			}).incrementAndGet();
			if (biomeName != null) {
				synchronized (this) {
					perBiome.computeIfAbsent(biomeName, k -> new long[Reason.values().length])[r.ordinal()]++;
				}
			}
		}

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
			for (AtomicLong a : List.of(attempts, gated, precheckRejected, prefitRejected, cacheHits, noStart, incomplete, steep, flooded, biome,
					accepted, stubNanos, assembleNanos, terrainNanos, sampleColumns, densitySamples, exactColumns, allNanos)) {
				a.set(0);
			}
			for (int i = 0; i < candidateUsed.length(); i++) {
				candidateUsed.set(i, 0);
			}
			perBiome.clear();
			count = 0;
			allCount = 0;
		}

		/** Durations (ns) of the attempts that reached the terrain work (passed the gate, pre-check and cache), sorted. */
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

		/** Per-biome outcomes: {@code biome=OK/FLAT/FLOOD/INCOMPLETE/BIOME}, most-evaluated first. */
		public synchronized String perBiome() {
			List<Map.Entry<String, long[]>> rows = new ArrayList<>(perBiome.entrySet());
			rows.sort((a, b) -> Long.compare(java.util.Arrays.stream(b.getValue()).sum(), java.util.Arrays.stream(a.getValue()).sum()));
			StringBuilder sb = new StringBuilder();
			for (var e : rows) {
				long[] v = e.getValue();
				sb.append(e.getKey()).append('=').append(v[0]).append('/').append(v[1]).append('/').append(v[2]).append('/').append(v[3])
					.append('/').append(v[4]).append(' ');
			}
			return sb.toString().trim();
		}

		@Override
		public String toString() {
			long evaluated = Math.max(1, accepted.get() + steep.get() + flooded.get() + incomplete.get() + biome.get());
			StringBuilder used = new StringBuilder();
			for (int i = 0; i < candidateUsed.length(); i++) {
				used.append(i == 0 ? "" : "/").append(candidateUsed.get(i));
			}
			return "attempts=" + attempts + " gated=" + gated + " precheckRejected=" + precheckRejected + " noStart=" + noStart
				+ " cacheHits=" + cacheHits + " prefitRejected=" + prefitRejected + " incomplete=" + incomplete + " flat=" + steep + " flood=" + flooded
				+ " biome=" + biome + " accepted=" + accepted + " (by candidate " + used + ")"
				+ " | avg ms/evaluated: stub " + String.format(java.util.Locale.ROOT, "%.2f", stubNanos.get() / 1e6 / evaluated)
				+ " assemble " + String.format(java.util.Locale.ROOT, "%.2f", assembleNanos.get() / 1e6 / evaluated)
				+ " terrain " + String.format(java.util.Locale.ROOT, "%.2f", terrainNanos.get() / 1e6 / evaluated)
				+ " (" + sampleColumns.get() / evaluated + " columns, " + String.format(java.util.Locale.ROOT, "%.1f", densitySamples.get() / (double) Math.max(1, sampleColumns.get()))
				+ " density samples each, " + exactColumns + " exact columns)";
		}
	}
}
