package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasErrorPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasScreenPayload;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Shared server helpers of the extras games: limits, pawn stakes, screen sync, result lines. */
public final class ExtrasGames {
	private ExtrasGames() {}

	/** Game ids for transactions and {@code PLAY_RESOLVED} (VIP cashback knows them; PvP dice has no house edge). */
	public static final String COIN = "coin_flip";
	public static final String WHEEL = "wheel_of_fortune";
	public static final String SCRATCH = "scratch_card";
	public static final String PLINKO = "plinko";
	public static final String DICE = "dice_duel";
	public static final String DICE_PVP = "dice_duel_pvp";

	public static Component error(String key, Object... args) {
		return Component.translatable("gui.burmaldaholic.error." + key, args);
	}

	/** Casino mode + per-game switch; sends the reason to the player when it fails. */
	public static boolean guard(ServerPlayer player, boolean gameEnabled) {
		if (!CasinoMode.isEnabled(player)) {
			player.sendOverlayMessage(error("casino_off"));
			return false;
		}
		if (!gameEnabled) {
			player.sendOverlayMessage(error("disabled"));
			return false;
		}
		return true;
	}

	public static long tierMax(ServerPlayer player) {
		return CoreServices.vip().maxBet(player.level().getServer(), player.getUUID());
	}

	/** {@code max(1, floor(tier max × fraction))} (wheel: ½, plinko: ⅕). */
	public static long fractionMax(ServerPlayer player, double fraction) {
		return Math.max(1, (long) Math.floor(tierMax(player) * fraction));
	}

	public static OddsContext odds(ServerPlayer player, String game, long bet) {
		return new OddsContext(player.getUUID(), game, bet);
	}

	/**
	 * Takes a pawn stake (§4.3) of kind {@code item} / {@code xp} / {@code hearts}. {@code amount} = levels or hearts.
	 * Refuses (and returns the escrow) if V exceeds {@code maxValue}.
	 */
	public static Result<Stake> takePawn(ServerPlayer player, String game, String kind, long amount, long maxValue) {
		int n = (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
		Result<Stake> r = switch (kind) {
			case "item" -> Stakes.heldItem(player, game);
			case "xp" -> Stakes.xp(player, game, n);
			case "hearts" -> Stakes.hearts(player, game, n);
			default -> Result.fail(error("invalid_amount"));
		};
		if (r.isOk() && maxValue > 0 && r.value().value() > maxValue) {
			Stakes.refund(player, r.value());
			return Result.fail(error("pawn_too_valuable", Texts.chips(r.value().value())));
		}
		return r;
	}

	/** Takes a chip stake ({@code kind = chips}) or a pawn stake. */
	public static Result<Stake> takeStake(ServerPlayer player, String game, CompoundTag args, long maxValue, boolean pawnAllowed) {
		String kind = args.getStringOr("stake", "chips");
		long amount = args.getLongOr("amount", 0);
		if (kind.equals("chips")) {
			return Stakes.chips(player, game, amount, 1, maxValue);
		}
		if (!pawnAllowed) {
			return Result.fail(error("invalid_bet_position"));
		}
		return takePawn(player, game, kind, amount, maxValue);
	}

	/** Pawn availability flags + current level for the stake selector (UI.md §9). */
	public static void writePawnInfo(CompoundTag tag, ServerPlayer player) {
		WagerConfig w = CasinoConfig.wager();
		tag.putBoolean("pawn_item", w.pawnEnabled && w.items.enabled);
		tag.putBoolean("pawn_xp", w.pawnEnabled && w.xp.enabled);
		tag.putBoolean("pawn_hearts", w.pawnEnabled && w.hearts.enabled);
		tag.putInt("xp_level", player.experienceLevel);
		tag.putInt("hearts_max", w.hearts.maxPerBet);
		tag.putLong("held_value", Stakes.appraise(player.getMainHandItem()));
	}

	public static void writeLimits(CompoundTag tag, ServerPlayer player, long max) {
		tag.putLong("balance", Economies.get().balance(player));
		tag.putLong("min", 1);
		tag.putLong("max", Math.max(1, max));
	}

	/** Colored result line from the net of a settled round: win / loss / push (UI.md §0.1 colors). */
	public static MutableComponent resultLine(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net)).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("gui.burmaldaholic.common.result.push").withStyle(ChatFormatting.GRAY);
	}

	public static void send(ServerPlayer player, String screen, boolean open, CompoundTag state) {
		if (ServerPlayNetworking.canSend(player, ExtrasScreenPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ExtrasScreenPayload(screen, open, state));
		}
	}

	/** Error line on the open extras screen and in the action bar. */
	public static void sendError(ServerPlayer player, Component message) {
		if (ServerPlayNetworking.canSend(player, ExtrasErrorPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ExtrasErrorPayload(message));
		}
		player.sendOverlayMessage(message);
	}

	public static void playSound(ServerPlayer player, SoundEvent sound, float pitch) {
		if (sound != null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8f, pitch);
		}
	}

	/**
	 * GAME_DESIGN.md §11.2/§11.3: the Creeper segment / Creeper card triggers the chaos {@code mob_wave} event.
	 * The chaos module publishes its trigger through Fabric's ObjectShare ({@code burmaldaholic:chaos/trigger},
	 * {@code BiFunction<ServerPlayer, String, String>}, argument {@code "event@source"}); absent → no-op.
	 */
	@SuppressWarnings("unchecked")
	public static void requestMobWave(ServerPlayer player, String source) {
		Object shared = FabricLoader.getInstance().getObjectShare().get("burmaldaholic:chaos/trigger");
		if (!(shared instanceof BiFunction<?, ?, ?> fn)) {
			return;
		}
		try {
			((BiFunction<ServerPlayer, String, String>) fn).apply(player, "mob_wave@" + source);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("[extras] chaos mob_wave trigger failed", e);
		}
	}
}
