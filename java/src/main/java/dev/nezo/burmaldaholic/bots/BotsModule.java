package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.net.BotsActionPayload;
import dev.nezo.burmaldaholic.bots.net.BotsScreenPayload;
import dev.nezo.burmaldaholic.core.bots.BotChatter;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * The "bots" module (BOTS.md): table settings screen + payloads, private tables / invites (Casino Card on a
 * player), {@code /casino table …} and {@code /casino bots …}, bot chatter delivery + per-player mute,
 * avatars (text-display nameplates), the heat line (Casino Menu → Bots) and bot advancements
 * ({@code members_only}, {@code no_robots}, {@code word_got_around}). The seat model, policies, purses,
 * ledger and jobs are core ({@code core.bots}); each game drives its own bots through {@code TableBots}
 * and exposes it with {@code BotTable#tableBots()}.
 */
public final class BotsModule implements CasinoModule {
	@Override
	public String id() {
		return "bots";
	}

	@Override
	public void register(ModuleContext ctx) {
		BotsActionPayload.TYPE = ctx.payloads().serverbound("bots_action", BotsActionPayload.CODEC, TableSettings::handle);
		BotsScreenPayload.TYPE = ctx.payloads().clientbound("bots_screen", BotsScreenPayload.CODEC);
		BotCommands.register();
		BotInteractions.register();
		BotChatter.setSink(new BotChatterDelivery());
		BotChatter.setMuteFilter(p -> BotsData.get(p.level().getServer()).muted(p.getUUID()));
		// games report their bot tables through core's BotTableUi (poker / baccarat default to it)
		dev.nezo.burmaldaholic.core.bots.BotTableUi.set(new dev.nezo.burmaldaholic.core.bots.BotTableUi() {
			@Override
			public void attach(dev.nezo.burmaldaholic.core.bots.BotTable table, dev.nezo.burmaldaholic.core.bots.TableBots bots) {
				BotTables.remember(table);
			}
		});
		CasinoMenu.register(new BotsMenuPage());
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> {
			BotHeat.onResult(player, result);
			BotAdvancements.onResult(player, result);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BotHeat.tick(server);
			BotChatterDelivery.tick(server);
			BotAvatars.tick(server);
		});
		ServerEntityEvents.ENTITY_LOAD.register(BotAvatars::onEntityLoad);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			TableSettings.remember(handler.getPlayer());
			if (CasinoMode.isEnabled(server)) {
				BotHeat.onJoin(handler.getPlayer());
			}
		});
		CasinoMode.onChange((server, enabled) -> {
			if (!enabled) {
				BotAvatars.removeAll();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BotAvatars.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BotHeat.clear();
			BotChatterDelivery.clear();
			BotInteractions.clear();
			BotTables.clear();
		});
	}
}
