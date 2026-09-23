package dev.nezo.burmaldaholic.games.extras.block;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wheel of Fortune machine (GAME_DESIGN.md §11.2, UI.md §9). Single bet 1…tier max × {@code extras.wheel.maxBetFraction};
 * chips go through the table API ({@link #placeBet}/{@link #settle}, so owned casinos work), pawn stakes (§4.3)
 * through {@link Stakes}. A spin is staked, drawn ({@code OddsService.play}, §14) and settled in the same tick;
 * the client animates the wheel onto the drawn index for {@link #SPIN_TICKS} and the chat line (and the Creeper's
 * chaos hook) follows when the animation ends.
 */
public class WheelBlockEntity extends CasinoTableBlockEntity {
	public static final int SPIN_TICKS = 80;

	private record Pending(UUID player, long due, Component line, boolean creeper) {}

	private final Map<UUID, CompoundTag> lastSpin = new HashMap<>();
	private final Map<UUID, Long> busyUntil = new HashMap<>();
	private final List<Pending> pending = new ArrayList<>();
	private int seq;

	public WheelBlockEntity(TableType<WheelBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public String gameId() {
		return ExtrasGames.WHEEL;
	}

	@Override
	protected int seatCount() {
		return 0;
	}

	static Wheel wheel() {
		return new Wheel(CasinoConfig.extras().wheel.segments, CasinoConfig.extras().wheel.multipliers);
	}

	private long maxFor(ServerPlayer player) {
		return ExtrasGames.fractionMax(player, CasinoConfig.extras().wheel.maxBetFraction);
	}

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		if (!action.equals("spin")) {
			return;
		}
		if (!CasinoConfig.extras().wheel.enabled) {
			sendError(player, ExtrasGames.error("disabled"));
			return;
		}
		long now = gameTime();
		if (busyUntil.getOrDefault(player.getUUID(), 0L) > now) {
			sendError(player, ExtrasGames.error("round_in_progress"));
			return;
		}
		Wheel wheel = wheel();
		long max = maxFor(player);
		String kind = args.getStringOr("stake", "chips");
		long amount = args.getLongOr("amount", 0);
		Stake pawn = null;
		long value;
		if (kind.equals("chips")) {
			Result<Long> bet = placeBet(player, amount, 1, max, Payouts.floorPay(amount, wheel.maxMultiplier()), true);
			if (!bet.isOk()) {
				return;
			}
			value = amount;
		} else {
			Result<Stake> r = ExtrasGames.takePawn(player, gameId(), kind, amount, max);
			if (!r.isOk()) {
				sendError(player, r.error());
				return;
			}
			pawn = r.value();
			value = pawn.value();
		}
		OddsContext ctx = ExtrasGames.odds(player, gameId(), value);
		OddsService odds = OddsService.get();
		CasinoRng rng = odds.rng(ctx);
		Wheel.Spin spin = odds.play(ctx, wheel.rtp(), () -> wheel.spin(rng), s -> Wheel.totalReturn(value, s) < value);
		long ret = Wheel.totalReturn(value, spin);
		if (pawn == null) {
			settle(player.getUUID(), ret);
		} else if (ret > value) {
			Stakes.settle(player, pawn, Stakes.Outcome.WIN, ret - value);
		} else if (ret == value) {
			Stakes.settle(player, pawn, Stakes.Outcome.PUSH, 0);
		} else {
			// Pawn forfeited; a partial return (Half back) is paid in chips so the pawn keeps the chip RTP.
			Stakes.settle(player, pawn, Stakes.Outcome.LOSS, 0);
			if (ret > 0) {
				Economies.get().deposit(player, ret, Transaction.payout(gameId()));
			}
		}
		long net = ret - value;
		CompoundTag t = new CompoundTag();
		t.putInt("seq", ++seq);
		t.putInt("index", spin.index());
		t.putString("code", spin.code());
		t.putLong("net", net);
		lastSpin.put(player.getUUID(), t);
		busyUntil.put(player.getUUID(), now + SPIN_TICKS);
		Component segment = Component.translatable(Wheel.segmentKey(spin.code()));
		pending.add(new Pending(player.getUUID(), now + SPIN_TICKS, Component.translatable("gui.burmaldaholic.extras.wheel.result", segment, ExtrasGames.resultLine(net)), spin.creeper()));
		syncViewers();
	}

	@Override
	protected void serverTick(ServerLevel level) {
		if (pending.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Pending p : new ArrayList<>(pending)) {
			if (p.due() > now) {
				continue;
			}
			pending.remove(p);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.player());
			if (player == null) {
				continue;
			}
			player.sendSystemMessage(p.line());
			if (p.creeper()) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.extras.wheel.creeper").withStyle(ChatFormatting.DARK_GREEN));
				ExtrasGames.requestMobWave(player, "wheel");
			}
		}
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		tag.putLong("max", Math.min(tag.getLongOr("max", 1), maxFor(viewer)));
		Wheel wheel = wheel();
		ListTag segs = new ListTag();
		wheel.segments().forEach(s -> segs.add(StringTag.valueOf(s)));
		tag.put("segments", segs);
		CompoundTag mult = new CompoundTag();
		for (String code : Wheel.CODES) {
			mult.putDouble(code, wheel.multiplier(code));
		}
		tag.put("multipliers", mult);
		tag.putInt("spin_ticks", SPIN_TICKS);
		ExtrasGames.writePawnInfo(tag, viewer);
		CompoundTag last = lastSpin.get(viewer.getUUID());
		if (last != null) {
			tag.put("result", last);
		}
		return tag;
	}
}
