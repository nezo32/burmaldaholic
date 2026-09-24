package dev.nezo.burmaldaholic.games.extras.block;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plinko machine (GAME_DESIGN.md §11.4, UI.md §9). Risk Low/Medium/High, bet 1…tier max × {@code extras.plinko.maxBetFraction}
 * (chips only). The drop is drawn ({@code OddsService.play}, §14, RTP of the chosen risk) and settled at once; the client
 * animates the exact server path (12 rows × {@link #STEP_TICKS} ticks) and the chat line follows when the ball lands.
 */
public class PlinkoBlockEntity extends CasinoTableBlockEntity {
	public static final int STEP_TICKS = 4;
	public static final int DROP_TICKS = Plinko.ROWS * STEP_TICKS + 4;

	private record Pending(UUID player, long due, Component line) {}

	private final Map<UUID, CompoundTag> lastDrop = new HashMap<>();
	private final Map<UUID, Long> busyUntil = new HashMap<>();
	private final List<Pending> pending = new ArrayList<>();
	private int seq;

	public PlinkoBlockEntity(TableType<PlinkoBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public String gameId() {
		return ExtrasGames.PLINKO;
	}

	@Override
	protected int seatCount() {
		return 0;
	}

	static double[] table(Plinko.Risk risk) {
		ExtrasConfig.Plinko cfg = CasinoConfig.extras().plinko;
		return Plinko.table(switch (risk) {
			case LOW -> cfg.low;
			case MEDIUM -> cfg.medium;
			case HIGH -> cfg.high;
		}, risk);
	}

	private long maxFor(ServerPlayer player) {
		return ExtrasGames.fractionMax(player, CasinoConfig.extras().plinko.maxBetFraction);
	}

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		if (action.startsWith(dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMachine.PREFIX) && level instanceof ServerLevel sl) {
			// Plinko Battle entries (PVP.md §7.4): Start / Join / Start now / Leave
			Component err = dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMachine.onAction(player, action, args, sl, getBlockPos());
			if (err != null) {
				sendError(player, err);
			}
			syncViewers();
			return;
		}
		if (!action.equals("drop")) {
			return;
		}
		if (!CasinoConfig.extras().plinko.enabled) {
			sendError(player, ExtrasGames.error("disabled"));
			return;
		}
		Plinko.Risk risk = Plinko.Risk.parse(args.getStringOr("risk", ""));
		if (risk == null) {
			sendError(player, ExtrasGames.error("invalid_bet_position"));
			return;
		}
		long now = gameTime();
		if (busyUntil.getOrDefault(player.getUUID(), 0L) > now) {
			sendError(player, ExtrasGames.error("round_in_progress"));
			return;
		}
		double[] table = table(risk);
		long bet = args.getLongOr("amount", 0);
		Result<Long> placed = placeBet(player, bet, 1, maxFor(player), Payouts.floorPay(bet, Plinko.maxMultiplier(table)), true);
		if (!placed.isOk()) {
			return;
		}
		OddsContext ctx = ExtrasGames.odds(player, gameId(), bet);
		OddsService odds = OddsService.get();
		CasinoRng rng = odds.rng(ctx);
		Plinko.Drop drop = odds.play(ctx, Plinko.rtp(table), () -> Plinko.drop(rng, table), d -> Plinko.totalReturn(bet, d) < bet);
		long ret = Plinko.totalReturn(bet, drop);
		double edge = switch (risk) {
			case LOW -> HouseEdges.PLINKO_LOW;
			case MEDIUM -> HouseEdges.PLINKO_MEDIUM;
			case HIGH -> HouseEdges.PLINKO_HIGH;
		};
		settle(player.getUUID(), ret, r -> r.withEdge(edge).withTags(risk.id()));
		if (risk == Plinko.Risk.HIGH && (drop.bin() == 0 || drop.bin() == table.length - 1)) {
			CasinoAdvancements.grant(player, "plinko_edge"); // §19: bin 0 or 12 on Plinko High
		}
		long net = ret - bet;
		CompoundTag t = new CompoundTag();
		t.putInt("seq", ++seq);
		t.putInt("path", Plinko.encode(drop.path()));
		t.putInt("bin", drop.bin());
		t.putString("risk", risk.id());
		t.putDouble("mult", drop.multiplier());
		t.putLong("net", net);
		lastDrop.put(player.getUUID(), t);
		busyUntil.put(player.getUUID(), now + DROP_TICKS);
		pending.add(new Pending(player.getUUID(), now + DROP_TICKS, Component.translatable("gui.burmaldaholic.extras.plinko.result",
			Texts.decimal(Payouts.formatMultiplier(drop.multiplier())), ExtrasGames.resultLine(net))));
		syncViewers();
	}

	@Override
	protected void serverTick(ServerLevel level) {
		if (pending.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Pending p : new ArrayList<>(pending)) {
			if (p.due() <= now) {
				pending.remove(p);
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.player());
				if (player != null) {
					player.sendSystemMessage(p.line());
				}
			}
		}
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		tag.putLong("max", Math.min(tag.getLongOr("max", 1), maxFor(viewer)));
		ListTag tables = new ListTag();
		for (Plinko.Risk risk : Plinko.Risk.values()) {
			CompoundTag rt = new CompoundTag();
			rt.putString("risk", risk.id());
			double[] table = table(risk);
			for (int i = 0; i < table.length; i++) {
				rt.putDouble("m" + i, table[i]);
			}
			tables.add(rt);
		}
		tag.put("tables", tables);
		tag.putInt("step_ticks", STEP_TICKS);
		dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMachine.writeState(tag, viewer); // Plinko Battle entries
		CompoundTag last = lastDrop.get(viewer.getUUID());
		if (last != null) {
			tag.put("result", last);
		}
		return tag;
	}
}
