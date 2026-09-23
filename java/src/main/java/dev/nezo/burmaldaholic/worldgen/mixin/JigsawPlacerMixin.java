package dev.nezo.burmaldaholic.worldgen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.nezo.burmaldaholic.worldgen.VillageCasinos;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Adds the village casino to vanilla village street-end pools at draw time (see {@link VillageCasinos}). */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin {
	@Shadow
	@Final
	private Registry<StructureTemplatePool> pools;

	@Shadow
	@Final
	private List<? super PoolElementStructurePiece> pieces;

	@WrapOperation(method = "tryPlacingChildren", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
	private List<StructurePoolElement> burmaldaholic$villageCasino(StructureTemplatePool pool, RandomSource random,
			Operation<List<StructurePoolElement>> original) {
		List<StructurePoolElement> shuffled = original.call(pool, random);
		return VillageCasinos.inject(this.pools, this.pieces, pool, shuffled, random);
	}
}
