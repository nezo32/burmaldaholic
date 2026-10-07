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
		try {
			Optional<GenerationStub> stub = jigsaw.findValidGenerationPoint(ctx); // cheap: start piece + biome check
			if (stub.isEmpty()) {
				STATS.noStart.incrementAndGet();
				return stub;
			}
			StructurePiecesBuilder built = stub.get().getPiecesBuilder(); // the jigsaw assembles here, ONCE
			List<StructurePiece> pieces = built.build().pieces();
			if (!hasRequired(pieces)) {
				STATS.incomplete.incrementAndGet();
				return Optional.empty();
			}
			Terrain terrain = terrain(ctx, pieces);
			if (terrain == Terrain.FLOODED) {
				STATS.flooded.incrementAndGet();
				return Optional.empty();
			}
			if (terrain == Terrain.STEEP) {
				STATS.steep.incrementAndGet();
				return Optional.empty();
			}
			STATS.accepted.incrementAndGet();
			return Optional.of(new GenerationStub(stub.get().position(), Either.right(built)));
		} finally {
			STATS.record(System.nanoTime() - t0);
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

	private Terrain terrain(GenerationContext ctx, List<StructurePiece> pieces) {
		BoundingBox union = pieces.getFirst().getBoundingBox();
		List<int[]> samples = new ArrayList<>();
		for (StructurePiece p : pieces) {
			BoundingBox b = p.getBoundingBox();
			union = BoundingBox.encapsulatingBoxes(List.of(union, b)).orElse(union);
			samples.add(new int[] {b.getCenter().getX(), b.getCenter().getZ()});
		}
		samples.add(new int[] {union.minX(), union.minZ()});
		samples.add(new int[] {union.maxX(), union.minZ()});
		samples.add(new int[] {union.minX(), union.maxZ()});
		samples.add(new int[] {union.maxX(), union.maxZ()});
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int[] s : samples) {
			int surface = ctx.chunkGenerator().getFirstOccupiedHeight(s[0], s[1], Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
			int floor = ctx.chunkGenerator().getFirstOccupiedHeight(s[0], s[1], Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
			if (surface - floor > 0) {
				return Terrain.FLOODED;
			}
			lo = Math.min(lo, surface);
			hi = Math.max(hi, surface);
			if (hi - lo > maxHeightRange) {
				return Terrain.STEEP;
			}
		}
		return Terrain.OK;
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
		private final long[] nanos = new long[8192];
		private int count;

		synchronized void record(long ns) {
			nanos[count % nanos.length] = ns;
			count++;
		}

		public synchronized void reset() {
			for (AtomicLong a : List.of(attempts, gated, noStart, incomplete, steep, flooded, accepted)) {
				a.set(0);
			}
			count = 0;
		}

		/** Recorded durations (ns) of the evaluated (non-gated) attempts, sorted. */
		public synchronized long[] sortedNanos() {
			long[] out = java.util.Arrays.copyOf(nanos, Math.min(count, nanos.length));
			java.util.Arrays.sort(out);
			return out;
		}

		@Override
		public String toString() {
			return "attempts=" + attempts + " gated=" + gated + " noStart(biome/jigsaw)=" + noStart + " incomplete=" + incomplete
				+ " steep=" + steep + " flooded=" + flooded + " accepted=" + accepted;
		}
	}
}
