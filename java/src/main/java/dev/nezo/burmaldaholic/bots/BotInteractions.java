package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.SettingsRules;
import dev.nezo.burmaldaholic.bots.net.BotsScreenPayload;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * World interactions of the bots module:
 * <ul>
 *   <li>Casino Card used on a player while seated as the host of a table that allows private tables →
 *       "Invite Bob to your Blackjack table?" (BOTS.md §8.4). Otherwise the card's normal use continues.</li>
 *   <li>A human who uses someone else's BOTS_ONLY table → the host gets the chat line with a clickable
 *       [Let them in] (§2.1; at most once per 5 s per asker). The refusal itself is core's {@code TableBots.admit}.</li>
 * </ul>
 */
final class BotInteractions {
	private static final Map<UUID, Long> LAST_ASK = new HashMap<>();

	private BotInteractions() {}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(entity instanceof ServerPlayer target) || target == sp) {
				return InteractionResult.PASS;
			}
			if (!sp.getItemInHand(hand).is(CoreContent.CASINO_CARD) || !CasinoMode.isEnabled(sp)) {
				return InteractionResult.PASS;
			}
			return cardOnPlayer(sp, target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && !sp.isShiftKeyDown() && CasinoMode.isEnabled(sp)) {
				BotTables.at(sp.level(), hit.getBlockPos()).ifPresent(f -> askToJoin(sp, f));
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean cardOnPlayer(ServerPlayer host, ServerPlayer target) {
		Optional<BotTables.Found> found = BotTables.seated(host);
		if (found.isEmpty()) {
			return false;
		}
		BotTables.Found f = found.get();
		SettingsRules.Actor actor = f.actor(host);
		if (!actor.host()) {
			return false;
		}
		SettingsRules.View v = SettingsRules.view(actor, f.facts(), false);
		if (!v.mayEditAccess() || !v.privateAllowed() || BotsScreenPayload.TYPE == null || !ServerPlayNetworking.canSend(host, BotsScreenPayload.TYPE)) {
			return false;
		}
		TableSettings.remember(target);
		CompoundTag s = new CompoundTag();
		s.putLong("pos", f.pos().asLong());
		s.putString("title", f.blockEntity().getBlockState().getBlock().getDescriptionId());
		s.putString("id", target.getUUID().toString());
		s.putString("name", target.getName().getString());
		ServerPlayNetworking.send(host, new BotsScreenPayload("invite_confirm", true, s));
		return true;
	}

	private static void askToJoin(ServerPlayer asker, BotTables.Found f) {
		if (f.seated(asker.getUUID()) || f.bots().settings().policy() != SeatPolicy.BOTS_ONLY) {
			return;
		}
		UUID host = f.host();
		if (host == null || host.equals(asker.getUUID())) {
			return;
		}
		long now = asker.level().getGameTime();
		Long last = LAST_ASK.get(asker.getUUID());
		if (last != null && now - last < 100) {
			return;
		}
		LAST_ASK.put(asker.getUUID(), now);
		ServerPlayer hostPlayer = asker.level().getServer().getPlayerList().getPlayer(host);
		if (hostPlayer != null) {
			TableSettings.askHost(hostPlayer, asker.getName().getString());
		}
	}

	static void clear() {
		LAST_ASK.clear();
	}
}
