package dev.nezo.burmaldaholic.multiplayer.mixin;

import dev.nezo.burmaldaholic.multiplayer.Ownership;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** §18.2: explosions don't destroy charters and linked tables of live casinos ({@code ownership.explosionProof}). */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void burmaldaholic$protectCasinos(CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> positions = cir.getReturnValue();
		if (positions == null || positions.isEmpty()) {
			return;
		}
		List<BlockPos> kept = null;
		for (int i = 0; i < positions.size(); i++) {
			BlockPos pos = positions.get(i);
			boolean prot = Ownership.isExplosionProof(level, pos);
			if (prot && kept == null) {
				kept = new ArrayList<>(positions.subList(0, i));
			} else if (!prot && kept != null) {
				kept.add(pos);
			}
		}
		if (kept != null) {
			cir.setReturnValue(kept);
		}
	}
}
