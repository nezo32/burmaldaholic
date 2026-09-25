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
import dev.nezo.burmaldaholic.games.extras.logic.ExtrasTiers;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim;
import dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoSync;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/**
 * Plinko machine (GAME_DESIGN.md §11.4, UI.md §9). Risk Low/Medium/High, bet 1…tier max × {@code extras.plinko.maxBetFraction}
 * (chips only). The drop is drawn ({@code OddsService.play}, §14, RTP of the chosen risk) and settled at once; the client
 * animates the exact server path (12 rows × {@link #STEP_TICKS} ticks) and the chat line follows when the ball lands.
 */
public class PlinkoBlockEntity extends CasinoTableBlockEntity {
	public static final int STEP_TICKS = 4;
	/** Until the ball lands in the screen's timeline (release, 12 rows, the fall into the bin; PlinkoAnim). */
	public static final int DROP_TICKS = (dev.nezo.burmaldaholic.games.extras.logic.anim.PlinkoAnim.landMs(STEP_TICKS * 50) + 49) / 50;

	private record Pending(UUID player, long due, Component line, long stake, long ret, boolean jackpot) {}

	/** Update-tag key of the in-world drop ({@link PlinkoSync}). */
	public static final String SYNC_KEY = "extras_plinko";
	/** The last published drop (client: from the update tag), or {@code null} before the first drop. */
	private @Nullable PlinkoSync sync;

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
		t.putLong("stake", bet); // the celebration's tier base (extras-pvp.md §0.4)
		lastDrop.put(player.getUUID(), t);
		busyUntil.put(player.getUUID(), now + DROP_TICKS);
		boolean jackpot = ExtrasTiers.plinkoJackpot(risk, drop.bin(), table.length);
		pending.add(new Pending(player.getUUID(), now + DROP_TICKS, Component.translatable("gui.burmaldaholic.extras.plinko.result",
			Texts.decimal(Payouts.formatMultiplier(drop.multiplier())), ExtrasGames.resultLine(net)), bet, ret, jackpot));
		publish(new PlinkoSync(seq, now, Plinko.encode(drop.path()), STEP_TICKS, PlinkoAnim.tier(drop.multiplier()), jackpot));
		syncViewers();
	}

	/** Sends the drop to every nearby client's machine renderer (extras-pvp.md §5.4): once, at the release. */
	private void publish(PlinkoSync s) {
		sync = s;
		setChanged();
		if (level instanceof ServerLevel serverLevel) {
			BlockState st = getBlockState();
			serverLevel.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
		}
	}

	/** The last published drop, or {@code null} (client: the in-world renderer reads it). */
	public @Nullable PlinkoSync plinkoSync() {
		return sync;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag t = new CompoundTag();
		if (sync != null) {
			t.putIntArray(SYNC_KEY, sync.encode());
		}
		return t;
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		if (level != null && level.isClientSide()) {
			sync = input.getIntArray(SYNC_KEY).map(PlinkoBlockEntity::decode).orElse(sync);
		}
	}

	private static @Nullable PlinkoSync decode(int[] data) {
		try {
			return PlinkoSync.decode(data);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The ball lands (the screen's landing): world particles for everyone around (vanilla), the celebration. */
	private void land(ServerLevel level, Pending p, @Nullable ServerPlayer player) {
		double x = worldPosition.getX() + 0.5;
		double y = worldPosition.getY() + 1.1;
		double z = worldPosition.getZ() + 0.5;
		if (p.jackpot()) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 20, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_TWINKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
		} else if (p.stake() > 0 && dev.nezo.burmaldaholic.core.anim.WinTier.of(p.ret(), p.stake(), dev.nezo.burmaldaholic.core.anim.WinTierTable.DEFAULT)
			.ordinal() >= dev.nezo.burmaldaholic.core.anim.WinTier.MEGA.ordinal()) {
			level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.2, 0.3, 0.2, 0.05);
		}
		if (player != null) {
			ExtrasGames.celebrate(player, gameId(), p.stake(), p.ret(), p.jackpot(), true);
		}
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
				land(level, p, player);
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
