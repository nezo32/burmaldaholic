package dev.nezo.burmaldaholic.games.extras.pvp.coin;

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
 * The two extras PvP mode advancements (PVP.md §11): {@code pvp_all_square} (Coin Flip Duel) and
 * {@code pvp_underdog} (Wheel Party). Files: {@code data/burmaldaholic/advancement/pvp/<id>.json} with a single
 * {@code minecraft:impossible} criterion. Core's {@link CasinoAdvancements#grant} only knows core ids, so the
 * modes award them here with the same rules (casino mode on, root first). A winner who is offline when the match
 * settles (a play-out on load) does not get it — the moment was not seen.
 */
public final class ModeAdvancements {
	public static final String ALL_SQUARE = "pvp_all_square";
	public static final String UNDERDOG = "pvp_underdog";
	public static final List<String> IDS = List.of(ALL_SQUARE, UNDERDOG);

	private ModeAdvancements() {}

	public static net.minecraft.resources.Identifier key(String id) {
		return Burmaldaholic.id("pvp/" + id);
	}

	/** Grants {@code id} to {@code player} if online; true if newly granted. */
	public static boolean grant(MinecraftServer server, UUID player, String id) {
		if (server == null || player == null || !IDS.contains(id) || !CasinoMode.isEnabled(server)) {
			return false;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online == null || online.hasDisconnected()) {
			return false;
		}
		CasinoAdvancements.grant(online, "root");
		AdvancementHolder holder = server.getAdvancements().get(key(id));
		if (holder == null) {
			return false;
		}
		AdvancementProgress progress = online.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) {
			return false;
		}
		List<String> remaining = new ArrayList<>();
		progress.getRemainingCriteria().forEach(remaining::add);
		boolean any = false;
		for (String criterion : remaining) {
			any |= online.getAdvancements().award(holder, criterion);
		}
		return any;
	}
}
