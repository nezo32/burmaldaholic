package dev.nezo.burmaldaholic.games.poker;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PokerConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.TablePresetProvider;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.poker.logic.Bots;
import dev.nezo.burmaldaholic.games.poker.logic.Hand;
import dev.nezo.burmaldaholic.games.poker.logic.PokerRng;
import dev.nezo.burmaldaholic.games.poker.logic.PokerTable;
import dev.nezo.burmaldaholic.games.poker.logic.Pots;
import dev.nezo.burmaldaholic.games.poker.logic.StakeLevel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Texas Hold'em cash table (GAME_DESIGN.md §7, UI.md §5). Humans buy in (balance → table stack), house
 * bots fill empty seats, hands run on {@link Hand} with per-action timers, side pots and rake.
 *
 * <p><b>Money</b>: poker is PvP, so the core open-stake/bankroll machinery is not used. The buy-in is
 * escrowed in the world bank ({@link AccountId#HOUSE}) and the stack is paid back on stand-up / removal
 * (the invested part as a transfer, the profit as a garnishable payout). Bots are funded by the bank; the
 * rake of raked pots goes to the owner's bankroll at owned tables (§18.2), otherwise it stays in the bank
 * (sink). Every human seat's crash-safe stack (the start stack of the running hand) is saved with the
 * block entity and paid back if the table is loaded again with seats still open (server stop, §4.1).
 */
public class PokerTableBlockEntity extends CasinoTableBlockEntity {
	private static final String STAKE_KEY = "burmaldaholic_poker_stake";
	private static final String REFUND_KEY = "burmaldaholic_poker_refunds";
	private static final Codec<Map<String, Long>> REFUND_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG);
	private static final int LOG_SIZE = 8;
	/** Player → table they are seated at (one poker seat per player). */
	private static final Map<UUID, PokerTableBlockEntity> SEATED = new ConcurrentHashMap<>();
	private static int botIds;

	private StakeLevel stake;
	private PokerTable table;
	private int eventIdx;
	private int actionSeq = -1;
	private final Deque<Component> log = new ArrayDeque<>();
	private final List<Component> lastResult = new ArrayList<>();
	private final Map<String, Long> pendingRefunds = new LinkedHashMap<>();
	private boolean refundsLoaded;
	/** Seats marked leaving only because the player disconnected (re-attached if they come back). */
	private final java.util.Set<String> droppedSeats = new java.util.HashSet<>();

	public PokerTableBlockEntity(TableType<PokerTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Seats are managed by the poker table model (humans + bots), not by core's seat list. */
	@Override
	protected int seatCount() {
		return 0;
	}

	private static PokerConfig cfg() {
		return CasinoConfig.poker();
	}

	private static long bbFor(StakeLevel level) {
		PokerConfig.Stakes s = cfg().stakes;
		return switch (level) {
			case MICRO -> s.micro.bb;
			case LOW -> s.low.bb;
			case MID -> s.mid.bb;
			case HIGH -> s.high.bb;
		};
	}

	private static int[] botMix(StakeLevel level) {
		PokerConfig.BotMix m = cfg().botMix;
		return switch (level) {
			case MICRO -> m.micro;
			case LOW -> m.low;
			case MID -> m.mid;
			case HIGH -> m.high;
		};
	}

	private static Pots.RakeConfig rakeConfig() {
		return new Pots.RakeConfig(cfg().rakePercent, cfg().rakeCapBb, cfg().rakeNoFlopNoDrop);
	}

	private static PokerRng rng() {
		CasinoRng r = OddsService.get().fair();
		return new PokerRng() {
			@Override
			public int nextInt(int bound) {
				return r.nextInt(bound);
			}

			@Override
			public double nextDouble() {
				return r.nextDouble();
			}
		};
	}

	private MinecraftServer server() {
		return level instanceof ServerLevel sl ? sl.getServer() : null;
	}

	private ServerPlayer online(String id) {
		MinecraftServer server = server();
		if (server == null || id.startsWith("bot:")) {
			return null;
		}
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private List<ServerPlayer> seatedOnline() {
		List<ServerPlayer> out = new ArrayList<>();
		if (table == null) {
			return out;
		}
		for (PokerTable.Seat s : table.humans()) {
			ServerPlayer p = online(s.id);
			if (p != null && !s.disconnected) {
				out.add(p);
			}
		}
		return out;
	}

	private int vipTier(ServerPlayer player) {
		return CoreServices.vip().tier(player.level().getServer(), player.getUUID());
	}

	// ---- actions ----------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "buy_in" -> buyIn(player, args.getStringOr("level", ""), args.getLongOr("amount", 0));
			case "top_up" -> topUp(player, args.getLongOr("amount", 0));
			case "stand_up" -> standUp(player);
			case "sit_out" -> {
				if (table != null && table.seatOf(id(player)) != null) {
					table.sitOut(id(player));
				}
			}
			case "sit_in" -> {
				if (table != null && table.seatOf(id(player)) != null) {
					table.sitIn(id(player));
					scheduleHand(20);
				}
			}
			case "act" -> act(player, args);
			default -> {
			}
		}
		syncViewers();
	}

	private static String id(ServerPlayer player) {
		return player.getUUID().toString();
	}

	private boolean humansSeated() {
		return table != null && !table.humans().isEmpty();
	}

	private void buyIn(ServerPlayer player, String levelId, long amount) {
		if (!cfg().enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		// Wager gate for the PvP entry: owner can't play at their own table, closed casino, loan Asset Freeze.
		Component veto = Wagers.check(player, new WagerVeto.Context(gameId(), Stake.Kind.CHIPS, worldPosition, true));
		if (veto != null) {
			sendError(player, veto);
			return;
		}
		PokerTableBlockEntity other = SEATED.get(player.getUUID());
		if (other != null && other != this && !other.isRemoved() && other.table != null && other.table.seatOf(id(player)) != null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.busy"));
			return;
		}
		if (table != null && table.seatOf(id(player)) != null) {
			return;
		}
		// A generated table may fix the stake level (Piglin Parlor: Low, §16.2).
		String fixed = preset().map(TablePresetProvider.TablePreset::pokerStakes).orElse("");
		StakeLevel level = humansSeated() ? stake : StakeLevel.byId(fixed.isEmpty() ? levelId : fixed);
		if (level == null) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return;
		}
		if (vipTier(player) < level.minTier()) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(level.minTier())));
			return;
		}
		Economy eco = Economies.get();
		long balance = eco.balance(player);
		long bb = bbFor(level);
		long[] range = StakeLevel.buyInRange(bb, cfg().minBuyInBb, cfg().maxBuyInBb, balance, 0);
		if (range[1] < range[0]) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (amount < range[0] || amount > range[1]) {
			sendError(player, Component.translatable("gui.burmaldaholic.poker.buy_in_range", Texts.number(range[0]), Texts.number(range[1])));
			return;
		}
		if (!humansSeated()) {
			stake = level;
			table = new PokerTable(cfg().maxSeats, bb, rakeConfig());
			log.clear();
			lastResult.clear();
			setPhase("waiting");
		}
		int seat = table.addHuman(id(player), player.getName().getString(), amount);
		if (seat < 0) {
			table.makeRoom();
			seat = table.addHuman(id(player), player.getName().getString(), amount);
		}
		if (seat < 0) {
			sendError(player, Component.translatable(table.inHand() ? "gui.burmaldaholic.error.round_in_progress" : "gui.burmaldaholic.error.table_full"));
			return;
		}
		if (!eco.tryWithdraw(player, amount, new Transaction(PokerModule.ID, "buy_in", Transaction.Kind.TRANSFER))) {
			table.removeSeat(id(player));
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
			return;
		}
		SEATED.put(player.getUUID(), this);
		setChanged();
		if (!table.inHand()) {
			scheduleHand(40);
		}
	}

	private void topUp(ServerPlayer player, long amount) {
		if (table == null || table.seatOf(id(player)) == null) {
			return;
		}
		if (!table.canTopUp(id(player))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.round_in_progress"));
			return;
		}
		Economy eco = Economies.get();
		long balance = eco.balance(player);
		long stack = table.seatOf(id(player)).stack;
		long[] range = StakeLevel.buyInRange(table.bb(), cfg().minBuyInBb, cfg().maxBuyInBb, balance, stack);
		if (range[1] < range[0]) {
			sendError(player, range[1] <= 0 && balance > 0
				? Component.translatable("gui.burmaldaholic.error.table_max", Texts.number(stack))
				: Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance)));
			return;
		}
		if (amount < range[0] || amount > range[1]) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.table_max", Texts.number(range[1])));
			return;
		}
		if (!eco.tryWithdraw(player, amount, new Transaction(PokerModule.ID, "buy_in", Transaction.Kind.TRANSFER))) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(eco.balance(player))));
			return;
		}
		table.topUp(id(player), amount);
		setChanged();
		if (!table.inHand()) {
			scheduleHand(20);
		}
	}

	private void standUp(ServerPlayer player) {
		if (table == null) {
			return;
		}
		String id = id(player);
		PokerTable.Seat seat = table.seatOf(id);
		if (seat == null) {
			return;
		}
		if (table.liveInHand(id)) {
			seat.leaving = true;
			Hand h = table.hand();
			if (h.toAct() >= 0 && h.player(h.toAct()).id.equals(id)) {
				apply(Hand.Action.fold());
			}
			if (table.seatOf(id) != null) {
				player.sendSystemMessage(PokerText.msg("leaving_after_hand"));
			}
			return;
		}
		if (table.inHand() && table.handIndexOf(id) >= 0) {
			// folded in the running hand: the seat's stack is final, leave now
			seat.leaving = true;
		}
		cashOut(id);
		afterHumanLeft();
	}

	private void act(ServerPlayer player, CompoundTag args) {
		if (table == null || !table.inHand()) {
			return;
		}
		Hand h = table.hand();
		String id = id(player);
		if (h.toAct() < 0 || !h.player(h.toAct()).id.equals(id)) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.not_your_turn"));
			return;
		}
		if (args.getIntOr("seq", h.seq()) != h.seq()) {
			return; // stale click
		}
		Hand.Action a = switch (args.getStringOr("kind", "")) {
			case "fold" -> Hand.Action.fold();
			case "check" -> Hand.Action.check();
			case "call" -> Hand.Action.call();
			case "raise" -> Hand.Action.raiseTo(args.getLongOr("to", 0));
			case "all_in" -> Hand.Action.allIn();
			default -> null;
		};
		if (a == null) {
			return;
		}
		table.recordAction(id);
		PokerTable.Seat seat = table.seatOf(id);
		if (seat != null && seat.sittingOut) {
			table.sitIn(id);
		}
		apply(a);
	}

	// ---- hand loop --------------------------------------------------------------------------------

	private void scheduleHand(int ticks) {
		if (table == null || table.inHand() || ticksLeft("next_hand") >= 0) {
			return;
		}
		startTimer("next_hand", ticks);
	}

	@Override
	protected void onTimer(String id) {
		try {
			switch (id) {
				case "next_hand" -> beginHand();
				case "action" -> onActionTimeout();
				case "bot" -> botAct();
				case "auto" -> autoAct();
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker table {} timer '{}' failed", worldPosition, id, e);
			shutdown();
		}
		syncViewers();
	}

	private void beginHand() {
		if (table == null || table.inHand()) {
			return;
		}
		if (!cfg().enabled || !(level instanceof ServerLevel serverLevel) || !CasinoMode.isEnabled(serverLevel)) {
			shutdown();
			return;
		}
		table.configure(bbFor(stake), rakeConfig());
		double max = cfg().maxDistance;
		for (PokerTable.Seat s : table.humans()) {
			ServerPlayer p = online(s.id);
			if (p == null) {
				s.disconnected = true;
				continue;
			}
			boolean far = p.level() != level || p.distanceToSqr(Vec3.atCenterOf(worldPosition)) > max * max;
			if (far && !s.sittingOut) {
				table.sitOut(s.id);
				p.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.too_far"));
			}
		}
		removeFinished();
		if (!humansSeated()) {
			afterHumanLeft();
			return;
		}
		boolean bots = cfg().botsEnabled && ownership().map(OwnedTable::bots).orElse(true); // owner "bots on/off" (§18.2)
		int maxBots = preset().map(TablePresetProvider.TablePreset::pokerBots).orElse(-1);
		PokerTable.FillResult fill = table.fillBots(new PokerTable.BotFill(bots, botMix(stake), cfg().botBuyInBb * table.bb(), maxBots),
			rng(), () -> "bot:" + (++botIds));
		for (PokerTable.Seat b : fill.left()) {
			if (b.stack <= 0) {
				broadcast(PokerText.msg("bot_busts", Texts.raw(b.name)));
			}
		}
		for (PokerTable.Seat b : fill.joined()) {
			broadcast(PokerText.msg("bot_joins", Texts.raw(b.name), PokerText.tier(b.tier)));
		}
		if (!table.canStart()) {
			boolean allSittingOut = table.humans().stream().allMatch(s -> s.sittingOut || s.stack <= 0);
			if (allSittingOut) {
				table.countIdleHand();
				removeFinished();
				if (!humansSeated()) {
					afterHumanLeft();
					return;
				}
			}
			setPhase("waiting");
			startTimer("next_hand", 100);
			return;
		}
		Hand h = table.startHand(rng());
		if (h == null) {
			return;
		}
		setPhase("hand");
		eventIdx = 0;
		lastResult.clear();
		addLog(PokerText.msg("new_hand", Texts.number(table.handNo()), Texts.number(table.sb()), Texts.number(table.bb()))
			.withStyle(ChatFormatting.GRAY));
		setChanged();
		streamEvents();
		drive();
	}

	private void addLog(Component line) {
		log.addLast(line);
		while (log.size() > LOG_SIZE) {
			log.removeFirst();
		}
	}

	private void broadcast(Component msg) {
		for (ServerPlayer p : seatedOnline()) {
			p.sendSystemMessage(msg);
		}
	}

	/** New hand events → table log + action bar of seated players. */
	private void streamEvents() {
		Hand h = table.hand();
		if (h == null) {
			return;
		}
		List<Hand.Event> events = h.events();
		Component last = null;
		for (int i = eventIdx; i < events.size(); i++) {
			last = PokerText.event(table, h, events.get(i));
			addLog(last);
		}
		eventIdx = events.size();
		if (last != null) {
			for (ServerPlayer p : seatedOnline()) {
				p.sendOverlayMessage(last);
			}
		}
	}

	/** Hands the turn to the next actor (human timer / bot think / auto-fold) or ends the hand. */
	private void drive() {
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		Hand h = table.hand();
		if (h == null) {
			return;
		}
		if (h.complete()) {
			endHand();
			return;
		}
		Hand.Player p = h.player(h.toAct());
		PokerTable.Seat seat = table.seatOf(p.id);
		actionSeq = h.seq();
		if (seat == null || !seat.human) {
			int lo = Math.min(cfg().botThinkMinTicks, cfg().botThinkMaxTicks);
			int hi = Math.max(cfg().botThinkMinTicks, cfg().botThinkMaxTicks);
			startTimer("bot", rng().between(lo, hi));
			return;
		}
		ServerPlayer player = online(p.id);
		if (player == null || seat.sittingOut || seat.leaving || seat.disconnected) {
			startTimer("auto", 10);
			return;
		}
		startTimer("action", cfg().actionTimerTicks);
		player.sendOverlayMessage(PokerText.gui("your_turn").withStyle(ChatFormatting.GREEN));
	}

	private boolean current() {
		return table != null && table.inHand() && table.hand().seq() == actionSeq;
	}

	private void apply(Hand.Action a) {
		Hand h = table.hand();
		if (h == null || h.complete()) {
			return;
		}
		try {
			h.apply(h.coerce(a));
		} catch (IllegalStateException e) {
			Burmaldaholic.LOGGER.warn("Poker: illegal action {} coerced to check/fold", a, e);
			h.apply(h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold());
		}
		setChanged();
		streamEvents();
		drive();
	}

	private void onActionTimeout() {
		if (!current()) {
			return;
		}
		Hand h = table.hand();
		String id = h.player(h.toAct()).id;
		Hand.Action auto = h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold();
		ServerPlayer player = online(id);
		if (player != null) {
			player.sendSystemMessage(PokerText.msg("timeout", PokerText.gui(auto.kind() == Hand.Action.Kind.CHECK ? "check" : "fold")));
		}
		if (table.recordTimeout(id, cfg().timeoutsToSitOut) && player != null) {
			player.sendSystemMessage(PokerText.msg("sat_out"));
		}
		apply(auto);
	}

	private void autoAct() {
		if (!current()) {
			return;
		}
		apply(Hand.Action.fold());
	}

	private void botAct() {
		if (!current()) {
			return;
		}
		Hand h = table.hand();
		PokerTable.Seat seat = table.seatOf(h.player(h.toAct()).id);
		Bots.Tier tier = seat != null && seat.tier != null ? seat.tier : Bots.Tier.REGULAR;
		Hand.Action a;
		try {
			a = Bots.decide(h, tier, table.vpipMap(), cfg().bot.regularSamples, cfg().bot.sharkSamples, rng());
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Poker bot failed; folding", e);
			a = Hand.Action.fold();
		}
		apply(a);
	}

	private void endHand() {
		Hand h = table.hand();
		if (h == null || h.result() == null) {
			return;
		}
		Hand.Result r = h.result();
		lastResult.clear();
		lastResult.addAll(PokerText.resultLines(table, h));
		for (Component line : lastResult) {
			broadcast(line);
		}
		MinecraftServer server = server();
		String bankroll = ownership().map(OwnedTable::bankrollId).orElse("");
		boolean sharkBusted = false;
		for (int k = 0; k < h.players().size(); k++) {
			Hand.Player p = h.player(k);
			PokerTable.Seat seat = table.seatOf(p.id);
			if (!p.human && p.stack() <= 0 && seat != null && seat.tier == Bots.Tier.SHARK) {
				sharkBusted = true;
			}
		}
		for (int k = 0; k < h.players().size(); k++) {
			Hand.Player p = h.player(k);
			if (!p.human || p.total() <= 0 || server == null || !(level instanceof ServerLevel sl)) {
				continue;
			}
			UUID uuid = UUID.fromString(p.id);
			// PvP pot: no house edge, no Golden Hour, no cashback; reported offline-safe (a disconnected seat
			// is folded and gets its streak / VIP credit on the next join).
			PlayResults.fire(server, uuid, CasinoEvents.PlayResult.of(gameId(), p.total(), r.won()[k]).pvp().withTable(sl, worldPosition, bankroll));
			if (r.won()[k] > 0) {
				final int kk = k;
				boolean royal = r.pots().stream().anyMatch(pot -> pot.winners().contains(kk) && pot.value() != 0
					&& "royal_flush".equals(HandEvaluator.handName(pot.value())));
				if (royal) {
					CasinoAdvancements.grant(server, uuid, "royal_flush");
				}
				if (sharkBusted) {
					CasinoAdvancements.grant(server, uuid, "shark_hunter");
				}
			}
		}
		collectRake(r.rake()); // core: to the owner's bankroll at owned tables, else stays in the bank

		if (CasinoConfig.debug().logRounds) {
			Burmaldaholic.LOGGER.info("[round] poker at {}: hand #{} pots {} rake {}", worldPosition, table.handNo(),
				r.pots().stream().map(Hand.PotResult::amount).toList(), r.rake());
		}
		table.settleHand();
		setPhase("result");
		setChanged();
		removeFinished();
		if (!humansSeated()) {
			afterHumanLeft();
			return;
		}
		scheduleHand(r.uncontested() ? 60 : 120);
	}

	/** Removes humans who stood up, disconnected, went broke or sat out too long (between hands). */
	private void removeFinished() {
		if (table == null || table.inHand()) {
			return;
		}
		for (PokerTable.Seat s : table.toRemove(cfg().sitOutHandsToRemove)) {
			cashOut(s.id);
		}
	}

	/** Removes a human seat and returns the stack to the balance (offline-safe). */
	private void cashOut(String id) {
		if (table == null) {
			return;
		}
		long amount = table.liveStack(id);
		PokerTable.Seat seat = table.removeSeat(id);
		if (seat == null) {
			return;
		}
		UUID uuid = UUID.fromString(id);
		SEATED.remove(uuid, this);
		droppedSeats.remove(id);
		payOut(uuid, amount, seat.invested);
		firePlayerLeft(uuid, removing() ? LeaveReason.REMOVED : seat.disconnected ? LeaveReason.DISCONNECT : LeaveReason.LEFT);
		ServerPlayer p = online(id);
		if (p != null) {
			p.sendSystemMessage(PokerText.msg("removed", Texts.chips(amount)));
		}
		setChanged();
	}

	private void payOut(UUID player, long amount, long invested) {
		MinecraftServer server = server();
		if (server == null || amount <= 0) {
			return;
		}
		Economy eco = Economies.get();
		long back = Math.min(amount, Math.max(0, invested));
		if (back > 0) {
			eco.deposit(server, player, back, new Transaction(PokerModule.ID, "cash_out", Transaction.Kind.TRANSFER));
		}
		if (amount > back) {
			eco.deposit(server, player, amount - back, Transaction.payout(PokerModule.ID));
		}
	}

	private void afterHumanLeft() {
		if (table == null || humansSeated()) {
			return;
		}
		if (table.inHand()) {
			table.abortHand();
		}
		cancelTimer("next_hand");
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		table = null;
		stake = null;
		log.clear();
		lastResult.clear();
		setPhase("idle");
		setChanged();
	}

	/** Casino off / game disabled / broken: cancel the running hand and pay everybody out. */
	private void shutdown() {
		if (table == null) {
			return;
		}
		table.abortHand();
		for (PokerTable.Seat s : table.humans()) {
			cashOut(s.id);
		}
		afterHumanLeft();
	}

	// ---- ticking ----------------------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel serverLevel) {
		MinecraftServer server = serverLevel.getServer();
		if (!refundsLoaded) {
			refundsLoaded = true;
			if (!pendingRefunds.isEmpty()) {
				for (Map.Entry<String, Long> e : pendingRefunds.entrySet()) {
					try {
						UUID uuid = UUID.fromString(e.getKey());
						Burmaldaholic.LOGGER.info("Poker table {}: returning {} chips to {} (table reloaded)", worldPosition, e.getValue(), uuid);
						Economies.get().deposit(server, uuid, e.getValue(), Transaction.refund(PokerModule.ID));
						ServerPlayer p = server.getPlayerList().getPlayer(uuid);
						if (p != null) {
							p.sendSystemMessage(PokerText.msg("removed", Texts.chips(e.getValue())));
						}
					} catch (IllegalArgumentException ignored) {
						// not a player id
					}
				}
				pendingRefunds.clear();
				stake = null;
				setChanged();
			}
		}
		if (table == null || server.getTickCount() % 20 != 0) {
			return;
		}
		if (!CasinoMode.isEnabled(serverLevel)) {
			shutdown();
			syncViewers();
			return;
		}
		boolean changed = false;
		for (PokerTable.Seat s : table.humans()) {
			if (table == null) {
				break;
			}
			ServerPlayer p = online(s.id);
			if (p != null && !p.isRemoved() && s.disconnected && droppedSeats.remove(s.id)) {
				// Review M1: a player who rejoins mid-hand gets the seat back (no forced fold / cash-out);
				// the stack is only ever paid by cashOut (offline-safe), never lost or overwritten.
				s.disconnected = false;
				s.leaving = false;
				changed = true;
				continue;
			}
			if ((p == null || p.isRemoved()) && !s.disconnected) {
				s.disconnected = true;
				if (!s.leaving) {
					droppedSeats.add(s.id);
				}
				s.leaving = true;
				changed = true;
				Hand h = table.hand();
				if (table.inHand() && h.toAct() >= 0 && h.player(h.toAct()).id.equals(s.id)) {
					apply(Hand.Action.fold());
				}
			}
		}
		if (changed && table != null && !table.inHand()) {
			removeFinished();
			afterHumanLeft();
		}
		if (changed) {
			syncViewers();
		}
	}

	/**
	 * Table broken (review B1): the running hand is played out now — every human acts by the timeout rule
	 * (check, else fold; all-in players are already committed), bots decide as usual, the board runs out and
	 * the pots are paid — then everybody is cashed out. No abort: breaking a table never returns chips
	 * already in a pot. Only casino mode off / game disabled aborts the hand (§2.1).
	 */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		if (table == null) {
			return;
		}
		cancelTimer("next_hand");
		cancelTimer("action");
		cancelTimer("bot");
		cancelTimer("auto");
		Hand h = table.hand();
		for (int guard = 0; guard < 400 && h != null && !h.complete(); guard++) {
			PokerTable.Seat seat = table.seatOf(h.player(h.toAct()).id);
			Hand.Action a;
			if (seat != null && !seat.human) {
				Bots.Tier tier = seat.tier != null ? seat.tier : Bots.Tier.REGULAR;
				try {
					a = Bots.decide(h, tier, table.vpipMap(), cfg().bot.regularSamples, cfg().bot.sharkSamples, rng());
				} catch (RuntimeException e) {
					a = Hand.Action.fold();
				}
			} else {
				a = h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold();
			}
			try {
				h.apply(h.coerce(a));
			} catch (IllegalStateException e) {
				h.apply(h.legal().canCheck() ? Hand.Action.check() : Hand.Action.fold());
			}
		}
		if (h != null && h.complete()) {
			streamEvents();
			endHand();
		}
		if (table != null) {
			for (PokerTable.Seat s : table.humans()) {
				cashOut(s.id);
			}
			afterHumanLeft();
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		SEATED.values().removeIf(t -> t == this);
	}

	// ---- persistence ------------------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		Map<String, Long> refunds = new HashMap<>(pendingRefunds);
		if (table != null) {
			for (PokerTable.Seat s : table.humans()) {
				long v = table.refundableStack(s.id);
				if (v > 0) {
					refunds.merge(s.id, v, Long::sum);
				}
			}
		}
		if (!refunds.isEmpty()) {
			output.store(REFUND_KEY, REFUND_CODEC, refunds);
		}
		if (stake != null) {
			output.putString(STAKE_KEY, stake.id());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		pendingRefunds.clear();
		input.read(REFUND_KEY, REFUND_CODEC).ifPresent(pendingRefunds::putAll);
		stake = StakeLevel.byId(input.getStringOr(STAKE_KEY, ""));
		refundsLoaded = false;
	}

	// ---- client state -----------------------------------------------------------------------------

	private static Tag encode(RegistryOps<Tag> ops, Component c) {
		return ComponentSerialization.CODEC.encodeStart(ops, c).result().orElseGet(CompoundTag::new);
	}

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag tag = baseState(viewer);
		RegistryOps<Tag> ops = viewer.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String me = id(viewer);
		int tier = vipTier(viewer);
		long balance = Economies.get().balance(viewer);
		tag.putBoolean("enabled", cfg().enabled);
		tag.putBoolean("seated", false);
		tag.putString("stake", stake == null || !humansSeated() ? "" : stake.id());
		tag.putInt("vip", tier);
		tag.putLong("rake_permille", Math.round(cfg().rakePercent * 1000));
		ListTag levels = new ListTag();
		for (StakeLevel l : StakeLevel.values()) {
			CompoundTag lt = new CompoundTag();
			long bb = bbFor(l);
			long[] range = StakeLevel.buyInRange(bb, cfg().minBuyInBb, cfg().maxBuyInBb, balance, 0);
			lt.putString("id", l.id());
			lt.putLong("bb", bb);
			lt.putLong("sb", StakeLevel.smallBlind(bb));
			lt.putInt("min_tier", l.minTier());
			lt.putLong("buy_min", range[0]);
			lt.putLong("buy_max", range[1]);
			lt.putLong("full_min", (long) Math.min(cfg().minBuyInBb, cfg().maxBuyInBb) * bb);
			lt.putLong("full_max", (long) Math.max(cfg().minBuyInBb, cfg().maxBuyInBb) * bb);
			lt.putLong("rake_cap", (long) cfg().rakeCapBb * bb);
			levels.add(lt);
		}
		tag.put("levels", levels);
		if (table == null) {
			tag.putInt("table_size", cfg().maxSeats);
			return tag;
		}
		tag.putInt("table_size", table.size());
		tag.putLong("bb", table.bb());
		tag.putLong("sb", table.sb());
		tag.putInt("hand_no", table.handNo());
		tag.putInt("button", table.button());
		PokerTable.Seat mySeat = table.seatOf(me);
		tag.putBoolean("seated", mySeat != null);
		if (mySeat != null) {
			tag.putBoolean("sitting_out", mySeat.sittingOut);
			tag.putBoolean("leaving", mySeat.leaving);
			tag.putBoolean("can_top_up", table.canTopUp(me));
			long[] top = StakeLevel.buyInRange(table.bb(), cfg().minBuyInBb, cfg().maxBuyInBb, balance, mySeat.stack);
			tag.putLong("top_max", top[1]);
		}
		Hand h = table.hand() != null ? table.hand() : table.lastHand();
		boolean live = table.inHand();
		int[] handSeats = table.handSeats();
		Hand.Result result = h != null && !live ? h.result() : null;
		tag.putBoolean("live", live);
		if (h != null) {
			tag.putString("street", live ? h.street().id() : (result != null && !result.uncontested() ? "showdown" : ""));
			tag.putIntArray("board", h.board().stream().mapToInt(Integer::intValue).toArray());
			tag.putInt("to_act", live && h.toAct() >= 0 ? handSeats[h.toAct()] : -1);
			tag.putLongArray("pots", live ? h.displayPots().stream().mapToLong(Long::longValue).toArray() : new long[0]);
			tag.putLong("pot_total", live ? h.potTotal() : 0);
		}
		Map<Integer, Component> lastAct = new HashMap<>();
		if (live) {
			for (Hand.Event e : h.events()) {
				if (e instanceof Hand.Dealt) {
					lastAct.clear();
				} else {
					lastAct.put(e instanceof Hand.Blind b ? b.player() : ((Hand.Acted) e).player(), PokerText.shortAction(h, e));
				}
			}
		}
		ListTag seats = new ListTag();
		for (int i = 0; i < table.size(); i++) {
			PokerTable.Seat s = table.seat(i);
			if (s == null) {
				continue;
			}
			CompoundTag st = new CompoundTag();
			st.putInt("index", i);
			st.put("name", encode(ops, PokerText.seatName(s)));
			st.putBoolean("bot", !s.human);
			st.putBoolean("you", s.id.equals(me));
			st.putBoolean("out", s.sittingOut);
			int k = h != null ? h.indexOf(s.id) : -1;
			boolean dealt = k >= 0 && (live || (result != null && handSeatsContain(handSeats, i, k)));
			if (dealt) {
				Hand.Player p = h.player(k);
				st.putLong("stack", live ? p.stack() : s.stack);
				st.putLong("bet", live ? p.bet() : 0);
				st.putBoolean("folded", p.folded());
				st.putBoolean("all_in", live && p.allIn());
				if (lastAct.containsKey(k)) {
					st.put("act", encode(ops, lastAct.get(k)));
				}
				boolean show = s.id.equals(me) || (result != null && result.shown().contains(k));
				if (show) {
					st.putIntArray("cards", p.hole());
					if (h.board().size() >= 3 && !p.folded()) {
						st.put("hand", encode(ops, PokerText.handName(h.valueOf(k))));
					}
				} else if (!p.folded()) {
					st.putInt("hidden", 2);
				}
				if (result != null) {
					st.putLong("won", result.won()[k]);
					st.putLong("net", result.net()[k]);
				}
			} else {
				st.putLong("stack", s.stack);
			}
			seats.add(st);
		}
		tag.put("table", seats);
		if (live && h.toAct() >= 0 && h.player(h.toAct()).id.equals(me)) {
			Hand.Legal l = h.legal();
			Hand.Player p = h.player(h.toAct());
			CompoundTag lt = new CompoundTag();
			lt.putInt("seq", h.seq());
			lt.putLong("to_call", l.toCall());
			lt.putBoolean("can_check", l.canCheck());
			lt.putBoolean("can_raise", l.canRaise());
			lt.putLong("min_to", l.minRaiseTo());
			lt.putLong("max_to", l.maxRaiseTo());
			lt.putBoolean("is_bet", l.isBet());
			lt.putLong("my_bet", p.bet());
			lt.putLong("my_stack", p.stack());
			lt.putLong("current_bet", h.currentBet());
			tag.put("legal", lt);
		}
		ListTag logTag = new ListTag();
		for (Component c : log) {
			logTag.add(encode(ops, c));
		}
		tag.put("log", logTag);
		ListTag resultTag = new ListTag();
		if (!live) {
			for (Component c : lastResult) {
				resultTag.add(encode(ops, c));
			}
		}
		tag.put("result", resultTag);
		return tag;
	}

	private static boolean handSeatsContain(int[] handSeats, int seat, int k) {
		return k < handSeats.length && handSeats[k] == seat;
	}
}
