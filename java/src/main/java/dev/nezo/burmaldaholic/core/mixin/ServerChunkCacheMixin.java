package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.table.TableLifecycle;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brackets the distance-manager update, during which vanilla fires {@code FULL_CHUNK_STATUS_CHANGE}: a busy table's
 * chunk-unload play-out waits until the update has returned (still before the chunk is saved), so its chunk access cannot
 * re-enter {@code DistanceManager.runAllUpdates} mid-iteration (vanilla NPE, T-HR round 2).
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Inject(method = "runDistanceManagerUpdates", at = @At("HEAD"))
	private void burmaldaholic$enterChunkUpdates(CallbackInfoReturnable<Boolean> cir) {
		TableLifecycle.enterChunkUpdates();
	}

	@Inject(method = "runDistanceManagerUpdates", at = @At("RETURN"))
	private void burmaldaholic$exitChunkUpdates(CallbackInfoReturnable<Boolean> cir) {
		TableLifecycle.exitChunkUpdates();
	}
}
