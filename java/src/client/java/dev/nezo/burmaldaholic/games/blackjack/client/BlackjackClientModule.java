package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client half of the "blackjack" module: table screens and the dealer renderer. */
public final class BlackjackClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "blackjack";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(BlackjackModule.TABLE, BlackjackScreen::new);
		ctx.tableScreen(BlackjackModule.HIGH_ROLLER_TABLE, BlackjackScreen::new);
		EntityRenderers.register(BlackjackModule.DEALER, BlackjackDealerRenderer::new);
	}
}
