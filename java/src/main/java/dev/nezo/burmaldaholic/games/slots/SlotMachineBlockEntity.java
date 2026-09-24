package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.SlotsV2Config;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.slots.api.SlotsApi;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetPublish;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotOutcomes;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRng;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTiers;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One slot machine cabinet running a slots v2 machine (SLOTS.md; docs/architecture/animation.md §7.2): the block tier
 * selects the machine ({@code copper} = Overworld Riches, {@code gold} = Nether Inferno, {@code netherite} = End Void).
 * Server-authoritative round: CONFIRM (validation, stake through {@link #placeBet} with the {@code cap × bet} owned
 * reservation) → DRAW the whole spin as one tape ({@code OddsService.play}, §8.2 streak re-draw; bought features are
 * never re-drawn) → PERSIST (saved with the open stake) → PRESENT (the {@link SlotTimeline} seed + the visible tape
 * section to the screen, the {@link CabinetSync} to every nearby cabinet renderer) → SETTLE at the reveal gate of the
 * shared timeline, or at once on skip past the gate / close / leave / removal / restart (F8: leaving = reveal).
 *
 * <p>A Treasure Hunt pauses the shared clock at the end of its intro until every chest is picked (the i-th pick reveals
 * entry i, D6/F7; autoplay picks one every 600 ms). Java v1 never persisted a 3×3 round (settle-only
 * {@link LegacySlots} covers the v1 record format).
 */
public class SlotMachineBlockEntity extends CasinoTableBlockEntity {
	static final String TIMER_V2 = "v2";
	static final String TIMER_V2_AUTO = "v2_auto";
	static final String ROUND_KEY = "burmaldaholic_slots_v2";
	private static final String CABINET_KEY = "cabinet";

	private final Tier tier;
	private int seq;
	private final Map<UUID, CompoundTag> autoSummaries = new HashMap<>();
	private @Nullable RoundV2 round;
	private final Map<UUID, Long> betsV2 = new HashMap<>();
	private final Map<UUID, Boolean> turboV2 = new HashMap<>();
	private @Nullable AutoV2 autoV2;
	private @Nullable CompoundTag lastV2;
	private int[] restStops = new int[5];
	/** Cells shown at rest (after tumbles / sticky reels): what the next spin spins up from; null = the strip window. */
	private int @Nullable [] restCells;
	private @Nullable CabinetSync cabinet;

	/** The drawn, persisted round (SLOTS.md §8.1 record). */
	static final class RoundV2 {
		UUID player;
		String playerName;
		Machine machine;
		long bet;
		long stake;
		boolean bought;
		boolean owned;
		SpinTape tape;
		long startTick;
		int seq;
		int speedPct;
		int seed;
		boolean anticipation;
		boolean auto;
		/** Timeline ms at which the shared clock is frozen waiting for a manual Treasure Hunt pick, or -1. */
		int holdMs = -1;
		/** Stops the reels rested on before the spin. */
		int[] prevStops = new int[5];
		/** Treasure Hunt chests opened so far, in pick order (presentation only). */
		final List<Integer> huntCells = new ArrayList<>();
		private @Nullable Timeline timeline;

		Timeline timeline(MachineDef def) {
			if (timeline == null) {
				timeline = SlotTimeline.build(tape, def, TimingProfile.SHARED.withSpeed(speedPct), TimingProfile.SHARED, seed, anticipation,
					CasinoConfig.slots().bigWinTiers);
			}
			return timeline;
		}

		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("v", SpinTape.VERSION);
			t.putString("player", player.toString());
			t.putString("name", playerName);
			t.putString("machine", machine.id);
			t.putLong("bet", bet);
			t.putLong("price", stake);
			t.putBoolean("bought", bought);
			t.putBoolean("owned", owned);
			t.putString("tape", TapeCodec.encode(tape));
			t.putLong("total", tape.payoutChips());
			t.putLong("start", startTick);
			t.putInt("seq", seq);
			t.putInt("speed", speedPct);
			t.putInt("seed", seed);
			t.putBoolean("anticipation", anticipation);
			t.putBoolean("auto", auto);
			t.putInt("hold", holdMs);
			t.putIntArray("prev", prevStops);
			t.putIntArray("hunt_cells", huntCells.stream().mapToInt(Integer::intValue).toArray());
			return t;
		}

		static @Nullable RoundV2 load(CompoundTag t) {
			try {
				RoundV2 r = new RoundV2();
				r.player = UUID.fromString(t.getStringOr("player", ""));
				r.playerName = t.getStringOr("name", "");
				r.machine = Machine.byId(t.getStringOr("machine", ""));
				r.bet = t.getLongOr("bet", 0);
				r.stake = t.getLongOr("price", 0);
				r.bought = t.getBooleanOr("bought", false);
				r.owned = t.getBooleanOr("owned", false);
				r.tape = TapeCodec.decode(t.getStringOr("tape", ""));
				r.startTick = t.getLongOr("start", 0);
				r.seq = t.getIntOr("seq", 0);
				r.speedPct = t.getIntOr("speed", 100);
				r.seed = t.getIntOr("seed", 0);
				r.anticipation = t.getBooleanOr("anticipation", true);
				r.auto = t.getBooleanOr("auto", false);
				r.holdMs = t.getIntOr("hold", -1);
				r.prevStops = t.getIntArray("prev").filter(a -> a.length == 5).orElse(new int[5]);
				for (int c : t.getIntArray("hunt_cells").orElse(new int[0])) r.huntCells.add(c);
				return r.bet > 0 && r.tape.bet() == r.bet ? r : null;
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Corrupt slots v2 round record {}", t, e);
				return null;
			}
		}
	}

	private static final class AutoV2 {
		final UUID player;
		int left;
		int spins;
		long bet;
		long wagered;
		long won;
		long lossLimit;
		boolean stopFeature;
		long stopWinMult;
		long startBalance;

		AutoV2(UUID player) {
			this.player = player;
		}
	}

	/**
	 * The machine holding each player's live round (server thread). One player never holds two rounds at once (review
	 * J-L8); stale entries (machine removed / unloaded, round settled) are ignored and dropped.
	 */
	private static final Map<UUID, SlotMachineBlockEntity> LIVE_ROUNDS = new HashMap<>();

	private static @Nullable SlotMachineBlockEntity liveRoundOf(UUID player) {
		SlotMachineBlockEntity be = LIVE_ROUNDS.get(player);
		if (be != null && (be.isRemoved() || be.round == null || !be.round.player.equals(player))) {
			LIVE_ROUNDS.remove(player);
			return null;
		}
		return be;
	}

	public SlotMachineBlockEntity(TableType<SlotMachineBlockEntity> type, BlockPos pos, BlockState state, Tier tier) {
		super(type, pos, state);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public String gameId() {
		return SlotsModule.ID;
	}

	@Override
	protected int seatCount() {
		return 1;
	}

	/** The machine's own edge (cashback, statistics). */
	@Override
	protected double houseEdge() {
		return SlotMachinesV2.houseEdge(machineV2(), false, ownership().isPresent());
	}

	@Override
	protected long minBet() {
		return SlotMachinesV2.cfg(machineV2()).bets[0];
	}

	@Override
	protected long tableMaxBet() {
		int[] b = SlotMachinesV2.cfg(machineV2()).bets;
		return b[b.length - 1];
	}

	/** A spinning player may walk away: the spin is settled at once (§4.1). */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	/** Test/automation hook: true while a round is in play. */
	public boolean spinning() {
		return round != null;
	}

	public Machine machineV2() {
		return SlotMachinesV2.machine(tier);
	}

	/** Test/automation hook: the persisted round's tape, or null. */
	public @Nullable SpinTape roundTape() {
		return round == null ? null : round.tape;
	}

	/** GameTest hook: replaces the RNG draw of the next rounds (null = the real draw). Never set in play. */
	private java.util.function.@Nullable Function<SlotDraw.Request, SpinTape> drawOverride;

	/** GameTest hook: draw the next rounds with {@code draw} (the stake, pools and settlement stay real); null resets. */
	public void drawForTesting(java.util.function.@Nullable Function<SlotDraw.Request, SpinTape> draw) {
		this.drawOverride = draw;
	}

	/** GameTest hook: the round timer fires now (the Treasure Hunt pause, an autoplay pick, or the reveal gate). */
	public void fireRoundTimerForTesting() {
		cancelTimer(TIMER_V2);
		onRoundTimer();
	}

	/** Test/automation hook: the stops the reels rest on (the next spin starts here). */
	public int[] restStops() {
		return restStops.clone();
	}

	/** Test/automation hook: the cells shown at rest, or null before the first spin (the strip window). */
	public int @Nullable [] restCells() {
		return restCells == null ? null : restCells.clone();
	}

	// ---- actions ------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "spin" -> {
				stopAutoV2(player.getUUID());
				startV2(player, args.getLongOr("bet", -1), false, false);
			}
			case "buy" -> {
				stopAutoV2(player.getUUID());
				startV2(player, args.getLongOr("bet", -1), true, false);
			}
			case "bet" -> {
				long bet = args.getLongOr("bet", -1);
				if (offeredBets(player).contains(bet)) {
					betsV2.put(player.getUUID(), bet);
				} else {
					sendError(player, Component.translatable("gui.burmaldaholic.slots.error.bet_unavailable"));
				}
			}
			case "turbo" -> turboV2.put(player.getUUID(), args.getBooleanOr("on", false) && CasinoConfig.slots().turboAllowed);
			case "skip" -> skipV2(player);
			case "pick" -> pickV2(player, args.getIntOr("chest", -1), false);
			case "pick_all" -> pickV2(player, -1, true);
			case "auto" -> startAutoV2(player, args);
			case "stop_auto" -> {
				if (autoV2 != null && autoV2.player.equals(player.getUUID())) {
					autoV2.left = 0;
					if (round == null) {
						endAutoV2("");
					}
				}
			}
			default -> {
			}
		}
		syncViewers();
	}

	/** Seats the player; a seat hogged by someone who closed the screen (and is not spinning) is freed. */
	private boolean claimSeat(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		if (level instanceof ServerLevel serverLevel && seats().isFull()) {
			for (var seat : seats().occupied()) {
				ServerPlayer other = serverLevel.getServer().getPlayerList().getPlayer(seat.player());
				boolean busy = round != null && round.player.equals(seat.player());
				if (!busy && (other == null || !viewing(other))) {
					leave(seat.player(), LeaveReason.LEFT);
				}
			}
		}
		return sit(player);
	}

	private boolean viewing(ServerPlayer p) {
		return p.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(worldPosition) && !p.isRemoved();
	}

	@Override
	protected void onTimer(String id) {
		if (TIMER_V2.equals(id)) {
			onRoundTimer();
		} else if (TIMER_V2_AUTO.equals(id)) {
			continueAutoV2();
		}
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		if (round != null && round.player.equals(player)) {
			finishV2(true); // F8: leaving = reveal; settled from the persisted tape
		}
		if (autoV2 != null && autoV2.player.equals(player)) {
			cancelTimer(TIMER_V2_AUTO);
			autoV2 = null;
		}
		super.onPlayerLeft(player, reason); // refunds anything still open (nothing, normally)
	}

	/** Bets of the machine's ladder this player may use: ≤ min(VIP tier max, owner max), ≥ owner min (SLOTS.md §6.1). */
	List<Long> offeredBets(ServerPlayer player) {
		long[] limits = limitsFor(player);
		List<Long> out = new ArrayList<>();
		for (int b : SlotMachinesV2.cfg(machineV2()).bets) {
			if (b >= limits[0] && b <= limits[1]) {
				out.add((long) b);
			}
		}
		return out;
	}

	/** Owner switch (SLOTS.md §8.6): bonus buy allowed at this table (house tables: always). */
	private boolean ownerAllowsBuy() {
		return ownership().map(OwnedTable::slotsBuy).orElse(true);
	}

	/** Owner switch (SLOTS.md §8.6): autoplay allowed at this table (house tables: always). */
	private boolean ownerAllowsAutoplay() {
		return ownership().map(OwnedTable::slotsAutoplay).orElse(true);
	}

	/**
	 * CONFIRM → DRAW → PERSIST. {@code autoSpin}: started by autoplay (the round opens Treasure Hunt chests by itself).
	 *
	 * @return true if a round started
	 */
	public boolean startV2(ServerPlayer player, long requestedBet, boolean buy, boolean autoSpin) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		MinecraftServer server = serverLevel.getServer();
		Machine m = machineV2();
		SlotsV2Config.Machine cfg = SlotMachinesV2.cfg(m);
		SlotMachineBlockEntity elsewhere = liveRoundOf(player.getUUID());
		if (round != null || (elsewhere != null && elsewhere != this)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return false;
		}
		if (!CasinoMode.isEnabled(player)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.casino_off"));
			return false;
		}
		if (!CasinoConfig.slots().enabled || !cfg.enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.slots.out_of_order"));
			return false;
		}
		int vip = CoreServices.vip().tier(server, player.getUUID());
		if (vip < cfg.minVipTier) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(cfg.minVipTier)));
			return false;
		}
		MachineDef def = SlotMachinesV2.def(m);
		List<Long> offered = offeredBets(player);
		long bet = requestedBet > 0 ? requestedBet : betsV2.getOrDefault(player.getUUID(), (long) cfg.defaultBet);
		if (!offered.contains(bet)) {
			if (requestedBet <= 0 && !offered.isEmpty()) {
				bet = offered.getFirst();
			} else {
				sendError(player, Component.translatable("gui.burmaldaholic.slots.error.bet_unavailable"));
				return false;
			}
		}
		long stake = bet;
		if (buy) {
			if (!CasinoConfig.slots().buyFeature.enabled || def.buyPriceFifths() <= 0 || !ownerAllowsBuy()) {
				sendError(player, Component.translatable("gui.burmaldaholic.slots.error.buy_disabled"));
				return false;
			}
			stake = def.buyPrice(bet);
			long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
			if (stake > tierMax * CasinoConfig.slots().buyFeature.tierMaxMultiple) {
				sendError(player, Component.translatable("gui.burmaldaholic.slots.error.buy_limit"));
				return false;
			}
		}
		if (!claimSeat(player)) {
			return false;
		}
		betsV2.put(player.getUUID(), bet);
		boolean owned = ownership().isPresent();
		Result<Long> placed = placeBet(player, stake, buy ? stake : minBet(), buy ? 0 : tableMaxBet(), SlotDraw.reservation(def, bet), !buy);
		if (!placed.isOk()) {
			return false;
		}
		JackpotPoolsV2 pools = owned ? null : JackpotPoolsV2.get(server);
		if (pools != null) {
			pools.contribute(m, def, stake);
		}
		long[] view = pools == null ? null : pools.pools(m, def);
		OddsContext ctx = new OddsContext(player.getUUID(), SlotsModule.ID, stake);
		CasinoRng rng = OddsService.get().rng(ctx);
		SlotRng slotRng = rng::nextInt;
		SlotDraw.Request request = new SlotDraw.Request(def, bet, buy, owned, view);
		final long betF = bet;
		// §8.2: the whole spin is one outcome for the streak re-draw; bought features are never re-drawn
		SpinTape tape = drawOverride != null ? drawOverride.apply(request)
			: buy ? SlotDraw.draw(request, slotRng)
			: OddsService.get().play(ctx, SlotMachinesV2.houseRtp(m), () -> SlotDraw.draw(request, slotRng), t -> t.payoutChips() < betF);
		if (pools != null) {
			pools.applyDraw(m, def, tape); // award at draw time: two players can never win the same money (§5.2)
		}
		RoundV2 r = new RoundV2();
		r.player = player.getUUID();
		r.playerName = player.getName().getString();
		r.machine = m;
		r.bet = bet;
		r.stake = stake;
		r.bought = buy;
		r.owned = owned;
		r.tape = tape;
		r.startTick = gameTime();
		r.seq = ++seq;
		r.speedPct = CasinoConfig.slots().turboAllowed && turboV2.getOrDefault(player.getUUID(), false) ? 200 : 100;
		r.seed = SeedMix.mix(SeedMix.mixLong(worldPosition.asLong()), r.seq);
		r.anticipation = CasinoConfig.slots().anticipation;
		r.auto = autoSpin;
		r.prevStops = restStops.clone();
		round = r;
		LIVE_ROUNDS.put(r.player, this);
		autoSummaries.remove(player.getUUID());
		setChanged(); // PERSIST before anything is shown (saved with the open stake)
		setPhase("spinning");
		scheduleV2();
		syncViewers();
		publishRound(r);
		return true;
	}

	/** Chests of the round's Treasure Hunt still to open (0 without a hunt). */
	private static int huntLeft(RoundV2 r) {
		SpinTape.Hunt h = r.tape.hunt();
		return h == null ? 0 : Math.max(0, SlotDraw.huntOpens(SlotMachinesV2.def(r.machine), r.tape) - h.opened());
	}

	/**
	 * Next server event of the round: the Treasure Hunt pause (end of its intro; the shared clock stops there until the
	 * picks are done, slots.md D6), an autoplay pick every 600 ms while paused, else the reveal gate.
	 */
	private void scheduleV2() {
		RoundV2 r = round;
		if (r == null) {
			return;
		}
		cancelTimer(TIMER_V2);
		if (r.holdMs >= 0) {
			if (r.auto) {
				startTimer(TIMER_V2, SlotTimeline.T_HUNT_OPEN / 50);
			}
			return;
		}
		Timeline tl = r.timeline(SlotMachinesV2.def(r.machine));
		long elapsedMs = (gameTime() - r.startTick) * 50;
		int target = huntLeft(r) > 0 ? SlotTimeline.huntPauseMs(tl) : tl.sharedEndMs();
		startTimer(TIMER_V2, (int) Math.max(0, Math.ceilDiv(target - elapsedMs, 50L)));
	}

	private void onRoundTimer() {
		RoundV2 r = round;
		if (r == null) {
			return;
		}
		if (huntLeft(r) > 0) {
			if (r.holdMs < 0) {
				r.holdMs = SlotTimeline.huntPauseMs(r.timeline(SlotMachinesV2.def(r.machine))); // a required choice: wait
				setPhase("pick");
				scheduleV2();
				syncViewers();
				publishRound(r);
			} else if (r.auto) {
				revealPick(r, -1);
			}
			return;
		}
		finishV2(false);
	}

	/** The i-th opened chest reveals entry i whichever chest was clicked (SLOTS.md §1.2); persisted per pick. */
	private void revealPick(RoundV2 r, int chest) {
		r.tape = r.tape.withHuntOpened(r.tape.hunt().opened() + 1);
		int c = chest >= 0 && chest < 15 && !r.huntCells.contains(chest) ? chest : -1;
		for (int k = 0; c < 0 && k < 15; k++) {
			if (!r.huntCells.contains(k)) {
				c = k;
			}
		}
		r.huntCells.add(Math.max(0, c));
		if (huntLeft(r) == 0) {
			// resume the shared clock where it paused (end of the hunt intro)
			r.startTick = gameTime() - r.holdMs / 50;
			r.holdMs = -1;
			setPhase("spinning");
		}
		setChanged();
		scheduleV2();
		syncViewers();
		publishRound(r);
	}

	private void pickV2(ServerPlayer player, int chest, boolean all) {
		RoundV2 r = round;
		if (r == null || !r.player.equals(player.getUUID()) || r.tape.hunt() == null) {
			return;
		}
		if (all) {
			r.auto = true;
		}
		if (r.holdMs >= 0 && huntLeft(r) > 0) {
			revealPick(r, chest);
		}
	}

	/** Skip / slam stop: the shared clock jumps to the end of the current beat group; never past a required pick (§6.4). */
	private void skipV2(ServerPlayer player) {
		RoundV2 r = round;
		if (r == null || !r.player.equals(player.getUUID()) || r.holdMs >= 0) {
			return;
		}
		Timeline tl = r.timeline(SlotMachinesV2.def(r.machine));
		long elapsed = (gameTime() - r.startTick) * 50;
		int gate = tl.sharedEndMs();
		int g = tl.groupAt(elapsed);
		if (g < 0 || elapsed >= gate) {
			return;
		}
		long target = Math.min(tl.groupEnd(g), gate);
		if (huntLeft(r) > 0) {
			target = Math.min(target, SlotTimeline.huntPauseMs(tl));
		}
		long shift = (target - elapsed) / 50;
		if (shift <= 0) {
			return;
		}
		r.startTick -= shift;
		scheduleV2();
		syncViewers();
		publishRound(r);
	}

	/**
	 * SETTLE from the persisted tape (SLOTS.md §1.2): the round's payout (spin total incl. the cap and owned fixed
	 * jackpots) through the table's bankroll; progressive awards credited separately from the pools so Golden Hour's net
	 * excludes them (§8.3); statistics, events, advancements, announcements, at most one chaos event (§8.4), autoplay.
	 *
	 * @param quiet the player is leaving / the table is removed / restart: no chaos, no autoplay
	 */
	public void finishV2(boolean quiet) {
		RoundV2 r = round;
		if (r == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		round = null;
		if (LIVE_ROUNDS.get(r.player) == this) {
			LIVE_ROUNDS.remove(r.player);
		}
		cancelTimer(TIMER_V2);
		setPhase("idle");
		MinecraftServer server = serverLevel.getServer();
		if (!hasStake(r.player)) {
			// the stake was already returned or settled elsewhere: paying the tape (or its pool awards) now would mint chips
			Burmaldaholic.LOGGER.error("Slots at {}: v2 round of {} has no open stake; dropped without payout", worldPosition, r.player);
			if (!r.owned) {
				JackpotPoolsV2.get(server).revealed(r.machine, r.tape);
			}
			setChanged();
			syncViewers();
			return;
		}
		MachineDef def = SlotMachinesV2.def(r.machine);
		SpinTape tape = r.tape.hunt() == null ? r.tape : r.tape.withHuntOpened(SlotDraw.huntOpens(def, r.tape));
		double edge = SlotMachinesV2.houseEdge(r.machine, r.bought, r.owned);
		List<String> tags = new ArrayList<>(List.of(r.machine.id));
		if (r.bought) {
			tags.add("buy");
		}
		if (tape.featureTriggered()) {
			tags.add("feature");
		}
		if (!tape.jackpots().isEmpty()) {
			tags.add("jackpot");
		}
		if (tape.capHit()) {
			tags.add("max_win");
		}
		settle(r.player, tape.totalChips(), res -> res.withTags(tags.toArray(String[]::new)).withEdge(edge));
		long progressive = tape.progressiveChips();
		if (progressive > 0) {
			Economies.get().transfer(server, AccountId.HOUSE, AccountId.player(r.player), progressive,
				new Economy.Transaction(gameId(), "jackpot", Economy.Transaction.Kind.PAYOUT));
		}
		JackpotPoolsV2 pools = JackpotPoolsV2.get(server);
		if (!r.owned) {
			pools.revealed(r.machine, tape);
		}
		pools.record(r.player, r.machine, r.stake, tape.payoutChips(), tape.featureTriggered() && !r.bought, tape.totalFifths(), tape);
		restAfter(r, def, tape);
		lastV2 = resultTagV2(r, tape);
		setChanged();
		ServerPlayer online = server.getPlayerList().getPlayer(r.player);
		if (online != null && online.isRemoved()) {
			online = null;
		}
		afterSettleV2(serverLevel, online, r, def, tape, quiet);
		AutoV2 a = autoV2;
		if (a != null && a.player.equals(r.player)) {
			a.spins++;
			a.left--;
			a.wagered += r.stake;
			a.won += tape.payoutChips();
			long balance = online == null ? 0 : Economies.get().balance(online);
			if (quiet || online == null || !viewing(online)) {
				endAutoV2("");
			} else if (!tape.jackpots().isEmpty()) {
				endAutoV2("jackpot");
			} else if (tape.capHit()) {
				endAutoV2("big_win");
			} else if (balance < a.bet) {
				endAutoV2("funds");
			} else if (a.stopFeature && tape.featureTriggered()) {
				endAutoV2("feature");
			} else if (a.stopWinMult > 0 && tape.payoutChips() >= a.stopWinMult * a.bet) {
				endAutoV2("big_win");
			} else if (a.startBalance - balance >= a.lossLimit) {
				endAutoV2("loss");
			} else if (a.left <= 0) {
				endAutoV2("");
			} else {
				startTimer(TIMER_V2_AUTO, 10);
			}
		}
		syncViewers();
	}

	/** The window the reels rest on after the round: the last reel phase's stops and final cells (what every view ends on). */
	private void restAfter(RoundV2 r, MachineDef def, SpinTape tape) {
		SlotScript script = new SlotScript(r.timeline(def), def, r.prevStops, Arrays.equals(restStops, r.prevStops) ? restCells : null);
		if (!script.phases().isEmpty()) {
			SlotScript.Phase last = script.phases().getLast();
			int[] stops = new int[5];
			for (int k = 0; k < 5; k++) stops[k] = last.reels[k] != null ? last.reels[k].stop() : last.restStops[k];
			restStops = stops;
			restCells = script.finalCells(last);
		} else {
			restStops = SlotTimeline.terminalStops(tape, r.prevStops);
		}
	}

	private void afterSettleV2(ServerLevel level, @Nullable ServerPlayer player, RoundV2 r, MachineDef def, SpinTape tape, boolean quiet) {
		MinecraftServer server = level.getServer();
		Component machineName = Component.translatable("gui.burmaldaholic.slots.machine." + r.machine.id);
		int announceFrom = CasinoConfig.slots().jackpot.announceMinTier.ordinal() + 1;
		for (SpinTape.JackpotAward j : tape.jackpots()) {
			Component tierName = Component.translatable("gui.burmaldaholic.slots.jackpot.tier." + SlotDefaults.TIER_KEYS[Math.max(1, Math.min(4, j.tier())) - 1]);
			if (player != null) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.slots.jackpot_self", tierName, Texts.chipsAcc(j.chips()))
					.withStyle(ChatFormatting.GOLD));
			}
			if (j.tier() >= announceFrom) {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.slots.jackpot_broadcast",
					Texts.raw(r.playerName), tierName, Texts.chipsAcc(j.chips()), machineName).withStyle(ChatFormatting.GOLD), false);
			}
		}
		SlotOutcomes.Facts facts = SlotOutcomes.facts(def, tape);
		try {
			SlotsApi.SPIN.invoker().onSpin(new SlotsApi.Spin(player, r.player, r.machine.id, r.bet, r.stake, tape.payoutChips(), List.of(),
				tape.progressiveChips(), false, r.owned));
			int[] tiers = tape.jackpots().stream().mapToInt(SpinTape.JackpotAward::tier).toArray();
			String bonus = tape.hunt() != null ? "hunt" : tape.hoard() != null ? "hoard" : tape.wheel() != null ? "wheel" : "";
			SlotsApi.ROUND.invoker().onRound(new SlotsApi.Round(player, r.player, r.machine.id, r.bet, r.stake, r.bought, r.owned,
				tape.payoutChips(), tape.progressiveChips(), SlotTiers.of(tape.totalFifths(), CasinoConfig.slots().bigWinTiers).name(),
				tape.freeSpins() != null, bonus, tiers, tape.capHit(), facts.maxTumbles(), level, worldPosition));
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("slots SPIN/ROUND listener failed", e);
		}
		for (String id : SlotOutcomes.advancements(def, tape)) {
			if (CasinoAdvancements.IDS.contains(id)) {
				CasinoAdvancements.grant(server, r.player, id);
			}
		}
		if (quiet || player == null || !CasinoMode.isEnabled(server) || !CasinoConfig.chaos().enabled) {
			return;
		}
		SlotOutcomes.ChaosPick pick = SlotOutcomes.chaos(def, tape, r.stake, OddsService.get().fair().nextDouble() < 0.3);
		if (pick == null) {
			return;
		}
		List<ServerPlayer> nearby = List.of();
		if (pick.priority() == 1) {
			double rad = SlotsApi.JACKPOT_SHOWER_RADIUS;
			nearby = level.players().stream().filter(q -> q != player && !q.isRemoved() && q.distanceToSqr(player) <= rad * rad).toList();
		}
		SlotsApi.ChaosEvent kind = switch (pick.event()) {
			case "jackpot" -> SlotsApi.ChaosEvent.JACKPOT;
			case "golden_hour" -> SlotsApi.ChaosEvent.GOLDEN_HOUR;
			case "random_teleport" -> SlotsApi.ChaosEvent.RANDOM_TELEPORT;
			case "mob_wave" -> SlotsApi.ChaosEvent.MOB_WAVE;
			default -> null;
		};
		if (kind != null) {
			try {
				SlotsApi.TRIGGER.invoker().onTrigger(new SlotsApi.Trigger(player, kind, pick.event(), r.machine.id, level, worldPosition,
					kind == SlotsApi.ChaosEvent.JACKPOT ? tape.progressiveChips() : 0, nearby));
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("slots TRIGGER listener failed", e);
			}
		}
		if (pick.priority() == 1) {
			ChaosBridge.jackpot(player);
			return;
		}
		long ghBefore = CoreServices.goldenHour().remainingTicks(server);
		String result = ChaosBridge.trigger(player, pick.event());
		if (pick.priority() == 3) {
			boolean started = "started".equals(result) || CoreServices.goldenHour().remainingTicks(server) > ghBefore;
			player.sendSystemMessage(Component.translatable(started ? "msg.burmaldaholic.slots.golden_scatters"
				: "msg.burmaldaholic.slots.golden_scatters_cooldown").withStyle(ChatFormatting.GOLD));
		} else if (pick.messageKey() != null && ChaosBridge.happened(result)) {
			Component msg = pick.arg() > 0 ? Component.translatable(pick.messageKey(), Texts.number(pick.arg())) : Component.translatable(pick.messageKey());
			player.sendSystemMessage(msg.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	private void startAutoV2(ServerPlayer player, CompoundTag args) {
		SlotsV2Config.Autoplay cfg = CasinoConfig.slots().autoplay;
		if (!cfg.enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		if (!ownerAllowsAutoplay()) {
			sendError(player, Component.translatable("gui.burmaldaholic.slots.error.autoplay_disabled"));
			return;
		}
		if (round != null || autoV2 != null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		int count = args.getIntOr("count", 0);
		int loss = args.getIntOr("loss_limit", 0);
		if (Arrays.stream(cfg.lossLimits).noneMatch(x -> x == loss)) {
			sendError(player, Component.translatable("gui.burmaldaholic.slots.error.loss_limit_required"));
			return;
		}
		if (Arrays.stream(cfg.counts).noneMatch(x -> x == count)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		long bet = args.getLongOr("bet", betsV2.getOrDefault(player.getUUID(), -1L));
		AutoV2 a = new AutoV2(player.getUUID());
		a.left = count;
		a.bet = bet;
		a.lossLimit = (long) loss * Math.max(bet, 1);
		a.stopFeature = args.getBooleanOr("stop_feature", true);
		a.stopWinMult = Math.max(0, args.getIntOr("stop_win", 50));
		a.startBalance = Economies.get().balance(player);
		autoV2 = a;
		if (!startV2(player, bet, false, true)) {
			autoV2 = null;
			return;
		}
		RoundV2 started = round;
		if (started != null && started.bet != bet) {
			// no / stale bet in the request: the round used the player's current bet; limits are relative to it (§6.4)
			a.bet = started.bet;
			a.lossLimit = (long) loss * started.bet;
		}
	}

	private void continueAutoV2() {
		AutoV2 a = autoV2;
		if (a == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(a.player);
		if (player == null || !viewing(player) || !ownerAllowsAutoplay()) {
			autoV2 = null;
			return;
		}
		if (!startV2(player, a.bet, false, true)) {
			endAutoV2("");
		}
		syncViewers();
	}

	private void stopAutoV2(UUID player) {
		if (autoV2 != null && autoV2.player.equals(player)) {
			cancelTimer(TIMER_V2_AUTO);
			endAutoV2("");
		}
	}

	private void endAutoV2(String notice) {
		AutoV2 a = autoV2;
		autoV2 = null;
		cancelTimer(TIMER_V2_AUTO);
		if (a == null || a.spins == 0) {
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putInt("spins", a.spins);
		t.putLong("bet", a.wagered);
		t.putLong("won", a.won);
		t.putString("notice", notice);
		autoSummaries.put(a.player, t);
	}

	/**
	 * The tape a client may see now (slots.md F7): while a Treasure Hunt is running only the entries already opened are
	 * sent, and the total / jackpot list (which would reveal the hunt) are withheld ({@code total} = -1).
	 */
	static SpinTape visibleTape(MachineDef def, SpinTape t) {
		SpinTape.Hunt h = t.hunt();
		if (h == null || h.opened() >= SlotDraw.huntOpens(def, t)) {
			return t;
		}
		int[] shown = Arrays.copyOf(h.entries(), h.opened());
		return new SpinTape(t.machine(), t.bet(), t.bought(), t.stops(), t.freeSpins(), new SpinTape.Hunt(shown, h.opened()), t.hoard(), t.wheel(),
			List.of(), -1, false);
	}

	/** Timeline seed + visible tape section of the running round (screen state). */
	private CompoundTag roundTag(RoundV2 r) {
		CompoundTag st = new CompoundTag();
		MachineDef def = SlotMachinesV2.def(r.machine);
		st.putInt("seq", r.seq);
		st.putString("game", "slots." + r.machine.id);
		st.putLong("start_tick", r.startTick);
		st.putInt("speed", r.speedPct);
		st.putInt("seed", r.seed);
		st.putBoolean("anticipation", r.anticipation);
		st.putInt("hold_ms", r.holdMs);
		st.putString("tape", TapeCodec.encode(visibleTape(def, r.tape)));
		st.putInt("hunt_board", def.features().pickBoard());
		st.putInt("gate_ticks", r.holdMs >= 0 ? -1 : r.timeline(def).sharedEndTicks());
		st.putString("player", r.playerName);
		return st;
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag t = baseState(viewer);
		t.put("v2", clientStateV2(viewer));
		t.putString("tier", tier.id());
		return t;
	}

	private CompoundTag clientStateV2(ServerPlayer viewer) {
		CompoundTag t = new CompoundTag();
		MinecraftServer server = viewer.level().getServer();
		Machine m = machineV2();
		SlotsV2Config.Machine cfg = SlotMachinesV2.cfg(m);
		MachineDef def = SlotMachinesV2.def(m);
		boolean owned = ownership().isPresent();
		t.putString("machine", m.id);
		t.putBoolean("enabled", CasinoConfig.slots().enabled && cfg.enabled);
		t.putInt("min_vip", cfg.minVipTier);
		t.putBoolean("vip_ok", CoreServices.vip().tier(server, viewer.getUUID()) >= cfg.minVipTier);
		t.putBoolean("owned", owned);
		List<Long> offered = offeredBets(viewer);
		t.putLongArray("bets", offered.stream().mapToLong(Long::longValue).toArray());
		long bet = betsV2.getOrDefault(viewer.getUUID(), (long) cfg.defaultBet);
		if (!offered.isEmpty() && !offered.contains(bet)) {
			bet = offered.getFirst();
		}
		t.putLong("bet", bet);
		boolean buyOk = CasinoConfig.slots().buyFeature.enabled && def.buyPriceFifths() > 0 && ownerAllowsBuy();
		t.putBoolean("buy_enabled", buyOk);
		if (buyOk) {
			t.putLong("buy_price", def.buyPrice(bet));
			t.putDouble("buy_rtp", SlotMachinesV2.buyRtp(m));
		}
		t.putDouble("rtp", owned ? SlotMachinesV2.ownedRtp(m) : SlotMachinesV2.houseRtp(m));
		t.putInt("max_win", def.capMultiple());
		t.putBoolean("turbo_allowed", CasinoConfig.slots().turboAllowed);
		t.putBoolean("turbo", turboV2.getOrDefault(viewer.getUUID(), false));
		t.putBoolean("autoplay", CasinoConfig.slots().autoplay.enabled && ownerAllowsAutoplay());
		t.putIntArray("auto_counts", CasinoConfig.slots().autoplay.counts);
		t.putIntArray("auto_loss_limits", CasinoConfig.slots().autoplay.lossLimits);
		t.putIntArray("big_win_tiers", CasinoConfig.slots().bigWinTiers);
		// the machine definition: the client builds frames and timelines from the same data as the server
		int[] pays = new int[def.paysFifths().length * 3];
		for (int s = 0; s < def.paysFifths().length; s++) {
			System.arraycopy(def.paysFifths()[s], 0, pays, s * 3, 3);
		}
		t.putIntArray("pays", pays);
		t.putIntArray("scatter_pays", def.scatterFifths());
		t.putIntArray("free_spins", def.freeSpins());
		t.putInt("retrigger", def.retrigger());
		t.putInt("fs_cap", def.fsCap());
		t.putInt("fs_mult", def.fsMultiplier());
		t.putIntArray("ladder", def.ladder());
		t.putIntArray("ladder_free", def.ladderFree());
		t.putInt("bonus_mask", def.bonusReelsMask());
		t.putInt("buy", def.buyPriceFifths());
		for (int r = 0; r < 5; r++) {
			t.putIntArray("strip" + r, def.strips()[r]);
		}
		int[][] rings = def.features().wheelRings();
		for (int i = 0; i < rings.length; i++) {
			t.putIntArray("wheel" + i, rings[i]);
		}
		t.putInt("hunt_board", def.features().pickBoard());
		t.putInt("hold_trigger", def.features().holdTrigger());
		t.putInt("hold_respins", def.features().holdRespins());
		t.putLong("jackpot_ref", def.features().jackpotRef());
		long[] meters = new long[4];
		JackpotPoolsV2 pools = JackpotPoolsV2.get(server);
		for (int k = 1; k <= 4; k++) {
			meters[k - 1] = owned ? def.features().ownedMult()[k - 1] * bet : pools.meter(m, def, k);
		}
		t.putLongArray("jackpots", meters);
		t.putLongArray("stats", pools.stats(viewer.getUUID(), m));
		RoundV2 r = round;
		// the window before the running spin (or the next one): stops and, after tumbles / sticky reels, its cells
		int[] rest = r != null ? r.prevStops : restStops;
		t.putIntArray("rest", rest);
		if (restCells != null && Arrays.equals(rest, restStops)) {
			t.putIntArray("rest_cells", restCells);
		}
		if (r != null) {
			CompoundTag st = roundTag(r);
			st.putBoolean("mine", r.player.equals(viewer.getUUID()));
			t.put("spin", st);
		}
		if (lastV2 != null) {
			CompoundTag res = lastV2.copy();
			res.putBoolean("mine", res.getStringOr("player_id", "").equals(viewer.getUUID().toString()));
			res.remove("player_id");
			t.put("result", res);
		}
		if (autoV2 != null) {
			CompoundTag a = new CompoundTag();
			a.putInt("left", autoV2.left);
			a.putBoolean("mine", autoV2.player.equals(viewer.getUUID()));
			t.put("auto", a);
		}
		CompoundTag summary = autoSummaries.get(viewer.getUUID());
		if (summary != null) {
			t.put("auto_summary", summary.copy());
		}
		return t;
	}

	private CompoundTag resultTagV2(RoundV2 r, SpinTape tape) {
		CompoundTag t = new CompoundTag();
		t.putInt("seq", r.seq);
		t.putString("tape", TapeCodec.encode(tape));
		t.putLong("bet", r.bet);
		t.putLong("stake", r.stake);
		t.putLong("total", tape.payoutChips());
		t.putString("tier", SlotTiers.of(tape.totalFifths(), CasinoConfig.slots().bigWinTiers).name());
		t.putString("player", r.playerName);
		t.putString("player_id", r.player.toString());
		return t;
	}

	// ---- in-world cabinet (J-L10, docs/architecture/animation.md §2.5) ------------------------------------------

	/** The round's cabinet sync (visible tape section, times from the round's timeline) to every nearby renderer. */
	private void publishRound(RoundV2 r) {
		MachineDef def = SlotMachinesV2.def(r.machine);
		SpinTape visible = visibleTape(def, r.tape);
		publishCabinet(CabinetPublish.of(def, visible, r.timeline(def), r.seq, r.startTick, r.seed, r.speedPct, r.prevStops,
			r.huntCells.stream().mapToInt(Integer::intValue).toArray(), CasinoConfig.slots().bigWinTiers));
	}

	/**
	 * Publishes a spin to every nearby client's cabinet renderer (block update); each client plays its world FX with its
	 * own FX settings ({@link SlotsFx#clientSync}).
	 */
	public void publishCabinet(CabinetSync sync) {
		cabinet = sync;
		setChanged();
		if (level instanceof ServerLevel serverLevel) {
			BlockState st = getBlockState();
			serverLevel.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
		}
	}

	/** The last published cabinet sync (client: from the update tag), or {@code null} before the first spin. */
	public @Nullable CabinetSync cabinetSync() {
		return cabinet;
	}

	private static @Nullable CabinetSync decodeCabinet(int[] data) {
		try {
			return CabinetSync.decode(data);
		} catch (IllegalArgumentException e) {
			return null; // older/newer format: the cabinet simply shows nothing until the next spin
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag t = new CompoundTag();
		if (cabinet != null) {
			t.putIntArray(CABINET_KEY, cabinet.encode());
		}
		return t;
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (round != null) {
			output.store(ROUND_KEY, CompoundTag.CODEC, round.save());
		}
		output.putIntArray("slots_rest", restStops);
		if (restCells != null) {
			output.putIntArray("slots_rest_cells", restCells);
		}
		if (cabinet != null) {
			output.putIntArray(CABINET_KEY, cabinet.encode());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(ROUND_KEY, CompoundTag.CODEC).ifPresent(t -> round = RoundV2.load(t));
		input.getIntArray("slots_rest").filter(a -> a.length == 5).ifPresent(a -> restStops = a);
		restCells = input.getIntArray("slots_rest_cells").filter(a -> a.length == 15).orElse(null);
		CabinetSync before = cabinet;
		cabinet = input.getIntArray(CABINET_KEY).map(SlotMachineBlockEntity::decodeCabinet).orElse(null);
		if (level != null && level.isClientSide() && cabinet != null && !cabinet.equals(before)) {
			SlotsFx.clientSync.accept(this); // world FX of the new spin, played with this viewer's FX settings
		}
	}

	/** Restart after a crash: the drawn round is settled from its persisted tape (SLOTS.md §8.1, §15 test 13). */
	@Override
	protected void resumeUnfinishedRound(ServerLevel level) {
		if (round != null) {
			Burmaldaholic.LOGGER.info("Slots at {}: settling the persisted v2 round of {} from its tape", worldPosition, round.player);
			finishV2(true);
		}
	}

	/** Removal / unload / stop: the drawn round is settled from its tape (never refunded), seated or not (§4.1). */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		if (round != null) {
			finishV2(true);
		}
		super.playOutForRemoval(level);
	}

	@Override
	protected boolean hasRoundInPlay() {
		return round != null;
	}

	@Override
	protected void serverTick(ServerLevel level) {
		// closing the machine screen = reveal (slots.md F8): the round settles at once from the tape
		RoundV2 r = round;
		if (r != null && !r.auto && level.getServer().getTickCount() % 10 == 0) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(r.player);
			if (p != null && !p.isRemoved() && !viewing(p)) {
				finishV2(true);
			}
		}
	}
}
