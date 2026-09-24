package dev.nezo.burmaldaholic.pvp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpRecordData;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu → Challenges = the PvP hub (PVP.md §3.11.2). Takes over the page id {@code challenges} that extras
 * registered for the Dice Duel: the old page is kept and shown behind the hub's [Dice Duel] button, and its
 * buttons (actions without the hub's {@code p:} prefix) are handed back to it unchanged.
 *
 * <p>Views per player: main (record, nemesis, your match, pending invites, open lobbies nearby, buttons), new match
 * (duel game → side / opponent + stake: nearby players and bots; Scratch Showdown can open a lobby), head-to-head,
 * dice (the extras page).
 */
public final class PvpHubPage implements CasinoMenu.Page {
	public static final String ID = "challenges";
	private static final String P = "p:";

	/** Per-player hub state (view and the choices of the New match view). */
	private static final class State {
		String view = "main";
		@Nullable String mode;
		boolean heads = true;
		SeatPolicy policy = SeatPolicy.HUMANS_ONLY;
		BotDifficulty difficulty = BotDifficulty.NORMAL;
	}

	private final Map<UUID, State> states = new ConcurrentHashMap<>();
	private final CasinoMenu.@Nullable Page dice;

	/** @param dice the page previously registered as {@code challenges} (extras' Dice Duel page), or null */
	public PvpHubPage(CasinoMenu.@Nullable Page dice) {
		this.dice = dice;
	}

	/** Registers the hub, keeping whatever page held the id before as the Dice Duel view. */
	public static PvpHubPage install() {
		CasinoMenu.Page old = null;
		for (CasinoMenu.Page p : CasinoMenu.pages()) {
			if (p.id().equals(ID) && !(p instanceof PvpHubPage)) {
				old = p;
			}
		}
		PvpHubPage hub = new PvpHubPage(old);
		CasinoMenu.register(hub);
		return hub;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.menu.challenges");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return CasinoConfig.pvp().enabled || diceVisible(player);
	}

	private boolean diceVisible(ServerPlayer player) {
		try {
			return dice != null && dice.visible(player);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private State state(ServerPlayer player) {
		return states.computeIfAbsent(player.getUUID(), k -> new State());
	}

	/** Opens the menu on the hub's main view. */
	public void openMain(ServerPlayer player) {
		state(player).view = "main";
		CasinoMenu.open(player, ID);
	}

	// ---- render --------------------------------------------------------------------------------

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		State s = state(player);
		if (!CasinoConfig.pvp().enabled && !s.view.equals("dice")) {
			s.view = diceVisible(player) ? "dice" : "main";
		}
		switch (s.view) {
			case "new" -> renderNew(player, s, out);
			case "rivals" -> renderRivals(player, out);
			case "dice" -> {
				if (dice != null) {
					dice.render(player, out);
				}
				if (CasinoConfig.pvp().enabled) {
					out.button(P + "view:main", Component.translatable("gui.burmaldaholic.common.back"));
				}
			}
			default -> renderMain(player, out);
		}
	}

	private void renderMain(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		PvpService pvp = Pvp.service();
		UUID me = player.getUUID();
		out.line(Component.translatable("gui.burmaldaholic.pvp.hub.title"), 0xFFD700);
		PvpService.Stats stats = pvp.stats(me);
		if (stats.wins() + stats.losses() == 0) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.no_record"), 0xAAAAAA);
		} else {
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.record", Texts.number(stats.wins()), Texts.number(stats.losses()),
				signed(stats.net())));
		}
		if (stats.nemesis() != null) {
			HeadToHead r = pvp.record(me, stats.nemesis());
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.nemesis", Texts.raw(nameOf(server, stats.nemesis())), Texts.number(r.wins()),
				Texts.number(r.losses())), 0xFF8888);
		}
		// your match
		Optional<PvpMatch> mine = pvp.matchOf(me);
		if (mine.isPresent() && mine.get().state() != MatchState.INVITED) {
			PvpMatch m = mine.get();
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.current", game(m.mode)), 0x55FF55);
			out.button(P + "show", Component.translatable("gui.burmaldaholic.pvp.hub.show"));
			if (m.state() == MatchState.LOBBY) {
				out.button(P + "leave", Component.translatable("gui.burmaldaholic.pvp.lobby.leave"));
			}
		} else if (mine.isPresent()) {
			PvpMatch m = mine.get();
			Participant target = other(m, me);
			out.line(Component.translatable("msg.burmaldaholic.pvp.invite.sent", target == null ? Component.empty() : PvpMatchView.name(m, target),
				game(m.mode), Texts.chips(PvpMatchView.entry(m))), 0xAAAAAA);
			out.button(P + "withdraw:" + m.id, Component.translatable("gui.burmaldaholic.pvp.hub.withdraw"));
		}
		// pending invites
		List<PvpMatch> invites = pvp.invitesFor(me);
		if (!invites.isEmpty()) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.pending"), 0xFFD700);
			for (PvpMatch m : invites) {
				Participant from = PvpUi.challenger(m);
				Component name = from == null ? Component.empty() : PvpMatchView.name(m, from);
				out.line(Component.translatable("gui.burmaldaholic.pvp.invite.body", name, game(m.mode), Texts.chips(from == null ? 0 : from.stake())));
				Component side = PvpUi.targetSide(m);
				if (side != null) {
					out.line(Component.translatable("gui.burmaldaholic.pvp.invite.side", side), 0xFFFF55);
				}
				long left = PvpMatchView.lobbyTicksLeft(server, m);
				if (left >= 0) {
					out.line(Component.translatable("gui.burmaldaholic.pvp.invite.expires",
						Texts.plural("unit.burmaldaholic.second_acc", PvpText.seconds(left))), 0xAAAAAA);
				}
				out.button(P + "accept:" + m.id, Component.translatable("gui.burmaldaholic.pvp.hub.accept", name, game(m.mode)));
				out.button(P + "decline:" + m.id, Component.translatable("gui.burmaldaholic.pvp.hub.decline", name));
			}
		}
		// open lobbies nearby
		List<PvpMatch> lobbies = pvp.lobbiesNear(player, null);
		out.line(lobbies.isEmpty() ? Component.translatable("gui.burmaldaholic.pvp.hub.no_lobbies")
			: Component.translatable("gui.burmaldaholic.pvp.hub.nearby", Texts.number(lobbies.size())), 0xFFD700);
		for (PvpMatch m : lobbies) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.hub.lobby_row", game(m.mode), where(server, m),
				Texts.number(m.participants().size()), Texts.number(PvpMatchView.maxPlayers(m))));
			Component join = Component.translatable("gui.burmaldaholic.pvp.lobby.join", Texts.chips(PvpMatchView.entry(m)));
			if (PvpModes.get(m.mode).map(PvpMode::equalStakes).orElse(true)) {
				out.button(P + "join:" + m.id, join);
			} else {
				out.amountButton(P + "join:" + m.id, Component.translatable("gui.burmaldaholic.pvp.hub.join"), CasinoConfig.pvp().minStake);
			}
		}
		out.blank();
		out.button(P + "view:new", Component.translatable("gui.burmaldaholic.pvp.hub.new_match"));
		out.button(P + "view:rivals", Component.translatable("gui.burmaldaholic.pvp.hub.rivals"));
		if (diceVisible(player)) {
			out.button(P + "view:dice", Component.translatable("gui.burmaldaholic.extras.dice.title"));
		}
		out.button(P + "invites", toggle("gui.burmaldaholic.pvp.settings.invites", acceptsInvites(server, me)));
		out.line(Component.translatable("gui.burmaldaholic.pvp.hub.machine_hint"), 0xAAAAAA);
	}

	/** Enabled modes that can be started from the hub (anchor-less: Coin Flip Duel, Scratch Showdown). */
	static List<PvpMode<?, ?>> duelModes() {
		List<PvpMode<?, ?>> out = new ArrayList<>();
		for (PvpMode<?, ?> m : PvpModes.all()) {
			try {
				if (m.anchor() == AnchorKind.NONE && m.enabled()) {
					out.add(m);
				}
			} catch (RuntimeException ignored) {
				// a stub mode
			}
		}
		return out;
	}

	private void renderNew(ServerPlayer player, State s, CasinoMenu.PageBuilder out) {
		out.line(Component.translatable("gui.burmaldaholic.pvp.new.title"), 0xFFD700);
		List<PvpMode<?, ?>> modes = duelModes();
		PvpMode<?, ?> mode = s.mode == null ? null : modes.stream().filter(m -> m.id().equals(s.mode)).findFirst().orElse(null);
		if (modes.isEmpty()) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.new.no_games"), 0xAAAAAA);
		} else if (mode == null) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.new.pick_game"));
			for (PvpMode<?, ?> m : modes) {
				out.button(P + "mode:" + m.id(), game(m.id()));
			}
		} else {
			out.line(game(mode.id()), 0x55FF55);
			if (mode.id().equals("coin")) {
				out.button(P + "side", Component.translatable("gui.burmaldaholic.pvp.toggle", Component.translatable("gui.burmaldaholic.pvp.new.side"),
					Component.translatable(s.heads ? "gui.burmaldaholic.extras.coin.heads" : "gui.burmaldaholic.extras.coin.tails")));
			}
			long min = CasinoConfig.pvp().minStake;
			long max = BetLimits.maxBet(player, Long.MAX_VALUE);
			out.line(Component.translatable("gui.burmaldaholic.pvp.new.stake_hint", Texts.chips(min), Texts.chips(Math.max(min, max))), 0xAAAAAA);
			out.line(Component.translatable("gui.burmaldaholic.pvp.new.opponent"), 0xFFD700);
			boolean any = false;
			int radius = CasinoConfig.pvp().joinRadius;
			for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
				if (other != player && other.level() == player.level() && !other.isSpectator() && other.distanceToSqr(player) <= (double) radius * radius) {
					out.amountButton(P + "ch:" + other.getUUID(), other.getDisplayName(), min);
					any = true;
				}
			}
			if (!any) {
				out.line(Component.translatable("gui.burmaldaholic.pvp.new.no_players"), 0xAAAAAA);
			}
			if (CasinoConfig.bots().enabled) {
				for (BotDifficulty d : BotDifficulty.values()) {
					Component level = Component.translatable(mode.hasDecisions() ? d.styleKey() : d.translationKey());
					out.amountButton(P + "bot:" + d.id(), Component.translatable("gui.burmaldaholic.pvp.bots.opponent", level), min);
				}
			}
			if (mode.maxPlayers() > 2) {
				out.button(P + "policy", Component.translatable("gui.burmaldaholic.pvp.toggle", Component.translatable("gui.burmaldaholic.pvp.bots.seats"),
					Component.translatable(s.policy.translationKey())));
				if (s.policy != SeatPolicy.HUMANS_ONLY && CasinoConfig.bots().enabled) {
					out.button(P + "difficulty", Component.translatable("gui.burmaldaholic.pvp.toggle",
						Component.translatable("gui.burmaldaholic.pvp.bots.difficulty"), Component.translatable(s.difficulty.translationKey())));
				}
				out.amountButton(P + "lobby", Component.translatable("gui.burmaldaholic.pvp.new.open_lobby"), min);
			}
		}
		out.button(P + (mode == null ? "view:main" : "mode:"), Component.translatable("gui.burmaldaholic.common.back"));
	}

	private void renderRivals(ServerPlayer player, CasinoMenu.PageBuilder out) {
		out.line(Component.translatable("gui.burmaldaholic.pvp.rivals.title"), 0xFFD700);
		List<PvpService.Rival> rivals = Pvp.service().rivals(player.getUUID(), 10);
		if (rivals.isEmpty()) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.rivals.none"), 0xAAAAAA);
		}
		for (PvpService.Rival r : rivals) {
			out.line(Component.translatable("gui.burmaldaholic.pvp.rivals.row", Texts.raw(r.name()), Texts.number(r.record().wins()),
				Texts.number(r.record().losses()), signed(r.record().net())), r.record().net() < 0 ? 0xFF8888 : 0xFFFFFF);
		}
		out.button(P + "view:main", Component.translatable("gui.burmaldaholic.common.back"));
	}

	// ---- actions -------------------------------------------------------------------------------

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		if (!action.startsWith(P)) {
			return dice == null ? null : dice.action(player, action, amount);
		}
		State s = state(player);
		String a = action.substring(P.length());
		int colon = a.indexOf(':');
		String verb = colon < 0 ? a : a.substring(0, colon);
		String arg = colon < 0 ? "" : a.substring(colon + 1);
		PvpService pvp = Pvp.service();
		MinecraftServer server = player.level().getServer();
		switch (verb) {
			case "view" -> {
				s.view = arg.isEmpty() ? "main" : arg;
				if (s.view.equals("new")) {
					List<PvpMode<?, ?>> modes = duelModes();
					s.mode = modes.size() == 1 ? modes.getFirst().id() : null;
				}
			}
			case "mode" -> s.mode = arg.isEmpty() ? null : arg;
			case "side" -> s.heads = !s.heads;
			case "policy" -> s.policy = SeatPolicy.values()[(s.policy.ordinal() + 1) % SeatPolicy.values().length];
			case "difficulty" -> s.difficulty = BotDifficulty.values()[(s.difficulty.ordinal() + 1) % BotDifficulty.values().length];
			case "invites" -> setAcceptsInvites(server, player.getUUID(), !acceptsInvites(server, player.getUUID()));
			case "show" -> {
				Optional<PvpMatch> m = pvp.matchOf(player.getUUID());
				if (m.isPresent() && m.get().state() != MatchState.INVITED) {
					PvpUi.push(player, m.get(), true);
				}
			}
			case "leave" -> pvp.leave(player);
			case "withdraw" -> pvp.withdraw(player, arg);
			case "accept" -> {
				return opened(player, pvp.accept(player, arg));
			}
			case "decline" -> pvp.decline(player, arg);
			case "join" -> {
				long stake = amount;
				Optional<PvpMatch> m = pvp.get(arg);
				if (m.isPresent() && PvpModes.get(m.get().mode).map(PvpMode::equalStakes).orElse(true)) {
					stake = PvpMatchView.entry(m.get());
				}
				return opened(player, pvp.join(player, arg, stake));
			}
			case "ch" -> {
				UUID target;
				try {
					target = UUID.fromString(arg);
				} catch (IllegalArgumentException e) {
					return null;
				}
				if (s.mode == null) {
					return Component.translatable("gui.burmaldaholic.pvp.error.needs_opponent");
				}
				Result<PvpMatch> r = pvp.challenge(player, s.mode, params(s.mode, s.heads, amount), amount, new PvpService.Opponent.PlayerTarget(target));
				if (r.isOk()) {
					s.view = "main";
				}
				return PvpUi.errorOf(r);
			}
			case "bot" -> {
				if (s.mode == null) {
					return null;
				}
				BotDifficulty d = BotDifficulty.byId(arg, BotDifficulty.NORMAL);
				Result<PvpMatch> r = pvp.challenge(player, s.mode, params(s.mode, s.heads, amount), amount, new PvpService.Opponent.BotTarget(d));
				if (r.isOk()) {
					s.view = "main";
				}
				return PvpUi.errorOf(r);
			}
			case "lobby" -> {
				if (s.mode == null) {
					return null;
				}
				PvpMode<?, ?> mode = PvpModes.get(s.mode).orElse(null);
				int size = mode == null ? 2 : mode.maxPlayers();
				BotSettings seating = new BotSettings(s.policy, Math.max(1, size - 1), s.difficulty, false, true, BotSpeed.NORMAL);
				Result<PvpMatch> r = pvp.openLobby(player, s.mode, params(s.mode, s.heads, amount), amount,
					new PvpService.Anchor(AnchorKind.NONE, player.level(), player.blockPosition()), seating, false);
				if (r.isOk()) {
					s.view = "main";
				}
				return opened(player, r);
			}
			default -> {
			}
		}
		return null;
	}

	/** On success, the match's screen replaces the menu. */
	private static @Nullable Component opened(ServerPlayer player, Result<PvpMatch> r) {
		if (r.isOk() && r.value() != null && r.value().state() != MatchState.INVITED) {
			PvpUi.push(player, r.value(), true);
		}
		return PvpUi.errorOf(r);
	}

	// ---- helpers -------------------------------------------------------------------------------

	/**
	 * Set-up params for a hub-created match: the mode's defaults, with the duel's stake and the challenger's
	 * coin side patched in when the mode's JSON has those fields ({@code stake}, {@code heads} / {@code challengerHeads}).
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static JsonElement params(String modeId, boolean heads, long stake) {
		PvpMode mode = PvpModes.get(modeId).orElse(null);
		JsonElement json;
		try {
			json = mode == null ? new JsonObject() : mode.encodeParams(mode.defaults());
		} catch (RuntimeException e) {
			json = new JsonObject();
		}
		if (json instanceof JsonObject o) {
			String side = sideKey(o, modeId);
			if (side != null) {
				o.addProperty(side, heads);
			} else if (modeId.equals("coin")) {
				o.addProperty("heads", heads);
			}
			if (o.has("stake")) {
				o.addProperty("stake", stake);
			}
		}
		return json;
	}

	/** The coin-side field of a mode's params ({@code heads}, or {@code challengerHeads}), or null. */
	static @Nullable String sideKey(JsonObject params, String modeId) {
		if (params.has("heads")) {
			return "heads";
		}
		if (params.has("challengerHeads")) {
			return "challengerHeads";
		}
		return null;
	}

	static Component game(String mode) {
		return Component.translatable(PvpMatchView.modeKey(mode));
	}

	static Component signed(long n) {
		MutableComponent num = Texts.number(Math.abs(n));
		return n > 0 ? Texts.raw("+").append(num) : n < 0 ? Texts.raw("−").append(num) : num;
	}

	static Component toggle(String labelKey, boolean on) {
		return Component.translatable("gui.burmaldaholic.pvp.toggle", Component.translatable(labelKey),
			Component.translatable(on ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off"));
	}

	private static Component where(MinecraftServer server, PvpMatch m) {
		String key = PvpMatchView.anchorNameKey(server, m);
		if (!key.isEmpty()) {
			return Component.translatable(key);
		}
		Participant host = PvpMatchView.participant(m, Math.max(0, PvpMatchView.hostIndex(m)));
		return host == null ? Component.empty() : PvpMatchView.name(m, host);
	}

	private static @Nullable Participant other(PvpMatch m, UUID me) {
		for (Participant p : m.participants()) {
			if (!p.occupant.key().equals(me.toString())) {
				return p;
			}
		}
		return null;
	}

	static String nameOf(MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getName().getString();
		}
		for (PvpService.Rival r : Pvp.service().rivals(id, 0)) {
			if (r.player().equals(id)) {
				return r.name();
			}
		}
		return id.toString().substring(0, 8);
	}

	// ---- "Accept PvP challenges" (stored in the player's PvP record JSON, pvp-bots.md §5.2) --------

	static boolean acceptsInvites(MinecraftServer server, UUID player) {
		try {
			JsonObject o = JsonParser.parseString(PvpRecordData.get(server).json(player)).getAsJsonObject();
			return !o.has("acceptInvites") || o.get("acceptInvites").getAsBoolean();
		} catch (RuntimeException e) {
			return true;
		}
	}

	static void setAcceptsInvites(MinecraftServer server, UUID player, boolean accept) {
		PvpRecordData data = PvpRecordData.get(server);
		JsonObject o;
		try {
			o = JsonParser.parseString(data.json(player)).getAsJsonObject();
		} catch (RuntimeException e) {
			o = new JsonObject();
		}
		if (!o.has("v")) {
			o.addProperty("v", 1);
		}
		o.addProperty("acceptInvites", accept);
		data.put(player, o.toString());
	}
}
