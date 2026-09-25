package dev.nezo.burmaldaholic.core.mixin.client;

import com.mojang.blaze3d.platform.Window;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The compact layout of the casino game screens ({@link FitScaled}): while such a screen works in a larger GUI than the
 * window's (it is drawn at {@code k / guiScale}), the GUI mouse position the screen receives — clicks, releases, drags,
 * scrolls, moves and the hover position of the render pass all read it — is divided by the same factor.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerFitMixin {
	@Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"), cancellable = true)
	private void burmaldaholic$fitX(Window window, CallbackInfoReturnable<Double> cir) {
		float f = FitScaled.current();
		if (f < 1f) cir.setReturnValue(cir.getReturnValueD() / f);
	}

	@Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"), cancellable = true)
	private void burmaldaholic$fitY(Window window, CallbackInfoReturnable<Double> cir) {
		float f = FitScaled.current();
		if (f < 1f) cir.setReturnValue(cir.getReturnValueD() / f);
	}
}
