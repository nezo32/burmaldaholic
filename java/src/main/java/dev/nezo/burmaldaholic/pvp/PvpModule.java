package dev.nezo.burmaldaholic.pvp;

import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpEvents;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.net.PvpActionPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The "pvp" module (PVP.md): PvP hub (Casino Menu → Challenges), invite / lobby / result UI, the Java
 * {@code PvpPresenter} + payloads, {@code /casino pvp …}, the admin PvP list, rivalry display and the core PvP
 * advancements (pvp_first_win, pvp_all_in, pvp_full_house, pvp_revenge, pvp_rampage). The engine itself is core
 * ({@code core.pvp}); the modes live in the modules owning their solo games (extras, slots).
 */
public final class PvpModule implements CasinoModule {
	private static @Nullable MinecraftServer server;
	private static @Nullable PvpHubPage hub;

	@Override
	public String id() {
		return "pvp";
	}

	/** The running server (null between worlds). */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	public static @Nullable PvpHubPage hub() {
		return hub;
	}

	@Override
	public void register(ModuleContext ctx) {
		Pvp.setPresenter(new PvpScreensPresenter());
		hub = PvpHubPage.install(); // after extras (ModuleList order): keeps its Dice Duel page behind a hub button
		CasinoMenu.register(new PvpAdminPage());
		PvpCommands.register();

		PvpSyncPayload.TYPE = ctx.payloads().clientbound("pvp_sync", PvpSyncPayload.CODEC);
		PvpActionPayload.TYPE = ctx.payloads().serverbound("pvp_action", PvpActionPayload.CODEC,
			(payload, context) -> PvpUi.handle(context.player(), payload));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			PvpUi.clearAll();
		});
		ServerTickEvents.END_SERVER_TICK.register(PvpUi::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> PvpAdvancements.deliver(handler.player)));
		CasinoMode.onChange((s, enabled) -> {
			if (enabled) {
				s.getPlayerList().getPlayers().forEach(PvpAdvancements::deliver);
			}
		});

		PvpEvents.MATCH_STARTED.register((s, match) -> {
			PvpUi.resetMatch(match.id);
			PvpAdvancements.onStarted(s, match);
			PvpUi.pushAll(s, match, true);
			if (match.grudge()) {
				grudgeBanner(s, match);
			}
		});
		PvpEvents.MATCH_SETTLED.register((s, match, payouts) -> {
			PvpAdvancements.onSettled(s, match, payouts); // the result window opens from presenter.result / the scan
		});
	}

	/** GRUDGE MATCH banner for both players + ravager roar (PVP.md §3.8). */
	static void grudgeBanner(MinecraftServer server, PvpMatch match) {
		List<ServerPlayer> players = PvpMatchView.onlineHumans(server, match);
		List<Participant> humans = match.participants().stream().filter(p -> p.occupant instanceof SeatOccupant.Human).toList();
		if (humans.size() != 2) {
			return;
		}
		Component subtitle = null;
		for (int k = 0; k < 2; k++) {
			SeatOccupant.Human a = (SeatOccupant.Human) humans.get(k).occupant;
			SeatOccupant.Human b = (SeatOccupant.Human) humans.get(1 - k).occupant;
			HeadToHead r = Pvp.service().record(a.id(), b.id());
			if (r.run() > 0) {
				subtitle = Component.translatable("gui.burmaldaholic.pvp.grudge.subtitle", PvpMatchView.name(match, humans.get(k)), Texts.number(r.run()));
			}
		}
		for (ServerPlayer p : players) {
			PvpUi.title(p, Component.translatable("gui.burmaldaholic.pvp.grudge.title"), subtitle, 50);
			PvpUi.sound(p, "minecraft:entity.ravager.roar", 0.5f, 1.0f);
		}
	}
}
