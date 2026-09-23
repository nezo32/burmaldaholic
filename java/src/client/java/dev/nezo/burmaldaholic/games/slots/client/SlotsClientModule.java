package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Client half of the "slots" module: the machine screen and item tooltips. */
public final class SlotsClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "slots";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		for (TableType<SlotMachineBlockEntity> type : SlotsModule.MACHINES.values()) {
			ctx.tableScreen(type, SlotMachineScreen::new);
		}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			for (Tier tier : Tier.values()) {
				TableType<SlotMachineBlockEntity> type = SlotsModule.MACHINES.get(tier);
				if (type != null && stack.is(type.item())) {
					lines.add(Component.translatable("tooltip.burmaldaholic." + tier.blockName()).withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}
}
