package dev.nezo.burmaldaholic.core.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.UnaryOperator;
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
 *   <li><b>Money</b>: {@link #placeBet} (wager gate, min / table max / VIP tier max / funds / owned-table
 *       rules, debits, reserves bankroll exposure) and {@link #settle} (pays out, releases the
 *       reservation, reports the round via {@code PlayResults.fire}, offline-safe). Per-bet stakes for games
 *       whose bets resolve one by one: {@code placeBet(player, betId, ...)} / {@link #settleBet} /
 *       {@link #refundBet}. A table that stops running — <b>broken</b>, chunk <b>unloaded</b> or server
 *       <b>stopped</b> — plays its rounds out ({@link #playOutNow}, review M1); only undrawn bets are
 *       returned. Open stakes are still saved with the block entity and refunded if the table is loaded
 *       again with an unfinished round, which only happens after a crash (§4.1).</li>
 *   <li><b>Phases &amp; timers</b>: {@link #setPhase}, {@link #startTimer} → {@link #onTimer} (world time).</li>
 *   <li><b>Sync</b>: {@link #syncViewers()} after every change; {@link #baseState} for common fields;
 *       {@link #sendError} shows an error line on the client screen.</li>
 * </ul>
 *
 * Keep the actual rules in pure Java ({@code games/<name>/logic}) so they are unit-testable.
 */
public abstract class CasinoTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private static final String STAKES_KEY = "burmaldaholic_open_stakes";

	/**
	 * A confirmed, not yet settled bet of one player at this table. Most games keep one aggregate
	 * stake per player ({@code bet = ""}); games whose bets resolve one by one (craps) key them by
	 * {@code bet} (see {@link #placeBet(ServerPlayer, String, long, long, long, long, boolean)}).
	 */
	public record OpenStake(UUID player, String bet, long amount, long reserved, String bankroll) {
		public static final Codec<OpenStake> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(OpenStake::player),
			Codec.STRING.optionalFieldOf("bet", "").forGetter(OpenStake::bet),
			Codec.LONG.fieldOf("amount").forGetter(OpenStake::amount),
			Codec.LONG.fieldOf("reserved").forGetter(OpenStake::reserved),
			Codec.STRING.fieldOf("bankroll").forGetter(OpenStake::bankroll)
		).apply(i, OpenStake::new));

		boolean house() {
			return bankroll.isEmpty();
		}

		StakeKey key() {
			return new StakeKey(player, bet);
		}
	}

	record StakeKey(UUID player, String bet) {}

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
	private final Map<StakeKey, OpenStake> openStakes = new LinkedHashMap<>();
	private final Map<String, Long> timers = new HashMap<>();
	private String phase = "idle";
	private boolean refundPending;
	private boolean removing;
	/** Net result per player of rounds played out by {@link #playOutNow} at a server stop (for the notice). */
	private @Nullable Map<UUID, Long> playedOut;

	/** {@link #playOutNow} reason used when the server stops: players get {@code msg.burmaldaholic.core.round_played_out}. */
	public static final String SERVER_STOPPING = "server stopping";

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
		if (hasStake(player)) {
			refund(player);
		}
	}

	/** Whether a seated player may be removed for distance right now (default: only when they have no open stake). */
	protected boolean canLeaveNow(UUID player) {
		return !hasStake(player);
	}

	/**
	 * The table is being removed (block broken) and every seated player has already left with
	 * {@link LeaveReason#REMOVED}. Rounds in play must be <b>played out now</b> (GAME_DESIGN.md §4.1:
	 * auto-complete with the default action, no refunds; review B1: breaking a table must never cancel a
	 * round whose result is drawn or drawable). The default fast-forwards the game's timers (earliest
	 * first) while stakes are open; games driven by {@link #serverTick} (roulette, poker) override it.
	 * Stakes still open afterwards are refunded (only legitimate before any draw, e.g. a betting phase).
	 */
	protected void playOutForRemoval(ServerLevel level) {
		for (int i = 0; i < 512 && !openStakes.isEmpty() && !timers.isEmpty(); i++) {
			String next = null;
			long best = Long.MAX_VALUE;
			for (Map.Entry<String, Long> e : timers.entrySet()) {
				if (e.getValue() < best) {
					best = e.getValue();
					next = e.getKey();
				}
			}
			timers.remove(next);
			onTimer(next);
		}
	}

	/** True while the block is being removed ({@link #playOutForRemoval} runs). */
	protected boolean removing() {
		return removing;
	}

	/**
	 * The table was loaded with open stakes (only after a crash, §4.1). A game that persisted a DRAWN outcome together
	 * with the stake (slots v2 tapes, SLOTS.md §8.1) settles it here from the persisted outcome; stakes still open
	 * afterwards are refunded as before. Default: nothing (every open stake is refunded). Additive hook (lane J-L8).
	 */
	protected void resumeUnfinishedRound(ServerLevel level) {}

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
			firePlayerLeft(player, reason);
			syncViewers();
		}
	}

	/** Fires {@link CasinoEvents#TABLE_LEFT} (games with their own seat model call it themselves). */
	protected void firePlayerLeft(UUID player, LeaveReason reason) {
		if (level instanceof ServerLevel serverLevel) {
			try {
				CasinoEvents.TABLE_LEFT.invoker().onTableLeft(serverLevel, worldPosition, player, reason);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("TABLE_LEFT listener failed", e);
			}
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

	/** The generated-casino preset of this table (worldgen, §16), if any. */
	public Optional<TablePresetProvider.TablePreset> preset() {
		return level instanceof ServerLevel serverLevel ? CoreServices.tablePresets().preset(serverLevel, worldPosition) : Optional.empty();
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
		Result<Long> r = placeBet(player, "", amount, min, tableMax, worstCasePayout, checkLimits);
		return r.isOk() ? Result.ok(stakeOf(player.getUUID(), "")) : r;
	}

	/**
	 * Per-bet variant: debits {@code amount} into the open bet {@code bet} of the player (created or
	 * increased, e.g. craps odds behind a line bet). Settle it with {@link #settleBet}, return it with
	 * {@link #refundBet}. Every new stake passes the wager gate ({@code Wagers.check}: owner can't play,
	 * closed table, loan Asset Freeze ...); raising an already open stake (double, odds) skips the vetoes.
	 *
	 * @return the bet's total amount, or the translated error (already sent to the player's screen)
	 */
	protected Result<Long> placeBet(ServerPlayer player, String bet, long amount, long min, long tableMax, long worstCasePayout, boolean checkLimits) {
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			OwnedTable o = owned.get();
			if (o.minBet() > 0) {
				min = Math.max(min, o.minBet());
			}
			if (o.maxBet() > 0) {
				tableMax = tableMax > 0 ? Math.min(tableMax, o.maxBet()) : o.maxBet();
			}
		}
		boolean raising = openStakes.containsKey(new StakeKey(player.getUUID(), bet));
		WagerVeto.Context ctx = WagerVeto.Context.at(gameId(), worldPosition);
		Component err;
		if (checkLimits) {
			err = BetLimits.validate(player, amount, min, tableMax, ctx);
		} else {
			err = raising ? null : Wagers.check(player, ctx);
			if (err == null) {
				err = amount <= 0 ? Component.translatable("gui.burmaldaholic.error.invalid_amount")
					: (Economies.get().balance(player) < amount
						? Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))) : null);
			}
		}
		if (err != null) {
			return fail(player, err);
		}
		MinecraftServer server = player.level().getServer();
		Economy eco = Economies.get();
		// Review M2: a player's open round stays with the bank it started with. If the table was linked /
		// unlinked since (ownership change), raises and further bets of that round still go to — and are
		// reserved on — the stake's recorded bankroll, so pay/refund/release always hit the right account.
		String bankroll = roundBankroll(player.getUUID()).orElseGet(() -> owned.map(OwnedTable::bankrollId).orElse(""));
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
		StakeKey key = new StakeKey(player.getUUID(), bet);
		OpenStake prev = openStakes.get(key);
		OpenStake next = prev == null ? new OpenStake(player.getUUID(), bet, amount, reserve, bankroll)
			: new OpenStake(player.getUUID(), bet, prev.amount() + amount, prev.reserved() + reserve, bankroll);
		openStakes.put(key, next);
		setChanged();
		if (preset().filter(p -> p.id().startsWith("high_roller")).isPresent()) {
			CasinoAdvancements.grant(player, "high_roller"); // §19: a bet in the End City High Roller Lounge
		}
		if (CoreSounds.CHIP_PLACE != null && level != null) {
			level.playSound(null, worldPosition, CoreSounds.CHIP_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
		}
		return Result.ok(next.amount());
	}

	/** Bankroll of the player's open round here ("" = world bank), if they have an open stake (review M2). */
	private Optional<String> roundBankroll(UUID player) {
		for (OpenStake s : openStakes.values()) {
			if (s.player().equals(player)) {
				return Optional.of(s.bankroll());
			}
		}
		return Optional.empty();
	}

	/** Total open stake of a player at this table over all their bets (0 if none). */
	public long stakeOf(UUID player) {
		long sum = 0;
		for (OpenStake s : openStakes.values()) {
			if (s.player().equals(player)) {
				sum += s.amount();
			}
		}
		return sum;
	}

	/** Open amount of one bet (0 if none). */
	public long stakeOf(UUID player, String bet) {
		OpenStake s = openStakes.get(new StakeKey(player, bet));
		return s == null ? 0 : s.amount();
	}

	/** Whether the player has any open stake here. */
	public boolean hasStake(UUID player) {
		for (StakeKey k : openStakes.keySet()) {
			if (k.player().equals(player)) {
				return true;
			}
		}
		return false;
	}

	public List<OpenStake> openStakes() {
		return List.copyOf(openStakes.values());
	}

	/** House edge reported for rounds at this table (§17; default: the game's lowest). Slots override per tier. */
	protected double houseEdge() {
		return HouseEdges.of(gameId());
	}

	/**
	 * Settles ALL of the player's open bets as one round with a total {@code payout} (0 = lost, stake =
	 * push, more = win; stake included). Pays from the house or the bankroll, releases the reservation and
	 * reports the round ({@code PlayResults.fire}: now, or on the player's next join if offline).
	 */
	public void settle(UUID player, long payout) {
		settle(player, payout, null);
	}

	/** Same; {@code detail} enriches the reported {@link PlayResult} (tags, the bet's own edge). */
	public void settle(UUID player, long payout, @Nullable UnaryOperator<PlayResult> detail) {
		List<OpenStake> mine = new ArrayList<>();
		openStakes.values().removeIf(s -> {
			if (s.player().equals(player)) {
				mine.add(s);
				return true;
			}
			return false;
		});
		if (mine.isEmpty()) {
			return;
		}
		OpenStake first = mine.getFirst();
		long amount = 0;
		long reserved = 0;
		for (OpenStake s : mine) {
			amount += s.amount();
			if (s.bankroll().equals(first.bankroll())) {
				reserved += s.reserved();
			} else if (!s.house() && level instanceof ServerLevel sl) {
				// never release a reservation against the wrong bankroll (review M2; stakes saved before the fix)
				Economies.get().bankrolls(sl.getServer()).release(s.bankroll(), s.reserved());
			}
		}
		pay(new OpenStake(player, "", amount, reserved, first.bankroll()), payout, detail);
	}

	/** Settles one bet of {@link #placeBet(ServerPlayer, String, long, long, long, long, boolean)}. */
	public void settleBet(UUID player, String bet, long payout, @Nullable UnaryOperator<PlayResult> detail) {
		OpenStake s = openStakes.remove(new StakeKey(player, bet));
		if (s != null) {
			pay(s, payout, detail);
		}
	}

	private void pay(OpenStake s, long payout, @Nullable UnaryOperator<PlayResult> detail) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		UUID player = s.player();
		MinecraftServer server = serverLevel.getServer();
		Economy eco = Economies.get();
		if (!s.house()) {
			eco.bankrolls(server).release(s.bankroll(), s.reserved());
		}
		if (payout > 0) {
			AccountId bank = s.house() ? AccountId.HOUSE : AccountId.bankroll(s.bankroll());
			// Review m8: the returned stake is the player's own money (never garnished); only the winnings are a
			// garnishable PAYOUT.
			long stakeBack = Math.min(payout, s.amount());
			credit(server, bank, player, stakeBack, new Transaction(gameId(), "stake_return", Transaction.Kind.TRANSFER), s);
			credit(server, bank, player, payout - stakeBack, Transaction.payout(gameId()), s);
		}
		setChanged();
		if (playedOut != null) {
			playedOut.merge(player, payout - s.amount(), Long::sum);
		}
		PlayResult result = PlayResult.of(gameId(), s.amount(), payout).withEdge(houseEdge()).withTable(serverLevel, worldPosition, s.bankroll());
		if (detail != null) {
			result = detail.apply(result);
		}
		PlayResults.fire(server, player, result);
		if (CasinoConfig.debug().logRounds) {
			Burmaldaholic.LOGGER.info("[round] {} at {}: player {} staked {} payout {}", gameId(), worldPosition, player, s.amount(), payout);
		}
	}

	private void credit(MinecraftServer server, AccountId bank, UUID player, long amount, Transaction why, OpenStake s) {
		if (amount <= 0) {
			return;
		}
		Economy eco = Economies.get();
		if (!eco.transfer(server, bank, AccountId.player(player), amount, why).ok()) {
			// Should not happen (exposure was reserved); never leave the player unpaid.
			Burmaldaholic.LOGGER.error("Bankroll {} could not pay {} to {} at {}", s.bankroll(), amount, player, worldPosition);
			eco.transfer(server, AccountId.HOUSE, AccountId.player(player), amount, why);
		}
	}

	/** Returns ALL of the player's open bets without a result (no PLAY_RESOLVED) and tells them. */
	public void refund(UUID player) {
		refund(player, false);
	}

	/** Same; {@code silent} skips the "round refunded" chat line (player cleared their own bets). */
	public void refund(UUID player, boolean silent) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long total = 0;
		for (OpenStake s : List.copyOf(openStakes.values())) {
			if (s.player().equals(player)) {
				openStakes.remove(s.key());
				refundStake(serverLevel.getServer(), s, true);
				total += s.amount();
			}
		}
		if (total > 0) {
			notifyRefund(serverLevel.getServer(), player, total, silent);
			setChanged();
		}
	}

	/** Returns one bet without a result. */
	public void refundBet(UUID player, String bet, boolean silent) {
		OpenStake s = openStakes.remove(new StakeKey(player, bet));
		if (s == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		refundStake(serverLevel.getServer(), s, true);
		notifyRefund(serverLevel.getServer(), player, s.amount(), silent);
		setChanged();
	}

	private void refundStake(MinecraftServer server, OpenStake s, boolean quiet) {
		Economy eco = Economies.get();
		AccountId bank = s.house() ? AccountId.HOUSE : AccountId.bankroll(s.bankroll());
		if (!s.house()) {
			eco.bankrolls(server).release(s.bankroll(), s.reserved());
		}
		if (!eco.transfer(server, bank, AccountId.player(s.player()), s.amount(), Transaction.refund(gameId())).ok()) {
			eco.transfer(server, AccountId.HOUSE, AccountId.player(s.player()), s.amount(), Transaction.refund(gameId()));
		}
		if (!quiet) {
			notifyRefund(server, s.player(), s.amount(), false);
		}
	}

	private static void notifyRefund(MinecraftServer server, UUID player, long amount, boolean silent) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !silent) {
			online.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.round_refunded", Texts.chipsAcc(amount)));
		}
	}

	/**
	 * PvP rake of a pot (poker, §7/§18.2). The pot chips are held by the world bank; at an owned table the
	 * rake moves to the owner's bankroll, otherwise it stays in the bank (sink). Fires
	 * {@link CasinoEvents#RAKE_COLLECTED} (owned-casino statistics).
	 */
	protected void collectRake(long rake) {
		if (rake <= 0 || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		String bankroll = ownership().map(OwnedTable::bankrollId).orElse("");
		if (!bankroll.isEmpty()) {
			Economy.TxResult tx = Economies.get().transfer(serverLevel.getServer(), AccountId.HOUSE, AccountId.bankroll(bankroll), rake,
				new Transaction(gameId(), "rake", Transaction.Kind.TRANSFER));
			if (!tx.ok()) {
				bankroll = "";
			}
		}
		try {
			CasinoEvents.RAKE_COLLECTED.invoker().onRake(serverLevel, worldPosition, rake, bankroll);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("RAKE_COLLECTED listener failed", e);
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
			try {
				resumeUnfinishedRound(level);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Table {} at {}: resuming an unfinished round failed", gameId(), worldPosition, e);
			}
			if (CasinoConfig.core().roundTimeoutRefund) {
				new ArrayList<>(openStakes.values()).forEach(s -> {
					Burmaldaholic.LOGGER.info("Refunding unfinished round at {}: {} chips to {}", worldPosition, s.amount(), s.player());
					refundStake(server, s, false);
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

	/**
	 * Table broken: everybody leaves ({@link LeaveReason#REMOVED}), rounds in play are played out
	 * ({@link #playOutForRemoval}, GAME_DESIGN.md §4.1, review B1), and only what is still open after
	 * that (bets of a round that has not drawn yet) is refunded.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		playOutNow("removed");
	}

	/**
	 * The table stops running: broken, its chunk unloads, or the server stops (review M1). Everybody leaves
	 * ({@link LeaveReason#REMOVED}), rounds in play are played out ({@link #playOutForRemoval}) and only
	 * stakes of a round that has not drawn yet are refunded — a drawn outcome is never undone, so "Save and
	 * Quit" is not a free roll. Idempotent: afterwards no stake is open, so the saved block entity carries
	 * nothing to refund on the next load. Called by core ({@link TableLifecycle}); games need not call it.
	 *
	 * @return true if anything was open (the chunk must be saved again)
	 */
	public boolean playOutNow(String why) {
		if (!(level instanceof ServerLevel serverLevel) || removing) {
			return false;
		}
		boolean busy = !openStakes.isEmpty() || (seats != null && !seats.isEmpty()) || hasRoundInPlay();
		if (!busy) {
			return false;
		}
		removing = true;
		if (SERVER_STOPPING.equals(why)) {
			playedOut = new LinkedHashMap<>();
		}
		try {
			if (seats != null) {
				seats.occupied().forEach(s -> leave(s.player(), LeaveReason.REMOVED));
			}
			try {
				playOutForRemoval(serverLevel);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Table {} at {}: play-out ({}) failed", gameId(), worldPosition, why, e);
			}
			if (playedOut != null) {
				playedOut.forEach((player, net) -> notifyPlayedOut(serverLevel.getServer(), player, net));
			}
			new ArrayList<>(openStakes.values()).forEach(s -> {
				Burmaldaholic.LOGGER.info("Table {} {} at {}: returning undrawn stake {} to {}", gameId(), why, worldPosition, s.amount(), s.player());
				refundStake(serverLevel.getServer(), s, false);
			});
			openStakes.clear();
			setChanged();
		} finally {
			removing = false;
			playedOut = null;
		}
		return true;
	}

	/** GAME_DESIGN §4.1 (CHANGED, review M1): "Your blackjack round was already decided, so it was played out: Win! +40". */
	private void notifyPlayedOut(MinecraftServer server, UUID player, long net) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online == null) {
			return;
		}
		Component result = net > 0 ? Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net))
			: net < 0 ? Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net))
			: Component.translatable("gui.burmaldaholic.common.result.push");
		online.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.round_played_out", gameLabel(), result));
	}

	/** The game's name ({@code gui.burmaldaholic.common.game.*}), e.g. for chat lines about a round. */
	protected Component gameLabel() {
		String id = gameId().equals("wheel_of_fortune") ? "wheel" : gameId();
		return Component.translatable("gui.burmaldaholic.common.game." + id);
	}

	/**
	 * Whether the game has a round in play that is not visible as an open stake or a core seat (poker's own
	 * seat model). Default false; {@link #playOutNow} then only runs when stakes or seats are open.
	 */
	protected boolean hasRoundInPlay() {
		return false;
	}

	// ---- sync ---------------------------------------------------------------------------------

	/**
	 * Common state every screen can use: {@code phase}, {@code timers} (id → ticks left), {@code seats}
	 * (list of {index, name, you}), {@code seat} (viewer's seat or -1), {@code stake} (viewer's open stake),
	 * {@code balance}, {@code min}/{@code max} (viewer's effective limits).
	 */
	/** Card tables: the server's {@code cards.theme} (AUTO = omitted, the client resolves by dimension). */
	protected static void putCardsTheme(CompoundTag tag) {
		String id = dev.nezo.burmaldaholic.core.config.CasinoConfig.cards().theme.id();
		if (!id.isEmpty()) {
			tag.putString("theme", id);
		}
	}

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
		input.read(STAKES_KEY, OpenStake.CODEC.listOf()).ifPresent(list -> list.forEach(s -> openStakes.put(s.key(), s)));
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
