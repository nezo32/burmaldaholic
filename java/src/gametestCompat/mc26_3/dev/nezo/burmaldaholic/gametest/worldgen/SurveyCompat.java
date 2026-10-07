package dev.nezo.burmaldaholic.gametest.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Minecraft 26.3 side of the worldgen GameTests' version shims: RandomState.create took a new parameter order and
 * climate samplers are created per use (like vanilla's ChunkGenerator does). Selected by build.gradle:
 * {@code src/gametestCompat/mc<version>}.
 */
final class SurveyCompat {
	private SurveyCompat() {}

	static RandomState randomState(NoiseGeneratorSettings settings, HolderGetter<NormalNoise> noises, long seed) {
		return RandomState.create(noises, seed, settings);
	}

	static Climate.Sampler sampler(RandomState randomState) {
		return randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
	}

	/** Block x/z of the dimension origin (26.3 can move it away from 0, 0). */
	static int[] origin(ChunkGenerator generator, RandomState randomState) {
		if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise) {
			var c = noise.getOrigin(randomState);
			return new int[] {c.getMiddleBlockX(), c.getMiddleBlockZ()};
		}
		return new int[] {0, 0};
	}

	/** Noise biome at quart coordinates. */
	static Holder<Biome> biome(ChunkGenerator generator, RandomState randomState, int qx, int qy, int qz) {
		return generator.getBiomeSource().createUncachedResolver(randomState).getNoiseBiome(qx, qy, qz);
	}
}
