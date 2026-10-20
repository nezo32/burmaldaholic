package dev.nezo.burmaldaholic.core.mixin.client;

import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A FRAMED v2 table screen (ui-v2.md §3.7) shows the world dimmed around its frame; the vanilla HUD (hotbar, hearts, chat
 * cut at the frame edge) is not drawn under it while it is open (review T-K2 P2-b). Unframed v2 screens cover the whole
 * GUI anyway; every other screen is untouched.
 */
@Mixin(Hud.class)
public abstract class HudV2Mixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void burmaldaholic$noHudAroundAFrame(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		Screen s = Minecraft.getInstance().gui.screen();
		if (s instanceof V2Chrome.Holder && s instanceof FitScaled f && (f.offsetX() != 0f || f.offsetY() != 0f)) ci.cancel();
	}
}
