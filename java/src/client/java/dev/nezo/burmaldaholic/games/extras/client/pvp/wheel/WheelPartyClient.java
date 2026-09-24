package dev.nezo.burmaldaholic.games.extras.client.pvp.wheel;

import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.PvpPanel;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyMode;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Client half of Wheel Party (task J-M2): the party panel, its button on the wheel screen, the live screen. */
public final class WheelPartyClient {
	private WheelPartyClient() {}

	/** Called once from {@code ExtrasClientModule.registerClient}. */
	public static void register() {
		PvpScreens.register(WheelPartyMode.ID, WheelPartyScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(WheelPartyNet.Panel.TYPE, (payload, context) -> accept(context.client(), payload));
	}

	/**
	 * Label of the wheel screen's party button from the machine state's {@code party} tag (null = no button):
	 * Start a Wheel Party / Join the party: n/N / Add to my slice.
	 */
	public static @Nullable Component buttonLabel(CompoundTag party) {
		if (!party.getBooleanOr("enabled", false)) {
			return null;
		}
		if (!party.getBooleanOr("open", false)) {
			return Component.translatable("gui.burmaldaholic.pvp.wheel.host");
		}
		if (party.getBooleanOr("joined", false)) {
			return Component.translatable("gui.burmaldaholic.pvp.wheel.add");
		}
		return Component.translatable("gui.burmaldaholic.pvp.wheel.join", Texts.number(party.getIntOr("players", 0)), Texts.number(party.getIntOr("max", 8)));
	}

	/** The wheel screen's party button: asks the server for the panel of the wheel at {@code pos}. */
	public static void open(BlockPos pos) {
		CompoundTag a = new CompoundTag();
		a.putInt("x", pos.getX());
		a.putInt("y", pos.getY());
		a.putInt("z", pos.getZ());
		send("open", a);
	}

	static void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new WheelPartyNet.Action(action, args));
	}

	private static void accept(Minecraft client, WheelPartyNet.Panel payload) {
		Screen current = client.gui.screen();
		if (current instanceof WheelPartyPanel panel) {
			if (!payload.state().isEmpty()) {
				panel.accept(payload.state());
			}
		} else if (payload.open()) {
			client.gui.setScreen(new WheelPartyPanel(payload.state()));
		}
		Screen now = client.gui.screen();
		if (now instanceof PvpPanel p && !payload.message().getString().isEmpty()) {
			p.showError(payload.message());
		}
	}
}
