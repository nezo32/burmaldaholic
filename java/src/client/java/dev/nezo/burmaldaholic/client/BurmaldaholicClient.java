package dev.nezo.burmaldaholic.client;

import dev.nezo.burmaldaholic.client.module.ClientModuleLoader;
import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint. Owned by core — feature developers edit only their own *ClientModule. */
public final class BurmaldaholicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientModuleLoader.load(ClientModuleList.create());
	}
}
