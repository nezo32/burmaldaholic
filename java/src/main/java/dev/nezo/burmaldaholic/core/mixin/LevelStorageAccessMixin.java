package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.mode.PendingCasinoMode;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@link PendingCasinoMode} on the storage access of one world directory. Volatile: the client
 * thread writes the value (Create World), the integrated server thread reads it (SERVER_STARTING).
 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
abstract class LevelStorageAccessMixin implements PendingCasinoMode {
	@Unique
	private volatile @Nullable Boolean burmaldaholic$pendingCasinoMode;

	@Override
	public void burmaldaholic$setPendingCasinoMode(boolean enabled) {
		burmaldaholic$pendingCasinoMode = enabled;
	}

	@Override
	public @Nullable Boolean burmaldaholic$takePendingCasinoMode() {
		Boolean value = burmaldaholic$pendingCasinoMode;
		burmaldaholic$pendingCasinoMode = null;
		return value;
	}
}
