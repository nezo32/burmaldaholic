package dev.nezo.burmaldaholic.multiplayer.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.multiplayer.MultiplayerModule;
import dev.nezo.burmaldaholic.multiplayer.net.CharterStatePayload;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client half of the "multiplayer" module: Casino Charter screen and the charter tooltip. */
public final class MultiplayerClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "multiplayer";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ClientPlayNetworking.registerGlobalReceiver(CharterStatePayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof CharterScreen screen) {
				screen.accept(payload);
			} else if (payload.open()) {
				mc.gui.setScreen(new CharterScreen(payload));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
			if (MultiplayerModule.CHARTER_ITEM != null && stack.is(MultiplayerModule.CHARTER_ITEM)) {
				lines.add(Component.translatable("tooltip.burmaldaholic.casino_charter", Texts.chips(CasinoConfig.ownership().licenseFee))
					.withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
