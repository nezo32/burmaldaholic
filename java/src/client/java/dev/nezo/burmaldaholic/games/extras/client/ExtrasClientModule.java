package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasErrorPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

/** Client half of the "extras" module: machine screens (wheel, plinko) and the item-game screens. */
public final class ExtrasClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return ExtrasModule.ID;
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(ExtrasModule.WHEEL, WheelScreen::new);
		ctx.tableScreen(ExtrasModule.PLINKO, PlinkoScreen::new);
<<<<<<< HEAD
		dev.nezo.burmaldaholic.games.extras.client.pvp.coin.CoinDuelClient.register(); // PvP Coin Flip Duel (J-M1)
		dev.nezo.burmaldaholic.games.extras.client.pvp.wheel.WheelPartyClient.register(); // PvP Wheel Party (J-M2)
=======
		dev.nezo.burmaldaholic.games.extras.client.pvp.ExtrasPvpScreens.register(); // Plinko Battle + Scratch Showdown screens
>>>>>>> worktree-agent-a19fec0b1773f637c
		ClientPlayNetworking.registerGlobalReceiver(ExtrasScreenPayload.TYPE, (payload, context) -> accept(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(ExtrasErrorPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof ExtrasScreen screen) {
				screen.showError(payload.message());
			}
		});
	}

	private static void accept(Minecraft client, ExtrasScreenPayload payload) {
		Screen current = client.gui.screen();
		String screen = payload.screen();
		CompoundTag state = payload.state();
		if (screen.equals("duel_invite")) {
			// Never interrupt another screen (combat, inventory...): chat + the dice screen list the challenge.
			if (current == null) {
				client.gui.setScreen(new DuelInviteScreen(state));
			}
			return;
		}
		if (current instanceof ExtrasScreen open && open.game().equals(screen) && !(open instanceof DuelInviteScreen)) {
			open.acceptState(state);
			return;
		}
		if (!payload.open()) {
			return;
		}
		ExtrasScreen next = switch (screen) {
			case "coin" -> new CoinFlipScreen(state);
			case "dice" -> new DiceScreen(state);
			case "scratch" -> new ScratchScreen(state);
			default -> null;
		};
		if (next != null) {
			client.gui.setScreen(next);
		}
	}
}
