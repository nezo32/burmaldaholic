package dev.nezo.burmaldaholic.games.slots.pvp;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.PvpEvents;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Slot Showdown's advancement (PVP.md §11): {@code pvp_phoenix} — win a Slot Showdown after a KABOOM halved
 * your score in that match (bots count as opponents). File {@code data/burmaldaholic/advancement/pvp/pvp_phoenix.json}
 * (parent {@code pvp/pvp_first_win}, owned by the pvp module). Core's {@link CasinoAdvancements#grant} only knows
 * the core ids, so this awards it like the baccarat module does: casino mode on, root first; a winner who is
 * offline at settle gets it on their next join (in-memory queue: a restart in between loses it — cosmetic).
 */
final class ShowdownAdvancements {
	static final String PHOENIX = "pvp_phoenix";
	private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();

	private ShowdownAdvancements() {}

	static void register() {
		PvpEvents.MATCH_SETTLED.register(ShowdownAdvancements::onSettled);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (PENDING.remove(player.getUUID()) != null && !grant(player)) {
				if (!CasinoMode.isEnabled(server)) {
					PENDING.put(player.getUUID(), true); // casino off: keep for later
				}
			}
		});
	}

	private static void onSettled(MinecraftServer server, PvpMatch match, long[] payouts) {
		if (!SlotShowdownMode.ID.equals(match.mode)) {
			return;
		}
		Outcome outcome = match.outcome();
		if (outcome == null) {
			return;
		}
		List<Participant> ps = match.participants();
		Set<UUID> done = new java.util.HashSet<>();
		for (int w : outcome.winners()) {
			if (w < 0 || w >= ps.size() || !(ps.get(w).occupant instanceof SeatOccupant.Human human) || !done.add(human.id())) {
				continue;
			}
			if (ShowdownScoring.phoenix(outcome, w)) {
				ServerPlayer online = server.getPlayerList().getPlayer(human.id());
				if (online != null && !online.hasDisconnected()) {
					grant(online);
				} else {
					PENDING.put(human.id(), true);
				}
			}
		}
	}

	/** Grants {@code pvp_phoenix}; true if newly granted. */
	static boolean grant(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!CasinoMode.isEnabled(server)) {
			return false;
		}
		CasinoAdvancements.grant(player, "root");
		AdvancementHolder holder = server.getAdvancements().get(Burmaldaholic.id("pvp/" + PHOENIX));
		if (holder == null) {
			return false;
		}
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) {
			return false;
		}
		boolean any = false;
		List<String> remaining = new ArrayList<>();
		progress.getRemainingCriteria().forEach(remaining::add);
		for (String criterion : remaining) {
			any |= player.getAdvancements().award(holder, criterion);
		}
		return any;
	}
}
