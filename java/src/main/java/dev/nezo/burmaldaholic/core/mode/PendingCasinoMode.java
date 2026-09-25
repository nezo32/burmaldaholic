package dev.nezo.burmaldaholic.core.mode;

import org.jspecify.annotations.Nullable;

/**
 * Duck interface on {@code LevelStorageSource.LevelStorageAccess} ({@code core.mixin.LevelStorageAccessMixin}).
 * The Create World screen stores the "Casino Mode" button value on the storage access of the world it
 * just created; the integrated server built with that same object consumes it on SERVER_STARTING.
 */
public interface PendingCasinoMode {
	void burmaldaholic$setPendingCasinoMode(boolean enabled);

	/** Returns and clears the pending value; null if none (existing world, dedicated server). */
	@Nullable Boolean burmaldaholic$takePendingCasinoMode();
}
