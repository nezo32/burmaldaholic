package dev.nezo.burmaldaholic.worldgen.mixin;

import dev.nezo.burmaldaholic.worldgen.CasinoMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the casino data markers after a casino template was placed in a chunk. Vanilla's own
 * {@code handleDataMarker} path never sees them: the element's settings drop structure blocks
 * before markers are collected.
 */
@Mixin(SinglePoolElement.class)
public abstract class SinglePoolElementMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void burmaldaholic$casinoMarkers(StructureTemplateManager templates, WorldGenLevel level, StructureManager structures,
			ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBB,
			RandomSource random, LiquidSettings liquidSettings, boolean keepJigsaws, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			CasinoMarkers.afterPlace((SinglePoolElement) (Object) this, templates, level, position, rotation, chunkBB, random);
		}
	}
}
