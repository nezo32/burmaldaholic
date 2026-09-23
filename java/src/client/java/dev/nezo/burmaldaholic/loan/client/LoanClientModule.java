package dev.nezo.burmaldaholic.loan.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "loan" module (screens, renderers, HUD, client payload receivers). */
public final class LoanClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "loan";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(loan): e.g. ctx.tableScreen(LoanModule.TABLE, LoanScreen::new);
	}
}
