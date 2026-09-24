package dev.nezo.burmaldaholic.games.baccarat.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client half of the "baccarat" module: the table screen (all three tables) and the dealer renderer. */
public final class BaccaratClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return BaccaratModule.ID;
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(BaccaratModule.TABLE, BaccaratScreen::new);
		ctx.tableScreen(BaccaratModule.HIGH_ROLLER_TABLE, BaccaratScreen::new);
		ctx.tableScreen(BaccaratModule.CHEMMY_TABLE, BaccaratScreen::new);
		EntityRenderers.register(BaccaratModule.DEALER, BaccaratDealerRenderer::new);
	}
}
