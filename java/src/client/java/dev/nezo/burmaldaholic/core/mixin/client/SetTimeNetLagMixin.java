package dev.nezo.burmaldaholic.core.mixin.client;

import dev.nezo.burmaldaholic.client.anim.NetLagHook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SY-SPEC §5: lets the latency-injection GameTest hook ({@link NetLagHook}, null in production) delay the server's time
 * packets like table state packets. Without a hook this is a single null check.
 */
@Mixin(ClientPacketListener.class)
abstract class SetTimeNetLagMixin {
	@Inject(method = "handleSetTime", at = @At("HEAD"), cancellable = true)
	private void burmaldaholic$netLag(ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (NetLagHook.hook == null) return;
		ClientPacketListener self = (ClientPacketListener) (Object) this;
		if (NetLagHook.intercept(() -> self.handleSetTime(packet))) ci.cancel();
	}
}
