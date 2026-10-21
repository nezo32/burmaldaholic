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

	/** Structure starts and casinos found by one {@link #run}. */
	record Count(int starts, int casinos) {
		Count plus(Count o) {
			return new Count(starts + o.starts, casinos + o.casinos);
		}
	}

	/**
	 * Always on (v0.1.2 fix for "no casinos"): on the GameTest server — a world that never chose a casino mode,
	 * so ON by default — the real structure-start generation with the shipped attach chances (0.5 / 0.6 / 0.5)
	 * puts casinos in villages, bastions and End Cities near spawn, at about the configured rate.
	 */
	@GameTest(maxTicks = 20, environment = CasinoQa.ENV)
	public void casinosGenerateWithDefaultChances(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		helper.assertTrue(dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.active(), "worldgen inactive: casino mode/worldgen off");
		var cfg = CasinoConfig.worldgen();
		helper.assertTrue(cfg.villageCasino.chance == 0.5 && cfg.piglinParlor.chance == 0.6 && cfg.highRoller.chance == 0.5,
			"attach chances are not the v0.1.2 defaults: " + cfg.villageCasino.chance + "/" + cfg.piglinParlor.chance + "/" + cfg.highRoller.chance);
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = dev.nezo.burmaldaholic.worldgen.CasinoBuilder.templates(server);
		Count village = new Count(0, 0);
		Count bastion = new Count(0, 0);
		Count end = new Count(0, 0);
		for (long seed : new long[] {SEEDS[0], SEEDS[1]}) {
			village = village.plus(run(reg, templates, seed, false, LevelStem.OVERWORLD, Level.OVERWORLD, "minecraft:villages", 2000));
			bastion = bastion.plus(run(reg, templates, seed, false, LevelStem.NETHER, Level.NETHER, "minecraft:nether_complexes", 1000));
			end = end.plus(run(reg, templates, seed, false, LevelStem.END, Level.END, "minecraft:end_cities", 3000));
		}
		Burmaldaholic.LOGGER.info("[survey] default chances, 2 seeds: villages {}, nether complexes {}, end cities {}", village, bastion, end);
		helper.assertTrue(village.casinos() >= 4, "too few village casinos within 2000 blocks: " + village);
		helper.assertTrue(bastion.casinos() >= 1, "no Piglin Parlor within 1000 blocks: " + bastion);
		helper.assertTrue(end.casinos() >= 4, "too few High Roller Lounges within 3000 blocks: " + end);
		// ≈ chance × street-end fit; v0.1.1's 0.35 / 0.20 would fall below these bounds
		helper.assertTrue(village.casinos() >= 0.3 * village.starts(), "village casino rate below 0.3: " + village);
		// v0.2 (WG): the Lounge stands beside the city only where the island has room (~90 % of cities at chance 1;
		// 34 % measured at the default 0.5 chance), so the floor is 0.25
		helper.assertTrue(end.casinos() >= 0.25 * end.starts(), "End City lounge rate below 0.25: " + end);
		helper.succeed();
	}

	/**
	 * Accuracy + cost of {@link dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe} against vanilla's exact
	 * columns on the default preset (R-CB M6a): random land/shore columns, guess = the exact surface 24 blocks away
	 * (like the projected start). Diagnostic; off unless {@code -Dburmaldaholic.worldgenSurvey=true}.
	 */
	@GameTest(maxTicks = 20, environment = CasinoQa.ENV)
	public void terrainProbeAccuracy(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		RegistryAccess reg = helper.getLevel().getServer().registryAccess();
		for (long seed : SEEDS) {
			World w = world(reg, seed, LevelStem.OVERWORLD);
			var probe = dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.of(w.gen(), w.randomState(), w.height());
			java.util.Random r = new java.util.Random(seed);
			int n = 0;
			int within1 = 0;
			int within2 = 0;
			int floorWithin1 = 0;
			int maxErr = 0;
			long exactNs = 0;
			long columnNs = 0;
			long fastNs = 0;
			List<String> worst = new ArrayList<>();
			int columnN = 0;
			for (int walk = 0; walk < 40; walk++) {
				int x0 = r.nextInt(8000) - 4000;
				int z0 = r.nextInt(8000) - 4000;
				int guess = dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.NO_GUESS;
				for (int i = 0; i < 8; i++) {
					// a footprint edge: probes 4 blocks apart, each guessing from the previous one (as the fit does)
					int x = x0 + 4 * i;
					int z = z0;
					long a = System.nanoTime();
					int[] ex = probe.exact(x, z);
					long b = System.nanoTime();
					if (i == 0) {
						w.gen().getBaseColumn(x, z, w.height(), w.randomState());
						columnNs += System.nanoTime() - b;
						columnN++;
					}
					long d = System.nanoTime();
					int[] f = probe.heights(x, z, guess);
					long e = System.nanoTime();
					guess = f[0];
					if (walk < 3) {
						continue; // warm-up
					}
					n++;
					exactNs += b - a;
					fastNs += e - d;
					int err = Math.abs(f[0] - ex[0]);
					maxErr = Math.max(maxErr, err);
					within1 += err <= 1 ? 1 : 0;
					within2 += err <= 2 ? 1 : 0;
					floorWithin1 += Math.abs(f[1] - ex[1]) <= 1 ? 1 : 0;
					if (err > 2 && worst.size() < 8) {
						worst.add(x + "," + z + ": exact " + ex[0] + "/" + ex[1] + " fast " + f[0] + "/" + f[1]);
					}
				}
			}
			StringBuilder grid = new StringBuilder();
			for (int[] g : new int[][] {{1, 1}, {1, 12}, {4, 12}, {8, 12}, {16, 12}, {28, 12}}) {
				probe.benchGrid(100, 40, 100, g[0], g[1]); // warm the code, then fresh positions (the samplers cache columns)
				long ns = 0;
				for (int rep = 0; rep < 3; rep++) {
					ns += probe.benchGrid(r.nextInt(8000) - 4000, 40, r.nextInt(8000) - 4000, g[0], g[1]);
				}
				ns /= 3;
				grid.append(g[0]).append('x').append(g[1]).append('x').append(g[0]).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", ns / 1e6)).append("ms ");
			}
			Burmaldaholic.LOGGER.info("[survey] PROBE grid (26.3 volume path) {}", grid);
			Burmaldaholic.LOGGER.info("[survey] PROBE seed={} fast={} n={} surface within 1: {}% within 2: {}% max err {} | floor within 1: {}% | "
					+ "ms/column exact(getBaseHeight) {} getBaseColumn {} fast {} | worst {}",
				seed, probe.fast(), n, 100 * within1 / n, 100 * within2 / n, maxErr, 100 * floorWithin1 / n,
				String.format(java.util.Locale.ROOT, "%.3f", exactNs / 1e6 / n), String.format(java.util.Locale.ROOT, "%.3f", columnNs / 1e6 / Math.max(1, columnN)),
				String.format(java.util.Locale.ROOT, "%.3f", fastNs / 1e6 / n), worst);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20, environment = CasinoQa.ENV)
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
			// configurations {biome pre-check, candidates, prefit, height range (-1 = the structure's), fast probe}; same seeds.
			// -Dburmaldaholic.worldgenSurvey.sweep=true compares the knobs (R-CB M13 tuning).
			boolean sweep = Boolean.getBoolean("burmaldaholic.worldgenSurvey.sweep");
			Object[][] configs = sweep
				? new Object[][] {{true, 13, false, -1, true, 4}, {true, 9, false, -1, true, 4}, {true, 13, true, -1, true, 4}, {true, 13, false, -1, true, 8},
					{true, 13, false, 8, true, 4}, {true, 13, false, 10, true, 4}, {true, 13, false, 14, true, 4}, {true, 13, false, 16, true, 4}}
				: new Object[][] {{CasinoStructure.biomePrecheck, CasinoStructure.candidates, CasinoStructure.prefit, CasinoStructure.heightRangeOverride,
					dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.enabled, CasinoStructure.fineSpacing}};
			boolean pre0 = CasinoStructure.biomePrecheck;
			int cand0 = CasinoStructure.candidates;
			boolean prefit0 = CasinoStructure.prefit;
			int range0 = CasinoStructure.heightRangeOverride;
			boolean fast0 = dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.enabled;
			int fine0 = CasinoStructure.fineSpacing;
			boolean stats0 = CasinoStructure.STATS.enabled;
			CasinoStructure.STATS.enabled = true;
			try {
				for (Object[] cfg : configs) {
					CasinoStructure.biomePrecheck = (Boolean) cfg[0];
					CasinoStructure.candidates = (Integer) cfg[1];
					CasinoStructure.prefit = (Boolean) cfg[2];
					CasinoStructure.heightRangeOverride = (Integer) cfg[3];
					dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.enabled = (Boolean) cfg[4];
					CasinoStructure.fineSpacing = (Integer) cfg[5];
					CasinoStructure.clearCaches();
					List<String> summary = new ArrayList<>();
					for (long seed : SEEDS) {
						summary.add(casinos(reg, templates, seed));
					}
					Burmaldaholic.LOGGER.info("[survey] CASINO SUMMARY precheck={} candidates={} prefit={} range={} fastProbe={} fineSpacing={} (radius {}): {}", cfg[0], cfg[1],
						cfg[2], cfg[3], cfg[4], cfg[5], CASINO_RADIUS, summary);
				}
			} finally {
				CasinoStructure.biomePrecheck = pre0;
				CasinoStructure.candidates = cand0;
				CasinoStructure.prefit = prefit0;
				CasinoStructure.heightRangeOverride = range0;
				dev.nezo.burmaldaholic.worldgen.structure.TerrainProbe.enabled = fast0;
				CasinoStructure.fineSpacing = fine0;
				CasinoStructure.STATS.enabled = stats0;
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
				+ "| unrecognisedStarts={} | gen median {} ms p99 {} ms over {} attempts | stats {} | per biome OK/FLAT/FLOOD/INCOMPLETE/BIOME {} | {} ms",
			seed, CASINO_SPACING_RADIUS, cells, excluded, biomeValid, rejected, rejectPct, found.size(),
			String.format(java.util.Locale.ROOT, "%.2f", found.size() / areaKm2), meanSpacing, nn.isEmpty() ? -1 : nn.get(nn.size() / 2), fmt(perStructure),
			CASINO_RADIUS, inner, fromSpawn, unrecognised, String.format(java.util.Locale.ROOT, "%.2f", medianMs),
			String.format(java.util.Locale.ROOT, "%.2f", p99Ms), ns.length, CasinoStructure.STATS, CasinoStructure.STATS.perBiome(), ms);
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
			if (piece instanceof dev.nezo.burmaldaholic.worldgen.structure.CasinoGroundPiece) {
				continue; // carve / skirt
			}
			if (!(piece instanceof PoolElementStructurePiece p) || CasinoElements.layoutOf(p.getElement()).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	static Count run(RegistryAccess reg, StructureTemplateManager templates, long seed, boolean forced,
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
		int starts = 0;
		int casinos = 0;
		for (int[] c : perStructure.values()) {
			starts += c[0];
			casinos += c[1];
		}
		// nether_complexes also holds fortresses: count bastions only
		if (perStructure.containsKey("bastion_remnant")) {
			starts = perStructure.get("bastion_remnant")[0];
			casinos = perStructure.get("bastion_remnant")[1];
		} else if (setId.equals("minecraft:nether_complexes")) {
			starts = 0;
			casinos = 0;
		}
		return new Count(starts, casinos);
	}

	private static String fmt(Map<String, int[]> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append(" starts/").append(v[1]).append(" casinos, "));
		return sb.append('}').toString();
	}
}
