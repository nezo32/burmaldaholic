package dev.nezo.burmaldaholic.games.roulette;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.AtmosphereBots;
import dev.nezo.burmaldaholic.core.bots.BotNames;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats.VirtualBot;
import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.RouletteConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBettor;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Transition;
import dev.nezo.burmaldaholic.games.roulette.logic.SlipLimits;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One roulette table (normal or High-Roller). Server-authoritative shared spin (GAME_DESIGN.md §9):
 * everybody at the table bets into the same round; the bet timer starts at the first bet once 2+ players
 * are seated; a lone player spins with "Spin". Money goes through {@link #placeBet} (one open stake per
 * player per spin, bankroll reservation = worst case over the 37 outcomes) and {@link #settle}.
 *
 * <p>Client actions: {@code bet} {type, nums[], amount}, {@code rebet}, {@code clear}, {@code spin}
 * (= ready), plus core's {@code sit}/{@code leave}. Leaving or disconnecting never cancels confirmed bets:
 * the spin proceeds and offline players are settled to their balance (§4.1). A server restart refunds
 * open bets (core). Casino mode switched off mid-round refunds bets that were not drawn yet; a spin whose
 * result was already drawn is settled.
 *
 * <p><b>Seats &amp; Bots</b> (BOTS.md §4.7): ATMOSPHERE bettors ({@link RouletteBettor}, style by personality,
 * difficulty hidden) sit on free seats ({@link AtmosphereBots}) and keep VIRTUAL slips beside the shared
 * round — never in {@code round}, never a stake. They bet at a random moment in the first 60–160 t of
 * BETTING, are always ready (never delay the spin) and their slips are settled against the same number
 * for display only. Safe point: start of / any time during BETTING.
 */
public class RouletteTableBlockEntity extends CasinoTableBlockEntity implements BotTable.Delegating {
	private static final String HISTORY_KEY = "roulette_history";
	private static final int NO_MORE_BETS_TICKS = 20;
	private static final int RESULT_TICKS = 60;

	private final boolean highRoller;
	private final RouletteRound<UUID> round;
	/** Last settled slip per player (Rebet, and what the RESULT screen shows). */
	private final Map<UUID, List<Bet>> lastSlips = new HashMap<>();
	/** Staked / returned of the spin being shown in RESULT. */
	private final Map<UUID, long[]> outcomes = new HashMap<>();
	/** A bot's virtual slip for this spin and its betting memory (never in {@link #round}, never a stake). */
	private static final class BotSlip {
		final BotProfile bot;
		RouletteBettor.Memory memory;
		/** game time of this round's bet moment (-1 = none yet) */
		long betAt = -1;
		List<Bet> bets = List.of();

		BotSlip(BotProfile bot, RouletteBettor.Memory memory) {
			this.bot = bot;
			this.memory = memory;
		}
	}

	/** A bot's virtual table limits: min bet, {@value} × min per spin (bots have no VIP tier). */
	static final int BOT_TOTAL_MULTIPLE = 50;
	/** Seats &amp; Bots: core TableBots + virtual bot seats */
	private final AtmosphereBots bots = new AtmosphereBots(this, RouletteModule.ID, false);
	private final Map<String, BotSlip> botSlips = new LinkedHashMap<>();
	/** A bot's virtual result of the last spin (display only). */
	private record BotResult(BotProfile bot, long staked, long returned) {}

	/** last spin's bot results for the RESULT display */
	private final List<BotResult> botResults = new ArrayList<>();

	public RouletteTableBlockEntity(TableType<RouletteTableBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.highRoller = RouletteModule.HIGH_ROLLER_NAME.equals(type.name());
		this.round = new RouletteRound<>(RouletteRound.Timings.DEFAULT, 12);
	}

	private static RouletteConfig cfg() {
		return CasinoConfig.roulette();
	}

	private static RouletteRound.Timings timings() {
		RouletteConfig c = cfg();
		return new RouletteRound.Timings(c.betTimerTicks, NO_MORE_BETS_TICKS, c.spinTicks, RESULT_TICKS);
	}

	public boolean isHighRoller() {
		return highRoller || preset().map(p -> p.id().startsWith("high_roller")).orElse(false); // worldgen lounge preset
	}

	@Override
	protected int seatCount() {
		return cfg().maxBettors;
	}

	@Override
	protected long minBet() {
		return cfg().minBet;
	}

	private long minTotal() {
		return isHighRoller() ? cfg().highRollerMinTotal : 0;
	}

	/** Limits for one player: VIP tier max (× High-Roller multiplier), owner min/max, config fractions. */
	SlipLimits limits(ServerPlayer player) {
		RouletteConfig c = cfg();
		MinecraftServer server = player.level().getServer();
		long tierMax = CoreServices.vip().maxBet(server, player.getUUID());
		long totalMax = isHighRoller() ? (long) Math.floor(tierMax * c.highRollerMaxMultiplier) : tierMax;
		long min = c.minBet;
		Optional<OwnedTable> owned = ownership();
		if (owned.isPresent()) {
			if (owned.get().minBet() > 0) {
				min = Math.max(min, owned.get().minBet());
			}
			if (owned.get().maxBet() > 0) {
				totalMax = Math.min(totalMax, owned.get().maxBet());
			}
		}
		return SlipLimits.of(min, totalMax, c.insideMaxFraction, 1.0, minTotal());
	}

	// ---- actions ------------------------------------------------------------------------------

	@Override
	public void onAction(ServerPlayer player, String action, CompoundTag args) {
		switch (action) {
			case "bet" -> {
				Optional<BetType> type = BetType.byId(args.getStringOr("type", ""));
				int[] nums = args.getIntArray("nums").orElse(new int[0]);
				long amount = args.getLongOr("amount", 0);
				if (type.isEmpty() || nums.length == 0 || nums.length > 18) {
					sendError(player, Component.translatable("gui.burmaldaholic.error.invalid_bet_position"));
					return;
				}
				List<Integer> list = new ArrayList<>();
				for (int n : nums) {
					list.add(n);
				}
				addBets(player, List.of(new Bet(Spot.of(type.get(), list), amount)));
			}
			case "rebet" -> {
				List<Bet> last = lastSlips.get(player.getUUID());
				if (last == null || last.isEmpty()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_bets"));
					return;
				}
				addBets(player, last);
			}
			case "clear" -> {
				if (!round.canBet()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_more_bets"));
					return;
				}
				if (!round.clear(player.getUUID()).isEmpty()) {
					refund(player.getUUID(), true); // the player cleared their own slip: no "round refunded" line
				}
				syncViewers();
			}
			case "spin" -> {
				if (!round.canBet()) {
					return;
				}
				List<Bet> mine = round.bets(player.getUUID());
				if (mine.isEmpty()) {
					sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_bets"));
					return;
				}
				Optional<SlipLimits.Violation> v = limits(player).checkSpin(mine);
				if (v.isPresent()) {
					sendError(player, message(v.get()));
					return;
				}
				round.setReady(player.getUUID(), true);
				syncViewers();
			}
			default -> { }
		}
	}

	private void addBets(ServerPlayer player, List<Bet> bets) {
		RouletteConfig c = cfg();
		if (!c.enabled) {
			sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		if (!round.canBet()) {
			sendError(player, Component.translatable("gui.burmaldaholic.roulette.no_more_bets"));
			return;
		}
		if (!isSeated(player) && !sit(player)) {
			return; // sit() already explained why (table full / own table)
		}
		UUID id = player.getUUID();
		List<Bet> current = round.bets(id);
		Optional<SlipLimits.Violation> v = limits(player).checkAdd(current, bets);
		if (v.isPresent()) {
			sendError(player, message(v.get()));
			return;
		}
		long amount = Bets.totalStaked(bets);
		List<Bet> next = Bets.mergeAll(current, bets);
		long reserve = Bets.worstCase(next, c.laPartage) - Bets.worstCase(current, c.laPartage);
		// Limits were checked above (per bet / per spot / per spin); placeBet re-checks funds and owner rules.
		Result<Long> r = placeBet(player, amount, 1, 0, reserve, false);
		if (r.isOk()) {
			round.addBets(id, bets);
			syncViewers();
		}
	}

	static Component message(SlipLimits.Violation v) {
		return switch (v.code()) {
			case INVALID_AMOUNT -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
			case INVALID_POSITION -> Component.translatable("gui.burmaldaholic.error.invalid_bet_position");
			case BET_TOO_LOW -> Component.translatable("gui.burmaldaholic.error.bet_too_low", Texts.number(v.value()));
			case INSIDE_MAX -> Component.translatable("gui.burmaldaholic.roulette.error.inside_max", Texts.number(v.value()));
			case TOTAL_MAX -> Component.translatable("gui.burmaldaholic.roulette.error.total_max", Texts.number(v.value()));
			case MIN_TOTAL -> Component.translatable("gui.burmaldaholic.roulette.error.min_total", Texts.number(v.value()));
		};
	}

	// ---- seats --------------------------------------------------------------------------------

	@Override
	public boolean sit(ServerPlayer player) {
		if (isSeated(player)) {
			return true;
		}
		// Seats & Bots: private table, someone's BOTS_ONLY table, claimant of a bot seat (BOTS.md §3.2)
		Result<Boolean> admit = bots.admit(player);
		if (!admit.isOk()) {
			sendError(player, admit.error());
			return false;
		}
		if (!Boolean.TRUE.equals(admit.value()) || !super.sit(player)) {
			return false; // claimant: TableBots already said "a bot gives up its seat after this round"
		}
		bots.noteJoin(player.getUUID());
		if (round.canBet()) {
			botSafePoint(); // atmosphere safe point: any time during BETTING
		}
		return true;
	}

	@Override
	public BotTable botDelegate() {
		return bots;
	}

	/** Thinking dots while betting is open: the bot whose (virtual) bet moment comes next. */
	@Override
	public @Nullable String botThinking() {
		if (!round.canBet() || botSlips.isEmpty()) {
			return null;
		}
		Map<String, Long> due = new LinkedHashMap<>();
		botSlips.forEach((key, s) -> due.put(key, s.bets.isEmpty() ? s.betAt : -1L));
		return dev.nezo.burmaldaholic.core.bots.logic.ThinkingBot.next(due, gameTime());
	}

	/** Seats &amp; Bots of this table (tests, bots UI). */
	public AtmosphereBots bots() {
		return bots;
	}

	/** Bets ride when a player walks away: they may always leave, the spin proceeds without them. */
	@Override
	protected boolean canLeaveNow(UUID player) {
		return true;
	}

	@Override
	protected void onPlayerLeft(UUID player, LeaveReason reason) {
		if (reason == LeaveReason.REMOVED && round.canBet()) {
			round.clear(player); // nothing drawn yet: core returns the stake of a broken table
		}
		if (round.canBet() || seats().isEmpty()) {
			botSafePoint(); // atmosphere safe point; the last human leaving ends the bot session
		}
		// Otherwise the confirmed bets stay in the round (§4.1 "roulette: spin proceeds"; review B1: also
		// when the table is broken mid-spin — see playOutForRemoval).
	}

	/**
	 * Table broken (review B1): a round past "no more bets" is spun and settled right now with the same
	 * fair draw; bets of the betting phase were never drawn and are returned by core.
	 */
	@Override
	protected void playOutForRemoval(ServerLevel level) {
		if (round.canBet()) {
			round.abort();
			return;
		}
		for (int i = 0; i < 4 && round.phase() != RouletteRound.Phase.RESULT && round.phase() != RouletteRound.Phase.BETTING; i++) {
			// Fast-forward to the end of the current phase. (A fixed "far future" tick stalled in SPIN when the
			// table was broken during NO_MORE_BETS: the spin's end was scheduled after it, so the drawn round
			// was refunded instead of settled.)
			long now = Math.max(gameTime(), round.endsAt());
			Transition<UUID> t = round.update(now, List.of(), () -> OddsService.get().fair().nextInt(Wheel.POCKETS), 0);
			if (t instanceof Transition.Result<UUID> r) {
				settleAll(level, r.result(), r.slips());
			}
		}
	}

	// ---- the shared spin ----------------------------------------------------------------------

	@Override
	protected void serverTick(ServerLevel level) {
		if (!CasinoMode.isEnabled(level)) {
			if (round.phase() == RouletteRound.Phase.SPIN) {
				// The result was already drawn: finish the spin and settle it (like a broken table) instead of refunding.
				Transition<UUID> t = round.update(Math.max(gameTime(), round.endsAt()), List.of(), () -> round.result(), 0);
				if (t instanceof Transition.Result<UUID> r) {
					settleAll(level, r.result(), r.slips());
				}
			}
			if (round.hasBets() || round.phase() != RouletteRound.Phase.BETTING) {
				round.abort().keySet().forEach(this::refund);
				outcomes.clear();
				setPhase(round.phase().id());
				syncViewers();
			}
			return;
		}
		round.configure(timings(), cfg().historyLength);
		if (round.canBet() && placeBotBets(false)) {
			syncViewers();
		}
		List<UUID> seated = seats().occupied().stream().map(TableSeats.Seat::player).toList();
		Transition<UUID> t = round.update(gameTime(), seated, () -> OddsService.get().fair().nextInt(Wheel.POCKETS), minTotal());
		if (t == null) {
			return;
		}
		switch (t) {
			case Transition.NoMoreBets<UUID> n -> {
				placeBotBets(true); // bots are always ready: whoever has not bet yet bets now
				refundDropped(level, n.dropped());
			}
			case Transition.Abandoned<UUID> a -> refundDropped(level, a.dropped());
			case Transition.Spin<UUID> s -> level.playSound(null, worldPosition, RouletteModule.SPIN_SOUND, SoundSource.BLOCKS, 1.0f, 1.0f);
			case Transition.Result<UUID> r -> {
				settleBots(level, r.result());
				settleAll(level, r.result(), r.slips());
			}
			case Transition.Reset<UUID> x -> {
				outcomes.clear();
				// Safe point: start of BETTING. Virtual slips vanish; new bet moments.
				for (BotSlip s : botSlips.values()) {
					s.bets = List.of();
					s.betAt = -1;
				}
				botResults.clear();
				botSafePoint();
			}
		}
		setPhase(round.phase().id());
		setChanged();
		syncViewers();
	}

	// ---- bots (atmosphere: virtual slips, always ready, never delay the spin) -------------------

	private long botMin() {
		return Math.max(cfg().minBet, ownership().map(o -> o.minBet()).orElse(0L));
	}

	/** Safe point (start of / during BETTING): bots join / leave / yield; new bet moments. */
	private void botSafePoint() {
		if (!(level instanceof ServerLevel sl) || removing()) {
			return;
		}
		if (bots.safePoint(sl) == null) {
			return;
		}
		java.util.Set<String> live = new java.util.HashSet<>();
		for (VirtualBot b : bots.bots()) {
			live.add(b.key);
			botSlips.computeIfAbsent(b.key, k -> new BotSlip(b.profile(), RouletteBettor.newMemory(bots.rng(), b.profile().personality(), botMin())));
		}
		botSlips.keySet().retainAll(live);
		if (round.canBet()) {
			for (BotSlip s : botSlips.values()) {
				if (s.bets.isEmpty() && s.betAt < 0) {
					s.betAt = gameTime() + bots.betDelay();
				}
			}
		}
		syncViewers();
	}

	/** Places the virtual bets whose moment has come ({@code force}: betting closes now, everyone bets). */
	private boolean placeBotBets(boolean force) {
		if (botSlips.isEmpty()) {
			return false;
		}
		long min = botMin();
		long totalMax = min * BOT_TOTAL_MULTIPLE;
		long insideMax = Math.max(min, (long) Math.floor(totalMax * cfg().insideMaxFraction));
		long now = gameTime();
		boolean any = false;
		for (BotSlip s : botSlips.values()) {
			if (!s.bets.isEmpty() || (!force && (s.betAt < 0 || now < s.betAt))) {
				continue;
			}
			try {
				s.bets = RouletteBettor.INSTANCE.act(s.bot, new RouletteBettor.View(min, insideMax, totalMax, s.memory), null, bots.rng());
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("roulette bot bet failed", e);
				s.bets = List.of();
			}
			any = true;
		}
		return any;
	}

	/** The number is known: settle the virtual slips for display, update the betting styles. */
	private void settleBots(ServerLevel level, int result) {
		boolean la = cfg().laPartage;
		botResults.clear();
		for (BotSlip s : botSlips.values()) {
			if (s.bets.isEmpty()) {
				continue;
			}
			long staked = Bets.totalStaked(s.bets);
			long ret = Bets.totalReturn(s.bets, result, la);
			botResults.add(new BotResult(s.bot, staked, ret));
			if (ret >= staked * 5 && ret > 0) {
				bots.quip(level, s.bot, "win_big", null);
			}
			s.memory = RouletteBettor.afterSpin(s.bot.personality(), s.memory, s.bets, result, la);
		}
	}

	/** "Bots bet (for fun): [BOT] Creeper42: Red 20, … · …" or the last spin's results; null without any. */
	private @Nullable Component botLine(boolean results) {
		List<Component> parts = new ArrayList<>();
		if (results) {
			for (BotResult r : botResults) {
				long net = r.returned() - r.staked();
				parts.add(Component.empty().append(BotNames.display(r.bot())).append(Texts.raw(": " + (net > 0 ? "+" : "")))
					.append(Texts.number(net)));
			}
		} else {
			for (BotSlip s : botSlips.values()) {
				if (s.bets.isEmpty()) {
					continue;
				}
				MutableComponent p = Component.empty().append(BotNames.display(s.bot)).append(Texts.raw(": "));
				for (int i = 0; i < s.bets.size(); i++) {
					Bet b = s.bets.get(i);
					if (i > 0) {
						p.append(Texts.raw(", "));
					}
					p.append(betLabel(b)).append(Texts.raw(" ")).append(Texts.number(b.amount()));
				}
				parts.add(p);
			}
		}
		if (parts.isEmpty()) {
			return null;
		}
		MutableComponent list = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				list.append(Texts.raw(" · "));
			}
			list.append(parts.get(i));
		}
		return Component.translatable("gui.burmaldaholic.bots.virtual_bets", list);
	}

	/** "Straight 17", "2nd dozen", "Red" (the screen's bet descriptions). */
	static Component betLabel(Bet b) {
		Spot s = b.spot();
		return switch (s.type()) {
			case STRAIGHT, SPLIT, STREET, TRIO, CORNER, SIX_LINE ->
				Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id(), Texts.raw(s.label()));
			case DOZEN, COLUMN -> Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id() + "." + s.outsideIndex());
			default -> Component.translatable("gui.burmaldaholic.roulette.bet." + s.type().id());
		};
	}

	/** The table is broken: its spin was played out by core; now every bot leaves (BOTS.md §3.5). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel sl) {
			bots.endSession(sl);
		}
		botSlips.clear();
	}

	private void refundDropped(ServerLevel level, List<UUID> dropped) {
		for (UUID id : dropped) {
			refund(id);
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				sendError(p, Component.translatable("gui.burmaldaholic.roulette.error.min_total", Texts.number(minTotal())));
			}
		}
	}

	private void settleAll(ServerLevel level, int result, Map<UUID, List<Bet>> slips) {
		boolean laPartage = cfg().laPartage;
		outcomes.clear();
		for (Map.Entry<UUID, List<Bet>> e : slips.entrySet()) {
			UUID id = e.getKey();
			List<Bet> bets = e.getValue();
			long staked = Bets.totalStaked(bets);
			long ret = Bets.totalReturn(bets, result, laPartage);
			String[] tags = bets.stream().map(b -> b.type().name().toLowerCase(java.util.Locale.ROOT)).distinct().toArray(String[]::new);
			settle(id, ret, r -> r.withTags(tags));
			long redWins = Wheel.color(result) == Wheel.Color.RED ? bets.stream().filter(b -> b.type() == BetType.RED).count() : 0;
			boolean zeroStraight = result == 0 && bets.stream().anyMatch(b -> b.type() == BetType.STRAIGHT && b.spot().covers(0));
			if (zeroStraight) {
				CasinoAdvancements.grant(level.getServer(), id, "zero_hero");
			}
			lastSlips.put(id, bets);
			outcomes.put(id, new long[] {staked, ret});
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			p.sendOverlayMessage(resultLine(result, staked, ret));
			reportRedWins(p, redWins);
			if (result == 0) {
				boolean zeroWon = bets.stream().anyMatch(b -> b.spot().covers(0));
				boolean evenMoney = bets.stream().anyMatch(b -> b.type().evenMoney());
				if (zeroWon) {
					p.sendSystemMessage(Component.translatable("msg.burmaldaholic.roulette.zero_hero"));
				} else if (laPartage && evenMoney) {
					p.sendSystemMessage(Component.translatable("gui.burmaldaholic.roulette.la_partage"));
				}
			}
		}
	}

	/**
	 * VIP contract {@code roulette_red} ("win 2 bets on red"): reported per winning red bet through the vip
	 * module's public hook (ObjectShare {@code burmaldaholic:vip/contract}; no import of the vip package).
	 */
	@SuppressWarnings("unchecked")
	private static void reportRedWins(ServerPlayer player, long wins) {
		if (wins <= 0) {
			return;
		}
		Object hook = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get("burmaldaholic:vip/contract");
		if (hook instanceof java.util.function.BiConsumer<?, ?> contract) {
			((java.util.function.BiConsumer<ServerPlayer, String>) contract).accept(player, "roulette_red@" + wins);
		}
	}

	/** A pocket number in its colour ("17" red / black / green). */
	public static MutableComponent pocket(int n) {
		ChatFormatting color = switch (Wheel.color(n)) {
			case RED -> ChatFormatting.RED;
			case BLACK -> ChatFormatting.WHITE;
			case GREEN -> ChatFormatting.GREEN;
		};
		return Texts.number(n).withStyle(color);
	}

	/** "17 Black — you win 180 chips" / "— not this time" / just "17 Black" for spectators. */
	public static Component resultLine(int result, long staked, long returned) {
		Component n = pocket(result);
		Component color = Component.translatable("gui.burmaldaholic.roulette.color." + Wheel.color(result).id());
		if (staked <= 0) {
			return Component.translatable("gui.burmaldaholic.roulette.result", n, color);
		}
		if (returned > 0) {
			return Component.translatable("gui.burmaldaholic.roulette.result_win", n, color, Texts.chipsAcc(returned)).withStyle(ChatFormatting.GREEN);
		}
		return Component.translatable("gui.burmaldaholic.roulette.result_lose", n, color);
	}

	// ---- client state -------------------------------------------------------------------------

	@Override
	public CompoundTag writeClientState(ServerPlayer viewer) {
		CompoundTag t = baseState(viewer);
		UUID id = viewer.getUUID();
		SlipLimits l = limits(viewer);
		t.putString("phase", round.phase().id());
		t.putLong("min", l.minBet());
		t.putLong("max", l.totalMax());
		t.putLong("inside_max", l.insideMax());
		t.putLong("min_total", l.minTotal());
		t.putBoolean("high_roller", isHighRoller());
		t.putBoolean("enabled", cfg().enabled);
		t.putLong("ticks_left", round.remaining(gameTime()));
		t.putInt("spin_ticks", round.timings().spinTicks());
		t.putInt("result", round.result());
		t.putIntArray("history", round.history().stream().mapToInt(Integer::intValue).toArray());
		boolean showingResult = round.phase() == RouletteRound.Phase.RESULT && outcomes.containsKey(id);
		List<Bet> mine = showingResult ? lastSlips.getOrDefault(id, List.of()) : round.bets(id);
		t.put("bets", writeBets(mine));
		t.putLong("total", Bets.totalStaked(mine));
		t.putBoolean("can_rebet", round.canBet() && lastSlips.containsKey(id));
		t.putBoolean("ready", round.isReady(id));
		t.putInt("ready_count", round.readyCount());
		t.putInt("bettors", round.bettors().size());
		// Other players' chips on the layout (spot key → amount), aggregated.
		Map<String, Long> others = new TreeMap<>();
		for (UUID other : round.bettors()) {
			if (!other.equals(id)) {
				round.bets(other).forEach(b -> others.merge(b.spot().key(), b.amount(), Long::sum));
			}
		}
		CompoundTag othersTag = new CompoundTag();
		others.forEach(othersTag::putLong);
		t.put("others", othersTag);
		writeBotState(t);
		long[] outcome = outcomes.get(id);
		if (outcome != null) {
			t.putLong("out_staked", outcome[0]);
			t.putLong("out_return", outcome[1]);
		}
		return t;
	}

	/**
	 * Seats &amp; Bots sync: {@code bots_header}, {@code bot_chips} (spot key → virtual amount, drawn hatched)
	 * and {@code bot_line} ("Bots bet (for fun): …" / the last spin's bot results).
	 */
	private void writeBotState(CompoundTag t) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		Component header = bots.header(sl);
		if (header != null) {
			t.put("bots_header", AtmosphereBots.encode(sl, header));
		}
		boolean result = round.phase() == RouletteRound.Phase.RESULT;
		CompoundTag chips = new CompoundTag();
		if (!result) {
			Map<String, Long> agg = new TreeMap<>();
			for (BotSlip s : botSlips.values()) {
				s.bets.forEach(b -> agg.merge(b.spot().key(), b.amount(), Long::sum));
			}
			agg.forEach(chips::putLong);
		}
		t.put("bot_chips", chips);
		Component line = botLine(result);
		if (line != null) {
			t.put("bot_line", AtmosphereBots.encode(sl, line));
		}
	}

	private static ListTag writeBets(List<Bet> bets) {
		ListTag list = new ListTag();
		for (Bet b : bets) {
			CompoundTag bt = new CompoundTag();
			bt.putString("spot", b.spot().key());
			bt.putLong("amount", b.amount());
			list.add(bt);
		}
		return list;
	}

	// ---- persistence (history only; open stakes are core's and refunded on reload) --------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		bots.save(output);
		if (!round.history().isEmpty()) {
			output.store(HISTORY_KEY, Codec.INT.listOf(), round.history());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bots.load(input);
		round.setHistory(input.read(HISTORY_KEY, Codec.INT.listOf()).orElse(List.of()));
	}
}
