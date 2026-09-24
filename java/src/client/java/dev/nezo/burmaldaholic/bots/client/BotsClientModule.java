package dev.nezo.burmaldaholic.bots.client;

import dev.nezo.burmaldaholic.bots.net.BotsActionPayload;
import dev.nezo.burmaldaholic.bots.net.BotsScreenPayload;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Client half of "bots": the Table settings screen (BOTS.md §8.2), the [⚙] button added to every bot
 * table's screen (top-right corner; opens the settings of that table), and the Casino Card invite
 * confirmation (§8.4).
 */
public final class BotsClientModule implements CasinoClientModule {
	/** The gear glyph of the [⚙] button (language-neutral symbol). */
	private static final String GEAR = "⚙";

	@Override
	public String id() {
		return "bots";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ClientPlayNetworking.registerGlobalReceiver(BotsScreenPayload.TYPE, (payload, context) -> accept(context.client(), payload));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CasinoTableScreen table && client.level != null
				&& client.level.getBlockEntity(table.getMenu().pos()) instanceof BotTable) {
				BlockPos pos = table.getMenu().pos();
				Button gear = Button.builder(Texts.raw(GEAR), b -> ClientPlayNetworking.send(new BotsActionPayload(pos, "open", new CompoundTag())))
					.bounds(width - 24, 4, 20, 20).build();
				gear.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.bots.settings.open")));
				Screens.getWidgets(screen).add(gear);
			}
		});
	}

	private static void accept(Minecraft client, BotsScreenPayload payload) {
		Screen current = client.gui.screen();
		switch (payload.screen()) {
			case "settings" -> {
				if (current instanceof TableSettingsScreen s && s.pos().asLong() == payload.state().getLongOr("pos", 0)) {
					s.accept(payload.state());
				} else if (payload.open()) {
					client.gui.setScreen(new TableSettingsScreen(payload.state()));
				} else if (client.player != null) {
					Component error = TableSettingsScreen.decodeError(payload.state());
					if (error != null) {
						client.player.sendOverlayMessage(error);
					}
				}
			}
			case "invite_confirm" -> {
				CompoundTag s = payload.state();
				BlockPos pos = BlockPos.of(s.getLongOr("pos", 0));
				String id = s.getStringOr("id", "");
				Component question = Component.translatable("gui.burmaldaholic.bots.private.confirm", Texts.raw(s.getStringOr("name", "")),
					Component.translatable(s.getStringOr("title", "gui.burmaldaholic.bots.admin.title")));
				client.gui.setScreen(new ConfirmScreen(yes -> {
					if (yes) {
						CompoundTag a = new CompoundTag();
						a.putString("id", id);
						ClientPlayNetworking.send(new BotsActionPayload(pos, "invite", a));
					}
					client.gui.setScreen(current);
				}, question, Component.empty(), Component.translatable("gui.burmaldaholic.bots.private.invite_submit"), Component.translatable("gui.cancel")));
			}
			default -> {
			}
		}
	}
}
