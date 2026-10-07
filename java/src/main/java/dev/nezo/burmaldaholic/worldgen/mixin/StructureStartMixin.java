package dev.nezo.burmaldaholic.worldgen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.nezo.burmaldaholic.worldgen.CasinoPlacement;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/**
 * One casino per structure start (D6, {@link CasinoPlacement}): sets the start's casino context around
 * {@code placeInChunk}, so the data markers of every casino piece placed in this call share one casino id and the
 * union box. Same signature in 26.2 and 26.3.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
	@WrapMethod(method = "placeInChunk")
	private void burmaldaholic$casinoContext(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, Operation<Void> original) {
		StructureStart self = (StructureStart) (Object) this;
		CasinoPlacement.Context ctx = self.isValid()
			? CasinoPlacement.of(level.getLevel().dimension().identifier().toString(), self.getPieces())
			: null;
		CasinoPlacement.with(ctx, () -> original.call(level, structures, generator, random, chunkBB, chunkPos));
	}
}
