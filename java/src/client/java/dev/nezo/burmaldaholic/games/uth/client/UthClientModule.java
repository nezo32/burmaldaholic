package dev.nezo.burmaldaholic.games.uth.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;

/** Client half of the "uth" module: the table screens and the table items' tooltips. */
public final class UthClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return UthModule.ID;
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(UthModule.TABLE, UthScreen::new);
		ctx.tableScreen(UthModule.HIGH_ROLLER_TABLE, UthScreen::new);
		ctx.tableScreen(UthModule.PLAYER_BANKED_TABLE, UthScreen::new);
		EntityRenderers.register(UthModule.DEALER, UthDealerRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (UthModule.TABLE == null) {
				return;
			}
			if (stack.is(UthModule.TABLE.item()) || stack.is(UthModule.HIGH_ROLLER_TABLE.item())) {
				lines.add(Component.translatable("tooltip.burmaldaholic.uth_table").withStyle(ChatFormatting.GRAY));
			} else if (stack.is(UthModule.PLAYER_BANKED_TABLE.item())) {
				Component pct = Texts.decimal(trimPercent(CasinoConfig.uth().pvp.rakePercent * 100));
				lines.add(Component.translatable("tooltip.burmaldaholic.uth_table_player_banked", pct).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** 1.0 → "1", 1.5 → "1.5" (digits only). */
	static String trimPercent(double v) {
		String s = String.format(java.util.Locale.ROOT, "%.2f", v);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}
}
