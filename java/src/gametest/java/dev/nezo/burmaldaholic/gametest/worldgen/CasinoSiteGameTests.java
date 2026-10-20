package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.CasinoBuilder;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoPlacement;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.HighRollerLounge;
import dev.nezo.burmaldaholic.worldgen.logic.site.NetherSite;
import dev.nezo.burmaldaholic.worldgen.logic.site.Tunnel;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoGroundPiece;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoTunnelPiece;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Casino sites in the real Nether and End (lane WG, owner playtest W1 / W2). The GameTest server never places
 * structures by itself, so each test generates the host's structure start with the level's own generator (our append
 * runs inside it, chance forced to 1), generates the chunks, places the start chunk by chunk (what {@code /place}
 * does) and then walks the real blocks:
 * <ul>
 *   <li>Nether: from the Parlor's front entry, on foot (up 1, down at most 3), never next to lava, to open air at
 *       least {@link NetherSite#GOAL} blocks outside the envelope and outside the bastion; the back tunnel must open
 *       into the bastion when there is one.</li>
 *   <li>End: the Lounge is at least 24 blocks from every End City piece (full height) and its front stair is reached on
 *       foot (up or down one block only) from end stone 8 blocks outside it.</li>
 * </ul>
 * Survey ({@code -Dburmaldaholic.worldgenSurvey=true}, {@link #siteSurvey}): fit and reach rates over 8 seeds on the
 * terrain model, logged with {@code [site]}.
 */
public class CasinoSiteGameTests {
	/** Bastion sites in the test Nether: far from spawn and from other tests, fixed so reruns place the same blocks. */
	private static final int[][] NETHER_SPOTS = {{24_000, -24_000}, {27_000, -21_000}, {21_000, -27_000}};
	private static final long[] SURVEY_SEEDS = {12345L, 42L, -4172144997902289642L, 8675309L, 1L, 777L, 2024L, -99L};

	// ---- Nether -------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 6000)
	public void netherParlorWalksOutToOpenAir(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		if (nether == null || !(nether.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
			Burmaldaholic.LOGGER.info("[site test] no noise Nether on this test server (26.3 GameTest Nether is flat): skipped,"
				+ " covered by CasinoSiteClientGameTests in a real world");
			helper.succeed();
			return;
		}
		checkNether(nether);
		helper.succeed();
	}

	/** The Nether check on a real noise Nether (server GameTest on 26.2, client GameTest world on 26.3). */
	static String checkNether(ServerLevel nether) {
		Holder<Structure> bastion = nether.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse("minecraft:bastion_remnant"))).orElseThrow();
		var cfg = CasinoConfig.worldgen().piglinParlor;
		double before = cfg.chance;
		int parlors = 0;
		int reached = 0;
		List<String> report = new ArrayList<>();
		try {
			cfg.chance = 1.0;
			for (int[] spot : NETHER_SPOTS) {
				ChunkPos chunk = new ChunkPos(spot[0] >> 4, spot[1] >> 4);
				StructureStart start = generate(nether, bastion, chunk);
				if (!start.isValid()) {
					report.add(chunk + ": no bastion");
					continue;
				}
				CasinoPlacement.Context casino = CasinoPlacement.of(nether.dimension().identifier().toString(), start.getPieces());
				if (casino == null) {
					report.add(chunk + ": no Parlor site (rejected)");
					continue;
				}
				check(casino.kind() == CasinoKind.PIGLIN_PARLOR, "bastion casino is a Parlor: " + casino.kind());
				parlors++;
				place(nether, start, 24);
				PoolElementStructurePiece parlor = casinoPiece(start);
				Layout layout = CasinoElements.layoutOf(parlor.getElement()).orElseThrow();
				int turns = CasinoStructures.quarterTurns(parlor.getRotation());
				Vec origin = vec(parlor.getPosition());
				NetherSite.Doors doors = CasinoStructures.parlorDoors(layout);
				BlockPos entry = pos(Geometry.toWorld(origin, doors.front(), turns));
				Geometry.Box env = casino.box();
				List<BoundingBox> host = new ArrayList<>();
				List<CasinoTunnelPiece> tunnels = new ArrayList<>();
				for (StructurePiece p : start.getPieces()) {
					if (p instanceof CasinoTunnelPiece t) {
						tunnels.add(t);
					} else if (p != parlor) {
						host.add(p.getBoundingBox());
					}
				}
				check(env.minY() >= nether.getChunkSource().getGenerator().getSeaLevel(), "Parlor floor above the lava sea: " + env);
				check(stand(nether, entry), "front entry is a walk cell: " + entry);
				Predicate<BlockPos> open = p -> NetherSite.outside(env, p.getX(), p.getZ()) >= NetherSite.GOAL
					&& host.stream().noneMatch(b -> b.isInside(p)) && passable(nether, p.above(2));
				BoundingBox limit = new BoundingBox(env.minX() - 48, env.minY() - 24, env.minZ() - 48, env.maxX() + 48, env.maxY() + 24,
					env.maxZ() + 48);
				Walk walk = walk(nether, entry, open, limit, 3, 400_000);
				String tunnelInfo = tunnels.stream().map(t -> t.tunnel().dir() + "x" + t.tunnel().length()).toList().toString();
				report.add(chunk + ": Parlor " + env + " tunnels " + tunnelInfo + " walk " + (walk.goal != null ? "OUT at " + walk.goal : "STUCK")
					+ " (" + walk.visited.size() + " cells)");
				check(walk.goal != null, "Parlor at " + env + ": no lava-free walk from the entry " + entry + " to open air "
					+ NetherSite.GOAL + " blocks out (" + walk.visited.size() + " cells reached; tunnels " + tunnelInfo + ")");
				reached++;
				if (tunnels.size() > 1) {
					// the back tunnel: the walk reaches the cell past its end (a bastion room or the air beside it)
					Tunnel back = tunnels.get(1).tunnel();
					int n = back.length();
					BlockPos past = new BlockPos(back.cx(n - 1) + back.dir().dx(), back.feet(n - 1), back.cz(n - 1) + back.dir().dz());
					Walk in = walk(nether, entry, p -> p.distManhattan(past) <= 2, limit, 3, 400_000);
					check(in.goal != null, "back door open into the bastion: " + past + " reached from the entry");
				}
			}
		} finally {
			cfg.chance = before;
		}
		report.forEach(r -> Burmaldaholic.LOGGER.info("[site test] nether {}", r));
		check(parlors >= 1, "at least one test bastion got a Parlor: " + report);
		Burmaldaholic.LOGGER.info("[site test] nether: {}/{} Parlors reachable on foot", reached, parlors);
		return reached + "/" + parlors + " Parlors reachable";
	}

	// ---- End ----------------------------------------------------------------------------------------------------

	@GameTest(maxTicks = 6000)
	public void endLoungeStandsBesideTheCity(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		if (end == null || !(end.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
			Burmaldaholic.LOGGER.info("[site test] no noise End on this test server: skipped, covered by CasinoSiteClientGameTests");
			helper.succeed();
			return;
		}
		checkEnd(end);
		helper.succeed();
	}

	/** The End check on a real noise End. */
	static String checkEnd(ServerLevel end) {
		Holder<Structure> city = end.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse("minecraft:end_city"))).orElseThrow();
		StructureSet set = end.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
			.getValue(Identifier.parse("minecraft:end_cities"));
		RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) set.placement();
		ChunkGeneratorStructureState state = end.getChunkSource().getGeneratorState();
		var cfg = CasinoConfig.worldgen().highRoller;
		double before = cfg.chance;
		StructureStart found = null;
		int cities = 0;
		try {
			cfg.chance = 1.0;
			for (int ring = 4; ring < 30 && found == null; ring++) {
				for (int i = 0; i < 8 && found == null; i++) {
					int cx = (int) Math.round(Math.cos(i * Math.PI / 4) * ring);
					int cz = (int) Math.round(Math.sin(i * Math.PI / 4) * ring);
					ChunkPos p = spread.getPotentialStructureChunk(state.getLevelSeed(), cx * spread.spacing(), cz * spread.spacing());
					if (!spread.isStructureChunk(state, p.x(), p.z())) {
						continue;
					}
					StructureStart s = generate(end, city, p);
					if (!s.isValid()) {
						continue;
					}
					cities++;
					if (CasinoPlacement.of(end.dimension().identifier().toString(), s.getPieces()) != null) {
						found = s;
					}
				}
			}
		} finally {
			cfg.chance = before;
		}
		check(found != null, "an End City with a Lounge among " + cities + " cities");
		String dim = end.dimension().identifier().toString();
		CasinoPlacement.Context lounge = CasinoPlacement.of(dim, found.getPieces());
		check(lounge.kind() == CasinoKind.HIGH_ROLLER, "End casino is the Lounge");
		Geometry.Box box = lounge.box();
		PoolElementStructurePiece piece = casinoPiece(found);
		List<BoundingBox> cityBoxes = new ArrayList<>();
		for (StructurePiece p : found.getPieces()) {
			if (p != piece && !(p instanceof CasinoGroundPiece)) {
				BoundingBox b = p.getBoundingBox();
				cityBoxes.add(b);
				Geometry.Box g = new Geometry.Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
				check(Geometry.gapXZ(box, g) >= 24, "Lounge " + box + " clear of city piece " + b);
			}
		}
		place(end, found, 16);
		// registered for /casino locate and the welcome title
		check(CasinoIndex.get(end.getServer()).casinos().stream()
			.anyMatch(r -> r.kind() == CasinoKind.HIGH_ROLLER && r.dimension().equals(dim) && r.box().equals(box)), "Lounge registered " + box);
		int turns = CasinoStructures.quarterTurns(piece.getRotation());
		Vec origin = vec(piece.getPosition());
		BlockPos foot = pos(Geometry.toWorld(origin, HighRollerLounge.STAIR_FOOT, turns));
		BlockPos door = pos(Geometry.toWorld(origin, HighRollerLounge.TERRACE_EDGE, turns));
		BoundingBox limit = new BoundingBox(box.minX() - 40, box.minY() - 20, box.minZ() - 40, box.maxX() + 40, box.maxY(), box.maxZ() + 40);
		Predicate<BlockPos> endStone = p -> NetherSite.outside(box, p.getX(), p.getZ()) >= 8 && end.getBlockState(p.below()).is(Blocks.END_STONE)
			&& cityBoxes.stream().noneMatch(b -> b.isInside(p));
		// up or down one block only: reachability is symmetric, so this is "the door from the end stone" too
		Walk walk = walk(end, door, endStone, limit, 1, 400_000);
		Burmaldaholic.LOGGER.info("[site test] end: Lounge {} beside a city of {} pieces, stair foot {}, walk {}", box, cityBoxes.size(), foot,
			walk.goal);
		check(walk.goal != null, "Lounge door " + door + " reached on foot from end stone (" + walk.visited.size() + " cells)");
		// on ground as high as the plate the stair is buried and the walk crosses its column above the foot
		check(walk.visited.stream().map(BlockPos::of).anyMatch(b -> b.getX() == foot.getX() && b.getZ() == foot.getZ()
			&& b.getY() >= foot.getY()), "the walk leaves by the front stair's column");
		return "Lounge " + box + " reached from end stone at " + walk.goal;
	}

	// ---- survey -------------------------------------------------------------------------------------------------

	/** Parlor / Lounge fit and reach rates over {@link #SURVEY_SEEDS} on the terrain model (off by default). */
	@GameTest(maxTicks = 20)
	public void siteSurvey(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		var server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = CasinoBuilder.templates(server);
		var cfg = CasinoConfig.worldgen();
		double p0 = cfg.piglinParlor.chance;
		double h0 = cfg.highRoller.chance;
		int bastions = 0;
		int parlors = 0;
		int backs = 0;
		int cities = 0;
		int lounges = 0;
		long nanos = 0;
		List<Integer> fronts = new ArrayList<>();
		try {
			cfg.piglinParlor.chance = 1;
			cfg.highRoller.chance = 1;
			for (long seed : SURVEY_SEEDS) {
				long t0 = System.nanoTime();
				for (StructureStart s : starts(reg, templates, seed, LevelStem.NETHER, Level.NETHER, "minecraft:nether_complexes", 1500,
						"minecraft:bastion_remnant")) {
					bastions++;
					List<CasinoTunnelPiece> t = s.getPieces().stream().filter(CasinoTunnelPiece.class::isInstance).map(CasinoTunnelPiece.class::cast)
						.toList();
					if (CasinoPlacement.of("minecraft:the_nether", s.getPieces()) != null) {
						parlors++;
						fronts.add(t.isEmpty() ? -1 : t.getFirst().tunnel().length());
						if (t.size() > 1) {
							backs++;
						}
					}
				}
				for (StructureStart s : starts(reg, templates, seed, LevelStem.END, Level.END, "minecraft:end_cities", 3000,
						"minecraft:end_city")) {
					cities++;
					if (CasinoPlacement.of("minecraft:the_end", s.getPieces()) != null) {
						lounges++;
					}
				}
				nanos += System.nanoTime() - t0;
			}
		} finally {
			cfg.piglinParlor.chance = p0;
			cfg.highRoller.chance = h0;
		}
		Burmaldaholic.LOGGER.info("[site] {} seeds: Parlors placed + reachable (terrain model) {}/{} = {}%, back door into the bastion {}/{}, "
			+ "front tunnel lengths {}", SURVEY_SEEDS.length, parlors, bastions, bastions == 0 ? 0 : 100 * parlors / bastions, backs, parlors,
			fronts);
		Burmaldaholic.LOGGER.info("[site] {} seeds: Lounges beside the city {}/{} = {}%; {} ms total", SURVEY_SEEDS.length, lounges, cities,
			cities == 0 ? 0 : 100 * lounges / cities, nanos / 1_000_000);
		helper.succeed();
	}

	// ---- helpers ------------------------------------------------------------------------------------------------

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	private static StructureStart generate(ServerLevel level, Holder<Structure> holder, ChunkPos chunk) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		return StructureGen.generate(holder, level.dimension(), level.registryAccess(), gen, rs, CasinoBuilder.templates(level.getServer()),
			level.getSeed(), chunk, level, b -> true);
	}

	/** Generates the chunks around the start (FULL) and places every piece chunk by chunk, like {@code /place}. */
	private static void place(ServerLevel level, StructureStart start, int margin) {
		BoundingBox bb = start.getBoundingBox();
		int x0 = (bb.minX() - margin) >> 4;
		int x1 = (bb.maxX() + margin) >> 4;
		int z0 = (bb.minZ() - margin) >> 4;
		int z1 = (bb.maxZ() + margin) >> 4;
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int cx = bb.minX() >> 4; cx <= bb.maxX() >> 4; cx++) {
			for (int cz = bb.minZ() >> 4; cz <= bb.maxZ() >> 4; cz++) {
				ChunkPos c = new ChunkPos(cx, cz);
				start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
					new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() - 1, c.getMaxBlockZ()), c);
			}
		}
	}

	private static PoolElementStructurePiece casinoPiece(StructureStart start) {
		for (StructurePiece p : start.getPieces()) {
			if (p instanceof PoolElementStructurePiece pe && CasinoElements.layoutOf(pe.getElement()).isPresent()) {
				return pe;
			}
		}
		throw new IllegalStateException("no casino piece");
	}

	record Walk(BlockPos goal, Set<Long> visited) {}

	/**
	 * Breadth-first walk over real blocks: 4 directions, up one block (head room needed), down at most
	 * {@code maxDrop}; a cell is never next to lava (feet, head and their six neighbours).
	 */
	static Walk walk(ServerLevel level, BlockPos start, Predicate<BlockPos> goal, BoundingBox limit, int maxDrop, int budget) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		if (!stand(level, start) || lava(level, start)) {
			return new Walk(null, seen);
		}
		queue.add(start);
		seen.add(start.asLong());
		while (!queue.isEmpty() && seen.size() < budget) {
			BlockPos p = queue.poll();
			if (goal.test(p)) {
				return new Walk(p, seen);
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = p.relative(d);
				BlockPos next = null;
				if (stand(level, n)) {
					next = n;
				} else if (stand(level, n.above()) && passable(level, p.above(2))) {
					next = n.above();
				} else if (passable(level, n) && passable(level, n.above())) {
					for (int k = 1; k <= maxDrop; k++) {
						BlockPos q = n.below(k);
						if (stand(level, q)) {
							next = q;
							break;
						}
						if (!passable(level, q)) {
							break;
						}
					}
				}
				if (next == null || !limit.isInside(next) || lava(level, next) || !seen.add(next.asLong())) {
					continue;
				}
				queue.add(next);
			}
		}
		return new Walk(null, seen);
	}

	static boolean passable(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty();
	}

	/** A thin block one stands in (carpet, bottom slab). */
	private static boolean low(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		VoxelShape shape = s.getCollisionShape(level, p);
		return s.getFluidState().isEmpty() && !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.5;
	}

	static boolean stand(ServerLevel level, BlockPos p) {
		boolean feetLow = low(level, p);
		if (!(feetLow || passable(level, p)) || !passable(level, p.above())) {
			return false;
		}
		if (feetLow) {
			return true;
		}
		BlockPos b = p.below();
		BlockState s = level.getBlockState(b);
		return !s.getCollisionShape(level, b).isEmpty() && s.getFluidState().isEmpty();
	}

	private static boolean lava(ServerLevel level, BlockPos feet) {
		for (BlockPos c : new BlockPos[] {feet, feet.above()}) {
			if (level.getFluidState(c).is(FluidTags.LAVA)) {
				return true;
			}
			for (Direction d : Direction.values()) {
				if (level.getFluidState(c.relative(d)).is(FluidTags.LAVA)) {
					return true;
				}
			}
		}
		return false;
	}

	private static BlockPos pos(Vec v) {
		return new BlockPos(v.x(), v.y(), v.z());
	}

	private static Vec vec(BlockPos p) {
		return new Vec(p.getX(), p.getY(), p.getZ());
	}

	/** Structure starts of {@code structureId} within {@code radius} of the origin for a seed (real generator of the preset). */
	private static List<StructureStart> starts(RegistryAccess reg, StructureTemplateManager templates, long seed, ResourceKey<LevelStem> stemKey,
			ResourceKey<Level> levelKey, String setId, int radius, String structureId) {
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(stemKey);
		ChunkGenerator gen = stem.generator();
		NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) gen;
		RandomState randomState = SurveyCompat.randomState(noise.generatorSettings().value(), reg, seed);
		HolderLookup<StructureSet> sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
		ChunkGeneratorStructureState state = gen.createState(sets, randomState, seed);
		DimensionType type = stem.type().value();
		LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
		StructureSet set = sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(setId))).value();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
		int spacing = placement.spacing();
		int rc = radius / 16;
		List<StructureStart> out = new ArrayList<>();
		for (int cx = Math.floorDiv(-rc, spacing) - 1; cx <= Math.floorDiv(rc, spacing) + 1; cx++) {
			for (int cz = Math.floorDiv(-rc, spacing) - 1; cz <= Math.floorDiv(rc, spacing) + 1; cz++) {
				ChunkPos chunk = placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
				int bx = chunk.getMiddleBlockX();
				int bz = chunk.getMiddleBlockZ();
				if ((long) bx * bx + (long) bz * bz > (long) radius * radius || !placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					continue;
				}
				for (StructureSet.StructureSelectionEntry sel : set.structures()) {
					Holder<Structure> holder = sel.structure();
					if (!holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?").equals(structureId)) {
						continue;
					}
					StructureStart start = StructureGen.generate(holder, levelKey, reg, gen, randomState, templates, seed, chunk, height,
						holder.value().biomes()::contains);
					if (start.isValid()) {
						out.add(start);
					}
				}
			}
		}
		return out;
	}
}
