package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.table.TableLifecycle;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plays out tables of unloading chunks once the distance manager has finished its update (INTEGRATION-1).
 * {@code FULL_CHUNK_STATUS_CHANGE} fires while {@code DistanceManager.runAllUpdates} iterates
 * {@code chunksToUpdateFutures}; touching the level there (any chunk lookup that is not "now") re-enters
 * {@code runAllUpdates}, which clears that set under the outer iterator: the vanilla NPE in
 * {@code ReferenceOpenHashSet$SetIterator.next} ← {@code DistanceManager.runAllUpdates:88}.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Inject(method = "runDistanceManagerUpdates", at = @At("RETURN"))
	private void burmaldaholic$playOutUnloadedTables(CallbackInfoReturnable<Boolean> cir) {
		TableLifecycle.drainUnloading();
	}
}
