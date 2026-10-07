package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.CasinoElements;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import java.util.ArrayList;
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

	@GameTest(maxTicks = 20)
	public void survey(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		var server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = server.getStructureManager();
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

	private static void run(RegistryAccess reg, StructureTemplateManager templates, long seed, boolean forced,
			ResourceKey<LevelStem> stemKey, ResourceKey<Level> levelKey, String setId, int radius) {
		LevelStem stem = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
			.createWorldDimensions().dimensions().get(stemKey);
		ChunkGenerator gen = stem.generator();
		NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) gen;
		RandomState randomState = RandomState.create(noise.generatorSettings().value(), reg.lookupOrThrow(Registries.NOISE), seed);
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
					StructureStart start = holder.value().generate(holder, levelKey, reg, gen, gen.getBiomeSource(), randomState, templates,
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
	}

	private static String fmt(Map<String, int[]> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append(" starts/").append(v[1]).append(" casinos, "));
		return sb.append('}').toString();
	}
}
