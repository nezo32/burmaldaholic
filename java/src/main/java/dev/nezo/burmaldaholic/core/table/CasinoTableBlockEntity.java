package dev.nezo.burmaldaholic.core.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative state of one casino table/machine. Subclass per game and implement
 * {@link #onAction} and {@link #writeClientState}; the base class provides:
 *
 * <ul>
 *   <li><b>Seats</b> ({@link #seatCount()}, {@link #sit}, {@link #leave}); generic client actions
 *       {@code "sit"} / {@code "leave"} are handled for you. Seated players who disconnect or walk
 *       further than {@code multiplayer.tableLeaveDistance} are removed via {@link #onPlayerLeft}
 *       (auto-complete their round there: e.g. blackjack stands).</li>
 *   <li><b>Money</b>: {@link #placeBet} (validates min / table max / VIP tier max / funds / owned-table
 *       rules, debits, reserves bankroll exposure) and {@link #settle} (pays out, releases the
 *       reservation, fires {@code PLAY_RESOLVED}). Open stakes are saved with the block entity and
 *       <b>refunded</b> automatically if the table is loaded again with an unfinished round (server
 *       restart, §4.1) or broken.</li>
 *   <li><b>Phases &amp; timers</b>: {@link #setPhase}, {@link #startTimer} → {@link #onTimer} (world time).</li>
 *   <li><b>Sync</b>: {@link #syncViewers()} after every change; {@link #baseState} for common fields;
 *       {@link #sendError} shows an error line on the client screen.</li>
 * </ul>
 *
 * Keep the actual rules in pure Java ({@code games/<name>/logic}) so they are unit-testable.
 */
public abstract class CasinoTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private static final String STAKES_KEY = "burmaldaholic_open_stakes";

	/** A confirmed, not yet settled bet of one player at this table. */
	public record OpenStake(UUID player, long amount, long reserved, String bankroll) {
		public static final Codec<OpenStake> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(OpenStake::player),
			Codec.LONG.fieldOf("amount").forGetter(OpenStake::amount),
			Codec.LONG.fieldOf("reserved").forGetter(OpenStake::reserved),
			Codec.STRING.fieldOf("bankroll").forGetter(OpenStake::bankroll)
		).apply(i, OpenStake::new));

		boolean house() {
			return bankroll.isEmpty();
		}
	}

	public enum LeaveReason {
		/** Pressed "Leave". */
		LEFT,
		/** Disconnected (auto-complete the round with the default action, §4.1). */
		DISCONNECT,
		/** Walked further than {@code multiplayer.tableLeaveDistance}. */
		TOO_FAR,
		/** Table broken / unloaded. */
		REMOVED
	}

	private final TableType<?> tableType;
	private @Nullable TableSeats seats;
	private final Map<UUID, OpenStake> openStakes = new LinkedHashMap<>();
	private final Map<String, Long> timers = new HashMap<>();
	private String phase = "idle";
	private boolean refundPending;

	protected CasinoTableBlockEntity(TableType<?> tableType, BlockPos pos, BlockState state) {
		super(tableType.blockEntityType(), pos, state);
		this.tableType = tableType;
	}

	public TableType<?> tableType() {
		return tableType;
	}

	// ---- game hooks ---------------------------------------------------------------------------

	/** Handle a client action. {@code args} is untrusted input. Casino mode, range and "has this table open" are checked. */
	public abstract void onAction(ServerPlayer player, String action, CompoundTag args);

	/** Viewer-specific snapshot for the client screen (start from {@link #baseState}). Hide secrets! */
	public abstract CompoundTag writeClientState(ServerPlayer viewer);

	/** Number of seats (1 = single-player machine, 0 = no seating e.g. cashier). Called once. */
	protected int seatCount() {
		return 1;
	}

	/** Game id used for transactions and {@code PLAY_RESOLVED} (default: the owning module id). */
	public String gameId() {
		return tableType.moduleId();
	}

	/** Table minimum bet (default 1). */
	protected long minBet() {
		return 1;
	}

	/** Table maximum bet; ≤ 0 = only the VIP tier max applies (default). */
	protected long tableMaxBet() {
		return 0;
	}

	/** A timer started with {@link #startTimer} expired. */
	protected void onTimer(String id) {}

	/**
	 * A seated player left (button, disconnect, distance, table removed). Override to auto-complete their
	 * part of the round (GAME_DESIGN.md §4.1: default action such as "stand", then {@link #settle}).
	 * The default REFUNDS any open stake, so a game that does nothing here never keeps chips hostage.
	 */
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		if (openStakes.containsKey(player)) {
			refund(player);
		}
	}

	/** Whether a seated player may be removed for distance right now (default: only when they have no open stake). */
	protected boolean canLeaveNow(UUID player) {
		return !openStakes.containsKey(player);
	}

	/** Called every server tick after core's bookkeeping. */
	protected void serverTick(ServerLevel level) {}

	// ---- seats --------------------------------------------------------------------------------

	public final TableSeats seats() {
		if (seats == null) {
			seats = new TableSeats(seatCount());
		}
		return seats;
	}

	/** Seats the player; sends {@code gui.burmaldaholic.error.table_full} if full. */
	public boolean sit(ServerPlayer player) {
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent() && owned.get().owner().equals(player.getUUID())) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.owner_cannot_play"));
			return false;
		}
		OptionalInt seat = seats().sit(player.getUUID(), player.getName().getString());
		if (seat.isEmpty()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.table_full"));
			return false;
		}
		syncViewers();
		return true;
	}

	public void leave(UUID player, LeaveReason reason) {
		if (seats().leave(player).isPresent()) {
			onPlayerLeft(player, reason);
			syncViewers();
		}
	}

	public boolean isSeated(ServerPlayer player) {
		return seats().isSeated(player.getUUID());
	}

	// ---- money --------------------------------------------------------------------------------

	/** The owned-casino record of this table, if any (multiplayer module). */
	public Optional<OwnedTable> ownership() {
		return level instanceof ServerLevel serverLevel ? CoreServices.tableOwnership().owner(serverLevel, worldPosition) : Optional.empty();
	}

	/** Effective limits for a player: [min, max] after table, owner settings and VIP tier. */
	public long[] limitsFor(ServerPlayer player) {
		long min = minBet();
		long tableMax = tableMaxBet();
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			if (owned.get().minBet() > 0) {
				min = Math.max(min, owned.get().minBet());
			}
			if (owned.get().maxBet() > 0) {
				tableMax = tableMax > 0 ? Math.min(tableMax, owned.get().maxBet()) : owned.get().maxBet();
			}
		}
		return new long[] {min, BetLimits.maxBet(player, tableMax)};
	}

	/** Same as {@link #placeBet(ServerPlayer, long, long, long, long, boolean)} with the table's own limits. */
	protected Result<Long> placeBet(ServerPlayer player, long amount, long worstCasePayout) {
		return placeBet(player, amount, minBet(), tableMaxBet(), worstCasePayout, true);
	}

	/**
	 * Validates and debits a bet (adds to the player's open stake at this table).
	 *
	 * @param worstCasePayout max total the house could pay for this bet (§18.2 solvency; ignored at house tables)
	 * @param checkLimits     false for doubles/splits/odds, which may exceed the max (§6.4) — funds are still checked
	 * @return the player's total open stake, or the translated error (already sent to the player's screen)
	 */
	protected Result<Long> placeBet(ServerPlayer player, long amount, long min, long tableMax, long worstCasePayout, boolean checkLimits) {
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			OwnedTable o = owned.get();
			if (o.owner().equals(player.getUUID())) {
				return fail(player, Component.translatable("gui.burmaldaholic.error.owner_cannot_play"));
			}
			if (!o.open()) {
				return fail(player, Component.translatable("gui.burmaldaholic.error.table_closed"));
			}
			if (o.minBet() > 0) {
				min = Math.max(min, o.minBet());
			}
			if (o.maxBet() > 0) {
				tableMax = tableMax > 0 ? Math.min(tableMax, o.maxBet()) : o.maxBet();
			}
		}
		Component err = checkLimits ? BetLimits.validate(player, amount, min, tableMax)
			: (amount <= 0 ? Component.translatable("gui.burmaldaholic.error.invalid_amount")
			: (Economies.get().balance(player) < amount
				? Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))) : null));
		if (err != null) {
			return fail(player, err);
		}
		MinecraftServer server = player.level().getServer();
		Economy eco = Economies.get();
		String bankroll = owned.map(OwnedTable::bankrollId).orElse("");
		long reserve = 0;
		if (!bankroll.isEmpty()) {
			reserve = Math.max(0, worstCasePayout);
			if (!eco.bankrolls(server).reserve(bankroll, reserve)) {
				long available = eco.bankrolls(server).get(bankroll).map(Economy.BankrollInfo::available).orElse(0L);
				return fail(player, Component.translatable(available <= 0 ? "gui.burmaldaholic.error.house_broke" : "gui.burmaldaholic.error.exposure"));
			}
		}
		AccountId bank = bankroll.isEmpty() ? AccountId.HOUSE : AccountId.bankroll(bankroll);
		Economy.TxResult tx = eco.transfer(server, AccountId.player(player.getUUID()), bank, amount, Transaction.bet(gameId()));
		if (!tx.ok()) {
			if (reserve > 0) {
				eco.bankrolls(server).release(bankroll, reserve);
			}
			return fail(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
		}
		OpenStake prev = openStakes.get(player.getUUID());
		OpenStake next = prev == null ? new OpenStake(player.getUUID(), amount, reserve, bankroll)
			: new OpenStake(player.getUUID(), prev.amount() + amount, prev.reserved() + reserve, bankroll);
		openStakes.put(player.getUUID(), next);
		setChanged();
		return Result.ok(next.amount());
	}

	/** Total open stake of a player at this table (0 if none). */
	public long stakeOf(UUID player) {
		OpenStake s = openStakes.get(player);
		return s == null ? 0 : s.amount();
	}

	public List<OpenStake> openStakes() {
		return List.copyOf(openStakes.values());
	}

	/**
	 * Settles the player's whole open stake with a total {@code payout} (0 = lost, stake = push,
	 * more = win; stake included). Pays from the house or the bankroll, releases the reservation and
	 * fires {@code PLAY_RESOLVED} (when the player is online). Works for offline players.
	 */
	public void settle(UUID player, long payout) {
		OpenStake s = openStakes.remove(player);
		if (s == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		MinecraftServer server = serverLevel.getServer();
		Economy eco = Economies.get();
		if (!s.house()) {
			eco.bankrolls(server).release(s.bankroll(), s.reserved());
		}
		if (payout > 0) {
			AccountId bank = s.house() ? AccountId.HOUSE : AccountId.bankroll(s.bankroll());
			Economy.TxResult tx = eco.transfer(server, bank, AccountId.player(player), payout, Transaction.payout(gameId()));
			if (!tx.ok()) {
				// Should not happen (exposure was reserved); never leave the player unpaid.
				Burmaldaholic.LOGGER.error("Bankroll {} could not pay {} to {} at {}", s.bankroll(), payout, player, worldPosition);
				eco.transfer(server, AccountId.HOUSE, AccountId.player(player), payout, Transaction.payout(gameId()));
			}
		}
		setChanged();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(online, new CasinoEvents.PlayResult(gameId(), s.amount(), payout));
		}
		if (CasinoConfig.debug().logRounds) {
			Burmaldaholic.LOGGER.info("[round] {} at {}: player {} staked {} payout {}", gameId(), worldPosition, player, s.amount(), payout);
		}
	}

	/** Returns the player's open stake without a result (no PLAY_RESOLVED). */
	public void refund(UUID player) {
		OpenStake s = openStakes.remove(player);
		if (s == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		refundStake(serverLevel.getServer(), s);
		setChanged();
	}

	private void refundStake(MinecraftServer server, OpenStake s) {
		Economy eco = Economies.get();
		AccountId bank = s.house() ? AccountId.HOUSE : AccountId.bankroll(s.bankroll());
		if (!s.house()) {
			eco.bankrolls(server).release(s.bankroll(), s.reserved());
		}
		if (!eco.transfer(server, bank, AccountId.player(s.player()), s.amount(), Transaction.refund(gameId())).ok()) {
			eco.transfer(server, AccountId.HOUSE, AccountId.player(s.player()), s.amount(), Transaction.refund(gameId()));
		}
		ServerPlayer online = server.getPlayerList().getPlayer(s.player());
		if (online != null) {
			online.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.round_refunded", Texts.chipsAcc(s.amount())));
		}
	}

	private Result<Long> fail(ServerPlayer player, Component error) {
		sendError(player, error);
		return Result.fail(error);
	}

	// ---- phases & timers ----------------------------------------------------------------------

	public String phase() {
		return phase;
	}

	protected void setPhase(String phase) {
		this.phase = phase;
	}

	/** Starts (or restarts) timer {@code id}; {@link #onTimer} fires after {@code ticks} of world time. */
	protected void startTimer(String id, int ticks) {
		timers.put(id, gameTime() + Math.max(0, ticks));
	}

	protected void cancelTimer(String id) {
		timers.remove(id);
	}

	/** Remaining ticks of a timer, or -1 if not running. */
	public long ticksLeft(String id) {
		Long deadline = timers.get(id);
		return deadline == null ? -1 : Math.max(0, deadline - gameTime());
	}

	protected long gameTime() {
		return level == null ? 0 : level.getGameTime();
	}

	// ---- ticking ------------------------------------------------------------------------------

	/** Called by {@link CasinoTableBlock}'s ticker on the server. */
	public final void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if (refundPending) {
			refundPending = false;
			if (CasinoConfig.core().roundTimeoutRefund) {
				new ArrayList<>(openStakes.values()).forEach(s -> {
					Burmaldaholic.LOGGER.info("Refunding unfinished round at {}: {} chips to {}", worldPosition, s.amount(), s.player());
					refundStake(server, s);
				});
			}
			openStakes.clear();
			setChanged();
		}
		if (!timers.isEmpty()) {
			long now = level.getGameTime();
			for (Map.Entry<String, Long> e : new ArrayList<>(timers.entrySet())) {
				if (e.getValue() <= now && timers.get(e.getKey()) != null && timers.get(e.getKey()).equals(e.getValue())) {
					timers.remove(e.getKey());
					onTimer(e.getKey());
				}
			}
		}
		if (server.getTickCount() % 20 == 0 && seats != null && !seats.isEmpty()) {
			double max = CasinoConfig.multiplayer().tableLeaveDistance;
			for (TableSeats.Seat seat : seats.occupied()) {
				ServerPlayer p = server.getPlayerList().getPlayer(seat.player());
				if (p == null || p.isRemoved()) {
					leave(seat.player(), LeaveReason.DISCONNECT);
				} else if ((p.level() != level || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) > max * max) && canLeaveNow(seat.player())) {
					leave(seat.player(), LeaveReason.TOO_FAR);
				}
			}
		}
		serverTick(level);
	}

	/** Table broken: remove everybody, then refund whatever is still open. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel) {
			if (seats != null) {
				seats.occupied().forEach(s -> leave(s.player(), LeaveReason.REMOVED));
			}
			new ArrayList<>(openStakes.values()).forEach(s -> refundStake(serverLevel.getServer(), s));
			openStakes.clear();
		}
	}

	// ---- sync ---------------------------------------------------------------------------------

	/**
	 * Common state every screen can use: {@code phase}, {@code timers} (id → ticks left), {@code seats}
	 * (list of {index, name, you}), {@code seat} (viewer's seat or -1), {@code stake} (viewer's open stake),
	 * {@code balance}, {@code min}/{@code max} (viewer's effective limits).
	 */
	protected CompoundTag baseState(ServerPlayer viewer) {
		CompoundTag tag = new CompoundTag();
		tag.putString("phase", phase);
		CompoundTag timerTag = new CompoundTag();
		timers.keySet().forEach(id -> timerTag.putLong(id, ticksLeft(id)));
		tag.put("timers", timerTag);
		ListTag seatList = new ListTag();
		for (TableSeats.Seat s : seats().occupied()) {
			CompoundTag st = new CompoundTag();
			st.putInt("index", s.index());
			st.putString("name", s.name());
			st.putBoolean("you", s.player().equals(viewer.getUUID()));
			seatList.add(st);
		}
		tag.put("seats", seatList);
		tag.putInt("seat_count", seats().size());
		tag.putInt("seat", seats().seatOf(viewer.getUUID()).orElse(-1));
		tag.putLong("stake", stakeOf(viewer.getUUID()));
		tag.putLong("balance", Economies.get().balance(viewer));
		long[] limits = limitsFor(viewer);
		tag.putLong("min", limits[0]);
		tag.putLong("max", limits[1]);
		return tag;
	}

	public void sendStateTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, new TableSyncPayload(worldPosition, writeClientState(player)));
	}

	/** Shows {@code message} as the error line of the player's table screen (and in the action bar). */
	public void sendError(ServerPlayer player, Component message) {
		if (ServerPlayNetworking.canSend(player, TableErrorPayload.TYPE)) {
			ServerPlayNetworking.send(player, new TableErrorPayload(worldPosition, message));
		}
		player.sendOverlayMessage(message);
	}

	/** Re-sends state to every player who currently has this table's screen open. */
	public void syncViewers() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		for (ServerPlayer player : serverLevel.players()) {
			if (player.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(worldPosition)) {
				sendStateTo(player);
			}
		}
	}

	/** Generic actions handled by core before {@link #onAction}. */
	void handleAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "sit" -> sit(player);
			case "leave" -> {
				if (!canLeaveNow(player.getUUID())) {
					sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
				} else {
					leave(player.getUUID(), LeaveReason.LEFT);
				}
			}
			default -> onAction(player, action, args);
		}
	}

	// ---- persistence --------------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!openStakes.isEmpty()) {
			output.store(STAKES_KEY, OpenStake.CODEC.listOf(), List.copyOf(openStakes.values()));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		openStakes.clear();
		input.read(STAKES_KEY, OpenStake.CODEC.listOf()).ifPresent(list -> list.forEach(s -> openStakes.put(s.player(), s)));
		refundPending = !openStakes.isEmpty();
	}

	// ---- menu ---------------------------------------------------------------------------------

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new CasinoTableMenu(tableType, syncId, inventory, worldPosition);
	}
}
