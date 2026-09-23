package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.PawnRules;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coin Flip with the Lucky Coin (GAME_DESIGN.md §11.1, UI.md §9): chips or a pawn stake (§4.3), and the
 * Hardcore Soul Wager (§4.4). A round is staked, drawn ({@code OddsService.play}, §14 streak re-draw) and
 * settled in the same tick; the flip animation on the client is cosmetic, so a disconnect never leaves a
 * round open. Soul Wager: confirmation on the screen, then a 5-second hold that the server enforces
 * ({@code soul_arm} … {@code soul} at least {@link #SOUL_HOLD_TICKS} later).
 */
public final class CoinFlipGame {
	public static final String SCREEN = "coin";
	public static final int SOUL_HOLD_TICKS = 100;
	/** Network jitter allowance on the 5 s hold. */
	private static final int SOUL_HOLD_SLACK = 6;
	private static final int SOUL_ARM_WINDOW = 20 * 60;

	private static final Map<UUID, Long> SOUL_ARMED = new HashMap<>();
	private static final Map<UUID, Integer> SEQ = new HashMap<>();

	private CoinFlipGame() {}

	private static boolean enabled() {
		return CasinoConfig.extras().coinFlip.enabled;
	}

	public static void open(ServerPlayer player) {
		if (!ExtrasGames.guard(player, enabled())) {
			return;
		}
		ExtrasGames.send(player, SCREEN, true, state(player, null));
	}

	static CompoundTag state(ServerPlayer player, CompoundTag result) {
		CompoundTag tag = new CompoundTag();
		ExtrasGames.writeLimits(tag, player, ExtrasGames.tierMax(player));
		ExtrasGames.writePawnInfo(tag, player);
		tag.putDouble("payout", CasinoConfig.extras().coinFlip.payout);
		boolean soul = Stakes.soulWagerAvailable(player);
		tag.putBoolean("soul", soul);
		if (soul) {
			WagerConfig w = CasinoConfig.wager();
			tag.putLong("soul_value", PawnRules.soulValue(Economies.get().balance(player), w.soul.minValue));
		}
		if (result != null) {
			tag.put("result", result);
		}
		return tag;
	}

	public static void action(ServerPlayer player, String action, CompoundTag args) {
		if (!ExtrasGames.guard(player, enabled())) {
			return;
		}
		switch (action) {
			case "flip" -> flip(player, args);
			case "soul_arm" -> {
				if (!Stakes.soulWagerAvailable(player)) {
					ExtrasGames.sendError(player, ExtrasGames.error("disabled"));
					return;
				}
				SOUL_ARMED.put(player.getUUID(), now(player));
			}
			case "soul" -> soul(player, args);
			case "soul_cancel" -> {
				SOUL_ARMED.remove(player.getUUID());
				player.sendSystemMessage(Component.translatable("gui.burmaldaholic.extras.soul.cancelled").withStyle(ChatFormatting.GRAY));
			}
			default -> {
			}
		}
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	private static CoinFlip.Result draw(ServerPlayer player, OddsContext ctx, CoinFlip.Side pick, double payout) {
		OddsService odds = OddsService.get();
		CasinoRng rng = odds.rng(ctx);
		return odds.play(ctx, CoinFlip.rtp(payout), () -> CoinFlip.flip(rng, pick), r -> !r.win());
	}

	private static void flip(ServerPlayer player, CompoundTag args) {
		CoinFlip.Side pick = CoinFlip.Side.parse(args.getStringOr("side", ""));
		if (pick == null) {
			ExtrasGames.sendError(player, ExtrasGames.error("invalid_bet_position"));
			return;
		}
		Result<Stake> taken = ExtrasGames.takeStake(player, ExtrasGames.COIN, args, 0, true);
		if (!taken.isOk()) {
			ExtrasGames.sendError(player, taken.error());
			return;
		}
		Stake stake = taken.value();
		double payout = CasinoConfig.extras().coinFlip.payout;
		CoinFlip.Result r = draw(player, ExtrasGames.odds(player, ExtrasGames.COIN, stake.value()), pick, payout);
		long winnings = CoinFlip.winnings(stake.value(), r.win(), payout);
		ExtrasGames.playSound(player, ExtrasModule.COIN_FLIP_SOUND, 1.0f);
		Stakes.settle(player, stake, r.win() ? Stakes.Outcome.WIN : Stakes.Outcome.LOSS, winnings);
		ExtrasGames.send(player, SCREEN, false, state(player, result(player, r, r.win() ? winnings : -stake.value(), stake.isPawn(), false)));
	}

	private static void soul(ServerPlayer player, CompoundTag args) {
		Long armed = SOUL_ARMED.remove(player.getUUID());
		long now = now(player);
		if (armed == null || now - armed < SOUL_HOLD_TICKS - SOUL_HOLD_SLACK || now - armed > SOUL_ARM_WINDOW) {
			ExtrasGames.sendError(player, Component.translatable("gui.burmaldaholic.extras.soul.hold"));
			return;
		}
		CoinFlip.Side pick = CoinFlip.Side.parse(args.getStringOr("side", ""));
		if (pick == null) {
			ExtrasGames.sendError(player, ExtrasGames.error("invalid_bet_position"));
			return;
		}
		Result<Stake> taken = Stakes.soul(player, ExtrasGames.COIN);
		if (!taken.isOk()) {
			ExtrasGames.sendError(player, taken.error());
			return;
		}
		Stake stake = taken.value();
		CoinFlip.Result r = draw(player, ExtrasGames.odds(player, ExtrasGames.COIN, stake.value()), pick, 1.0);
		ExtrasGames.playSound(player, ExtrasModule.COIN_FLIP_SOUND, 0.7f);
		if (r.win()) {
			Stakes.settle(player, stake, Stakes.Outcome.WIN, stake.value());
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.soul.won", Texts.chips(stake.value())).withStyle(ChatFormatting.GOLD));
			player.level().getServer().getPlayerList().broadcastSystemMessage(
				Component.translatable("msg.burmaldaholic.extras.soul.broadcast_won", player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
			ExtrasGames.send(player, SCREEN, false, state(player, result(player, r, stake.value(), false, true)));
		} else {
			// Screen first: the loss kills the player (death screen replaces ours).
			ExtrasGames.send(player, SCREEN, false, state(player, result(player, r, 0, false, true)));
			Stakes.settle(player, stake, Stakes.Outcome.LOSS, 0);
		}
	}

	private static CompoundTag result(ServerPlayer player, CoinFlip.Result r, long net, boolean pawn, boolean soul) {
		int seq = SEQ.merge(player.getUUID(), 1, Integer::sum);
		CompoundTag t = new CompoundTag();
		t.putInt("seq", seq);
		t.putString("pick", r.pick().id());
		t.putString("landed", r.landed().id());
		t.putBoolean("win", r.win());
		t.putLong("net", net);
		t.putBoolean("pawn", pawn);
		t.putBoolean("soul", soul);
		return t;
	}

	public static void forget(UUID player) {
		SOUL_ARMED.remove(player);
		SEQ.remove(player);
	}
}
