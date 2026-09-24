package dev.nezo.burmaldaholic.worldgen.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The server's structure template manager. Its getter was renamed in 26.3
 * ({@code getStructureManager} → {@code getStructureTemplateManager}); the field name is the same in
 * both versions.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("structureTemplateManager")
	StructureTemplateManager burmaldaholic$structureTemplateManager();
}
