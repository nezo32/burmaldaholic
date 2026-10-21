package dev.nezo.burmaldaholic.client.comps;

import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.cashier.CounterTabs;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.comps.CompsModule;
import dev.nezo.burmaldaholic.comps.FreeBets;
import dev.nezo.burmaldaholic.comps.net.CompsActionPayload;
import dev.nezo.burmaldaholic.comps.net.CompsSyncPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client half of the comps module: the Comps counter tab, the sync receiver and the token's days-left tooltip line.
 */
public final class CompsClient implements CasinoClientModule {
	private static volatile CompsSyncPayload last = CompsSyncPayload.EMPTY;
	/** Tests: replaces the network send (payloads are captured instead). */
	public static Consumer<CompsActionPayload> sender;

	@Override
	public String id() {
		return CompsModule.ID;
	}

	public static CompsSyncPayload last() {
		return last;
	}

	static void send(CompsActionPayload p) {
		Consumer<CompsActionPayload> s = sender;
		if (s != null) {
			s.accept(p);
		} else if (CompsActionPayload.TYPE != null && ClientPlayNetworking.canSend(CompsActionPayload.TYPE)) {
			ClientPlayNetworking.send(p);
		}
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		CounterTabs.register(CompsTab.ID, CompsTab::new);
		ClientPlayNetworking.registerGlobalReceiver(CompsSyncPayload.TYPE, (payload, context) -> {
			last = payload;
			CounterShell.current().ifPresent(CounterShell::relayout);
		});
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
			if (FreeBets.TOKEN == null || !stack.is(FreeBets.TOKEN)) {
				return;
			}
			Long e = stack.get(FreeBets.EXPIRES);
			var level = Minecraft.getInstance().level;
			if (e != null && level != null) {
				long days = Math.max(0, (e - level.getGameTime() + 23999) / 24000);
				lines.add(Component.translatable("item.burmaldaholic.free_bet_token.expires", Texts.plural("unit.burmaldaholic.day", days))
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}
}
