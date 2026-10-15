package dev.nezo.burmaldaholic.worldgen.mixin;

import dev.nezo.burmaldaholic.worldgen.WorldgenRuntime;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoStructure;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /locate}, explorer maps and {@code /casino locate} ask {@code StructureCheck} whether a start may exist in a
 * chunk; it remembers "not present" for the level's lifetime. While the standalone casino is gated off (casino mode,
 * {@code worldgen.enabled}, {@code worldgen.standalone.enabled}) every casino chunk would be remembered as empty and
 * stay so after the gate opens (R-CB minor "/locate caches none"). The answer while gated is therefore given here,
 * without consulting or filling the cache. Same signature in 26.2 and 26.3.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
	@Inject(method = "checkStart", at = @At("HEAD"), cancellable = true)
	private void burmaldaholic$gatedCasinoNotCached(ChunkPos pos, Structure structure, StructurePlacement placement, boolean createReference,
			CallbackInfoReturnable<StructureCheckResult> cir) {
		if (structure instanceof CasinoStructure && !WorldgenRuntime.standaloneActive()) {
			cir.setReturnValue(StructureCheckResult.START_NOT_PRESENT);
		}
	}
}
