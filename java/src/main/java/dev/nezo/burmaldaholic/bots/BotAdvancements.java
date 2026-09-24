package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.bots.logic.AdvancementRules;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bots module's advancements (BOTS.md §10): {@code members_only}, {@code no_robots},
 * {@code word_got_around}. Files {@code data/burmaldaholic/advancement/bots/<id>.json} (single
 * {@code minecraft:impossible} criterion, lang keys in core). Granted to online players only (every
 * condition is observed while the player is online; heat is re-checked on join).
 */
final class BotAdvancements {
	static final List<String> IDS = List.of(AdvancementRules.MEMBERS_ONLY, AdvancementRules.NO_ROBOTS, AdvancementRules.WORD_GOT_AROUND);

	private BotAdvancements() {}

	static net.minecraft.resources.Identifier key(String id) {
		return Burmaldaholic.id("bots/" + id);
	}

	/** Grants to an online player; true if newly granted. */
	static boolean grant(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		if (!IDS.contains(id) || player.hasDisconnected() || !CasinoMode.isEnabled(server)) {
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

	/** A settled round at a table: members_only / no_robots. */
	static void onResult(ServerPlayer player, PlayResult r) {
		if (r.deferred() || r.table() == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.getLevel(r.table().dimension());
		if (level == null) {
			return;
		}
		Optional<BotTables.Found> found = BotTables.at(level, r.table().pos());
		if (found.isEmpty()) {
			return;
		}
		BotTables.Found f = found.get();
		var seated = new HashSet<>(f.table().seatedHumans());
		if (AdvancementRules.membersOnly(f.bots().access().isPrivate(), TableSettings.INVITES.playsWithInvitee(f.key(), player.getUUID(), seated))) {
			grant(player, AdvancementRules.MEMBERS_ONLY);
		}
		if (AdvancementRules.noRobots(f.bots().settings().policy(), seated.size(), f.botsSeated()) && seated.contains(player.getUUID())) {
			grant(player, AdvancementRules.NO_ROBOTS);
		}
	}
}
