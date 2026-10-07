package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Spawn-rate survey (diagnostic, docs/design/CASINO_BUILDINGS.md §1–2, architecture doc §9.4): runs the REAL
 * structure-start generation of the default ("normal") world preset for a few seeds — vanilla placement grid
 * (incl. exclusion zones), weighted structure choice, biome checks, jigsaw assembly, our structure type and
 * mixins — without generating any chunk, and logs how many villages / bastions / End Cities / standalone casinos
 * and how many casinos fall within a radius. Off unless {@code -Dburmaldaholic.worldgenSurvey=true} (e.g.
 * {@code JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dburmaldaholic.worldgenSurvey=true" ./gradlew runGameTest}); results
 * are logged with the prefix {@code [survey]}. The standalone set {@code burmaldaholic:casinos} needs pieces in the
 * pack (real ones, or {@code -PcasinoPlaceholders} for platform runs).
 */
public class WorldgenSpawnSurvey {
	private static final long[] SEEDS = {12345L, 42L, -4172144997902289642L, 8675309L};
	private static final int CASINO_RADIUS = 2000;
	private static final int CASINO_SPACING_RADIUS = 6000;

	@GameTest(maxTicks = 20)
	public void survey(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		var server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(server);
		boolean standalone = reg.lookupOrThrow(Registries.STRUCTURE_SET)
			.get(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(PackFiles.SET))).isPresent();
		if (standalone) {
			// configurations: "legacy-like" (no biome pre-check, one candidate) vs the shipped defaults; same seeds
			boolean sweep = Boolean.getBoolean("burmaldaholic.worldgenSurvey.sweep");
			Object[][] configs = sweep ? new Object[][] {{false, 1}, {true, 1}, {true, CasinoStructure.candidates}, {true, 9}}
				: new Object[][] {{CasinoStructure.biomePrecheck, CasinoStructure.candidates}};
			boolean pre0 = CasinoStructure.biomePrecheck;
			int cand0 = CasinoStructure.candidates;
			try {
				for (Object[] cfg : configs) {
					CasinoStructure.biomePrecheck = (Boolean) cfg[0];
					CasinoStructure.candidates = (Integer) cfg[1];
					CasinoStructure.clearCaches();
					List<String> summary = new ArrayList<>();
					for (long seed : SEEDS) {
						summary.add(casinos(reg, templates, seed));
					}
					Burmaldaholic.LOGGER.info("[survey] CASINO SUMMARY precheck={} candidates={} (radius {}): {}", cfg[0], cfg[1], CASINO_RADIUS, summary);
				}
			} finally {
				CasinoStructure.biomePrecheck = pre0;
				CasinoStructure.candidates = cand0;
				CasinoStructure.clearCaches();
			}
		} else {
			Burmaldaholic.LOGGER.info("[survey] no {} structure set in the pack (no complete style yet) — standalone survey skipped", PackFiles.SET);
		}
		if (Boolean.getBoolean("burmaldaholic.worldgenSurvey.casinosOnly")) {
			helper.succeed();
			return;
		}
		var cfg = CasinoConfig.worldgen();
		double v0 = cfg.villageCasino.chance;
		double p0 = cfg.piglinParlor.chance;
		double h0 = cfg.highRoller.chance;
		try {
			for (boolean forced : new boolean[] {false, true}) {
				if (forced) {
					cfg.villageCasino.chance = 1;
					cfg.piglinParlor.chance = 1;
					cfg.highRoller.chance = 1;
				}
				for (long seed : SEEDS) {
					run(reg, templates, seed, forced, LevelStem.OVERWORLD, Level.OVERWORLD, "minecraft:villages", 2000);
					run(reg, templates, seed, forced, LevelStem.NETHER, Level.NETHER, "minecraft:nether_complexes", 1000);
					run(reg, templates, seed, forced, LevelStem.END, Level.END, "minecraft:end_cities", 3000);
				}
			}
		} finally {
			cfg.villageCasino.chance = v0;
			cfg.piglinParlor.chance = p0;
			cfg.highRoller.chance = h0;
		}
		helper.succeed();
	}

	/** The default preset's generator + structure state of one dimension for a seed. */
	record World(ChunkGenerator gen, RandomState randomState, ChunkGeneratorStructureState state, LevelHeightAccessor height,
			HolderLookup<StructureSet> sets) {}

	static World world(RegistryAccess reg, long seed, ResourceKey<LevelStem> stemKey) {
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(stemKey);
		ChunkGenerator gen = stem.generator();
		NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) gen;
		RandomState randomState = SurveyCompat.randomState(noise.generatorSettings().value(), reg, seed);
		HolderLookup<StructureSet> sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
		ChunkGeneratorStructureState state = gen.createState(sets, randomState, seed);
		DimensionType type = stem.type().value();
		return new World(gen, randomState, state, LevelHeightAccessor.create(type.minY(), type.height()), sets);
	}

	/**
	 * Same weighted selection as {@code ChunkGenerator.createStructures}: structures of the set in a seeded
	 * weighted order, the first valid start wins. Null when none is valid.
	 */
	static StructureStart startAt(RegistryAccess reg, StructureTemplateManager templates, World w, ResourceKey<Level> levelKey, StructureSet set,
			long seed, ChunkPos chunk, Map<String, int[]> perStructure) {
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunk.x(), chunk.z());
		int total = options.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry o : options) {
				choice -= o.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry sel = options.get(index);
			Holder<net.minecraft.world.level.levelgen.structure.Structure> holder = sel.structure();
			StructureStart start = StructureGen.generate(holder, levelKey, reg, w.gen(), w.randomState(), templates, seed, chunk, w.height(),
				holder.value().biomes()::contains);
			if (start.isValid()) {
				String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
				perStructure.computeIfAbsent(id, k -> new int[2])[0]++;
				return start;
			}
			options.remove(index);
			total -= sel.weight();
		}
		return null;
	}

	/**
	 * The standalone casino set: placement cells (exclusion zone applied by vanilla's {@code isStructureChunk}), per
	 * cell outcome (casino / biome-valid but rejected by completeness, steepness or water / no valid biome), distance
	 * of the nearest casino from spawn, density → mean spacing, generation timings. Acceptance (§9.4): ≥ 8 casinos
	 * within 2000 blocks, median nearest ≤ 700, rejection ≤ 40 % of biome-valid cells.
	 */
	private static String casinos(RegistryAccess reg, StructureTemplateManager templates, long seed) {
		World w = world(reg, seed, LevelStem.OVERWORLD);
		StructureSet set = w.sets().getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(PackFiles.SET))).value();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
		int spacing = placement.spacing();
		int rc = CASINO_SPACING_RADIUS / 16;
		int cells = 0;
		int excluded = 0;
		int biomeValid = 0;
		int rejected = 0;
		int inner = 0;
		Map<String, int[]> perStructure = new TreeMap<>();
		List<int[]> found = new ArrayList<>();
		int[] o = SurveyCompat.origin(w.gen(), w.randomState()); // spawn region centre (0, 0 in 26.2)
		int ocx = o[0] >> 4;
		int ocz = o[1] >> 4;
		Map<String, Integer> noBiome = new TreeMap<>();
		CasinoStructure.STATS.reset();
		long t0 = System.nanoTime();
		for (int cx = Math.floorDiv(ocx - rc, spacing) - 1; cx <= Math.floorDiv(ocx + rc, spacing) + 1; cx++) {
			for (int cz = Math.floorDiv(ocz - rc, spacing) - 1; cz <= Math.floorDiv(ocz + rc, spacing) + 1; cz++) {
				ChunkPos chunk = placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
				int bx = chunk.getMiddleBlockX() - o[0];
				int bz = chunk.getMiddleBlockZ() - o[1];
				if ((long) bx * bx + (long) bz * bz > (long) CASINO_SPACING_RADIUS * CASINO_SPACING_RADIUS) {
					continue;
				}
				cells++;
				if (!placement.isStructureChunk(w.state(), chunk.x(), chunk.z())) {
					excluded++;
					continue;
				}
				long rejectedBefore = CasinoStructure.STATS.incomplete.get() + CasinoStructure.STATS.steep.get() + CasinoStructure.STATS.flooded.get();
				StructureStart start = startAt(reg, templates, w, Level.OVERWORLD, set, seed, chunk, perStructure);
				long rejectedNow = CasinoStructure.STATS.incomplete.get() + CasinoStructure.STATS.steep.get() + CasinoStructure.STATS.flooded.get();
				if (start != null) {
					biomeValid++;
					BoundingBox b = start.getBoundingBox();
					found.add(new int[] {b.getCenter().getX() - o[0], b.getCenter().getZ() - o[1], recognised(start) ? 1 : 0});
				} else if (rejectedNow > rejectedBefore) {
					biomeValid++;
					rejected++;
				} else {
					var biome = SurveyCompat.biome(w.gen(), w.randomState(), QuartPos.fromBlock(chunk.getMinBlockX()), QuartPos.fromBlock(64),
						QuartPos.fromBlock(chunk.getMinBlockZ()));
					noBiome.merge(biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
				}
			}
		}
		long ms = (System.nanoTime() - t0) / 1_000_000;
		Burmaldaholic.LOGGER.info("[survey] CASINOS seed={} origin={},{} cells without a casino biome (biome at y 64): {}", seed, o[0], o[1], noBiome);
		List<Integer> fromSpawn = new ArrayList<>();
		int unrecognised = 0;
		for (int[] f : found) {
			int d = (int) Math.round(Math.hypot(f[0], f[1]));
			if (d <= CASINO_RADIUS) {
				inner++;
				fromSpawn.add(d);
			}
			unrecognised += f[2] == 0 ? 1 : 0;
		}
		fromSpawn.sort(Integer::compare);
		List<Integer> nn = new ArrayList<>();
		for (int[] a : found) {
			if (Math.hypot(a[0], a[1]) > CASINO_SPACING_RADIUS - 1500) {
				continue; // edge effect: neighbours outside the surveyed disc
			}
			double best = Double.MAX_VALUE;
			for (int[] b : found) {
				if (a != b) {
					best = Math.min(best, Math.hypot(a[0] - b[0], a[1] - b[1]));
				}
			}
			nn.add((int) Math.round(best));
		}
		nn.sort(Integer::compare);
		double areaKm2 = Math.PI * CASINO_SPACING_RADIUS * (double) CASINO_SPACING_RADIUS / 1e6;
		int meanSpacing = found.isEmpty() ? -1 : (int) Math.round(Math.sqrt(areaKm2 * 1e6 / found.size()));
		long[] ns = CasinoStructure.STATS.sortedNanos();
		double medianMs = ns.length == 0 ? 0 : ns[ns.length / 2] / 1e6;
		double p99Ms = ns.length == 0 ? 0 : ns[Math.min(ns.length - 1, (int) (ns.length * 0.99))] / 1e6;
		int rejectPct = biomeValid == 0 ? 0 : Math.round(100f * rejected / biomeValid);
		long[] all = CasinoStructure.STATS.sortedAllNanos();
		double allMedian = all.length == 0 ? 0 : all[all.length / 2] / 1e6;
		double allP99 = all.length == 0 ? 0 : all[Math.min(all.length - 1, (int) (all.length * 0.99))] / 1e6;
		double perCell = CasinoStructure.STATS.allNanos.get() / 1e6 / Math.max(1, cells - excluded);
		double p = found.size() / (double) Math.max(1, cells);
		String predicted = p == 0 ? "-" : String.format(java.util.Locale.ROOT, "S=%d→%.0f S=28→%.0f S=32→%.0f", spacing,
			16 * spacing / Math.sqrt(p), 16 * 28 / Math.sqrt(p), 16 * 32 / Math.sqrt(p));
		Burmaldaholic.LOGGER.info("[survey] CASINOS seed={} cost: all attempts median {} ms p99 {} ms over {} | per placement cell {} ms | "
				+ "casinos per cell {} → predicted mean spacing {}", seed, String.format(java.util.Locale.ROOT, "%.2f", allMedian),
			String.format(java.util.Locale.ROOT, "%.2f", allP99), all.length, String.format(java.util.Locale.ROOT, "%.1f", perCell),
			String.format(java.util.Locale.ROOT, "%.3f", p), predicted);
		Burmaldaholic.LOGGER.info("[survey] CASINOS seed={} r={}: cells={} excludedByVillages/placement={} biomeValid={} rejected={} ({}%) casinos={} "
				+ "({} per km², mean spacing ≈ {} blocks, median nearest-neighbour {}) perStructure={} | within {}: {} casinos, distances from spawn {} "
				+ "| unrecognisedStarts={} | gen median {} ms p99 {} ms over {} attempts | stats {} | {} ms",
			seed, CASINO_SPACING_RADIUS, cells, excluded, biomeValid, rejected, rejectPct, found.size(),
			String.format(java.util.Locale.ROOT, "%.2f", found.size() / areaKm2), meanSpacing, nn.isEmpty() ? -1 : nn.get(nn.size() / 2), fmt(perStructure),
			CASINO_RADIUS, inner, fromSpawn, unrecognised, String.format(java.util.Locale.ROOT, "%.2f", medianMs),
			String.format(java.util.Locale.ROOT, "%.2f", p99Ms), ns.length, CasinoStructure.STATS, ms);
		return "seed " + seed + ": " + inner + " within " + CASINO_RADIUS + ", nearest " + (fromSpawn.isEmpty() ? "-" : fromSpawn.getFirst())
			+ ", spacing≈" + meanSpacing + ", NN median " + (nn.isEmpty() ? -1 : nn.get(nn.size() / 2)) + ", rejected " + rejectPct + "%"
			+ ", p=" + String.format(java.util.Locale.ROOT, "%.3f", p) + " (" + predicted + ")"
			+ ", assembled median/p99 " + String.format(java.util.Locale.ROOT, "%.1f/%.1f", medianMs, p99Ms)
			+ " ms, all median/p99 " + String.format(java.util.Locale.ROOT, "%.2f/%.1f", allMedian, allP99) + " ms, per cell "
			+ String.format(java.util.Locale.ROOT, "%.1f", perCell) + " ms";
	}

	/** Every piece of a standalone start is one of our layouts (the pack and {@code Layouts.byId} agree). */
	private static boolean recognised(StructureStart start) {
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece p) || CasinoElements.layoutOf(p.getElement()).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static void run(RegistryAccess reg, StructureTemplateManager templates, long seed, boolean forced,
			ResourceKey<LevelStem> stemKey, ResourceKey<Level> levelKey, String setId, int radius) {
		World w = world(reg, seed, stemKey);
		StructureSet set = w.sets().getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(setId))).value();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
		int spacing = placement.spacing();
		int rc = radius / 16;
		int cells = 0;
		int candidates = 0;
		Map<String, int[]> perStructure = new TreeMap<>(); // id -> {starts, casinos}
		List<Integer> casinoDist = new ArrayList<>();
		for (int cx = Math.floorDiv(-rc, spacing) - 1; cx <= Math.floorDiv(rc, spacing) + 1; cx++) {
			for (int cz = Math.floorDiv(-rc, spacing) - 1; cz <= Math.floorDiv(rc, spacing) + 1; cz++) {
				ChunkPos chunk = placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
				int bx = chunk.getMiddleBlockX();
				int bz = chunk.getMiddleBlockZ();
				if ((long) bx * bx + (long) bz * bz > (long) radius * radius) {
					continue;
				}
				cells++;
				if (!placement.isStructureChunk(w.state(), chunk.x(), chunk.z())) {
					continue;
				}
				candidates++;
				Map<String, int[]> one = new TreeMap<>();
				StructureStart start = startAt(reg, templates, w, levelKey, set, seed, chunk, one);
				if (start == null) {
					continue;
				}
				String id = one.keySet().iterator().next();
				int[] c = perStructure.computeIfAbsent(id, k -> new int[2]);
				c[0]++;
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof PoolElementStructurePiece p) {
						Optional<Layout> l = CasinoElements.layoutOf(p.getElement());
						if (l.isPresent()) {
							c[1]++;
							BoundingBox b = p.getBoundingBox();
							casinoDist.add((int) Math.round(Math.hypot(b.getCenter().getX(), b.getCenter().getZ())));
							break;
						}
					}
				}
			}
		}
		casinoDist.sort(Integer::compare);
		Burmaldaholic.LOGGER.info("[survey] seed={} dim={} forced={} radius={} set={} spacing={} sep={} cells={} candidates={} perStructure={} casinoDistances={}",
			seed, stemKey.identifier().getPath(), forced, radius, setId, spacing, placement.separation(), cells, candidates,
			fmt(perStructure), casinoDist);
	}

	private static String fmt(Map<String, int[]> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append(" starts/").append(v[1]).append(" casinos, "));
		return sb.append('}').toString();
	}
}
