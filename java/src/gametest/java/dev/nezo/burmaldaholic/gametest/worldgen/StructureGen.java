package dev.nezo.burmaldaholic.gametest.worldgen;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code Structure.generate} for 26.2 AND 26.3: 26.3 added a {@code Climate.Sampler} parameter, so the GameTests
 * call it reflectively, filling every parameter by its type (same values the vanilla callers pass).
 */
final class StructureGen {
	private static final Method GENERATE;

	static {
		Method found = null;
		for (Method m : Structure.class.getMethods()) {
			if (m.getName().equals("generate") && m.getReturnType() == StructureStart.class) {
				found = m;
			}
		}
		if (found == null) {
			throw new IllegalStateException("Structure.generate not found");
		}
		GENERATE = found;
	}

	private StructureGen() {}

	static StructureStart generate(Holder<Structure> holder, ResourceKey<Level> dimension, RegistryAccess registries, ChunkGenerator generator,
			RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunk, LevelHeightAccessor height,
			Predicate<Holder<Biome>> validBiome) {
		Class<?>[] types = GENERATE.getParameterTypes();
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			Class<?> t = types[i];
			if (t == Holder.class) {
				args[i] = holder;
			} else if (t == ResourceKey.class) {
				args[i] = dimension;
			} else if (t == RegistryAccess.class) {
				args[i] = registries;
			} else if (t == ChunkGenerator.class) {
				args[i] = generator;
			} else if (t == BiomeSource.class) {
				args[i] = generator.getBiomeSource();
			} else if (t == Climate.Sampler.class) {
				args[i] = randomState.sampler();
			} else if (t == RandomState.class) {
				args[i] = randomState;
			} else if (t == StructureTemplateManager.class) {
				args[i] = templates;
			} else if (t == long.class) {
				args[i] = seed;
			} else if (t == ChunkPos.class) {
				args[i] = chunk;
			} else if (t == int.class) {
				args[i] = 0; // references
			} else if (t == LevelHeightAccessor.class) {
				args[i] = height;
			} else if (t == Predicate.class) {
				args[i] = validBiome;
			} else {
				throw new IllegalStateException("Structure.generate: unexpected parameter " + t);
			}
		}
		try {
			return (StructureStart) GENERATE.invoke(holder.value(), args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
