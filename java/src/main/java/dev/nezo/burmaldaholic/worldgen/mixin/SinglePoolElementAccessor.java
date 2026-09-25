package dev.nezo.burmaldaholic.worldgen.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the template location of a pool element (to recognise casino elements). */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
	@Accessor("template")
	Either<Identifier, StructureTemplate> burmaldaholic$template();
}
