package dev.nezo.burmaldaholic.worldgen.mixin;

import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Template name of an End City piece ({@code tower_top}, {@code fat_tower_top}, ...). */
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceAccessor {
	@Accessor("templateName")
	String burmaldaholic$templateName();
}
