package dev.nezo.burmaldaholic.worldgen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.nezo.burmaldaholic.worldgen.CasinoStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Appends casino rooms to bastions / End Cities once their pieces are built (see
 * {@link CasinoStructures}). Arguments are captured by type because 26.3 added a parameter to
 * {@code Structure.generate}.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
	@WrapOperation(method = "generate", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationStub;getPiecesBuilder()Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;"))
	private StructurePiecesBuilder burmaldaholic$appendCasino(Structure.GenerationStub stub, Operation<StructurePiecesBuilder> original,
			@Local(argsOnly = true) Holder<Structure> selected, @Local(argsOnly = true) RegistryAccess registries,
			@Local(argsOnly = true) StructureTemplateManager templates, @Local(argsOnly = true) long seed,
			@Local(argsOnly = true) ChunkPos chunk) {
		StructurePiecesBuilder builder = original.call(stub);
		CasinoStructures.append(selected, registries, templates, seed, chunk, builder);
		return builder;
	}
}
