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
import org.spongepowered.asm.mixin.Unique;

/**
 * One casino per structure start (D6, {@link CasinoPlacement}): sets the start's casino context around
 * {@code placeInChunk}, so the data markers of every casino piece placed in this call share one casino id and the
 * union box. Same signature in 26.2 and 26.3.
 *
 * <p>R-CB M6e: {@code placeInChunk} runs once per chunk the start touches (up to ~25 for a casino, and for every
 * vanilla structure start); the context is computed once per start object and memoised — every chunk computes the
 * same value from the same pieces, so a race only computes it twice.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
	@Unique
	private static final Object burmaldaholic$NONE = new Object();

	/** The memoised context, {@link #burmaldaholic$NONE} for "no casino piece", null before the first call. */
	@Unique
	private volatile Object burmaldaholic$casino;

	@WrapMethod(method = "placeInChunk")
	private void burmaldaholic$casinoContext(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, Operation<Void> original) {
		StructureStart self = (StructureStart) (Object) this;
		CasinoPlacement.Context ctx = null;
		if (self.isValid()) {
			Object memo = burmaldaholic$casino;
			if (memo == null) {
				ctx = CasinoPlacement.of(level.getLevel().dimension().identifier().toString(), self.getPieces());
				burmaldaholic$casino = ctx == null ? burmaldaholic$NONE : ctx;
			} else if (memo != burmaldaholic$NONE) {
				ctx = (CasinoPlacement.Context) memo;
			}
		}
		CasinoPlacement.Context current = ctx;
		CasinoPlacement.with(current, () -> original.call(level, structures, generator, random, chunkBB, chunkPos));
	}
}
