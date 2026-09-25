package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Client half of the "craps" module: table screen and item tooltip. */
public final class CrapsClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return CrapsModule.ID;
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(CrapsModule.TABLE, CrapsScreen::new);
		CrapsTableRenderer.register(); // in-world dice and puck (tables.md §2.6)
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(CrapsModule.TABLE.item())) {
				lines.add(Component.translatable("tooltip.burmaldaholic.craps_table").withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
