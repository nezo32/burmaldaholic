package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client half of Coin Flip Duel (task J-M1): the set-up panel receiver and the live screen for {@code PvpScreens}. */
public final class CoinDuelClient {
	private CoinDuelClient() {}

	/** Called once from {@code ExtrasClientModule.registerClient}. */
	public static void register() {
		PvpScreens.register(CoinDuelMode.ID, CoinDuelScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(CoinDuelNet.Screen.TYPE, (payload, context) -> accept(context.client(), payload));
	}

	private static void accept(Minecraft client, CoinDuelNet.Screen payload) {
		Screen current = client.gui.screen();
		if (payload.screen().equals("setup")) {
			if (current instanceof CoinDuelSetupScreen open) {
				open.accept(payload.state());
			} else {
				client.gui.setScreen(new CoinDuelSetupScreen(payload.state()));
			}
			return;
		}
		if (payload.state().getBooleanOr("close", false)) {
			if (current instanceof CoinDuelSetupScreen) {
				client.gui.setScreen(null);
			}
			return;
		}
		if (current instanceof PvpPanel panel && !payload.message().getString().isEmpty()) {
			panel.showError(payload.message());
		}
	}
}
