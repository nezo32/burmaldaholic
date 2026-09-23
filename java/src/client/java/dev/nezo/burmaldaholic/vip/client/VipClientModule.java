package dev.nezo.burmaldaholic.vip.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.hud.HudLine;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import dev.nezo.burmaldaholic.vip.net.VipActionPayload;
import dev.nezo.burmaldaholic.vip.net.VipErrorPayload;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client half of the "vip" module: Casino Menu (Wallet / VIP / Contracts) on key {@code B}
 * ({@code key.burmaldaholic.open_menu}; core has no menu yet) and on Diamond Casino Card use,
 * plus the HUD segment (progress to the next tier, contracts done today).
 */
public final class VipClientModule implements CasinoClientModule {
	private static KeyMapping openMenu;

	@Override
	public String id() {
		return "vip";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		KeyMapping.Category category = KeyMapping.Category.register(Burmaldaholic.id("vip"));
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.burmaldaholic.open_menu", InputConstants.KEY_B, category));

		ClientPlayNetworking.registerGlobalReceiver(VipSyncPayload.TYPE, (payload, context) -> {
			VipClientState.accept(payload);
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.screen() instanceof CasinoMenuScreen screen) {
				screen.refresh();
			} else if (payload.open() && mc.gui.screen() == null) {
				mc.gui.setScreen(new CasinoMenuScreen(CasinoMenuScreen.Tab.WALLET));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(VipErrorPayload.TYPE, (payload, context) -> {
			if (Minecraft.getInstance().gui.screen() instanceof CasinoMenuScreen screen) {
				screen.showError(payload.message());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VipClientState.reset());
		ClientTickEvents.END_CLIENT_TICK.register(VipClientModule::tick);

		ctx.hudSegment("vip_hud", 110, (hud, out) -> {
			if (!VipClientState.received()) {
				return;
			}
			VipSyncPayload d = VipClientState.data();
			VipRules.Progress p = VipRules.progress(d.wagered(), thresholds(), d.tier());
			if (!p.maxed()) {
				out.accept(HudLine.of(Component.translatable("hud.burmaldaholic.vip.next", VipTiers.name(p.next()),
					Texts.number((long) Math.floor(p.fraction() * 100))), 0xFFAAAAAA));
			}
			if (d.contractsOn() && !d.contracts().isEmpty()) {
				long done = d.contracts().stream().filter(VipSyncPayload.ContractView::done).count();
				out.accept(HudLine.of(Component.translatable("hud.burmaldaholic.vip.contracts", Texts.number(done), Texts.number(d.contracts().size())),
					done == d.contracts().size() ? 0xFF55FF55 : 0xFFAAAAAA));
			}
		});
	}

	/** {@code vip.threshold.*} from the (server-synced) config. */
	static long[] thresholds() {
		var t = CasinoConfig.vip().threshold;
		return new long[] {t.silver, t.gold, t.platinum, t.diamond, t.netherite};
	}

	/** Asks the server for fresh data (menu opened / refreshed). */
	static void requestSync() {
		if (VipActionPayload.TYPE != null && ClientPlayNetworking.canSend(VipActionPayload.TYPE)) {
			ClientPlayNetworking.send(new VipActionPayload(VipActionPayload.SYNC, 0));
		}
	}

	static void reroll(int index) {
		if (VipActionPayload.TYPE != null && ClientPlayNetworking.canSend(VipActionPayload.TYPE)) {
			ClientPlayNetworking.send(new VipActionPayload(VipActionPayload.REROLL, index));
		}
	}

	private static void tick(Minecraft mc) {
		while (openMenu != null && openMenu.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null && ClientCasinoState.hasStatus() && CasinoMode.isEnabled(mc.player)) {
				mc.gui.setScreen(new CasinoMenuScreen(CasinoMenuScreen.Tab.WALLET));
			}
		}
	}
}
