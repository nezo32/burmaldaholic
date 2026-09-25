package dev.nezo.burmaldaholic.vip.mixin;

import dev.nezo.burmaldaholic.vip.VipCosmetics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** VIP name cosmetics on the server: tier-colored name (Silver+) and chat title (Platinum+), §12. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void burmaldaholic$vipName(CallbackInfoReturnable<Component> cir) {
		Component decorated = VipCosmetics.decorateName((Player) (Object) this, cir.getReturnValue());
		if (decorated != null) {
			cir.setReturnValue(decorated);
		}
	}
}
