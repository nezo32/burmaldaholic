package dev.nezo.burmaldaholic.pvp;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.pvp.logic.PvpAchievementRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The PvP advancements of the pvp module (PVP.md §11: {@code pvp_first_win}, {@code pvp_all_in},
 * {@code pvp_full_house}, {@code pvp_revenge}, {@code pvp_rampage}), awarded from {@code PvpEvents}. Files:
 * {@code data/burmaldaholic/advancement/pvp/<id>.json} (parents in core's tree, one {@code minecraft:impossible}
 * criterion). Offline players get them on their next join ({@link PvpUiData}).
 */
public final class PvpAdvancements {
	/** Grudge matches: the participant on the losing run, captured when the match starts. */
	private static final Map<String, UUID> UNDERDOG = new ConcurrentHashMap<>();

	private PvpAdvancements() {}

	public static net.minecraft.resources.Identifier key(String id) {
		return Burmaldaholic.id("pvp/" + id);
	}

	static void onStarted(MinecraftServer server, PvpMatch match) {
		if (!match.grudge()) {
			return;
		}
		List<UUID> humans = humans(match);
		if (humans.size() != 2 || match.participants().size() != 2) {
			return;
		}
		int losses = CasinoConfig.pvp().grudgeLosses;
		for (int k = 0; k < 2; k++) {
			if (Pvp.service().record(humans.get(k), humans.get(1 - k)).grudge(losses)) {
				UNDERDOG.put(match.id, humans.get(k));
			}
		}
	}

	static void onSettled(MinecraftServer server, PvpMatch match, long[] payouts) {
		PvpUi.cachePayouts(match.id, payouts);
		UUID underdog = UNDERDOG.remove(match.id);
		Outcome o = match.outcome();
		List<UUID> humans = humans(match);
		PvpUiData data = PvpUiData.get(server);
		for (Participant p : match.participants()) {
			if (!(p.occupant instanceof SeatOccupant.Human h)) {
				continue;
			}
			boolean won = won(o, payouts, p.index);
			Set<String> opponents = new LinkedHashSet<>();
			for (UUID other : humans) {
				if (!other.equals(h.id())) {
					opponents.add(other.toString());
				}
			}
			boolean grudge = match.grudge() && match.participants().size() == 2 && opponents.size() == 1;
			boolean isUnderdog = false;
			if (grudge && won) {
				UUID other = UUID.fromString(opponents.iterator().next());
				isUnderdog = underdog != null ? underdog.equals(h.id())
					: PvpAchievementRules.underdogWon(Pvp.service().record(h.id(), other).run(), CasinoConfig.pvp().grudgeLosses);
			}
			PvpAchievementRules.Streak after = data.streak(h.id()).after(won, opponents);
			data.setStreak(h.id(), after);
			PvpAchievementRules.Facts facts = new PvpAchievementRules.Facts(won, p.allIn(), opponents.size(), grudge, isUnderdog, after);
			for (String id : PvpAchievementRules.earned(facts)) {
				grant(server, h.id(), id);
			}
		}
	}

	static boolean won(Outcome o, long[] payouts, int index) {
		if (o != null) {
			for (int w : o.winners()) {
				if (w == index) {
					return true;
				}
			}
			return false;
		}
		return payouts != null && index >= 0 && index < payouts.length && payouts[index] > 0;
	}

	private static List<UUID> humans(PvpMatch match) {
		List<UUID> out = new ArrayList<>();
		for (Participant p : match.participants()) {
			if (p.occupant instanceof SeatOccupant.Human h && !out.contains(h.id())) {
				out.add(h.id());
			}
		}
		return out;
	}

	/** Offline-safe grant. */
	public static void grant(MinecraftServer server, UUID player, String id) {
		if (server == null || player == null || !PvpAchievementRules.IDS.contains(id)) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected() && CasinoMode.isEnabled(server)) {
			grant(online, id);
		} else {
			PvpUiData.get(server).queue(player, id);
		}
	}

	/** Grants to an online player; true if newly granted. */
	public static boolean grant(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		if (!PvpAchievementRules.IDS.contains(id) || !CasinoMode.isEnabled(server)) {
			return false;
		}
		CasinoAdvancements.grant(player, "root");
		AdvancementHolder holder = server.getAdvancements().get(key(id));
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

	public static boolean has(ServerPlayer player, String id) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(key(id));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Join (or casino mode switched on): deliver queued grants. */
	static void deliver(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!CasinoMode.isEnabled(server)) {
			return;
		}
		for (String id : PvpUiData.get(server).take(player.getUUID())) {
			grant(player, id);
		}
	}
}
