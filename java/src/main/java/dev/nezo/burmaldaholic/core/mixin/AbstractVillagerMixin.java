package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.earnings.Earnings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villager and wandering-trader trades pay chips (§3.4.3). */
@Mixin(AbstractVillager.class)
abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void burmaldaholic$tradeReward(MerchantOffer offer, CallbackInfo ci) {
		if (((AbstractVillager) (Object) this).getTradingPlayer() instanceof ServerPlayer player) {
			Earnings.onTrade(player, offer);
		}
	}
}
