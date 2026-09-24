package dev.nezo.burmaldaholic.games.extras.pvp.coin;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpEvents;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Machine-side entries of Coin Flip Duel (PVP.md §3.3.1, §4.5; task J-M1): the Lucky Coin used on a player opens
 * the set-up panel (client {@code CoinDuelSetupScreen}); the panel's "Throw down the gauntlet" calls
 * {@code PvpService.challenge}; the live duel screen's buttons (Double or nothing, let it ride, rematch, taunt)
 * call {@code decide / rematch / taunt}. Everything else (invites, escrow, the flip, timers, bots) is the engine.
 * {@link #openSetup} with no target offers the bot opponents (hub "Play vs bots…").
 */
public final class CoinDuelEntries {
	private CoinDuelEntries() {}

	/** Called once from {@code ExtrasPvpModes.register}. */
	public static void register() {
		CoinDuelNet.register(CoinDuelEntries::onAction);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Player target) || ExtrasModule.LUCKY_COIN == null || !player.getItemInHand(hand).is(ExtrasModule.LUCKY_COIN)
				|| player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				if (!CasinoMode.isEnabled(sp)) {
					return InteractionResult.PASS;
				}
				if (target instanceof ServerPlayer tp) {
					openSetup(sp, tp);
				}
			}
			return InteractionResult.SUCCESS;
		});
		PvpEvents.MATCH_SETTLED.register(CoinChains::onSettled);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CoinChains.clear());
	}

	static boolean modeEnabled() {
		return PvpModes.get(CoinDuelMode.ID).map(PvpMode::enabled).orElse(false);
	}

	/**
	 * Opens the set-up panel: against {@code target}, or (null) against a bot of a chosen Style. Public for the
	 * pvp hub ("New match…" / "Play vs bots…").
	 */
	public static void openSetup(ServerPlayer player, @Nullable ServerPlayer target) {
		if (!ExtrasGames.guard(player, modeEnabled())) {
			return;
		}
		if (target != null && target.getUUID().equals(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.pvp.error.self"));
			return;
		}
		CompoundTag s = new CompoundTag();
		s.putLong("balance", Economies.get().balance(player));
		s.putLong("min", Math.max(1, CasinoConfig.pvp().minStake));
		s.putLong("max", Math.max(1, ExtrasGames.tierMax(player)));
		s.putInt("rake_bp", CasinoConfig.pvp().rakeBasisPoints);
		s.putInt("max_doubles", CasinoConfig.pvp().coin.maxDoubles);
		if (target != null) {
			s.putString("target", target.getUUID().toString());
			s.putString("target_name", target.getName().getString());
			HeadToHead h2h = Pvp.service().record(player.getUUID(), target.getUUID());
			s.putInt("h2h_wins", h2h.wins());
			s.putInt("h2h_losses", h2h.losses());
		}
		s.putBoolean("bots", botsAllowed());
		CoinDuelNet.send(player, "setup", s, Component.empty());
	}

	static boolean botsAllowed() {
		return CasinoConfig.bots().enabled && CasinoConfig.bots().pvp.maxPerMatch > 0;
	}

	static void onAction(ServerPlayer player, CoinDuelNet.Action payload) {
		CompoundTag a = payload.args();
		PvpService pvp = Pvp.service();
		switch (payload.action()) {
			case "challenge" -> challenge(player, a);
			case "decide" -> {
				String decision = a.getStringOr("decision", "");
				long option = a.getLongOr("option", 0);
				if ((decision.equals(CoinChain.DON_OFFER) && option >= 0 && option <= 2)
					|| (decision.equals(CoinChain.LET_IT_RIDE) && (option == 0 || option == 1))) {
					pvp.decide(player, decision, option);
				}
			}
			case "rematch" -> pvp.rematch(player, a.getStringOr("id", ""));
			case "withdraw" -> pvp.withdraw(player, a.getStringOr("id", ""));
			case "taunt" -> {
				Result<Void> r = pvp.taunt(player, a.getIntOr("line", 0));
				if (!r.isOk()) {
					message(player, r.error());
				}
			}
			default -> {
			}
		}
	}

	private static void challenge(ServerPlayer player, CompoundTag a) {
		if (!ExtrasGames.guard(player, modeEnabled())) {
			return;
		}
		long stake = a.getLongOr("stake", 0);
		CoinDuelMode.Params params = new CoinDuelMode.Params(stake, a.getBooleanOr("heads", true));
		String invalid = CoinDuelMode.validate(params, CasinoConfig.pvp().minStake);
		if (invalid != null) {
			message(player, Component.translatable(invalid, dev.nezo.burmaldaholic.core.text.Texts.chips(CasinoConfig.pvp().minStake)));
			return;
		}
		PvpService.Opponent opponent;
		String target = a.getStringOr("target", "");
		if (!target.isEmpty()) {
			UUID id;
			try {
				id = UUID.fromString(target);
			} catch (IllegalArgumentException e) {
				return;
			}
			opponent = new PvpService.Opponent.PlayerTarget(id);
		} else if (botsAllowed() && !a.getStringOr("bot", "").isEmpty()) {
			opponent = new PvpService.Opponent.BotTarget(BotDifficulty.byId(a.getStringOr("bot", ""), BotDifficulty.NORMAL));
		} else {
			message(player, Component.translatable("gui.burmaldaholic.pvp.error.needs_opponent"));
			return;
		}
		Result<PvpMatch> r = Pvp.service().challenge(player, CoinDuelMode.ID, new CoinDuelMode().encodeParams(params), stake, opponent);
		if (!r.isOk()) {
			message(player, r.error());
			return;
		}
		CompoundTag close = new CompoundTag();
		close.putBoolean("close", true);
		CoinDuelNet.send(player, "message", close, Component.empty());
	}

	private static void message(ServerPlayer player, @Nullable Component error) {
		Component m = error == null ? Component.translatable("gui.burmaldaholic.error.disabled") : error;
		CoinDuelNet.send(player, "message", new CompoundTag(), m);
		player.sendOverlayMessage(m);
	}
}
