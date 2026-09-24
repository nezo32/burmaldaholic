package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpEvents;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.ModeAdvancements;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Machine entries of Wheel Party (PVP.md §3.3.2, §6.1, §6.5; task J-M2). The wheel's machine screen gets one
 * button (Start a Wheel Party / Join the party: n/N / Add to my slice, from {@link #writeSummary}) that opens the
 * party panel (client {@code WheelPartyPanel}); its actions call {@code PvpService.openLobby / join / topUp /
 * start / leave} with the wheel as the anchor. Only the anchor wheel itself joins (one wheel, §6.1). Escrow,
 * countdown, No more bets, bots and the spin are the engine; stake checks here only give early, precise errors
 * (the engine re-checks everything).
 */
public final class WheelPartyEntries {
	/** Max distance (blocks) from the wheel for panel actions (the machine's own use distance). */
	static final double REACH = 8.0;
	private static final int PUSH_EVERY = 10;

	private record OpenPanel(GlobalPos wheel, int hash) {}

	private static final Map<UUID, OpenPanel> OPEN = new HashMap<>();

	private WheelPartyEntries() {}

	/** Called once from {@code ExtrasPvpModes.register}. */
	public static void register() {
		WheelPartyNet.register(WheelPartyEntries::onAction);
		ServerTickEvents.END_SERVER_TICK.register(WheelPartyEntries::tick);
		PvpEvents.MATCH_SETTLED.register(WheelPartyEntries::onSettled);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OPEN.clear());
	}

	static boolean modeEnabled() {
		return PvpModes.get(WheelPartyMode.ID).map(PvpMode::enabled).orElse(false);
	}

	static boolean botsAllowed() {
		return CasinoConfig.bots().enabled && CasinoConfig.bots().pvp.maxPerMatch > 0;
	}

	// ---- hook from the wheel machine (WheelBlockEntity.writeClientState) ----------------------------------

	/**
	 * Adds {@code party = {enabled, open, players, max, joined}} to the wheel's machine state so its screen can
	 * label the Wheel Party button.
	 */
	public static void writeSummary(CompoundTag tag, ServerPlayer viewer, WheelBlockEntity wheel) {
		CompoundTag p = new CompoundTag();
		boolean enabled = modeEnabled() && wheel.ownership().map(o -> CasinoConfig.pvp().allowOwnedMachines).orElse(true);
		p.putBoolean("enabled", enabled);
		if (enabled && wheel.getLevel() instanceof ServerLevel level) {
			Optional<PvpMatch> party = partyAt(level, wheel.getBlockPos());
			p.putBoolean("open", party.isPresent());
			party.ifPresent(m -> {
				p.putInt("players", m.participants().size());
				p.putInt("max", WheelPartyMode.maxPlayersNow());
				p.putBoolean("joined", find(m, viewer.getUUID()) != null);
			});
		}
		tag.put("party", p);
	}

	// ---- queries -------------------------------------------------------------------------------------

	/** The party anchored at this wheel: the open lobby, else one still spinning. */
	static Optional<PvpMatch> partyAt(ServerLevel level, BlockPos pos) {
		GlobalPos at = GlobalPos.of(level.dimension(), pos);
		PvpMatch spinning = null;
		for (PvpMatch m : Pvp.service().all()) {
			if (!WheelPartyMode.ID.equals(m.mode) || m.anchorKind != AnchorKind.WHEEL_OF_FORTUNE || !at.equals(m.anchor)) {
				continue;
			}
			if (m.state() == MatchState.LOBBY) {
				return Optional.of(m);
			}
			if (m.state() == MatchState.DRAWN) {
				spinning = m;
			}
		}
		return Optional.ofNullable(spinning);
	}

	static @Nullable Participant find(PvpMatch m, UUID player) {
		String key = player.toString();
		for (Participant p : m.participants()) {
			if (p.occupant.key().equals(key)) {
				return p;
			}
		}
		return null;
	}

	static long cap(PvpMatch m) {
		try {
			return m.params.getAsJsonObject().get("cap").getAsLong();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	// ---- panel state ----------------------------------------------------------------------------------

	static CompoundTag panelState(ServerPlayer viewer, ServerLevel level, BlockPos pos) {
		CompoundTag s = new CompoundTag();
		s.putInt("x", pos.getX());
		s.putInt("y", pos.getY());
		s.putInt("z", pos.getZ());
		long min = Math.max(1, CasinoConfig.pvp().minStake);
		long tierMax = Math.max(1, ExtrasGames.tierMax(viewer));
		s.putLong("min", min);
		s.putLong("tier_max", tierMax);
		s.putLong("balance", Economies.get().balance(viewer));
		s.putInt("max_players", WheelPartyMode.maxPlayersNow());
		s.putInt("rake_bp", CasinoConfig.pvp().rakeBasisPoints);
		s.putBoolean("bots", botsAllowed());
		s.putInt("max_bots", CasinoConfig.bots().pvp.maxPerMatch);
		s.putLong("default_cap", Math.max(min, Math.min(tierMax, min * 10)));
		partyAt(level, pos).ifPresent(m -> s.put("party", partyTag(m, viewer.getUUID())));
		return s;
	}

	static CompoundTag partyTag(PvpMatch m, UUID viewer) {
		CompoundTag t = new CompoundTag();
		t.putString("id", m.id);
		t.putString("state", m.state().name());
		t.putLong("cap", cap(m));
		t.putLong("pot", m.pot());
		UUID host = m.host();
		t.putBoolean("is_host", viewer.equals(host));
		ListTag slices = new ListTag();
		Participant me = null;
		for (Participant p : m.participants()) {
			CompoundTag sl = new CompoundTag();
			sl.putInt("index", p.index);
			sl.putLong("stake", p.stake());
			sl.putBoolean("bot", p.isBot());
			sl.putString("name", p.occupant.name());
			if (p.occupant instanceof SeatOccupant.Bot b) {
				sl.putString("level", b.profile().level().id());
			}
			if (p.occupant instanceof SeatOccupant.Human h) {
				sl.putBoolean("host", h.id().equals(host));
			}
			sl.putBoolean("all_in", p.allIn());
			slices.add(sl);
			if (p.occupant.key().equals(viewer.toString())) {
				me = p;
			}
		}
		t.put("slices", slices);
		t.putInt("you", me == null ? -1 : me.index);
		t.putLong("your_stake", me == null ? 0 : me.stake());
		return t;
	}

	// ---- actions ------------------------------------------------------------------------------------

	static void onAction(ServerPlayer player, WheelPartyNet.Action payload) {
		CompoundTag a = payload.args();
		PvpService pvp = Pvp.service();
		switch (payload.action()) {
			case "open" -> open(player, new BlockPos(a.getIntOr("x", 0), a.getIntOr("y", 0), a.getIntOr("z", 0)));
			case "close" -> OPEN.remove(player.getUUID());
			case "host" -> atWheel(player).ifPresent(pos -> host(player, pos, a));
			case "join" -> atWheel(player).ifPresent(pos -> join(player, pos, a.getLongOr("stake", 0)));
			case "top_up" -> topUp(player, a.getLongOr("extra", 0));
			case "spin" -> pvp.matchOf(player.getUUID()).filter(m -> WheelPartyMode.ID.equals(m.mode))
				.ifPresent(m -> report(player, pvp.start(player, m.id)));
			case "leave" -> pvp.leave(player);
			case "rematch" -> pvp.rematch(player, a.getStringOr("id", ""));
			case "taunt" -> report(player, pvp.taunt(player, a.getIntOr("line", 0)));
			default -> {
			}
		}
		push(player, false, Component.empty());
	}

	private static void open(ServerPlayer player, BlockPos pos) {
		if (!ExtrasGames.guard(player, modeEnabled())) {
			return;
		}
		ServerLevel level = player.level();
		if (!(level.getBlockEntity(pos) instanceof WheelBlockEntity wheel) || !near(player, pos)) {
			return;
		}
		if (wheel.ownership().isPresent() && !CasinoConfig.pvp().allowOwnedMachines) {
			player.sendOverlayMessage(ExtrasGames.error("disabled"));
			return;
		}
		OPEN.put(player.getUUID(), new OpenPanel(GlobalPos.of(level.dimension(), pos.immutable()), 0));
		push(player, true, Component.empty());
	}

	private static boolean near(ServerPlayer player, BlockPos pos) {
		return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH;
	}

	/** The wheel of the player's open panel, if they are still at it. */
	private static Optional<BlockPos> atWheel(ServerPlayer player) {
		OpenPanel p = OPEN.get(player.getUUID());
		if (p == null || !p.wheel().dimension().equals(player.level().dimension()) || !near(player, p.wheel().pos())
			|| !(player.level().getBlockEntity(p.wheel().pos()) instanceof WheelBlockEntity)) {
			return Optional.empty();
		}
		return Optional.of(p.wheel().pos());
	}

	private static void host(ServerPlayer player, BlockPos pos, CompoundTag a) {
		if (!ExtrasGames.guard(player, modeEnabled())) {
			return;
		}
		ServerLevel level = player.level();
		if (partyAt(level, pos).isPresent()) {
			join(player, pos, a.getLongOr("stake", 0));
			return;
		}
		long min = Math.max(1, CasinoConfig.pvp().minStake);
		long tierMax = ExtrasGames.tierMax(player);
		long cap = a.getLongOr("cap", 0);
		long stake = a.getLongOr("stake", 0);
		WheelPartyMode.Params params = new WheelPartyMode.Params(cap);
		if (WheelPartyMode.validate(params, min) != null) {
			message(player, error(WheelMath.ERROR_STAKE_MIN, player, cap, min, tierMax));
			return;
		}
		if (cap > tierMax) {
			message(player, error(WheelMath.ERROR_TIER_MAX, player, cap, min, tierMax));
			return;
		}
		String bad = WheelMath.checkStake(0, stake, cap, tierMax, min, false);
		if (bad != null) {
			message(player, error(bad, player, cap, min, tierMax));
			return;
		}
		SeatPolicy policy = botsAllowed() ? SeatPolicy.byId(a.getStringOr("policy", ""), SeatPolicy.HUMANS_ONLY) : SeatPolicy.HUMANS_ONLY;
		int size = Math.max(2, Math.min(WheelPartyMode.maxPlayersNow(), a.getIntOr("size", 2)));
		int bots = policy == SeatPolicy.HUMANS_ONLY ? 0 : Math.min(CasinoConfig.bots().pvp.maxPerMatch, size - 1);
		BotDifficulty difficulty = BotDifficulty.byId(a.getStringOr("difficulty", ""), BotDifficulty.MIXED);
		BotSettings seating = new BotSettings(policy, bots, difficulty, false, true, BotSpeed.NORMAL);
		Result<PvpMatch> r = Pvp.service().openLobby(player, WheelPartyMode.ID, new WheelPartyMode().encodeParams(params), stake,
			new PvpService.Anchor(AnchorKind.WHEEL_OF_FORTUNE, level, pos.immutable()), seating, false);
		report(player, r);
	}

	private static void join(ServerPlayer player, BlockPos pos, long stake) {
		Optional<PvpMatch> party = partyAt(player.level(), pos);
		if (party.isEmpty() || party.get().state() != MatchState.LOBBY) {
			message(player, Component.translatable("gui.burmaldaholic.pvp.error.lobby_gone"));
			return;
		}
		PvpMatch m = party.get();
		long min = Math.max(1, CasinoConfig.pvp().minStake);
		long tierMax = ExtrasGames.tierMax(player);
		String bad = WheelMath.checkStake(0, stake, cap(m), tierMax, min, false);
		if (bad != null) {
			message(player, error(bad, player, cap(m), min, tierMax));
			return;
		}
		report(player, Pvp.service().join(player, m.id, stake));
	}

	private static void topUp(ServerPlayer player, long extra) {
		Optional<PvpMatch> match = Pvp.service().matchOf(player.getUUID()).filter(m -> WheelPartyMode.ID.equals(m.mode));
		if (match.isEmpty()) {
			message(player, Component.translatable("gui.burmaldaholic.pvp.error.lobby_gone"));
			return;
		}
		PvpMatch m = match.get();
		Participant me = find(m, player.getUUID());
		long min = Math.max(1, CasinoConfig.pvp().minStake);
		long tierMax = ExtrasGames.tierMax(player);
		String bad = WheelMath.checkStake(me == null ? 0 : me.stake(), extra, cap(m), tierMax, min, m.state() != MatchState.LOBBY);
		if (bad != null) {
			message(player, error(bad, player, cap(m), min, tierMax));
			return;
		}
		report(player, Pvp.service().topUp(player, m.id, extra));
	}

	static Component error(String key, ServerPlayer player, long cap, long min, long tierMax) {
		return switch (key) {
			case WheelMath.ERROR_OVER_CAP -> Component.translatable(WheelMath.ERROR_OVER_CAP, Texts.chips(cap));
			case WheelMath.ERROR_STAKE_MIN -> Component.translatable(WheelMath.ERROR_STAKE_MIN, Texts.chips(min));
			case WheelMath.ERROR_TIER_MAX -> Component.translatable(WheelMath.ERROR_TIER_MAX, Texts.number(tierMax),
				VipTiers.name(CoreServices.vip().tier(player.level().getServer(), player.getUUID())));
			default -> Component.translatable(key);
		};
	}

	private static void report(ServerPlayer player, Result<?> r) {
		if (!r.isOk()) {
			message(player, r.error() == null ? ExtrasGames.error("disabled") : r.error());
		}
	}

	private static void message(ServerPlayer player, Component m) {
		player.sendOverlayMessage(m);
		if (OPEN.containsKey(player.getUUID())) {
			WheelPartyNet.send(player, false, state(player), m);
		}
	}

	private static CompoundTag state(ServerPlayer player) {
		OpenPanel p = OPEN.get(player.getUUID());
		if (p == null) {
			return new CompoundTag();
		}
		return panelState(player, player.level(), p.wheel().pos());
	}

	/** Sends the panel state now (and remembers it, so the tick only re-sends changes). */
	private static void push(ServerPlayer player, boolean open, Component message) {
		OpenPanel p = OPEN.get(player.getUUID());
		if (p == null) {
			return;
		}
		CompoundTag s = state(player);
		OPEN.put(player.getUUID(), new OpenPanel(p.wheel(), s.hashCode()));
		WheelPartyNet.send(player, open, s, message);
	}

	private static void tick(MinecraftServer server) {
		if (OPEN.isEmpty() || server.getTickCount() % PUSH_EVERY != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, OpenPanel>> it = OPEN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, OpenPanel> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !CasinoMode.isEnabled(server) || !e.getValue().wheel().dimension().equals(player.level().dimension())
				|| !near(player, e.getValue().wheel().pos())) {
				it.remove();
				continue;
			}
			CompoundTag s = panelState(player, player.level(), e.getValue().wheel().pos());
			if (s.hashCode() != e.getValue().hash()) {
				e.setValue(new OpenPanel(e.getValue().wheel(), s.hashCode()));
				WheelPartyNet.send(player, false, s, Component.empty());
			}
		}
	}

	// ---- advancements -------------------------------------------------------------------------------

	static void onSettled(MinecraftServer server, PvpMatch match, long[] payouts) {
		if (!WheelPartyMode.ID.equals(match.mode)) {
			return;
		}
		Outcome outcome = match.outcome();
		if (outcome == null) {
			return;
		}
		for (PvpEvent e : outcome.events()) {
			if (!e.kind().equals("underdog")) {
				continue;
			}
			for (Participant p : match.participants()) {
				if (p.index == e.seat() && p.occupant instanceof SeatOccupant.Human h) {
					ModeAdvancements.grant(server, h.id(), ModeAdvancements.UNDERDOG);
				}
			}
		}
	}
}
