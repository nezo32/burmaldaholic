package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The three §19 advancements of Ultimate Texas Hold'em ({@code uth_four_x}, {@code uth_royal},
 * {@code uth_house_seat}). Core's {@link CasinoAdvancements} only grants the ids it knows, so these live in
 * {@code data/burmaldaholic/advancement/uth/<id>.json} (parents in core's tree, one {@code minecraft:impossible}
 * criterion) and are awarded here with the same rules: casino mode on, {@code root} first, a player who is
 * offline gets it on their next join (kept in memory until the server stops).
 */
public final class UthAdvancements {
	public static final String FOUR_X = "uth_four_x";
	public static final String ROYAL = "uth_royal";
	public static final String HOUSE_SEAT = "uth_house_seat";
	public static final List<String> IDS = List.of(FOUR_X, HOUSE_SEAT, ROYAL);

	private static final Map<UUID, Set<String>> PENDING = new HashMap<>();

	private UthAdvancements() {}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Set<String> ids = PENDING.remove(handler.getPlayer().getUUID());
			if (ids != null) {
				ids.forEach(id -> grant(handler.getPlayer(), id));
			}
		});
	}

	public static Identifier key(String id) {
		return Burmaldaholic.id("uth/" + id);
	}

	/** Offline-safe grant. */
	public static void grant(MinecraftServer server, UUID player, String id) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			grant(online, id);
		} else if (IDS.contains(id)) {
			PENDING.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(id);
		}
	}

	/** Grants to an online player; true if newly granted. */
	public static boolean grant(ServerPlayer player, String id) {
		if (player == null || !IDS.contains(id) || !CasinoMode.isEnabled(player)) {
			return false;
		}
		CasinoAdvancements.grant(player, "root");
		MinecraftServer server = player.level().getServer();
		AdvancementHolder holder = server.getAdvancements().get(key(id));
		if (holder == null) {
			return false;
		}
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) {
			return false;
		}
		boolean any = false;
		List<String> remaining = new java.util.ArrayList<>();
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
}
