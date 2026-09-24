package dev.nezo.burmaldaholic.bots.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of "bots": the Table settings screen (BOTS.md §8.2) opened from every table screen's [⚙]. */
public final class BotsClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "bots";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(J-B2): settings payload receiver → TableSettingsScreen; seat-plate helpers live in client.table.
	}
}
