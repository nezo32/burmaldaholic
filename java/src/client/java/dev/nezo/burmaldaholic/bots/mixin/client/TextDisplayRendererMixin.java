package dev.nezo.burmaldaholic.bots.mixin.client;

import dev.nezo.burmaldaholic.bots.client.BotTagVisibility;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides a bot's {@code [BOT]} nameplate while it sits at a table whose in-world board shows it on a seat plate
 * (docs/architecture/ui-v2.md §7.4; {@link BotTagVisibility} decides). Replaces the former global TEXT_DISPLAY renderer
 * override: the vanilla renderer (and any other mod's mixins on it) stays in place, every other text display is untouched.
 * A hidden plate keeps no text sub-state, so the vanilla {@code submitInner} draws nothing. The target signature is the
 * same on 26.2 and 26.3 (checked with javap on both client jars).
 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
		at = @At("TAIL"))
	private void burmaldaholic$hideBotTagAtBoard(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.textRenderState != null && BotTagVisibility.hidden(entity)) state.textRenderState = null;
	}
}
