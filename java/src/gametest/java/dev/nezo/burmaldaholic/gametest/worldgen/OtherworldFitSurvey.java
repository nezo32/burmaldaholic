package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Gates;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.mixin.TemplateStructurePieceAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Fit survey for the Nether / End casino annexes (lane CB-4, architecture doc §4.3 / §4.4): generates the real
 * bastion and End City structure starts of the default preset for the survey seeds (our own annexes switched
 * off) and replays {@code CasinoStructures.appendParlor / appendLounge} placement geometry — beside the host /
 * on the highest tower top, chunk reach ≤ 7, no collision with a host piece — for candidate footprints.
 * Off unless {@code -Dburmaldaholic.worldgenSurvey=true}; results are logged with the prefix {@code [fit]}.
 */
public class OtherworldFitSurvey {
	private static final long[] SEEDS = {12345L, 42L, -4172144997902289642L, 8675309L, 1L, 777L};
	private static final Set<String> TOWER_TOPS = Set.of("tower_top", "fat_tower_top");
	private static final int MAX_CHUNK_REACH = 7;
	private static final Vec[] PARLOR_SIZES = {new Vec(21, 12, 21), new Vec(33, 16, 33)};
	private static final Vec[] LOUNGE_SIZES = {new Vec(15, 9, 15), new Vec(23, 14, 23), new Vec(25, 14, 25)};

	@GameTest(maxTicks = 20)
	public void fit(GameTestHelper helper) {
		if (!Boolean.getBoolean("burmaldaholic.worldgenSurvey")) {
			helper.succeed();
			return;
		}
		var server = helper.getLevel().getServer();
		RegistryAccess reg = server.registryAccess();
		StructureTemplateManager templates = server.getStructureManager();
		var cfg = CasinoConfig.worldgen();
		double p0 = cfg.piglinParlor.chance;
		double h0 = cfg.highRoller.chance;
		int[] parlorFit = new int[PARLOR_SIZES.length];
		int[] loungeFit = new int[LOUNGE_SIZES.length];
		int bastions = 0;
		int cities = 0;
		try {
			cfg.piglinParlor.chance = 0;
			cfg.highRoller.chance = 0;
			for (long seed : SEEDS) {
				for (StructureStart s : starts(reg, templates, seed, LevelStem.NETHER, Level.NETHER, "minecraft:nether_complexes", 1500,
						"minecraft:bastion_remnant")) {
					bastions++;
					for (int i = 0; i < PARLOR_SIZES.length; i++) {
						if (parlorFits(seed, s, PARLOR_SIZES[i])) {
							parlorFit[i]++;
						}
					}
				}
				for (StructureStart s : starts(reg, templates, seed, LevelStem.END, Level.END, "minecraft:end_cities", 3000,
						"minecraft:end_city")) {
					cities++;
					for (int i = 0; i < LOUNGE_SIZES.length; i++) {
						if (loungeFits(seed, s, LOUNGE_SIZES[i])) {
							loungeFit[i]++;
						}
					}
				}
			}
		} finally {
			cfg.piglinParlor.chance = p0;
			cfg.highRoller.chance = h0;
		}
		for (int i = 0; i < PARLOR_SIZES.length; i++) {
			Burmaldaholic.LOGGER.info("[fit] parlor {} fits {}/{} bastions", PARLOR_SIZES[i], parlorFit[i], bastions);
		}
		for (int i = 0; i < LOUNGE_SIZES.length; i++) {
			Burmaldaholic.LOGGER.info("[fit] lounge {} fits {}/{} end cities", LOUNGE_SIZES[i], loungeFit[i], cities);
		}
		helper.succeed();
	}

	private static boolean parlorFits(long seed, StructureStart s, Vec size) {
		ChunkPos chunk = s.getChunkPos();
		Geometry.Box host = box(s.getBoundingBox());
		int first = (int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_PARLOR ^ 0x51DE) * 4);
		for (int i = 0; i < 4; i++) {
			Facing side = Facing.values()[(first + i) % 4];
			if (fits(Geometry.beside(host, size, side, 0), chunk, s.getPieces())) {
				return true;
			}
		}
		return false;
	}

	private static boolean loungeFits(long seed, StructureStart s, Vec size) {
		ChunkPos chunk = s.getChunkPos();
		List<StructurePiece> tops = new ArrayList<>();
		for (StructurePiece piece : s.getPieces()) {
			if (piece instanceof TemplateStructurePiece t
				&& TOWER_TOPS.contains(((TemplateStructurePieceAccessor) t).burmaldaholic$templateName())) {
				tops.add(piece);
			}
		}
		tops.sort(Comparator.comparingInt((StructurePiece p) -> p.getBoundingBox().maxY()).reversed());
		Facing facing = Facing.values()[(int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_LOUNGE ^ 0x51DE) * 4)];
		for (StructurePiece top : tops) {
			if (fits(Geometry.onTop(box(top.getBoundingBox()), size, facing), chunk, s.getPieces())) {
				return true;
			}
		}
		return false;
	}

	private static boolean fits(Geometry.Placement placement, ChunkPos chunk, List<StructurePiece> pieces) {
		if (Geometry.chunkReach(placement.box(), chunk.x(), chunk.z()) > MAX_CHUNK_REACH) {
			return false;
		}
		Geometry.Box b = placement.box();
		BoundingBox bb = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
		for (StructurePiece p : pieces) {
			if (p.getBoundingBox().intersects(bb)) {
				return false;
			}
		}
		return true;
	}

	private static Geometry.Box box(BoundingBox bb) {
		return new Geometry.Box(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
	}

	private static List<StructureStart> starts(RegistryAccess reg, StructureTemplateManager templates, long seed,
			ResourceKey<LevelStem> stemKey, ResourceKey<Level> levelKey, String setId, int radius, String structureId) {
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
					String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
					if (!id.equals(structureId)) {
						continue;
					}
					StructureStart start = holder.value().generate(holder, levelKey, reg, gen, gen.getBiomeSource(), randomState, templates,
						seed, chunk, 0, height, holder.value().biomes()::contains);
					if (start.isValid()) {
						out.add(start);
					}
				}
			}
		}
		return out;
	}
}
