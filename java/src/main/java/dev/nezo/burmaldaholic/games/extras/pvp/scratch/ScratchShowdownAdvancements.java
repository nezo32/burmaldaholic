package dev.nezo.burmaldaholic.games.extras.pvp.scratch;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.PvpEvents;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Scratch Showdown advancement (PVP.md §8.6, §11): {@code pvp_lucky_feet} — win (or split-win) a Scratch
 * Showdown with two Rabbit's Feet on your card; may be earned against bots (§3.15.2). It lives with the other
 * PvP advancements, {@code data/burmaldaholic/advancement/pvp/pvp_lucky_feet.json} (parent {@code pvp/pvp_first_win},
 * PVP.md §11), and is awarded here: casino mode on, {@code root} first, offline winners get
 * it on their next join (kept in memory until the server stops).
 */
public final class ScratchShowdownAdvancements {
	public static final String LUCKY_FEET = "pvp_lucky_feet";
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	private ScratchShowdownAdvancements() {}

	public static void register() {
		PvpEvents.MATCH_SETTLED.register(ScratchShowdownAdvancements::onSettled);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (PENDING.remove(handler.getPlayer().getUUID())) {
				grant(handler.getPlayer());
			}
		});
	}

	public static Identifier key() {
		return Burmaldaholic.id("pvp/" + LUCKY_FEET);
	}

	static void onSettled(MinecraftServer server, PvpMatch match, long[] payouts) {
		if (!ScratchShowdownMode.ID.equals(match.mode)) {
			return;
		}
		Outcome outcome = match.outcome();
		if (outcome == null) {
			return;
		}
		List<Participant> ps = match.participants();
		for (int seat : luckyWinners(outcome)) {
			if (seat < ps.size() && ps.get(seat).occupant instanceof SeatOccupant.Human h) {
				ServerPlayer online = server.getPlayerList().getPlayer(h.id());
				if (online != null && !online.hasDisconnected()) {
					grant(online);
				} else {
					PENDING.add(h.id());
				}
			}
		}
	}

	/** Grants to an online player; true if newly granted. */
	public static boolean grant(ServerPlayer player) {
		if (player == null || !CasinoMode.isEnabled(player)) {
			return false;
		}
		CasinoAdvancements.grant(player, "root");
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(key());
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

	/** Winners (incl. split winners) whose card shows ≥ 2 Rabbit's Feet. Pure. */
	public static Set<Integer> luckyWinners(Outcome outcome) {
		Set<Integer> winners = new HashSet<>();
		for (int w : outcome.winners()) {
			winners.add(w);
		}
		Set<Integer> out = new HashSet<>();
		for (PvpEvent e : outcome.events()) {
			if (e.kind().equals("lucky_feet") && winners.contains(e.seat())) {
				out.add(e.seat());
			}
		}
		return out;
	}
}
