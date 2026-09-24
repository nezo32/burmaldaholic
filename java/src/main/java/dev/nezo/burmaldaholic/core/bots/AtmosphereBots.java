package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats.VirtualBot;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Seats &amp; Bots wiring shared by the house-banked ATMOSPHERE tables (blackjack, roulette, craps; BOTS.md
 * §2–§4.7, pvp-bots.md §4.6). Java counterpart of Bedrock's per-game {@code TableSeating}: owns the core
 * {@link TableBots} (policy, host, claimants, private access, persistence) and the {@link VirtualSeats}
 * beside core's human {@code TableSeats}, and reaches the optional bots UI through {@link BotTableUi}
 * (no-op fallback).
 *
 * <p>Atmosphere bots never touch money: this class has no access to stakes or the economy, their seats
 * return nothing to a purse, and games only ever settle core stakes of humans.
 *
 * <p>Use from a table block entity:
 * <ol>
 *   <li>field {@code bots = new AtmosphereBots(this, "blackjack", true)}; the BE implements
 *       {@link BotTable.Delegating} with {@code botDelegate() = bots}, so the bots UI finds its TableBots;</li>
 *   <li>in {@code sit}: {@link #admit} first — {@code fail} → show the error; {@code ok(false)} → quiet
 *       refusal (the claimant was told "a bot gives up its seat after this round"); then {@link #noteJoin};</li>
 *   <li>at the safe point (start of / during BETTING): {@link #safePoint};</li>
 *   <li>{@link #save}/{@link #load} in saveAdditional/loadAdditional; {@link #endSession} after a removed
 *       table played its round out.</li>
 * </ol>
 */
public final class AtmosphereBots implements BotTable {
	private static final String SAVE_KEY = "burmaldaholic_bots";

	private final CasinoTableBlockEntity be;
	private final String gameId;
	private final boolean difficultyMatters;
	private final VirtualSeats seats;
	private @Nullable TableBots tb;
	private @Nullable CompoundTag loaded;
	private boolean attached;
	/** a safe point is running (seating a claimant re-enters through the game's sit) */
	private boolean inSafePoint;
	/** humans in sit-down order (host = longest seated) */
	private final List<UUID> order = new ArrayList<>();

	public AtmosphereBots(CasinoTableBlockEntity be, String gameId, boolean difficultyMatters) {
		this.be = be;
		this.gameId = gameId;
		this.difficultyMatters = difficultyMatters;
		this.seats = new VirtualSeats(() -> be.seats().size(), this::humanSeats);
	}

	// ---- BotTable hooks ----------------------------------------------------------------------------

	@Override
	public String botGameId() {
		return gameId;
	}

	@Override
	public BotRole botRole() {
		return BotRole.ATMOSPHERE;
	}

	@Override
	public int botSeatCount() {
		return seats.seats();
	}

	@Override
	public List<UUID> seatedHumans() {
		List<UUID> now = new ArrayList<>();
		for (TableSeats.Seat s : be.seats().occupied()) {
			now.add(s.player());
		}
		order.retainAll(now);
		for (UUID id : now) {
			if (!order.contains(id)) {
				order.add(id);
			}
		}
		return List.copyOf(order);
	}

	@Override
	public List<SeatOccupant> occupants() {
		return seats.occupants();
	}

	@Override
	public boolean seatBot(SeatOccupant.Bot bot, long stack) {
		return seats.seatBot(bot);
	}

	/** Atmosphere bots hold nothing: nothing ever goes back to a purse. */
	@Override
	public long unseatBot(String botKey) {
		seats.unseatBot(botKey);
		return 0;
	}

	@Override
	public SeatingMath.YieldRule yieldRule() {
		return SeatingMath.YieldRule.HIGHEST_SEAT;
	}

	@Override
	public boolean botDifficultyMatters() {
		return difficultyMatters;
	}

	/** INTEGRATION HOOK (J-G6): the worldgen preset's level mix when present (see {@link #table()}). */
	@Override
	public int[] botDifficultyMix() {
		return BotTable.super.botDifficultyMix();
	}

	/** INTEGRATION HOOK (J-G6): the worldgen preset's name theme when present (see {@link #table()}). */
	@Override
	public dev.nezo.burmaldaholic.core.bots.logic.BotRoster.Theme botNameTheme() {
		return BotTable.super.botNameTheme();
	}

	@Override
	public String botTableKey() {
		if (be.getLevel() != null) {
			BlockPos p = be.getBlockPos();
			return be.getLevel().dimension().identifier() + "@" + p.getX() + "," + p.getY() + "," + p.getZ();
		}
		return gameId + "@" + be.getBlockPos().toShortString();
	}

	@Override
	public @Nullable BlockPos botTablePos() {
		return be.getBlockPos();
	}

	@Override
	public boolean botTableActive() {
		return !be.isRemoved();
	}

	/** The table's TableBots (created on first use once the block entity is in a level), for the bots UI. */
	@Override
	public @Nullable TableBots tableBots() {
		return tb != null || be.getLevel() == null ? tb : table();
	}

	private List<VirtualSeats.HumanSeat> humanSeats() {
		List<VirtualSeats.HumanSeat> out = new ArrayList<>();
		for (TableSeats.Seat s : be.seats().occupied()) {
			out.add(new VirtualSeats.HumanSeat(s.player(), s.name(), s.index()));
		}
		return out;
	}

	// ---- state ---------------------------------------------------------------------------------------

	/** The core TableBots (created on first use, with the craftable or worldgen defaults of the game). */
	public TableBots table() {
		if (tb == null) {
			// INTEGRATION HOOK (J-G6): when core has CoreServices.tablePresets().botDefaults(level, pos, gameId)
			// → Optional<BotPreset(defaults, nameTheme, levelMix)>, use preset.defaults() here and keep the preset
			// so botNameTheme() / botDifficultyMix() below return preset.nameTheme() / preset.levelMix().
			boolean generated = be.getLevel() != null && be.preset().isPresent();
			tb = new TableBots(this, TableBots.defaultsFor(gameId, generated), OwnerControls.unowned(Math.max(1, seats.seats())));
			if (loaded != null) {
				tb.load(loaded);
				loaded = null;
			}
		}
		return tb;
	}

	public VirtualSeats seats() {
		return seats;
	}

	/** Seated bots by seat (empty until the table has a bot session). */
	public List<VirtualBot> bots() {
		return tb == null ? List.of() : seats.list();
	}

	/** The bot on seat {@code index}, or null. */
	public @Nullable VirtualBot botAt(int index) {
		return tb == null ? null : seats.at(index);
	}

	/** The session's bot rng (never the game rng). */
	public BotRng rng() {
		return table().rng();
	}

	public BotSpeed speed() {
		return table().settings().effectiveSpeed();
	}

	/** {@code bots.think.fastFactor}. */
	public static double fastFactor() {
		return CasinoConfig.bots().think.fastFactor;
	}

	/** Ticks after BETTING opens at which a bot places its virtual bet. */
	public int betDelay() {
		return VirtualSeats.betDelay(rng(), speed(), fastFactor());
	}

	// ---- entry points --------------------------------------------------------------------------------

	/** A human sat down (keeps the sit-down order for the host rule). */
	public void noteJoin(UUID player) {
		if (!order.contains(player)) {
			order.add(player);
		}
	}

	/**
	 * {@code sit} step (after the game's own checks): private tables, someone else's BOTS_ONLY table and a
	 * full table with bot seats. ok(true) = sit now; ok(false) = claimant — refuse QUIETLY (TableBots
	 * already said "a bot gives up its seat after this round"); fail = show the error.
	 */
	public Result<Boolean> admit(ServerPlayer player) {
		if (be.isSeated(player) || be.ownership().map(o -> o.owner().equals(player.getUUID())).orElse(false)) {
			return Result.ok(true); // seated already / the owner: core's sit decides
		}
		try {
			return table().admit(player);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("{} bots admit failed", gameId, e);
			return Result.ok(true);
		}
	}

	/**
	 * The game's safe point (start of / during BETTING): pending settings, bots join / leave / yield,
	 * claimants are seated. Returns the result, or null when bots failed (logged; the game goes on).
	 */
	public TableBots.@Nullable SafePointResult safePoint(ServerLevel level) {
		if (inSafePoint) {
			return null; // re-entered from seating a claimant: the outer safe point finishes the work
		}
		inSafePoint = true;
		try {
			return runSafePoint(level);
		} finally {
			inSafePoint = false;
		}
	}

	private TableBots.@Nullable SafePointResult runSafePoint(ServerLevel level) {
		TableBots t = table();
		TableBots.SafePointResult r;
		try {
			r = t.safePoint(level);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("{} bots safe point failed", gameId, e);
			return null;
		}
		seats.resolve();
		t.announce(level, r);
		updateUi(t);
		for (UUID id : r.seatedClaimants()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && be.sit(p)) {
				if (!(p.containerMenu instanceof CasinoTableMenu m && m.pos().equals(be.getBlockPos()))) {
					p.openMenu(be);
				}
				be.sendStateTo(p);
			}
		}
		if (!r.joined().isEmpty()) {
			quip(level, r.joined().getFirst().profile, "join", null);
		}
		return r;
	}

	private void updateUi(TableBots t) {
		boolean in = t.inSession();
		if (in == attached) {
			return;
		}
		attached = in;
		try {
			if (in) {
				BotTableUi.get().attach(this, t);
			} else {
				BotTableUi.get().detach(this, t);
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("bots ui {} failed", in ? "attach" : "detach", e);
		}
	}

	/**
	 * Bot quip (BOTS.md §7.4): through the bots UI when it takes it, else core's chatter
	 * ({@link TableBots#say} → {@link BotChatter}: rate limits, table toggle, delivery).
	 * INTEGRATION HOOK: if core {@code BotChatter} becomes the bots module's event sink
	 * ({@code BotChatter.event(level, pos, bot, event, human)}), route the fallback there.
	 */
	public void quip(ServerLevel level, BotProfile bot, String event, @Nullable String human) {
		if (tb == null) {
			return;
		}
		try {
			if (!BotTableUi.get().quip(this, tb, level, bot, event, human)) {
				tb.say(level, bot, event, human);
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("bot quip failed", e);
		}
	}

	/** Header line ("Humans + 3 bots · Mixed · Open to all"), only when bots are possible here. */
	public @Nullable Component header(ServerLevel level) {
		if (tb == null || !Bots.enabled()) {
			return null;
		}
		try {
			Component ui = BotTableUi.get().header(this, tb, level);
			return ui != null ? ui : tb.inSession() ? tb.summary(level) : null;
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("bots header failed", e);
			return null;
		}
	}

	/** Table stopped for good (broken): after its round was played out, every bot leaves. */
	public void endSession(ServerLevel level) {
		if (tb == null) {
			return;
		}
		try {
			tb.endSession(level);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("{} bots end session failed", gameId, e);
		}
		seats.clear();
		updateUi(tb);
	}

	// ---- persistence ----------------------------------------------------------------------------------

	public void save(ValueOutput output) {
		CompoundTag tag = new CompoundTag();
		if (tb != null) {
			tb.save(tag);
		} else if (loaded != null) {
			tag = loaded.copy(); // never used since the load: keep what was stored
		} else {
			return;
		}
		output.store(SAVE_KEY, CompoundTag.CODEC, tag);
	}

	public void load(ValueInput input) {
		input.read(SAVE_KEY, CompoundTag.CODEC).ifPresent(tag -> {
			if (tb != null) {
				tb.load(tag);
			} else {
				loaded = tag;
			}
		});
	}

	// ---- client sync helpers ----------------------------------------------------------------------------

	/** A component for a table sync tag (decoded by the client screen with the same codec). */
	public static Tag encode(ServerLevel level, Component c) {
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ComponentSerialization.CODEC.encodeStart(ops, c).result().orElseGet(CompoundTag::new);
	}
}
