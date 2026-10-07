package dev.nezo.burmaldaholic.gametest.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Minecraft 26.2 side of the worldgen GameTests' version shims (26.3 reworked RandomState / climate samplers).
 * Selected by build.gradle: {@code src/gametestCompat/mc<version>}.
 */
final class SurveyCompat {
	private SurveyCompat() {}

	static RandomState randomState(NoiseGeneratorSettings settings, RegistryAccess reg, long seed) {
		return RandomState.create(settings, reg.lookupOrThrow(Registries.NOISE), seed);
	}

	static Climate.Sampler sampler(RandomState randomState) {
		return randomState.sampler();
	}

	/** Block x/z of the dimension origin (always 0, 0 in 26.2). */
	static int[] origin(ChunkGenerator generator, RandomState randomState) {
		return new int[] {0, 0};
	}

	/** Noise biome at quart coordinates. */
	static Holder<Biome> biome(ChunkGenerator generator, RandomState randomState, int qx, int qy, int qz) {
		return generator.getBiomeSource().getNoiseBiome(qx, qy, qz, randomState.sampler());
	}
}
