package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.AdvancementRules;
import dev.nezo.burmaldaholic.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Heat advancement (BOTS.md §5.4): after a round against money bots settles, a player who reached "Word got
 * around" (or worse) gets {@code word_got_around}. The check runs one tick after the result, so the game has
 * recorded the ledger entry by then; also on join (a round settled offline).
 *
 * <p>The heat LINES ({@code msg.burmaldaholic.bots.word_got_around} / {@code …sulking}) and their bot quips
 * are NOT sent here: core's {@code TableBots} safe point is their one source (once per table, player and
 * day, through the table's chatter queue so the Chatter toggle applies). This class used to send them too,
 * so tables heard every line twice and the quip ignored the toggle (review wave 3).
 */
final class BotHeat {
	private static final Set<UUID> PENDING = new LinkedHashSet<>();

	private BotHeat() {}

	static void onResult(ServerPlayer player, PlayResult r) {
		if (BotRounds.vsBots(r)) {
			PENDING.add(player.getUUID());
		}
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<UUID> now = List.copyOf(PENDING);
		PENDING.clear();
		for (UUID id : now) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				check(player);
			}
		}
	}

	static void check(ServerPlayer player) {
		HeatStage stage = TableSettings.heat(player);
		if (AdvancementRules.wordGotAround(stage)) {
			BotAdvancements.grant(player, AdvancementRules.WORD_GOT_AROUND);
		}
	}

	static void onJoin(ServerPlayer player) {
		if (BotLedger.netToday(player.level().getServer(), player.getUUID()) > 0) {
			check(player);
		}
	}

	static void clear() {
		PENDING.clear();
	}
}
