package dev.nezo.burmaldaholic.core.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server's world storage access, which carries the pending Create World casino mode choice. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerStorageAccessor {
	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess burmaldaholic$storageSource();
}
