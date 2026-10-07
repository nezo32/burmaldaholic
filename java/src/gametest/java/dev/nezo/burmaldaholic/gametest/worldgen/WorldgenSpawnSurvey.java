package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
 * Spawn-rate survey (diagnostic, docs/design/CASINO_BUILDINGS.md §1): runs the REAL structure-start
 * generation of the default ("normal") world preset for a few seeds — vanilla placement grid, biome
 * checks, jigsaw assembly and our mixins — without generating any chunk, and logs how many villages /
 * bastions / End Cities and how many casinos fall within a radius. Off unless
 * {@code -Dburmaldaholic.worldgenSurvey=true} (e.g. {@code JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dburmaldaholic.worldgenSurvey=true"
 * ./gradlew runGameTest}); results are logged with the prefix {@code [survey]}.
 */
public class WorldgenSpawnSurvey {
	private static final long[] SEEDS = {12345L, 42L, -4172144997902289642L, 8675309L};

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
	@GameTest(maxTicks = 20)
	public void casinosGenerateWithDefaultChances(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		helper.assertTrue(dev.nezo.burmaldaholic.worldgen.WorldgenRuntime.active(), "worldgen inactive: casino mode/worldgen off");
		var cfg = CasinoConfig.worldgen();
		helper.assertTrue(cfg.villageCasino.chance == 0.5 && cfg.piglinParlor.chance == 0.6 && cfg.highRoller.chance == 0.5,
			"attach chances are not the v0.1.2 defaults: " + cfg.villageCasino.chance + "/" + cfg.piglinParlor.chance + "/" + cfg.highRoller.chance);
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = templates(server);
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
		helper.assertTrue(end.casinos() >= 0.35 * end.starts(), "End City lounge rate below 0.35: " + end);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void survey(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		var server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = templates(server);
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

	private static Count run(RegistryAccess reg, StructureTemplateManager templates, long seed, boolean forced,
			ResourceKey<LevelStem> stemKey, ResourceKey<Level> levelKey, String setId, int radius) {
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(stemKey);
		ChunkGenerator gen = stem.generator();
		NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) gen;
		RandomState randomState = randomState(noise.generatorSettings().value(), reg.lookupOrThrow(Registries.NOISE), seed);
		HolderLookup<StructureSet> sets = reg.lookupOrThrow(Registries.STRUCTURE_SET);
		ChunkGeneratorStructureState state = gen.createState(sets, randomState, seed);
		DimensionType type = stem.type().value();
		LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
		StructureSet set = sets.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(setId))).value();
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
				if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
					continue;
				}
				candidates++;
				// Same weighted selection as ChunkGenerator.createStructures.
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
					StructureStart start = generate(holder, levelKey, reg, gen, randomState, templates,
						seed, chunk, 0, height, holder.value().biomes()::contains);
					if (start.isValid()) {
						String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
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
						break;
					}
					options.remove(index);
					total -= sel.weight();
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

	// ---- 26.2 / 26.3 differences (one source set): resolved by reflection, dev (Mojang-named) runtime only ----

	private static StructureTemplateManager templates(net.minecraft.server.MinecraftServer server) {
		for (java.lang.reflect.Method m : net.minecraft.server.MinecraftServer.class.getMethods()) {
			if (m.getParameterCount() == 0 && m.getReturnType() == StructureTemplateManager.class) {
				return (StructureTemplateManager) invoke(m, server);
			}
		}
		throw new IllegalStateException("no StructureTemplateManager getter on MinecraftServer");
	}

	/** 26.2: create(settings, noises, seed); 26.3: create(noises, seed, settings). */
	private static RandomState randomState(net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
			Object noises, long seed) {
		for (java.lang.reflect.Method m : RandomState.class.getMethods()) {
			if (!m.getName().equals("create") || m.getParameterCount() != 3 || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
				continue;
			}
			Object[] args = new Object[3];
			Class<?>[] types = m.getParameterTypes();
			for (int i = 0; i < 3; i++) {
				args[i] = types[i] == long.class ? seed
					: types[i].isInstance(settings) ? settings
					: noises;
			}
			if (Arrays.stream(types).anyMatch(t -> t == long.class) && Arrays.stream(types).anyMatch(t -> t.isInstance(settings))) {
				return (RandomState) invoke(m, null, args);
			}
		}
		throw new IllegalStateException("no RandomState.create(settings, noises, seed)");
	}

	/** {@code Structure.generate}; 26.3 adds a {@code Climate.Sampler} parameter (built like ChunkGenerator does). */
	private static StructureStart generate(Holder<net.minecraft.world.level.levelgen.structure.Structure> holder, ResourceKey<Level> levelKey,
			RegistryAccess reg, ChunkGenerator gen, RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunk,
			int references, LevelHeightAccessor height, java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> biomes) {
		for (java.lang.reflect.Method m : net.minecraft.world.level.levelgen.structure.Structure.class.getMethods()) {
			if (!m.getName().equals("generate") || m.getReturnType() != StructureStart.class) {
				continue;
			}
			Class<?>[] types = m.getParameterTypes();
			Object[] args = new Object[types.length];
			for (int i = 0; i < types.length; i++) {
				Class<?> t = types[i];
				args[i] = t == Holder.class ? holder
					: t == ResourceKey.class ? levelKey
					: t == RegistryAccess.class ? reg
					: t == ChunkGenerator.class ? gen
					: t == net.minecraft.world.level.biome.BiomeSource.class ? gen.getBiomeSource()
					: t == RandomState.class ? randomState
					: t == StructureTemplateManager.class ? templates
					: t == long.class ? seed
					: t == ChunkPos.class ? chunk
					: t == int.class ? references
					: t == LevelHeightAccessor.class ? height
					: t == java.util.function.Predicate.class ? biomes
					: t.getSimpleName().equals("Sampler") ? climateSampler(randomState)
					: null;
				if (args[i] == null) {
					throw new IllegalStateException("Structure.generate: unknown parameter " + t);
				}
			}
			return (StructureStart) invoke(m, holder.value(), args);
		}
		throw new IllegalStateException("no Structure.generate");
	}

	/** 26.3: {@code randomState.createClimateSampler(SamplerContext.builder().enableCaches().build())}; 26.2: {@code sampler()}. */
	private static Object climateSampler(RandomState randomState) {
		for (java.lang.reflect.Method m : RandomState.class.getMethods()) {
			if (m.getName().equals("sampler") && m.getParameterCount() == 0) {
				return invoke(m, randomState);
			}
			if (m.getName().equals("createClimateSampler") && m.getParameterCount() == 1) {
				try {
					Class<?> ctx = m.getParameterTypes()[0];
					Object builder = ctx.getMethod("builder").invoke(null);
					builder = builder.getClass().getMethod("enableCaches").invoke(builder);
					Object context = builder.getClass().getMethod("build").invoke(builder);
					return invoke(m, randomState, context);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		throw new IllegalStateException("no climate sampler on RandomState");
	}

	private static Object invoke(java.lang.reflect.Method m, Object target, Object... args) {
		try {
			return m.invoke(target, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(m.toString(), e);
		}
	}

	private static String fmt(Map<String, int[]> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append(" starts/").append(v[1]).append(" casinos, "));
		return sb.append('}').toString();
	}
}
