package dev.nezo.burmaldaholic.loan.client;

import com.mojang.serialization.DynamicOps;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jspecify.annotations.Nullable;

/**
 * Client half of the "loan" module: entity renderers (generated skins on vanilla models) and the two
 * server-driven screens (Loan Shark, collector negotiation). The HUD loan line is drawn by core from
 * the synced player status (DebtProvider).
 */
public final class LoanClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "loan";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		LoanRenderers.register();
		ClientPlayNetworking.registerGlobalReceiver(LoanUiPayload.TYPE, (payload, context) -> onUi(context.client(), payload));
		// Debt Collectors' arrival (global §4.9, lane J-L3)
		if (dev.nezo.burmaldaholic.loan.net.LoanFxPayload.TYPE != null) {
			ClientPlayNetworking.registerGlobalReceiver(dev.nezo.burmaldaholic.loan.net.LoanFxPayload.TYPE, (payload, context) -> CollectorFx.onPayload(payload));
		}
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> CollectorFx.tick());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CollectorFx.reset());
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS, dev.nezo.burmaldaholic.Burmaldaholic.id("loan_vignette"),
			CollectorFx::extractVignette);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.TITLE_AND_SUBTITLE, dev.nezo.burmaldaholic.Burmaldaholic.id("loan_card"),
			CollectorFx::extractCard);
	}

	private static void onUi(Minecraft mc, LoanUiPayload payload) {
		Screen current = mc.gui.screen();
		if (LoanUiPayload.LOAN.equals(payload.screen())) {
			if (current instanceof LoanScreen screen) {
				if (!payload.open() && payload.data().isEmpty()) {
					screen.onClose();
				} else {
					screen.accept(payload.data());
				}
			} else if (payload.open()) {
				mc.gui.setScreen(new LoanScreen(payload.data()));
			}
		} else if (LoanUiPayload.NEGOTIATE.equals(payload.screen())) {
			if (payload.open()) {
				mc.gui.setScreen(new NegotiationScreen(payload.data()));
			} else if (current instanceof NegotiationScreen screen) {
				screen.onClose();
			}
		}
	}

	private static DynamicOps<Tag> ops() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
	}

	/** A text component the server stored in the screen state. */
	static @Nullable Component readComponent(CompoundTag state, String key) {
		Tag tag = state.get(key);
		if (tag == null) {
			return null;
		}
		return ComponentSerialization.CODEC.parse(ops(), tag).result().orElse(null);
	}

	/** Client-side validation message (e.g. an empty amount field) in the same slot the server uses. */
	static void putError(CompoundTag state, Component message) {
		ComponentSerialization.CODEC.encodeStart(ops(), message).result().ifPresent(t -> state.put("message", t));
		state.putBoolean("error", true);
	}

	/** Client game-test hook: the collectors' arrival card is showing (lane J-L3). */
	public static boolean arrivalActive() {
		return CollectorFx.active();
	}
}
