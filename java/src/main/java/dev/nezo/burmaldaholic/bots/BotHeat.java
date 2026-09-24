package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.AdvancementRules;
import dev.nezo.burmaldaholic.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.bots.BotChatter;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Heat stage announcements (BOTS.md §5.4): after a round against money bots settles, the player's
 * {@code botNetToday} (core {@link BotLedger}) is compared with the cap; crossing "Word got around" or
 * "Sulking" tells the table once per day, lets a bot say the matching quip and grants
 * {@code word_got_around}. The check runs one tick after the result, so the game has recorded the
 * ledger entry by then. Also checked on join (a round settled offline).
 */
final class BotHeat {
	static final HeatStage.Watch WATCH = new HeatStage.Watch();
	private static final List<Pending> PENDING = new ArrayList<>();

	private record Pending(UUID player, @Nullable GlobalPos table) {}

	private BotHeat() {}

	static void onResult(ServerPlayer player, PlayResult r) {
		if (BotRounds.vsBots(r)) {
			PENDING.add(new Pending(player.getUUID(), r.table()));
		}
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> now = List.copyOf(PENDING);
		PENDING.clear();
		for (Pending p : now) {
			ServerPlayer player = server.getPlayerList().getPlayer(p.player());
			if (player != null) {
				check(player, p.table());
			}
		}
	}

	static void check(ServerPlayer player, @Nullable GlobalPos table) {
		MinecraftServer server = player.level().getServer();
		HeatStage stage = TableSettings.heat(player);
		if (stage == HeatStage.NONE) {
			return;
		}
		if (AdvancementRules.wordGotAround(stage)) {
			BotAdvancements.grant(player, AdvancementRules.WORD_GOT_AROUND);
		}
		long day = BotEconomyMath.mcDay(server.overworld().getGameTime());
		if (!WATCH.crossed(player.getUUID(), day, stage)) {
			return;
		}
		String name = player.getName().getString();
		Component line = stage == HeatStage.SULKING
			? Component.translatable("msg.burmaldaholic.bots.sulking", Texts.raw(name))
			: Component.translatable("msg.burmaldaholic.bots.word_got_around", Texts.raw(name));
		Optional<BotTables.Found> found = Optional.empty();
		if (table != null) {
			ServerLevel level = server.getLevel(table.dimension());
			if (level != null) {
				found = BotTables.at(level, table.pos());
			}
		}
		if (found.isEmpty()) {
			player.sendSystemMessage(line);
			return;
		}
		BotTables.Found f = found.get();
		for (UUID id : f.table().seatedHumans()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(line);
			}
		}
		if (!f.seated(player.getUUID())) {
			player.sendSystemMessage(line);
		}
		List<TableBots.SeatedBot> bots = f.bots().bots();
		if (!bots.isEmpty() && CasinoConfig.bots().chatter.enabled) {
			BotChatter.event(f.level(), f.pos(), bots.getFirst().profile, stage == HeatStage.SULKING ? "sulk" : "word_got_around", name);
		}
	}

	static void onJoin(ServerPlayer player) {
		long net = BotLedger.netToday(player.level().getServer(), player.getUUID());
		if (net > 0) {
			check(player, null);
		}
	}

	static void clear() {
		PENDING.clear();
	}
}
