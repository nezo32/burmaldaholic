package dev.nezo.burmaldaholic.core.mixin.client;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla toasts (advancements, recipes) wait while a v2 table screen is open (review DES 6: they covered the balance and
 * the layout at the moment of the result). Neither drawn nor updated: the queue is kept and a visible toast's time does
 * not run; they show when the screen closes. The kit shows its own notices on the felt ({@code V2Toast}).
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerV2Mixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void burmaldaholic$waitDuringV2(CallbackInfo ci) {
		if (minecraft.gui.screen() instanceof V2Chrome.Holder) ci.cancel();
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void burmaldaholic$hideDuringV2(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (minecraft.gui.screen() instanceof V2Chrome.Holder) ci.cancel();
	}
}
