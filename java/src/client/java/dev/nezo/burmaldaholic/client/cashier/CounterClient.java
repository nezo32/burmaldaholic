package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.core.store.net.CounterOpenPayload;
import dev.nezo.burmaldaholic.core.store.net.CounterResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client receivers of the counter payloads (core client module). */
public final class CounterClient {
	private CounterClient() {}

	public static void register() {
		CounterTabs.register(ExchangeTab.ID, ExchangeTab::new);
		CounterTabs.register(StoreTab.ID, StoreTab::new);
		ClientStore.register();
		ClientPlayNetworking.registerGlobalReceiver(CounterOpenPayload.TYPE, (payload, context) ->
			Minecraft.getInstance().gui.setScreen(new CounterSceneScreen(payload.source(), payload.tabs(), payload.dimension())));
		ClientPlayNetworking.registerGlobalReceiver(CounterResultPayload.TYPE, (payload, context) ->
			CounterShell.current().ifPresent(s -> s.result(payload.ok(), payload.message())));
	}
}
