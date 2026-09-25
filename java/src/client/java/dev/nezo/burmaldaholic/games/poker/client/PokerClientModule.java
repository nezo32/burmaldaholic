package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Client half of the "poker" module: the table screen and the table item's tooltip. */
public final class PokerClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return PokerModule.ID;
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(PokerModule.TABLE, PokerScreen::new);
		PokerTableRenderer.register(); // J-C10: the hand on the table top for spectators
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (PokerModule.TABLE != null && stack.is(PokerModule.TABLE.item())) {
				lines.add(Component.translatable("tooltip.burmaldaholic.poker_table").withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
