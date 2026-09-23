package dev.nezo.burmaldaholic.client.module;

import java.util.List;

public final class ClientModuleLoader {
	private ClientModuleLoader() {}

	public static void load(List<CasinoClientModule> modules) {
		for (CasinoClientModule module : modules) {
			try {
				module.registerClient(new ClientModuleContext(module.id()));
			} catch (RuntimeException e) {
				throw new IllegalStateException("Client module '" + module.id() + "' failed to register", e);
			}
		}
	}
}
