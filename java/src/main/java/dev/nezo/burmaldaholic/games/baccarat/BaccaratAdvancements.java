package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The four baccarat advancements of GAME_DESIGN §19 ({@code baccarat_natural}, {@code tie_streak},
 * {@code banco}, {@code bank_holder}). Files: {@code data/burmaldaholic/advancement/baccarat/<id>.json}
 * (parents in core's tree, single {@code minecraft:impossible} criterion). Core's
 * {@link CasinoAdvancements#grant} only knows the core ids, so the module awards them itself with the
 * same rules: casino mode on, root first, offline players on their next join ({@link BaccaratData}).
 */
public final class BaccaratAdvancements {
	public static final List<String> IDS = List.of("baccarat_natural", "tie_streak", "banco", "bank_holder");

	private BaccaratAdvancements() {}

	public static net.minecraft.resources.Identifier key(String id) {
		return Burmaldaholic.id("baccarat/" + id);
	}

	/** Offline-safe grant. */
	public static void grant(MinecraftServer server, UUID player, String id) {
		if (server == null || player == null || !IDS.contains(id)) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			grant(online, id);
		} else {
			BaccaratData.get(server).queueAdvancement(player, id);
		}
	}

	/** Grants to an online player; true if newly granted. */
	public static boolean grant(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		if (!IDS.contains(id) || !CasinoMode.isEnabled(server)) {
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

	/** Join: deliver queued grants. */
	static void deliver(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (String id : BaccaratData.get(server).takeAdvancements(player.getUUID())) {
			if (!grant(player, id) && !CasinoMode.isEnabled(server)) {
				BaccaratData.get(server).queueAdvancement(player.getUUID(), id); // casino off: keep for later
			}
		}
	}
}
