package dev.nezo.burmaldaholic.vip.mixin;

import dev.nezo.burmaldaholic.vip.Contracts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Contract sources from vanilla statistics: fish caught, villager trades (§3.4.4). */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "awardStat", at = @At("HEAD"))
	private void burmaldaholic$vipContractStat(Stat<?> stat, int amount, CallbackInfo ci) {
		Contracts.onStat((ServerPlayer) (Object) this, stat, amount);
	}
}
