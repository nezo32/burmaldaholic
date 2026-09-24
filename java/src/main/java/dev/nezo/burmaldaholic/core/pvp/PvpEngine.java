package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.bots.Bots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.ChatterLimiter;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch.Phase;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.CoinChain;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.Eligibility;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.LobbyRules;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.ModeCalls;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpBotRules;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpPlayerRecord;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Rivalry;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.core.pvp.logic.Taunts;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The PvP engine (PVP.md §3, §3.15; BOTS.md §3.6, §4.8, §5; docs/architecture/pvp-bots.md §3). Server
 * thread only; timers run on {@code END_SERVER_TICK} in world ticks (overworld game time).
 *
 * <p>Money: every escrow (accept / join / top-up / bot seat / rematch / chain link) and every settlement is
 * ONE {@code Economy.batch}; the bank holds the escrow. The tape is drawn with the FAIR rng and persisted
 * (DRAWN) before the first reveal; the outcome is recomputed from the stored tape, so a restart, a server
 * stop, casino mode off or an admin cancel settle exactly what would have been shown.
 */
final class PvpEngine implements PvpService {
	static final String GAME = "pvp";
	private static final Map<String, Integer> QUIP_VARIANTS = Map.of("pvp_win", 3, "pvp_loss", 3, "duel_accept", 3, "duel_decline", 2);

	private final Map<String, PvpMatch> matches = new LinkedHashMap<>();
	/** "target|challenger" → tick until which the target's decline blocks new challenges. */
	private final Map<String, Long> declined = new HashMap<>();
	private final Map<String, ChatterLimiter> chatter = new HashMap<>();
	private final List<BiPredicate<MinecraftServer, UUID>> busyChecks = new CopyOnWriteArrayList<>();
	private @Nullable MinecraftServer server;
	/** Engine-wide sequence of chain questions (review wave 2, m2). */
	private long decisionSeqCounter;
	/** First / maximum settlement retry back-off (review wave 2, m6). */
	static final int SETTLE_RETRY_FIRST = 20;
	static final int SETTLE_RETRY_MAX = 1200;

	enum Reason { NORMAL, OFFLINE, STOP, CASINO_OFF, ADMIN }

	// =====================================================================================================
	// lifecycle
	// =====================================================================================================

	void registerLifecycle() {
		ServerLifecycleEvents.SERVER_STARTED.register(this::onStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> reset());
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		CasinoMode.onChange((s, on) -> {
			if (!on) {
				casinoOff(s);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> onDisconnect(handler.getPlayer()));
		// a closed bankroll's tombstone stays while a live match may still refund / pay / rake to it (review wave 2, M1)
		dev.nezo.burmaldaholic.core.economy.BankrollReferences.add((s, id) -> referencesBankroll(id));
	}

	/** Some live match (not yet history) is anchored at bankroll {@code id} or has bots funded by it. */
	boolean referencesBankroll(String id) {
		for (PvpMatch m : matches.values()) {
			if (m.phase == Phase.HISTORY || m.phase == Phase.CLOSED) {
				continue;
			}
			if (m.bankroll.equals(id)) {
				return true;
			}
			for (Participant p : m.participants) {
				if (p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL && b.purse().bankrollId().equals(id)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Engine fields of the match view (pvp module screens): the viewer's pending decision, chain, timers. */
	void registerViewContributor() {
		PvpViewContributors.register((match, viewer, view) -> {
			long now = server == null ? 0 : now(server);
			view.addProperty("phase", match.phase());
			view.addProperty("ticksLeft", Math.max(0, match.phaseEnd - now));
			if (match.noMoreBetsAt > 0) {
				view.addProperty("noMoreBetsIn", Math.max(0, match.noMoreBetsAt - now));
			}
			if (match.chain != null) {
				JsonObject ch = new JsonObject();
				ch.addProperty("link", match.chain.link());
				ch.addProperty("loser", match.chain.loser());
				ch.addProperty("deficit", match.chain.deficit());
				ch.addProperty("over", match.chain.over());
				if (match.offerBlock != null) {
					ch.addProperty("offerBlock", match.offerBlock);
				}
				view.add("chain", ch);
			}
			if (viewer != null) {
				decisionFor(viewer.getUUID()).filter(d -> matches.get(match.id) == match && participantOf(match, viewer.getUUID()) != null
					&& d.link() == match.link).ifPresent(d -> {
					JsonObject o = new JsonObject();
					o.addProperty("id", d.decision());
					o.addProperty("match", match.id);
					o.addProperty("seq", match.decisionSeq);
					o.addProperty("ticksLeft", d.ticksLeft());
					o.addProperty("deficit", d.deficit());
					o.addProperty("link", d.link());
					o.addProperty("side", match.donSide);
					o.addProperty("called", match.donSide == 1 ? 2 : 1);
					if (match.offerBlock != null && "coin.don_offer".equals(d.decision())) {
						o.addProperty("blocked", match.offerBlock);
						o.addProperty("blockedArg", match.chain == null ? 0 : match.chain.nextStake());
					}
					view.add("decision", o);
				});
			}
		});
	}

	void addBusyCheck(BiPredicate<MinecraftServer, UUID> check) {
		busyChecks.add(check);
	}

	private void reset() {
		matches.clear();
		declined.clear();
		chatter.clear();
		server = null;
	}

	private void onStarted(MinecraftServer s) {
		server = s;
		playOutSaved(s);
	}

	/**
	 * World load (PVP.md §3.6): LOBBY → refund every entry (note {@code lobby.refunded} on join); DRAWN → settle
	 * from the tape (note {@code result.offline}); SETTLED → kept for the history until {@code pvp.historyTicks}.
	 */
	void playOutSaved(MinecraftServer s) {
		server = s;
		PvpMatchData data = PvpMatchData.get(s);
		for (Map.Entry<String, String> e : data.raw().entrySet()) {
			PvpMatch m;
			try {
				m = MatchJson.decode(e.getValue());
			} catch (RuntimeException ex) {
				Burmaldaholic.LOGGER.error("PvP: dropping unreadable match {}", e.getKey(), ex);
				data.remove(e.getKey());
				continue;
			}
			m.botRng = Bots.newRng();
			switch (m.state) {
				case LOBBY -> {
					matches.put(m.id, m);
					refundAll(s, m, true, false);
					close(s, m, MatchState.CANCELLED);
				}
				case DRAWN -> {
					matches.put(m.id, m);
					settle(s, m, Reason.OFFLINE);
				}
				case SETTLED -> {
					m.phase = Phase.HISTORY;
					m.forcedSettle = true;
					matches.put(m.id, m);
				}
				default -> data.remove(m.id);
			}
		}
	}

	/** Test/admin hook: forget the in-memory state and play out the saved matches as a world load does. */
	void simulateRestart(MinecraftServer s) {
		matches.values().forEach(PvpEngine::releaseBots);
		matches.clear();
		declined.clear();
		chatter.clear();
		playOutSaved(s);
	}

	private void onStopping(MinecraftServer s) {
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			try {
				if (m.state == MatchState.DRAWN) {
					settle(s, m, Reason.STOP);
				} else if (m.phase == Phase.INVITE) {
					close(s, m, MatchState.CANCELLED);
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP: play-out at stop failed for {}", m.id, e);
			}
		}
	}

	/** Casino mode off (PVP.md §3.6): invites withdrawn, lobbies refunded, DRAWN settled at once. */
	private void casinoOff(MinecraftServer s) {
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			try {
				switch (m.phase) {
					case INVITE -> {
						notifyInviteEnd(s, m, "msg.burmaldaholic.pvp.invite.withdrawn");
						close(s, m, MatchState.CANCELLED);
					}
					case LOBBY, NO_MORE_BETS -> {
						refundAll(s, m, false, true);
						close(s, m, MatchState.CANCELLED);
					}
					case REVEAL -> settle(s, m, Reason.CASINO_OFF);
					case OFFER_LOSER, OFFER_WINNER, REMATCH -> m.phase = Phase.HISTORY;
					default -> {
					}
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP: casino-off play-out failed for {}", m.id, e);
			}
		}
	}

	private void onJoin(ServerPlayer player) {
		MinecraftServer s = server(player);
		PvpPlayerRecord rec = rec(s, player.getUUID());
		if (rec.notes.isEmpty()) {
			return;
		}
		for (PvpPlayerRecord.Note n : rec.notes) {
			switch (n.type()) {
				case "refunded" -> player.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.lobby.refunded", Texts.chips(n.amount())));
				case "casino_off" -> player.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.result.casino_off",
					PvpText.modeName(n.mode()), noteLine(n.amount())));
				default -> player.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.result.offline", PvpText.modeName(n.mode()),
					noteLine(n.amount())));
			}
		}
		rec.notes.clear();
		saveRec(s, player.getUUID(), rec);
	}

	private static MutableComponent noteLine(long net) {
		return net > 0 ? Component.translatable("gui.burmaldaholic.pvp.result.you_win", Texts.chips(net))
			: Component.translatable("gui.burmaldaholic.pvp.result.you_lose", Texts.chips(-net));
	}

	private void onDisconnect(ServerPlayer player) {
		MinecraftServer s = server(player);
		UUID id = player.getUUID();
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			if (m.phase == Phase.INVITE && (id.equals(m.host) || id.equals(m.invitee))) {
				notifyInviteEnd(s, m, "msg.burmaldaholic.pvp.invite.withdrawn");
				close(s, m, MatchState.CANCELLED);
			} else if ((m.phase == Phase.LOBBY) && participantOf(m, id) != null) {
				leaveLobby(s, m, id);
			}
		}
	}

	// =====================================================================================================
	// tick
	// =====================================================================================================

	private void tick(MinecraftServer s) {
		server = s;
		if (matches.isEmpty()) {
			return;
		}
		long now = now(s);
		boolean on = CasinoMode.isEnabled(s);
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			if (!matches.containsKey(m.id)) {
				continue;
			}
			try {
				if (on || m.phase == Phase.HISTORY) {
					tickMatch(s, m, now);
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP: tick failed for match {}", m.id, e);
			}
		}
		if (s.getTickCount() % 200 == 0) {
			declined.values().removeIf(until -> until <= now);
		}
	}

	private void tickMatch(MinecraftServer s, PvpMatch m, long now) {
		if ((m.phase == Phase.LOBBY || m.phase == Phase.NO_MORE_BETS) && now % 20 == 0 && anchorLost(s, m)) {
			cancelLobby(s, m); // machine broken, or its charter closed / changed: entries back (Bedrock watchLobbyAnchors)
			return;
		}
		switch (m.phase) {
			case INVITE -> tickInvite(s, m, now);
			case LOBBY -> {
				if (equalStakes(m)) {
					tickLobby(s, m, now);
				} else {
					tickWheelLobby(s, m, now);
				}
			}
			case NO_MORE_BETS -> {
				if (now >= m.phaseEnd) {
					if (m.participants.size() < 2) {
						cancelLobby(s, m);
					} else {
						begin(s, m, now);
					}
				}
			}
			case REVEAL -> tickReveal(s, m, now);
			case OFFER_LOSER, OFFER_WINNER -> tickOffer(s, m, now);
			case REMATCH -> tickRematch(s, m, now);
			case HISTORY -> {
				if (now - m.settledTick >= CasinoConfig.pvp().historyTicks) {
					close(s, m, MatchState.CLOSED);
				}
			}
			default -> {
			}
		}
	}

	// =====================================================================================================
	// duels (§3.3.1)
	// =====================================================================================================

	@Override
	public Result<PvpMatch> challenge(ServerPlayer challenger, String mode, JsonElement params, long stake, Opponent opponent) {
		MinecraftServer s = server(challenger);
		PvpMode<?, ?> md = PvpModes.get(mode).orElse(null);
		if (md == null) {
			return fail("gui.burmaldaholic.error.disabled");
		}
		String bad = ModeCalls.validate(md, params);
		if (bad != null) {
			return fail(bad);
		}
		params = snapshot(md, params);
		GlobalPos anchor = GlobalPos.of(challenger.level().dimension(), challenger.blockPosition());
		long now = now(s);
		BotRng rng = Bots.newRng();
		BotSettings seating = opponent instanceof Opponent.BotTarget bt0
			? new BotSettings(SeatPolicy.BOTS_ONLY, 1, bt0.difficulty(), false, true, BotSpeed.NORMAL) : BotSettings.HUMANS_ONLY;
		PvpMatch m = new PvpMatch(newId(), mode, params.deepCopy(), AnchorKind.NONE, anchor, "", seating, false, now, "", 1,
			MatchState.INVITED);
		m.duel = true;
		m.host = challenger.getUUID();
		m.botRng = rng;
		m.phase = Phase.INVITE;
		m.phaseEnd = now + CasinoConfig.pvp().inviteTimeoutTicks;
		m.participants.add(new Participant(0, human(challenger), stake));
		switch (opponent) {
			case Opponent.PlayerTarget pt -> {
				ServerPlayer target = s.getPlayerList().getPlayer(pt.player());
				if (target == null) {
					return Result.fail(Component.translatable("gui.burmaldaholic.pvp.error.target_unavailable", Texts.raw("?")));
				}
				if (target.getUUID().equals(challenger.getUUID())) {
					return fail("gui.burmaldaholic.pvp.error.self");
				}
				Component e = eligibility(challenger, md, m, null, stake, Role.CHALLENGER, null, false, false);
				if (e == null) {
					e = eligibility(target, md, m, null, stake, Role.TARGET, challenger, false, false);
				}
				if (e != null) {
					return Result.fail(e);
				}
				m.invitee = target.getUUID();
				m.participants.add(new Participant(1, human(target), stake));
				matches.put(m.id, m);
				// Invite chat lines (clickable [Accept] / [Decline]) are sent by the pvp module's presenter (lobbyChanged).
				presenter().sound(m, "minecraft:item.goat_horn.sound.0", 1);
			}
			case Opponent.BotTarget bt -> {
				if (!Bots.enabled()) {
					return fail("gui.burmaldaholic.bots.error.disabled");
				}
				Component e = eligibility(challenger, md, m, null, stake, Role.CHALLENGER, null, true, true);
				if (e != null) {
					return Result.fail(e);
				}
				if (Bots.acquire(1) == 0) {
					return fail("msg.burmaldaholic.bots.none_available");
				}
				m.botsAcquired = 1;
				Participant bot = new Participant(1, newBot(m, bt.difficulty(), Purse.BANK), stake);
				bot.botActAt = now + PvpBotRules.acceptTicks(rng, BotSpeed.NORMAL);
				m.participants.add(bot);
				matches.put(m.id, m);
			}
		}
		presenter().lobbyChanged(m);
		return Result.ok(m);
	}

	private void tickInvite(MinecraftServer s, PvpMatch m, long now) {
		Participant bot = m.participants.size() > 1 && m.participants.get(1).isBot() ? m.participants.get(1) : null;
		if (bot != null && bot.botActAt >= 0 && now >= bot.botActAt) {
			bot.botActAt = -1;
			botAnswersDuel(s, m, now);
			return;
		}
		if (now >= m.phaseEnd) {
			ServerPlayer challenger = online(s, m.host);
			if (challenger != null && m.participants.size() > 1) {
				challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.expired",
					PvpText.name(m.participants.get(1), mode(m))).withStyle(ChatFormatting.GRAY));
			}
			close(s, m, MatchState.CANCELLED);
		}
	}

	/** A bot opponent accepts after its think time unless its purse can't fund it or the player is capped (BOTS.md §3.6). */
	private void botAnswersDuel(MinecraftServer s, PvpMatch m, long now) {
		ServerPlayer challenger = online(s, m.host);
		Participant bot = m.participants.get(1);
		if (challenger == null) {
			close(s, m, MatchState.CANCELLED);
			return;
		}
		if (BotLedger.sulking(s, challenger.getUUID())) {
			quip(s, m, bot, "duel_decline", challenger.getDisplayName(), now);
			challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.declined", PvpText.name(bot, mode(m))));
			close(s, m, MatchState.CANCELLED);
			return;
		}
		PvpMode<?, ?> md = mode(m);
		Component e = md == null ? Component.translatable("gui.burmaldaholic.error.disabled")
			: eligibility(challenger, md, m, null, m.participants.get(0).stake(), Role.MONEY, null, true, true);
		if (e != null) {
			challenger.sendSystemMessage(e.copy().withStyle(ChatFormatting.RED));
			close(s, m, MatchState.CANCELLED);
			return;
		}
		if (!escrow(s, m, List.of(m.participants.get(0), bot))) {
			challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.failed").withStyle(ChatFormatting.RED));
			close(s, m, MatchState.CANCELLED);
			return;
		}
		quip(s, m, bot, "duel_accept", challenger.getDisplayName(), now);
		challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.accepted", PvpText.name(bot, md)));
		begin(s, m, now);
	}

	@Override
	public Result<PvpMatch> accept(ServerPlayer target, String matchId) {
		MinecraftServer s = server(target);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.phase != Phase.INVITE || !target.getUUID().equals(m.invitee)) {
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		ServerPlayer challenger = online(s, m.host);
		PvpMode<?, ?> md = mode(m);
		if (challenger == null || md == null) {
			close(s, m, MatchState.CANCELLED);
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		long stake = m.participants.get(0).stake();
		Component e = eligibility(target, md, m, null, stake, Role.MONEY, null, false, false);
		Component other = eligibility(challenger, md, m, null, stake, Role.MONEY_OTHER, null, false, false);
		if (e != null || other != null) {
			close(s, m, MatchState.CANCELLED);
			if (other != null) {
				challenger.sendSystemMessage((e != null ? e : other).copy().withStyle(ChatFormatting.RED));
			}
			return Result.fail(e != null ? e : other);
		}
		if (!escrow(s, m, m.participants)) {
			Component f = Component.translatable("msg.burmaldaholic.pvp.invite.failed");
			challenger.sendSystemMessage(f.copy().withStyle(ChatFormatting.RED));
			close(s, m, MatchState.CANCELLED);
			return Result.fail(f);
		}
		m.invitee = null;
		challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.accepted", target.getDisplayName()));
		begin(s, m, now(s));
		return Result.ok(m);
	}

	@Override
	public void decline(ServerPlayer target, String matchId) {
		MinecraftServer s = server(target);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.phase != Phase.INVITE || !target.getUUID().equals(m.invitee)) {
			return;
		}
		declined.put(target.getUUID() + "|" + m.host, now(s) + CasinoConfig.pvp().declineCooldownTicks);
		ServerPlayer challenger = online(s, m.host);
		if (challenger != null) {
			challenger.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.invite.declined", target.getDisplayName()));
		}
		close(s, m, MatchState.CANCELLED);
	}

	@Override
	public void withdraw(ServerPlayer challenger, String matchId) {
		MinecraftServer s = server(challenger);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.phase != Phase.INVITE || !challenger.getUUID().equals(m.host)) {
			return;
		}
		notifyInviteEnd(s, m, "msg.burmaldaholic.pvp.invite.withdrawn");
		close(s, m, MatchState.CANCELLED);
	}

	private void notifyInviteEnd(MinecraftServer s, PvpMatch m, String key) {
		ServerPlayer target = online(s, m.invitee);
		if (target != null) {
			target.sendSystemMessage(Component.translatable(key, PvpText.name(m.participants.get(0), mode(m))).withStyle(ChatFormatting.GRAY));
		}
	}

	// =====================================================================================================
	// lobbies (§3.3.2, §3.15.1)
	// =====================================================================================================

	@Override
	public Result<PvpMatch> openLobby(ServerPlayer host, String mode, JsonElement params, long stake, Anchor anchor, BotSettings seating,
			boolean inviteOnly) {
		MinecraftServer s = server(host);
		PvpMode<?, ?> md = PvpModes.get(mode).orElse(null);
		if (md == null) {
			return fail("gui.burmaldaholic.error.disabled");
		}
		String bad = ModeCalls.validate(md, params);
		if (bad != null) {
			return fail(bad);
		}
		params = snapshot(md, params);
		boolean machine = anchor != null && anchor.kind() != AnchorKind.NONE;
		GlobalPos gp = machine ? GlobalPos.of(anchor.level().dimension(), anchor.pos().immutable())
			: GlobalPos.of(host.level().dimension(), host.blockPosition());
		OwnedTable owned = machine ? CoreServices.tableOwnership().owner(anchor.level(), anchor.pos()).orElse(null) : null;
		if (owned != null && !CasinoConfig.pvp().allowOwnedMachines) {
			return fail("gui.burmaldaholic.error.table_closed");
		}
		BotSettings seat = seating == null ? BotSettings.HUMANS_ONLY : seating;
		if (seat.policy() != SeatPolicy.HUMANS_ONLY && !Bots.enabled()) {
			if (seat.policy() == SeatPolicy.BOTS_ONLY) {
				return fail("gui.burmaldaholic.bots.error.disabled");
			}
			seat = seat.withPolicy(SeatPolicy.HUMANS_ONLY);
		}
		if (owned != null && seat.policy() != SeatPolicy.HUMANS_ONLY
				&& !(CasinoConfig.bots().owned.funding == BotsConfig.Funding.OWNER_BANKROLL && owned.bots())) {
			// BOTS.md §5.1 / PVP.md §3.15.1: bots can't be funded here → humans only.
			host.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.bots.no_bots_here").withStyle(ChatFormatting.GRAY));
			seat = seat.withPolicy(SeatPolicy.HUMANS_ONLY);
		}
		long now = now(s);
		PvpMatch m = new PvpMatch(newId(), mode, params.deepCopy(), machine ? anchor.kind() : AnchorKind.NONE, gp,
			owned == null ? "" : owned.bankrollId(), seat, inviteOnly, now, "", 1, MatchState.LOBBY);
		m.host = host.getUUID();
		m.botRng = Bots.newRng();
		m.phase = Phase.LOBBY;
		m.lobbyEnd = now + CasinoConfig.pvp().lobbyTimeoutTicks;
		m.phaseEnd = m.lobbyEnd;
		m.lastJoin = now;
		if (!md.equalStakes() && stake > cap(m, Long.MAX_VALUE)) {
			return Result.fail(Component.translatable("gui.burmaldaholic.pvp.error.over_cap", Texts.chips(cap(m, 0))));
		}
		boolean bots = seat.policy() != SeatPolicy.HUMANS_ONLY;
		boolean houseBots = bots && owned == null;
		Component e = eligibility(host, md, m, owned, stake, Role.MONEY, null, bots, seat.policy() == SeatPolicy.BOTS_ONLY && houseBots);
		if (e != null) {
			return Result.fail(e);
		}
		Participant hp = new Participant(0, human(host), stake);
		if (!escrow(s, m, List.of(hp))) {
			return Result.fail(Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.chips(Economies.get().balance(host))));
		}
		m.participants.add(hp);
		matches.put(m.id, m);
		persist(s, m);
		announceOpened(s, m, host, machine ? anchor : null);
		if (seat.policy() == SeatPolicy.BOTS_ONLY) {
			int n = LobbyRules.botsToSeat(SeatPolicy.BOTS_ONLY, seat.count(), maxPlayers(md), 1, 0, CasinoConfig.bots().pvp.maxPerMatch, false, true);
			seatBots(s, m, n, now);
			if (m.participants.size() < 2) {
				cancelLobby(s, m);
				return fail("gui.burmaldaholic.bots.error.disabled");
			}
			if (equalStakes(m)) {
				begin(s, m, now);
			} else {
				startCountdown(s, m, now, 100); // PVP.md §3.15.1: BOTS_ONLY Wheel Party, stakes at once, 100 t
			}
		}
		presenter().lobbyChanged(m);
		return Result.ok(m);
	}

	private void announceOpened(MinecraftServer s, PvpMatch m, ServerPlayer host, @Nullable Anchor anchor) {
		Component msg = anchor != null
			? Component.translatable("msg.burmaldaholic.pvp.lobby.opened", host.getDisplayName(), PvpText.modeName(m.mode),
				anchor.level().getBlockState(anchor.pos()).getBlock().getName(), Texts.chips(m.participants.get(0).stake()))
			: Component.translatable("msg.burmaldaholic.pvp.lobby.opened_here", host.getDisplayName(), PvpText.modeName(m.mode),
				Texts.chips(m.participants.get(0).stake()));
		if (m.inviteOnly || m.seating.policy() == SeatPolicy.BOTS_ONLY) {
			return;
		}
		for (ServerPlayer p : near(s, m, CasinoConfig.pvp().joinRadius)) {
			p.sendSystemMessage(msg);
		}
		presenter().sound(m, "minecraft:item.goat_horn.sound.0", -1);
	}

	@Override
	public Result<PvpMatch> join(ServerPlayer player, String matchId, long stake) {
		MinecraftServer s = server(player);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.state != MatchState.LOBBY) {
			return fail(m == null || m.phase.ordinal() > Phase.REVEAL.ordinal() ? "gui.burmaldaholic.pvp.error.lobby_gone"
				: "gui.burmaldaholic.pvp.error.lobby_started");
		}
		if (m.phase != Phase.LOBBY) {
			return fail(m.phase == Phase.NO_MORE_BETS ? "gui.burmaldaholic.pvp.error.no_more_bets" : "gui.burmaldaholic.pvp.error.lobby_started");
		}
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			return fail("gui.burmaldaholic.error.disabled");
		}
		if (m.seating.policy() == SeatPolicy.BOTS_ONLY) {
			return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.bots_only_table", hostName(s, m)));
		}
		if (participantOf(m, player.getUUID()) != null) {
			return fail("gui.burmaldaholic.pvp.error.self");
		}
		if (m.inviteOnly && CasinoConfig.bots().privateTables.enabled && !m.guests.contains(player.getUUID()) && !player.getUUID().equals(m.host)
				&& !net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
			return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.private_table", hostName(s, m)));
		}
		// a full lobby with bots: the last bot to join yields its seat, after the human's escrow succeeded (m7)
		Participant yielding = m.participants.size() >= maxPlayers(md) ? lastBot(m) : null;
		if (m.participants.size() >= maxPlayers(md) && yielding == null) {
			return fail("gui.burmaldaholic.pvp.error.lobby_full");
		}
		long amount = equalStakes(m) ? m.participants.get(0).stake() : stake;
		if (!equalStakes(m) && amount > cap(m, Long.MAX_VALUE)) {
			return Result.fail(Component.translatable("gui.burmaldaholic.pvp.error.over_cap", Texts.chips(cap(m, 0))));
		}
		OwnedTable owned = owned(s, m);
		Component e = eligibility(player, md, m, owned, amount, Role.JOINER, null, m.hasBots() || m.seating.policy() != SeatPolicy.HUMANS_ONLY, false);
		if (e != null) {
			return Result.fail(e);
		}
		Participant p = new Participant(m.participants.size(), human(player), amount);
		if (!escrow(s, m, List.of(p))) {
			return Result.fail(Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.chips(Economies.get().balance(player))));
		}
		if (yielding != null) {
			if (!refundOne(s, yielding)) {
				refundOne(s, p);
				return fail("gui.burmaldaholic.pvp.error.lobby_full");
			}
			quip(s, m, yielding, "yield", player.getDisplayName(), now(s));
			if (m.botsAcquired > 0) {
				Bots.release(1);
				m.botsAcquired--;
			}
			tell(s, m, Component.translatable("msg.burmaldaholic.bots.left", PvpText.name(yielding, md)), false);
			boolean allIn = p.allIn();
			renumber(m, yielding);
			p = new Participant(m.participants.size(), p.occupant, amount);
			p.setAllIn(allIn);
		}
		m.participants.add(p);
		long now = now(s);
		m.lastJoin = now;
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.lobby.joined", player.getDisplayName(), Texts.number(m.participants.size()),
			Texts.number(maxPlayers(md))), false);
		persist(s, m);
		if (equalStakes(m)) {
			if (m.participants.size() >= maxPlayers(md)) {
				begin(s, m, now);
			}
		} else if (m.participants.size() >= 2 && m.noMoreBetsAt == 0) {
			startCountdown(s, m, now, CasinoConfig.pvp().wheel.countdownTicks);
		}
		presenter().lobbyChanged(m);
		return Result.ok(m);
	}

	@Override
	public Result<Long> topUp(ServerPlayer player, String matchId, long extra) {
		MinecraftServer s = server(player);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.state != MatchState.LOBBY) {
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		if (m.phase != Phase.LOBBY) {
			return fail("gui.burmaldaholic.pvp.error.no_more_bets");
		}
		PvpMode<?, ?> md = mode(m);
		Participant p = participantOf(m, player.getUUID());
		if (md == null || p == null || md.equalStakes() || extra <= 0) {
			return fail("gui.burmaldaholic.error.invalid_amount");
		}
		long bound = Math.min(cap(m, Long.MAX_VALUE), CoreServices.vip().maxBet(s, player.getUUID()));
		if (p.stake() + extra > bound) {
			return Result.fail(Component.translatable("gui.burmaldaholic.pvp.error.over_cap", Texts.chips(bound)));
		}
		Component e = eligibility(player, md, m, owned(s, m), extra, Role.TOP_UP, null, m.hasBots(), false);
		if (e != null) {
			return Result.fail(e);
		}
		Participant delta = new Participant(p.index, p.occupant, extra);
		if (!escrow(s, m, List.of(delta))) {
			return Result.fail(Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.chips(Economies.get().balance(player))));
		}
		p.addStake(extra);
		if (delta.allIn()) {
			p.setAllIn(true);
		}
		announceTopUp(s, m, p, extra);
		persist(s, m);
		presenter().lobbyChanged(m);
		return Result.ok(p.stake());
	}

	private void announceTopUp(MinecraftServer s, PvpMatch m, Participant p, long extra) {
		long pot = m.pot();
		long pct = pot <= 0 ? 0 : Math.round(p.stake() * 100.0 / pot);
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.wheel.top_up", PvpText.name(p, mode(m)), Texts.chips(extra),
			Component.translatable("gui.burmaldaholic.pvp.percent", Texts.number(pct))), true);
	}

	@Override
	public void leave(ServerPlayer player) {
		MinecraftServer s = server(player);
		UUID id = player.getUUID();
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			if (participantOf(m, id) == null) {
				continue;
			}
			switch (m.phase) {
				case INVITE -> {
					if (id.equals(m.host)) {
						withdraw(player, m.id);
					} else {
						decline(player, m.id);
					}
				}
				case LOBBY -> leaveLobby(s, m, id);
				case OFFER_LOSER, OFFER_WINNER -> decideFor(s, m, participantOf(m, id), m.phase == Phase.OFFER_LOSER ? "coin.don_offer"
					: "coin.let_it_ride", 0);
				case REMATCH -> {
					Participant p = participantOf(m, id);
					p.answered = true;
					p.setWantsRematch(false);
				}
				default -> {
				}
			}
		}
	}

	/** The bot that joined last (PvP yield rule, BOTS.md §3.3), or null. */
	private static @Nullable Participant lastBot(PvpMatch m) {
		for (int i = m.participants.size() - 1; i >= 0; i--) {
			if (m.participants.get(i).isBot()) {
				return m.participants.get(i);
			}
		}
		return null;
	}

	@Override
	public Result<Boolean> inviteToLobby(ServerPlayer host, UUID guest) {
		MinecraftServer s = server(host);
		PvpMatch m = null;
		for (PvpMatch x : matches.values()) {
			if (x.phase == Phase.LOBBY && host.getUUID().equals(x.host)) {
				m = x;
			}
		}
		if (m == null) {
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		if (guest.equals(host.getUUID())) {
			return fail("gui.burmaldaholic.pvp.error.self");
		}
		if (m.guests.size() >= CasinoConfig.bots().privateTables.maxInvites && !m.guests.contains(guest)) {
			return Result.fail(Component.translatable("gui.burmaldaholic.bots.error.invites_full", Texts.number(CasinoConfig.bots().privateTables.maxInvites)));
		}
		ServerPlayer g = online(s, guest);
		if (g == null) {
			return Result.fail(Component.translatable("gui.burmaldaholic.pvp.error.target_unavailable", Texts.raw("?")));
		}
		m.guests.add(guest);
		BlockPos at = m.anchor.pos();
		MutableComponent line = Component.translatable("msg.burmaldaholic.bots.invited", host.getDisplayName(), PvpText.modeName(m.mode),
			Texts.raw(at.getX() + " " + at.getY() + " " + at.getZ())).withStyle(ChatFormatting.GOLD);
		String cmd = "/casino pvp join " + m.id;
		line.append(Texts.raw(" ")).append(Component.translatable("gui.burmaldaholic.pvp.invite.accept_button").withStyle(st -> st
			.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
			.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Texts.raw(cmd)))));
		g.sendSystemMessage(line);
		presenter().lobbyChanged(m);
		return Result.ok(true);
	}

	/** Returns one entry from the bank escrow; false if the transaction failed (humans, bankroll bots). */
	private boolean refundOne(MinecraftServer s, Participant p) {
		UUID h = p.humanId();
		AccountId to = h != null ? AccountId.player(h)
			: p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL ? AccountId.bankroll(b.purse().bankrollId()) : null;
		if (to == null || p.stake() <= 0) {
			return true;
		}
		return Economies.get().batch(s).debit(AccountId.HOUSE, p.stake()).credit(to, p.stake()).commit(Transaction.refund(GAME)).ok();
	}

	private void leaveLobby(MinecraftServer s, PvpMatch m, UUID id) {
		Participant p = participantOf(m, id);
		if (p == null) {
			return;
		}
		refund(s, m, p, false, false);
		List<LobbyRules.Seat> seats = new ArrayList<>();
		for (Participant x : m.participants) {
			seats.add(new LobbyRules.Seat(x.occupant.key(), x.isBot()));
		}
		String nextHost = id.equals(m.host) ? LobbyRules.nextHost(seats, id.toString()) : m.host == null ? null : m.host.toString();
		renumber(m, p);
		ServerPlayer leaver = online(s, id);
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.lobby.left", leaver != null ? leaver.getDisplayName() : Texts.raw(p.occupant.name())),
			false);
		if (nextHost == null) {
			cancelLobby(s, m);
			return;
		}
		if (!nextHost.equals(String.valueOf(m.host))) {
			m.host = UUID.fromString(nextHost);
			tell(s, m, Component.translatable("msg.burmaldaholic.pvp.lobby.new_host", hostName(s, m)), false);
		}
		persist(s, m);
		presenter().lobbyChanged(m);
	}

	@Override
	public Result<PvpMatch> start(ServerPlayer host, String matchId) {
		MinecraftServer s = server(host);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.phase != Phase.LOBBY) {
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		if (!host.getUUID().equals(m.host)) {
			return fail("gui.burmaldaholic.bots.error.host_locked");
		}
		long now = now(s);
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			return fail("gui.burmaldaholic.error.disabled");
		}
		if (m.seating.policy() == SeatPolicy.MIXED) {
			seatBots(s, m, LobbyRules.botsToSeat(SeatPolicy.MIXED, m.seating.count(), maxPlayers(md), m.participants.size(), bots(m),
				CasinoConfig.bots().pvp.maxPerMatch, m.seating.keepFree(), true), now);
		}
		if (m.participants.size() < Math.max(2, md.minPlayers())) {
			return fail("gui.burmaldaholic.pvp.lobby.need_more");
		}
		if (equalStakes(m)) {
			begin(s, m, now);
		} else {
			noMoreBets(s, m, now);
		}
		return Result.ok(m);
	}

	@Override
	public void fillWithBots(ServerPlayer host, String matchId) {
		MinecraftServer s = server(host);
		PvpMatch m = matches.get(matchId);
		if (m == null || m.phase != Phase.LOBBY || !host.getUUID().equals(m.host) || m.seating.policy() != SeatPolicy.MIXED) {
			return;
		}
		fillMixed(s, m, now(s));
	}

	private void fillMixed(MinecraftServer s, PvpMatch m, long now) {
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			return;
		}
		int n = LobbyRules.botsToSeat(SeatPolicy.MIXED, m.seating.count(), maxPlayers(md), m.participants.size(), bots(m),
			CasinoConfig.bots().pvp.maxPerMatch, m.seating.keepFree(), false);
		if (n <= 0) {
			return;
		}
		seatBots(s, m, n, now);
		if (equalStakes(m)) {
			if (m.participants.size() >= maxPlayers(md)) {
				begin(s, m, now);
			}
		} else if (m.participants.size() >= 2 && m.noMoreBetsAt == 0) {
			startCountdown(s, m, now, CasinoConfig.pvp().wheel.countdownTicks);
		}
	}

	private void tickLobby(MinecraftServer s, PvpMatch m, long now) {
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			cancelLobby(s, m);
			return;
		}
		if (m.seating.policy() == SeatPolicy.MIXED && now - m.lastJoin >= CasinoConfig.bots().pvp.fillDelayTicks) {
			m.lastJoin = now; // one fill attempt per delay
			fillMixed(s, m, now);
			if (m.phase != Phase.LOBBY) {
				return;
			}
		}
		if (now >= m.lobbyEnd) {
			int atStart = m.seating.policy() == SeatPolicy.MIXED ? LobbyRules.botsToSeat(SeatPolicy.MIXED, m.seating.count(), maxPlayers(md),
				m.participants.size(), bots(m), CasinoConfig.bots().pvp.maxPerMatch, m.seating.keepFree(), true) : 0;
			if (LobbyRules.startRule(m.participants.size(), maxPlayers(md), false, true, atStart) == LobbyRules.Decision.START) {
				if (atStart > 0) {
					seatBots(s, m, atStart, now);
				}
				if (m.participants.size() >= 2) {
					begin(s, m, now);
					return;
				}
			}
			cancelLobby(s, m);
		}
	}

	private void tickWheelLobby(MinecraftServer s, PvpMatch m, long now) {
		if (m.noMoreBetsAt == 0) {
			if (m.seating.policy() == SeatPolicy.MIXED && now - m.lastJoin >= CasinoConfig.bots().pvp.fillDelayTicks) {
				m.lastJoin = now;
				fillMixed(s, m, now);
			}
			if (m.noMoreBetsAt == 0 && now >= m.lobbyEnd) {
				cancelLobby(s, m);
			}
			return;
		}
		Iterator<Long> it = m.botJoinsAt.iterator();
		while (it.hasNext()) {
			long at = it.next();
			if (at <= now) {
				it.remove();
				PvpMode<?, ?> md = mode(m);
				if (md != null && m.participants.size() < maxPlayers(md)) {
					seatBots(s, m, 1, now);
				}
			}
		}
		for (Participant p : new ArrayList<>(m.participants)) {
			if (p.isBot() && p.botActAt >= 0 && now >= p.botActAt) {
				p.botActAt = -1;
				botTopUp(s, m, p);
			}
		}
		if (now >= m.noMoreBetsAt) {
			noMoreBets(s, m, now);
		}
	}

	/** Wheel Party countdown (PVP.md §6.1): "No more bets" at {@code total − noMoreBetsTicks}; MIXED bots join during it. */
	private void startCountdown(MinecraftServer s, PvpMatch m, long now, int total) {
		int nmb = CasinoConfig.pvp().wheel.noMoreBetsTicks;
		m.noMoreBetsAt = now + Math.max(0, total - nmb);
		m.phaseEnd = m.noMoreBetsAt;
		BotRng rng = rng(m);
		if (m.seating.policy() == SeatPolicy.MIXED) {
			PvpMode<?, ?> md = mode(m);
			int n = md == null ? 0 : LobbyRules.botsToSeat(SeatPolicy.MIXED, m.seating.count(), maxPlayers(md), m.participants.size(), bots(m),
				CasinoConfig.bots().pvp.maxPerMatch, m.seating.keepFree(), false);
			long span = Math.max(1, m.noMoreBetsAt - now - 40);
			for (int i = 0; i < n; i++) {
				m.botJoinsAt.add(now + 20 + rng.nextInt((int) Math.min(Integer.MAX_VALUE, span)));
			}
			m.botJoinsAt.sort(Long::compare);
		}
		for (Participant p : m.participants) {
			if (p.isBot()) {
				scheduleTopUp(m, p, now);
			}
		}
		presenter().lobbyChanged(m);
	}

	/** Bots consider a top-up in the last 3 s before "No more bets" (BOTS.md §4.8). */
	private void scheduleTopUp(PvpMatch m, Participant bot, long now) {
		if (m.noMoreBetsAt <= 0) {
			return;
		}
		long at = m.noMoreBetsAt - rng(m).between(10, 60);
		bot.botActAt = Math.max(now, at);
	}

	private void botTopUp(MinecraftServer s, PvpMatch m, Participant bot) {
		PvpMode<?, ?> md = mode(m);
		if (md == null || !(bot.occupant instanceof SeatOccupant.Bot b)) {
			return;
		}
		long cap = cap(m, Long.MAX_VALUE);
		DecisionView v = new DecisionView("wheel.top_up", bot.index, 0, 0, CasinoConfig.pvp().minStake, cap, bot.stake(), medianHumanStake(m),
			(int) Math.max(0, m.noMoreBetsAt - now(s)));
		long extra = ModeCalls.botDecide(md, v, b.profile().level(), rng(m));
		extra = Math.min(extra, cap - bot.stake());
		if (extra <= 0) {
			return;
		}
		Participant delta = new Participant(bot.index, bot.occupant, extra);
		if (!fundBot(s, b.purse(), extra) || !escrow(s, m, List.of(delta))) {
			return;
		}
		bot.addStake(extra);
		announceTopUp(s, m, bot, extra);
		persist(s, m);
		presenter().lobbyChanged(m);
	}

	private void noMoreBets(MinecraftServer s, PvpMatch m, long now) {
		m.phase = Phase.NO_MORE_BETS;
		m.botJoinsAt.clear();
		m.phaseEnd = now + CasinoConfig.pvp().wheel.noMoreBetsTicks;
		m.noMoreBetsAt = now;
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.wheel.no_more_bets"), true);
		presenter().lobbyChanged(m);
	}

	/**
	 * A machine lobby whose anchor is gone (the block was broken) or whose casino changed since creation (the
	 * charter broke, closed the table, or a charter linked a house machine): the lobby cannot go on with the
	 * money routing it was opened with.
	 */
	private boolean anchorLost(MinecraftServer s, PvpMatch m) {
		if (m.anchorKind == AnchorKind.NONE) {
			return false;
		}
		ServerLevel level = s.getLevel(m.anchor.dimension());
		if (level == null || !level.isLoaded(m.anchor.pos())) {
			return false;
		}
		if (level.getBlockState(m.anchor.pos()).isAir()) {
			return true;
		}
		OwnedTable owned = owned(s, m);
		if (m.bankroll.isEmpty()) {
			return owned != null;
		}
		return owned == null || !owned.bankrollId().equals(m.bankroll) || !owned.open();
	}

	private void cancelLobby(MinecraftServer s, PvpMatch m) {
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.lobby.cancelled").withStyle(ChatFormatting.GRAY), false);
		refundAll(s, m, false, false);
		close(s, m, MatchState.CANCELLED);
	}

	// =====================================================================================================
	// bots in a match (BOTS.md §3.4, §3.6, §4.8)
	// =====================================================================================================

	private SeatOccupant.Bot newBot(PvpMatch m, BotDifficulty setting, Purse purse) {
		Set<String> used = new HashSet<>();
		for (Participant p : m.participants) {
			if (p.occupant instanceof SeatOccupant.Bot b) {
				used.add(b.profile().nameId());
			}
		}
		BotsConfig b = CasinoConfig.bots();
		BotProfile profile = BotRoster.create(rng(m), setting, b.difficultyMix, BotRoster.Theme.ANY, used, b.personalities);
		return new SeatOccupant.Bot(profile, BotRole.MONEY, purse);
	}

	private Purse purseFor(PvpMatch m) {
		return m.bankroll.isEmpty() ? Purse.BANK : Purse.bankroll(m.bankroll);
	}

	/** Seats up to {@code n} bots (stake: the entry, Wheel Party: its decision), each escrowed from its purse. */
	private void seatBots(MinecraftServer s, PvpMatch m, int n, long now) {
		if (n <= 0 || !Bots.enabled() || Bots.activeBudgetLeft() <= 0) {
			return;
		}
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			return;
		}
		Purse purse = purseFor(m);
		if (purse.houseFunded()) {
			for (Participant p : m.participants) {
				UUID h = p.humanId();
				if (h != null && BotLedger.sulking(s, h)) {
					return; // house bots refuse a capped player (BOTS.md §5.4)
				}
			}
		}
		List<Component> names = new ArrayList<>();
		for (int i = 0; i < n && m.participants.size() < maxPlayers(md); i++) {
			if (Bots.acquire(1) == 0) {
				break; // PvP bots count against bots.maxActive
			}
			m.botsAcquired++;
			SeatOccupant.Bot bot = newBot(m, m.seating.difficulty(), purse);
			long stake;
			if (equalStakes(m)) {
				stake = m.participants.get(0).stake();
			} else {
				long cap = cap(m, Long.MAX_VALUE);
				DecisionView v = new DecisionView("wheel.stake", m.participants.size(), 0, 0, CasinoConfig.pvp().minStake, cap, 0,
					medianHumanStake(m), (int) Math.max(0, m.noMoreBetsAt - now));
				stake = Math.max(CasinoConfig.pvp().minStake, Math.min(cap, ModeCalls.botDecide(md, v, bot.profile().level(), rng(m))));
			}
			Participant p = new Participant(m.participants.size(), bot, stake);
			if (!fundBot(s, purse, stake) || !escrow(s, m, List.of(p))) {
				Bots.release(1);
				m.botsAcquired--;
				break;
			}
			m.participants.add(p);
			names.add(PvpText.name(p, md));
			if (!equalStakes(m)) {
				scheduleTopUp(m, p, now);
			}
		}
		if (!names.isEmpty()) {
			for (Component name : names) {
				tell(s, m, Component.translatable("msg.burmaldaholic.pvp.bots.joined", name), false);
			}
			persist(s, m);
			presenter().lobbyChanged(m);
		}
	}

	/** Can the purse fund {@code amount}? (bank: always; bankroll: {@code available} covers it). */
	private static boolean fundBot(MinecraftServer s, Purse purse, long amount) {
		if (purse.kind() != Purse.Kind.BANKROLL) {
			return true;
		}
		return Economies.get().bankrolls(s).get(purse.bankrollId()).map(b -> b.available() >= amount).orElse(false);
	}

	/**
	 * A PvP bot's quip (BOTS.md §7.4) through core {@link dev.nezo.burmaldaholic.core.bots.BotChatter}: its rate
	 * limits and chance, the bots module's delivery (per-player mute, emotes, audience around the anchor).
	 */
	private void quip(MinecraftServer s, PvpMatch m, Participant bot, String trigger, Component human, long now) {
		if (!(bot.occupant instanceof SeatOccupant.Bot b) || !m.seating.chatter() || !CasinoConfig.bots().chatter.enabled) {
			return;
		}
		ServerLevel level = s.getLevel(m.anchor.dimension());
		if (level != null) {
			dev.nezo.burmaldaholic.core.bots.BotChatter.event(level, m.anchor.pos(), b.profile(), trigger, human.getString());
		}
	}

	private static int bots(PvpMatch m) {
		int n = 0;
		for (Participant p : m.participants) {
			if (p.isBot()) {
				n++;
			}
		}
		return n;
	}

	private static long medianHumanStake(PvpMatch m) {
		long[] st = m.participants.stream().filter(p -> !p.isBot()).mapToLong(Participant::stake).sorted().toArray();
		return st.length == 0 ? 0 : st[(st.length - 1) / 2];
	}

	// =====================================================================================================
	// START → DRAWN → REVEAL (§3.6, §3.11.4)
	// =====================================================================================================

	/** Rules re-checked, tape drawn with the FAIR rng and persisted BEFORE any reveal; the timeline starts. */
	private void begin(MinecraftServer s, PvpMatch m, long now) {
		PvpMode<?, ?> md = mode(m);
		if (md == null) {
			cancelLobby(s, m);
			return;
		}
		m.state = MatchState.STARTING;
		m.invitee = null;
		m.botJoinsAt.clear();
		int n = m.participants.size();
		long[] stakes = stakes(m);
		JsonElement tape;
		Outcome outcome;
		try {
			tape = ModeCalls.draw(md, fairRng(), n, m.params);
			outcome = ModeCalls.score(md, tape, stakes, m.params);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP: mode {} failed to draw/score; match {} refunded", m.mode, m.id, e);
			m.state = MatchState.LOBBY;
			cancelLobby(s, m);
			return;
		}
		m.tape = tape;
		m.drawnTick = now;
		m.state = MatchState.DRAWN;
		persist(s, m); // DRAWN is saved before the first reveal packet (PVP.md §3.6)
		m.outcome = outcome;
		computeGrudge(s, m);
		List<Step> steps = new ArrayList<>();
		int allIn = (int) m.participants.stream().filter(Participant::allIn).count();
		steps.add(new Step("pvp.countdown", CasinoConfig.pvp().countdownTicks + 20 * allIn, -1, false, new JsonObject()));
		try {
			steps.addAll(ModeCalls.timeline(md, tape, outcome, m.params));
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP: mode {} timeline failed for {}", m.mode, m.id, e);
		}
		steps.add(new Step("pvp.final", 40, -1, false, new JsonObject()));
		if (n > 2) {
			for (int place = n; place >= 2; place--) {
				JsonObject d = new JsonObject();
				d.addProperty("place", place);
				steps.add(new Step("pvp.place", 20, place, false, d));
			}
			steps.add(new Step("pvp.pause", 30, -1, false, new JsonObject()));
		}
		steps.add(new Step("pvp.winner", 40, -1, false, new JsonObject()));
		m.steps = List.copyOf(steps);
		m.phase = Phase.REVEAL;
		m.cursor = 0;
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.match.start", PvpText.modeName(m.mode), Texts.chips(m.pot())), false);
		if (m.grudge) {
			Participant under = m.participants.get(m.grudgeUnderdog);
			Participant over = m.participants.get(1 - m.grudgeUnderdog);
			tell(s, m, Component.translatable("gui.burmaldaholic.pvp.grudge.title").withStyle(ChatFormatting.DARK_RED), false);
			tell(s, m, Component.translatable("gui.burmaldaholic.pvp.grudge.subtitle", PvpText.name(over, md),
				Texts.number(-rec(s, under.humanId()).vs(over.humanId()).run())), false);
			presenter().sound(m, "minecraft:entity.ravager.roar", -1);
		}
		fire(() -> PvpEvents.MATCH_STARTED.invoker().onStarted(s, m));
		presenter().lobbyChanged(m);
		startStep(s, m, now);
	}

	private void computeGrudge(MinecraftServer s, PvpMatch m) {
		m.grudge = false;
		m.grudgeUnderdog = -1;
		if (m.participants.size() != 2 || m.hasBots()) {
			return;
		}
		UUID a = m.participants.get(0).humanId();
		UUID b = m.participants.get(1).humanId();
		int u = Rivalry.grudgeUnderdog(rec(s, a).vs(b), rec(s, b).vs(a), CasinoConfig.pvp().grudgeLosses);
		if (u >= 0) {
			m.grudge = true;
			m.grudgeUnderdog = u;
		}
	}

	private void startStep(MinecraftServer s, PvpMatch m, long now) {
		while (m.phase == Phase.REVEAL && m.cursor < m.steps.size()) {
			Step step = m.steps.get(m.cursor);
			m.phaseEnd = now + Math.max(0, step.ticks());
			for (Participant p : m.participants) {
				p.setPressed(false);
				p.botActAt = -1;
				if (step.waitForAll() && p.occupant instanceof SeatOccupant.Bot b) {
					p.botActAt = now + PvpBotRules.pressTicks(rng(m), b.profile().level(), m.seating.effectiveSpeed(), step.ticks());
				}
			}
			List<ServerPlayer> viewers = viewers(s, m);
			switch (step.kind()) {
				case "pvp.winner" -> {
					settle(s, m, Reason.NORMAL);
					return;
				}
				case "pvp.place" -> {
					m.revealedPlaces++;
					presenter().finalReveal(m, step.round(), viewers);
					presenter().sound(m, "minecraft:block.note_block.bell", -1);
				}
				case "pvp.final" -> {
					presenter().revealStep(m, step, viewers);
					presenter().sound(m, "minecraft:block.note_block.basedrum", -1);
				}
				default -> presenter().revealStep(m, step, viewers);
			}
			if (step.ticks() > 0) {
				return;
			}
			m.cursor++;
		}
	}

	private void tickReveal(MinecraftServer s, PvpMatch m, long now) {
		if (m.cursor >= m.steps.size()) {
			if (now >= m.settleRetryAt) {
				settle(s, m, m.forcedSettle ? Reason.OFFLINE : Reason.NORMAL);
			}
			return;
		}
		Step step = m.steps.get(m.cursor);
		boolean advance = now >= m.phaseEnd;
		if (!advance && step.waitForAll()) {
			boolean all = true;
			for (Participant p : m.participants) {
				if (p.isBot()) {
					if (!p.pressed() && p.botActAt >= 0 && now >= p.botActAt) {
						p.setPressed(true);
					}
				}
				if (!p.pressed() && (p.isBot() || online(s, p.humanId()) != null)) {
					all = false;
				}
			}
			advance = all;
		}
		if (advance) {
			m.cursor++;
			startStep(s, m, now);
		}
	}

	@Override
	public void press(ServerPlayer player) {
		for (PvpMatch m : matches.values()) {
			Participant p = participantOf(m, player.getUUID());
			if (p != null && m.phase == Phase.REVEAL && m.cursor < m.steps.size() && m.steps.get(m.cursor).waitForAll()) {
				p.setPressed(true);
			}
		}
	}

	// =====================================================================================================
	// SETTLE (§3.4, §3.8, §3.11.5)
	// =====================================================================================================

	private void settle(MinecraftServer s, PvpMatch m, Reason reason) {
		if (m.state == MatchState.SETTLED) {
			return; // idempotent
		}
		PvpMode<?, ?> md = mode(m);
		if (m.tape == null || md == null) {
			Burmaldaholic.LOGGER.warn("PvP: match {} ({}) cannot be scored — stakes refunded", m.id, m.mode);
			refundAll(s, m, reason != Reason.NORMAL, reason == Reason.CASINO_OFF);
			close(s, m, MatchState.CANCELLED);
			return;
		}
		Outcome outcome;
		try {
			outcome = ModeCalls.score(md, m.tape, stakes(m), m.params);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP: scoring failed for {} — stakes refunded", m.id, e);
			refundAll(s, m, reason != Reason.NORMAL, reason == Reason.CASINO_OFF);
			close(s, m, MatchState.CANCELLED);
			return;
		}
		m.outcome = outcome;
		List<Settlement.Seat> seats = seats(m);
		Settlement.Result r = Settlement.settle(seats, outcome.winners(), outcome.seatOrder(), CasinoConfig.pvp().rakeBasisPoints,
			!m.bankroll.isEmpty());
		Economy.Batch batch = Economies.get().batch(s);
		batch.debit(AccountId.HOUSE, -r.houseDelta());
		for (int i = 0; i < m.participants.size(); i++) {
			Participant p = m.participants.get(i);
			UUID h = p.humanId();
			if (h != null && r.payouts()[i] > 0) {
				batch.credit(AccountId.player(h), r.payouts()[i]);
			}
		}
		if (r.bankrollDelta() > 0) {
			batch.credit(AccountId.bankroll(m.bankroll), r.bankrollDelta());
		}
		Economy.TxResult tx = batch.commit(Transaction.payout(GAME));
		if (!tx.ok()) {
			settleFailed(s, m, reason, String.valueOf(tx.failed()));
			return;
		}
		m.settleBackoff = 0;
		m.settleRetryAt = 0;
		m.payouts = r.payouts();
		m.rake = r.rake();
		m.state = MatchState.SETTLED;
		releaseBots(m); // the bots' seats are free again (a rematch / next link acquires its own)
		long now = now(s);
		m.settledTick = now;
		m.forcedSettle = reason != Reason.NORMAL;
		m.revealedPlaces = m.participants.size();
		persist(s, m);
		if (r.rakeToBankroll() > 0) {
			ServerLevel level = s.getLevel(m.anchor.dimension());
			if (level != null) {
				long rake = r.rakeToBankroll();
				fire(() -> CasinoEvents.RAKE_COLLECTED.invoker().onRake(level, m.anchor.pos(), rake, m.bankroll));
			}
		}
		afterSettle(s, m, md, seats, r, reason, now);
	}

	/**
	 * A settlement transaction failed (review wave 2, m6): the match stays DRAWN (escrow held) and the reveal
	 * tick retries after 20 t, doubling up to 1 200 t. Every failure is logged; it settles exactly once.
	 */
	private void settleFailed(MinecraftServer s, PvpMatch m, Reason reason, String why) {
		m.settleBackoff = m.settleBackoff <= 0 ? SETTLE_RETRY_FIRST : Math.min(SETTLE_RETRY_MAX, m.settleBackoff * 2);
		m.settleRetryAt = now(s) + m.settleBackoff;
		m.forcedSettle = reason != Reason.NORMAL;
		m.phase = Phase.REVEAL;
		m.cursor = m.steps.size();
		matches.putIfAbsent(m.id, m);
		Burmaldaholic.LOGGER.error("PvP: settlement of {} failed ({}); retry in {} t", m.id, why, m.settleBackoff);
	}

	private void afterSettle(MinecraftServer s, PvpMatch m, PvpMode<?, ?> md, List<Settlement.Seat> seats, Settlement.Result r, Reason reason,
			long now) {
		Outcome o = m.outcome;
		int n = m.participants.size();
		boolean[] won = new boolean[n];
		for (int w : o.winners()) {
			won[w] = true;
		}
		boolean split = o.winners().length > 1;
		boolean houseBots = m.hasBots() && m.bankroll.isEmpty();
		// ---- per human: play result (VIP / contracts / statistics), heat, notes ----
		for (int i = 0; i < n; i++) {
			Participant p = m.participants.get(i);
			UUID h = p.humanId();
			if (h == null) {
				continue;
			}
			boolean anyBots = false;
			boolean onlyBots = true;
			for (int j = 0; j < n; j++) {
				if (j != i) {
					anyBots |= m.participants.get(j).isBot();
					onlyBots &= m.participants.get(j).isBot();
				}
			}
			PlayResult pr = PlayResult.of(GAME, p.stake(), r.payouts()[i]).pvp()
				.withTable(m.anchorKind == AnchorKind.NONE ? null : m.anchor, m.bankroll).withTags(m.mode);
			pr = BotRounds.tag(pr, anyBots, onlyBots);
			if (anyBots) {
				pr = BotRounds.withShare(pr, Settlement.botShare(seats, i));
			}
			PlayResult fired = pr;
			fire(() -> PlayResults.fire(s, h, fired));
			if (houseBots) {
				BotLedger.record(s, h, Settlement.botAttributedNet(seats, r.payouts(), i));
			}
			MutableComponent line = PvpText.resultLine(p.stake(), r.payouts()[i], split && won[i]);
			ServerPlayer online = online(s, h);
			long net = r.payouts()[i] - p.stake();
			switch (reason) {
				case NORMAL, ADMIN -> {
					if (online != null) {
						online.sendSystemMessage(line.withStyle(won[i] ? ChatFormatting.GREEN : ChatFormatting.GRAY));
					} else {
						note(s, h, new PvpPlayerRecord.Note("offline", m.mode, net));
					}
				}
				case CASINO_OFF -> {
					if (online != null) {
						online.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.result.casino_off", PvpText.modeName(m.mode), line));
					} else {
						note(s, h, new PvpPlayerRecord.Note("casino_off", m.mode, net));
					}
				}
				case OFFLINE, STOP -> note(s, h, new PvpPlayerRecord.Note("offline", m.mode, net));
			}
		}
		// ---- rivalry, win streaks, grudge (humans only) ----
		List<Rivalry.Player> rp = new ArrayList<>();
		Map<UUID, PvpPlayerRecord> recs = new HashMap<>();
		for (Participant p : m.participants) {
			UUID h = p.humanId();
			rp.add(new Rivalry.Player(h, p.occupant.name(), p.stake()));
			if (h != null) {
				recs.put(h, rec(s, h));
			}
		}
		List<Rivalry.Callout> callouts = Rivalry.apply(rp, r.payouts(), o.winners(), recs, CasinoConfig.pvp().streakAnnounce);
		recs.forEach((id, rec) -> saveRec(s, id, rec));
		boolean legendary = false;
		for (Rivalry.Callout c : callouts) {
			legendary |= c.tier() == 2;
			announceStreak(s, m, md, c);
		}
		if (m.grudge && m.grudgeUnderdog >= 0 && won[m.grudgeUnderdog] && !split) {
			Participant under = m.participants.get(m.grudgeUnderdog);
			Participant over = m.participants.get(1 - m.grudgeUnderdog);
			tell(s, m, Component.translatable("msg.burmaldaholic.pvp.grudge.revenge", PvpText.name(under, md), PvpText.name(over, md))
				.withStyle(ChatFormatting.GOLD), true);
		}
		// ---- announcements (§3.11.5) ----
		if (reason == Reason.NORMAL || reason == Reason.ADMIN) {
			announceResult(s, m, md, r, o, legendary);
			botsAfterMatch(s, m, won, now);
		}
		long[] payouts = r.payouts().clone();
		fire(() -> PvpEvents.MATCH_SETTLED.invoker().onSettled(s, m, payouts));
		if (reason == Reason.NORMAL || reason == Reason.ADMIN) {
			List<ServerPlayer> viewers = viewers(s, m);
			fire(() -> presenter().finalReveal(m, 1, viewers));
			presenter().sound(m, "burmaldaholic:win", o.winners().length > 0 ? o.winners()[0] : -1);
			fire(() -> presenter().result(m, onlineHumans(s, m)));
		}
		// ---- what comes next: Double or nothing offers / rematch window ----
		if (reason != Reason.NORMAL) {
			m.phase = Phase.HISTORY;
			return;
		}
		if ("coin".equals(m.mode) && n == 2 && o.winners().length == 1) {
			int loser = 1 - o.winners()[0];
			m.chain = CoinChain.after(m.prevChain, m.link, loser, m.participants.get(0).stake(), CasinoConfig.pvp().coin.maxDoubles);
			if (m.chain.allSquare()) {
				tell(s, m, Component.translatable("msg.burmaldaholic.pvp.coin.all_square", PvpText.name(m.participants.get(0), md),
					PvpText.name(m.participants.get(1), md)).withStyle(ChatFormatting.GOLD), false);
			} else if (!m.chain.over()) {
				m.offerBlock = offerBlock(s, m, m.chain.nextStake());
				if (m.offerBlock == null) {
					openOffer(s, m, Phase.OFFER_LOSER, m.chain.loser(), now);
					return;
				}
			}
		}
		openRematch(s, m, now);
	}

	private @Nullable String offerBlock(MinecraftServer s, PvpMatch m, long d) {
		List<Long> tier = new ArrayList<>();
		List<Long> bal = new ArrayList<>();
		for (Participant p : m.participants) {
			UUID h = p.humanId();
			if (h != null) {
				tier.add(CoreServices.vip().maxBet(s, h));
				bal.add(Economies.get().balance(s, h));
			} else if (p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL) {
				bal.add(fundBot(s, b.purse(), d) ? d : 0L);
			}
		}
		return CoinChain.offerBlock(d, tier.stream().mapToLong(Long::longValue).toArray(), bal.stream().mapToLong(Long::longValue).toArray());
	}

	private void announceStreak(MinecraftServer s, PvpMatch m, PvpMode<?, ?> md, Rivalry.Callout c) {
		ServerPlayer p = online(s, c.player());
		Component name = p != null ? p.getDisplayName() : Texts.raw(nameIn(m, c.player()));
		Component msg;
		UUID breaker = null;
		if (c.tier() < 0) {
			Participant b = c.breaker() >= 0 ? m.participants.get(c.breaker()) : null;
			breaker = b == null ? null : b.humanId();
			msg = Component.translatable("msg.burmaldaholic.pvp.streak.broken", b == null ? Texts.raw("?") : PvpText.name(b, md), name,
				Texts.number(c.streak()));
		} else {
			String key = switch (c.tier()) {
				case 0 -> "msg.burmaldaholic.pvp.streak.heating";
				case 1 -> "msg.burmaldaholic.pvp.streak.rampage";
				default -> "msg.burmaldaholic.pvp.streak.legendary";
			};
			msg = Component.translatable(key, name, Texts.number(c.streak())).withStyle(ChatFormatting.GOLD);
		}
		if (c.tier() == 2) {
			s.getPlayerList().broadcastSystemMessage(msg, false);
			presenter().sound(m, "minecraft:item.totem.use", -1);
		} else {
			tell(s, m, msg, true);
		}
		UUID br = breaker;
		fire(() -> PvpEvents.STREAK.invoker().onStreak(s, c.player(), c.streak(), c.tier(), br));
	}

	private void announceResult(MinecraftServer s, PvpMatch m, PvpMode<?, ?> md, Settlement.Result r, Outcome o, boolean legendary) {
		List<Component> winners = new ArrayList<>();
		List<Component> losers = new ArrayList<>();
		Set<Integer> w = new HashSet<>();
		for (int i : o.winners()) {
			w.add(i);
			winners.add(PvpText.name(m.participants.get(i), md));
		}
		for (int i = 0; i < m.participants.size(); i++) {
			if (!w.contains(i)) {
				losers.add(PvpText.name(m.participants.get(i), md));
			}
		}
		long paid = r.pot() - r.rake();
		Component title = o.winners().length > 1 ? Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title")
			: Component.translatable("gui.burmaldaholic.pvp.result.winner_title", winners.isEmpty() ? Texts.raw("?") : winners.get(0));
		tell(s, m, title.copy().withStyle(ChatFormatting.GOLD), true);
		tell(s, m, Component.translatable("gui.burmaldaholic.pvp.result.pot_line", Texts.chips(r.pot()), Texts.chips(r.rake()), Texts.chips(paid)),
			false);
		int humans = (int) m.participants.stream().filter(p -> !p.isBot()).count();
		boolean big = r.pot() >= CasinoConfig.pvp().announceServerWidePot || legendary;
		// No server-wide broadcast when every counterparty was a bot (BOTS.md §5.3).
		if (big && humans >= 2) {
			Component b = o.winners().length > 1
				? Component.translatable("msg.burmaldaholic.pvp.result.broadcast_split", PvpText.modeName(m.mode), PvpText.list(winners), Texts.chips(paid))
				: Component.translatable("msg.burmaldaholic.pvp.result.broadcast", PvpText.modeName(m.mode), winners.get(0),
					Texts.chips(r.payouts()[o.winners()[0]]), PvpText.list(losers));
			s.getPlayerList().broadcastSystemMessage(b.copy().withStyle(ChatFormatting.GOLD), false);
		}
	}

	/** Bots' chatter and taunts after a match (BOTS.md §7.4, PVP.md §3.15.2). */
	private void botsAfterMatch(MinecraftServer s, PvpMatch m, boolean[] won, long now) {
		Component human = Texts.raw("?");
		for (Participant p : m.participants) {
			if (!p.isBot()) {
				ServerPlayer sp = online(s, p.humanId());
				human = sp != null ? sp.getDisplayName() : Texts.raw(p.occupant.name());
				break;
			}
		}
		for (Participant p : m.participants) {
			if (p.occupant instanceof SeatOccupant.Bot b) {
				quip(s, m, p, won[p.index] ? "pvp_win" : "pvp_loss", human, now);
				if (won[p.index] && rng(m).chance(0.3)) {
					List<Integer> lines = new ArrayList<>();
					for (int i = 0; i < Taunts.LINES.size(); i++) {
						boolean friendly = Taunts.friendly(i);
						if (b.profile().level() == BotDifficulty.EASY ? friendly : b.profile().level() != BotDifficulty.HARD || !friendly) {
							lines.add(i);
						}
					}
					sayTaunt(s, m, p, rng(m).pick(lines), now);
				}
			}
		}
	}

	// =====================================================================================================
	// Coin Flip Duel chain (§4.2) and decisions
	// =====================================================================================================

	private void openOffer(MinecraftServer s, PvpMatch m, Phase phase, int who, long now) {
		m.phase = phase;
		m.decisionSeq = ++decisionSeqCounter;
		m.phaseEnd = now + CasinoConfig.pvp().decisionTimeoutTicks;
		for (Participant p : m.participants) {
			p.botActAt = -1;
		}
		Participant p = m.participants.get(who);
		if (p.isBot()) {
			p.botActAt = now + PvpBotRules.decisionTicks(rng(m), m.seating.effectiveSpeed(), CasinoConfig.pvp().decisionTimeoutTicks);
		}
		presenter().lobbyChanged(m);
	}

	private int chainWinner(PvpMatch m) {
		return m.chain == null ? -1 : 1 - m.chain.loser();
	}

	private void tickOffer(MinecraftServer s, PvpMatch m, long now) {
		int who = m.phase == Phase.OFFER_LOSER ? m.chain.loser() : chainWinner(m);
		Participant p = m.participants.get(who);
		if (p.occupant instanceof SeatOccupant.Bot b && p.botActAt >= 0 && now >= p.botActAt) {
			p.botActAt = -1;
			PvpMode<?, ?> md = mode(m);
			String decision = m.phase == Phase.OFFER_LOSER ? "coin.don_offer" : "coin.let_it_ride";
			DecisionView v = view(m, decision, who, now);
			long choice = md == null ? 0 : ModeCalls.botDecide(md, v, b.profile().level(), rng(m));
			if (m.phase == Phase.OFFER_LOSER && choice != 0 && md != null) {
				long side = ModeCalls.botDecide(md, new DecisionView("coin.side", who, m.link, m.chain.deficit(), CasinoConfig.pvp().minStake, 0,
					p.stake(), 0, v.ticksLeft()), b.profile().level(), rng(m));
				choice = 1 + (side == 1 ? 1 : 0);
			}
			decideFor(s, m, p, decision, choice);
			return;
		}
		if (now >= m.phaseEnd) {
			decideFor(s, m, p, m.phase == Phase.OFFER_LOSER ? "coin.don_offer" : "coin.let_it_ride", 0); // walk away / take the money
		}
	}

	private DecisionView view(PvpMatch m, String decision, int seat, long now) {
		return new DecisionView(decision, seat, m.link, m.chain == null ? 0 : m.chain.deficit(), CasinoConfig.pvp().minStake, 0,
			m.participants.get(seat).stake(), medianHumanStake(m), (int) Math.max(0, m.phaseEnd - now));
	}

	@Override
	public void decide(ServerPlayer player, String decision, long option, @Nullable String matchId, long seq) {
		MinecraftServer s = server(player);
		for (PvpMatch m : new ArrayList<>(matches.values())) {
			if (matchId != null && !matchId.isEmpty() && !matchId.equals(m.id)) {
				continue;
			}
			if (seq >= 0 && seq != m.decisionSeq) {
				continue; // a stale form / screen answering an earlier question (review wave 2, m2)
			}
			Participant p = participantOf(m, player.getUUID());
			if (p != null && (m.phase == Phase.OFFER_LOSER || m.phase == Phase.OFFER_WINNER)) {
				decideFor(s, m, p, decision, option);
			}
		}
	}

	private void decideFor(MinecraftServer s, PvpMatch m, @Nullable Participant p, String decision, long option) {
		if (p == null || m.chain == null) {
			return;
		}
		long now = now(s);
		PvpMode<?, ?> md = mode(m);
		if (m.phase == Phase.OFFER_LOSER && p.index == m.chain.loser() && "coin.side".equals(decision)) {
			m.donSide = option == 1 ? 1 : 0; // Bedrock parity: the side first, then coin.don_offer 1
			presenter().lobbyChanged(m);
		} else if (m.phase == Phase.OFFER_LOSER && p.index == m.chain.loser() && "coin.don_offer".equals(decision)) {
			if (option <= 0) {
				tell(s, m, Component.translatable("msg.burmaldaholic.pvp.coin.walked_away", PvpText.name(p, md)), false);
				openRematch(s, m, now);
			} else {
				m.donSide = option >= 2 ? 1 : option == 1 && m.donSide >= 0 ? m.donSide : 0;
				openOffer(s, m, Phase.OFFER_WINNER, chainWinner(m), now);
			}
		} else if (m.phase == Phase.OFFER_WINNER && p.index == chainWinner(m) && "coin.let_it_ride".equals(decision)) {
			if (option == 1) {
				startChainLink(s, m, now);
			} else {
				tell(s, m, Component.translatable("msg.burmaldaholic.pvp.coin.cashed_out", PvpText.name(p, md)), false);
				openRematch(s, m, now);
			}
		}
	}

	/** Next flip of the chain: a NEW match (chainOf, link + 1), stake D each, the loser's call (PVP.md §4.2). */
	private void startChainLink(MinecraftServer s, PvpMatch m, long now) {
		long d = m.chain.nextStake();
		boolean loserHeads = m.donSide == 0;
		boolean challengerHeads = loserHeads; // participant 0 of the next flip is the chain loser who called the side
		PvpMode<?, ?> mdc = mode(m);
		JsonElement fromMode = null;
		try {
			fromMode = mdc == null ? null : ModeCalls.chainParams(mdc, m.params, d, challengerHeads);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP: chainParams failed for {}", m.mode, e);
		}
		JsonElement params = fromMode != null ? fromMode : m.params.deepCopy();
		if (fromMode == null && params.isJsonObject()) {
			JsonObject po = params.getAsJsonObject();
			if (po.has("stake")) {
				po.addProperty("stake", d);
			}
			po.addProperty("challengerHeads", challengerHeads);
		}
		PvpMatch next = new PvpMatch(newId(), m.mode, params, m.anchorKind, m.anchor, m.bankroll, m.seating, m.inviteOnly, now,
			m.chainOf.isEmpty() ? m.id : m.chainOf, m.link + 1, MatchState.STARTING);
		next.duel = true;
		next.host = m.host;
		next.botRng = m.botRng;
		int loser = m.chain.loser();
		next.prevChain = new CoinChain(m.chain.link(), 0, m.chain.deficit(), m.chain.over());
		next.participants.add(new Participant(0, m.participants.get(loser).occupant, d));
		next.participants.add(new Participant(1, m.participants.get(1 - loser).occupant, d));
		PvpMode<?, ?> md = mode(m);
		for (Participant p : next.participants) {
			UUID h = p.humanId();
			if (h == null) {
				continue;
			}
			ServerPlayer sp = online(s, h);
			Component e = sp == null || md == null ? Component.translatable("gui.burmaldaholic.pvp.error.target_unavailable", Texts.raw(p.occupant.name()))
				: eligibility(sp, md, next, null, d, Role.MONEY, null, next.hasBots(), next.hasBots());
			if (e != null) {
				tell(s, m, e.copy().withStyle(ChatFormatting.RED), false);
				openRematch(s, m, now);
				return;
			}
		}
		if (!escrow(s, next, next.participants)) {
			tell(s, m, Component.translatable("msg.burmaldaholic.pvp.invite.failed").withStyle(ChatFormatting.RED), false);
			openRematch(s, m, now);
			return;
		}
		m.phase = Phase.HISTORY;
		next.botsAcquired = Bots.acquire((int) next.participants.stream().filter(Participant::isBot).count());
		matches.put(next.id, next);
		tell(s, next, Component.translatable("msg.burmaldaholic.pvp.coin.don_called", Texts.number(next.link - 1), Texts.chips(2 * d))
			.withStyle(ChatFormatting.GOLD), true);
		begin(s, next, now);
	}

	@Override
	public Optional<DecisionView> decisionFor(UUID player) {
		for (PvpMatch m : matches.values()) {
			Participant p = participantOf(m, player);
			if (p == null || m.chain == null) {
				continue;
			}
			long now = server == null ? 0 : now(server);
			if (m.phase == Phase.OFFER_LOSER && p.index == m.chain.loser()) {
				return Optional.of(view(m, "coin.don_offer", p.index, now));
			}
			if (m.phase == Phase.OFFER_WINNER && p.index == chainWinner(m)) {
				return Optional.of(view(m, "coin.let_it_ride", p.index, now));
			}
		}
		return Optional.empty();
	}

	// =====================================================================================================
	// rematch (§3.10)
	// =====================================================================================================

	private void openRematch(MinecraftServer s, PvpMatch m, long now) {
		m.phase = Phase.REMATCH;
		m.phaseEnd = now + CasinoConfig.pvp().decisionTimeoutTicks;
		for (Participant p : m.participants) {
			p.answered = false;
			p.setWantsRematch(false);
			p.botActAt = p.isBot() ? now + PvpBotRules.decisionTicks(rng(m), m.seating.effectiveSpeed(), CasinoConfig.pvp().decisionTimeoutTicks) : -1;
		}
		presenter().lobbyChanged(m);
	}

	@Override
	public void rematch(ServerPlayer player, String matchId) {
		MinecraftServer s = server(player);
		PvpMatch m = matches.get(matchId);
		Participant p = m == null ? null : participantOf(m, player.getUUID());
		if (p == null || m.phase != Phase.REMATCH || p.answered) {
			return;
		}
		p.answered = true;
		p.setWantsRematch(true);
		for (Participant o : m.participants) {
			ServerPlayer op = online(s, o.humanId());
			if (op != null && o != p) {
				op.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.rematch.requested", player.getDisplayName()));
			}
		}
		presenter().lobbyChanged(m);
		tickRematch(s, m, now(s));
	}

	private void tickRematch(MinecraftServer s, PvpMatch m, long now) {
		boolean[] won = new boolean[m.participants.size()];
		for (int w : m.outcome.winners()) {
			won[w] = true;
		}
		boolean allAnswered = true;
		for (Participant p : m.participants) {
			if (p.occupant instanceof SeatOccupant.Bot b && !p.answered && p.botActAt >= 0 && now >= p.botActAt) {
				p.answered = true;
				p.setWantsRematch(rng(m).chance(PvpBotRules.rematchChance(b.profile().level(), !won[p.index])));
			}
			if (!p.answered && (p.isBot() || online(s, p.humanId()) != null)) {
				allAnswered = false;
			}
		}
		if (!allAnswered && now < m.phaseEnd) {
			return;
		}
		List<Participant> yes = m.participants.stream().filter(Participant::wantsRematch).toList();
		boolean humanYes = yes.stream().anyMatch(p -> !p.isBot());
		boolean enough = m.duel ? yes.size() == m.participants.size() : yes.size() >= 2;
		m.phase = Phase.HISTORY;
		if (!enough || !humanYes) {
			for (Participant p : yes) {
				ServerPlayer sp = online(s, p.humanId());
				if (sp != null) {
					sp.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.rematch.expired").withStyle(ChatFormatting.GRAY));
				}
			}
			presenter().lobbyChanged(m);
			return;
		}
		startRematch(s, m, yes, now);
	}

	private void startRematch(MinecraftServer s, PvpMatch old, List<Participant> yes, long now) {
		PvpMode<?, ?> md = mode(old);
		if (md == null) {
			return;
		}
		PvpMatch m = new PvpMatch(newId(), old.mode, old.params.deepCopy(), old.anchorKind, old.anchor, old.bankroll, old.seating, old.inviteOnly,
			now, "", 1, MatchState.STARTING);
		m.duel = old.duel;
		m.botRng = old.botRng;
		for (Participant p : yes) {
			UUID h = p.humanId();
			if (h != null) {
				ServerPlayer sp = online(s, h);
				Component e = sp == null ? Component.translatable("gui.burmaldaholic.error.busy")
					: eligibility(sp, md, m, owned(s, m), p.stake(), Role.MONEY, null, old.hasBots(), false);
				if (e != null) {
					tell(s, old, Component.translatable("msg.burmaldaholic.pvp.rematch.dropped", PvpText.name(p, md)), false);
					if (sp != null) {
						sp.sendSystemMessage(e.copy().withStyle(ChatFormatting.RED));
					}
					continue;
				}
				if (m.host == null || h.equals(old.host)) {
					m.host = h;
				}
			} else if (p.occupant instanceof SeatOccupant.Bot b && !fundBot(s, b.purse(), p.stake())) {
				continue;
			}
			m.participants.add(new Participant(m.participants.size(), p.occupant, p.stake()));
		}
		boolean humanLeft = m.participants.stream().anyMatch(p -> !p.isBot());
		if (m.participants.size() < 2 || !humanLeft || (m.duel && m.participants.size() < old.participants.size())) {
			tell(s, old, Component.translatable("msg.burmaldaholic.pvp.rematch.expired").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		if (!escrow(s, m, m.participants)) {
			tell(s, old, Component.translatable("msg.burmaldaholic.pvp.invite.failed").withStyle(ChatFormatting.RED), false);
			return;
		}
		m.botsAcquired = Bots.acquire((int) m.participants.stream().filter(Participant::isBot).count());
		matches.put(m.id, m);
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.rematch.start").withStyle(ChatFormatting.GOLD), false);
		if (equalStakes(m)) {
			begin(s, m, now);
		} else {
			// Wheel Party: the party re-opens with everyone's previous stake (they may top up during the countdown).
			m.state = MatchState.LOBBY;
			m.phase = Phase.LOBBY;
			m.lobbyEnd = now + CasinoConfig.pvp().lobbyTimeoutTicks;
			m.lastJoin = now;
			persist(s, m);
			startCountdown(s, m, now, CasinoConfig.pvp().wheel.countdownTicks);
		}
	}

	// =====================================================================================================
	// taunts (§3.9)
	// =====================================================================================================

	@Override
	public Result<Void> taunt(ServerPlayer player, int line) {
		MinecraftServer s = server(player);
		if (!Taunts.valid(line)) {
			return fail("gui.burmaldaholic.error.invalid_amount");
		}
		PvpMatch m = null;
		for (PvpMatch x : matches.values()) {
			if (participantOf(x, player.getUUID()) != null && x.phase != Phase.INVITE && x.phase != Phase.HISTORY && x.phase != Phase.CLOSED) {
				m = x;
			}
		}
		if (m == null) {
			return fail("gui.burmaldaholic.pvp.error.lobby_gone");
		}
		Participant p = participantOf(m, player.getUUID());
		long now = now(s);
		var t = CasinoConfig.pvp().taunts;
		String err = Taunts.check(t.enabled, p.taunts(), t.maxPerMatch, p.lastTaunt, now, t.cooldownTicks);
		if (err != null) {
			long secs = Math.max(1, (p.lastTaunt + t.cooldownTicks - now + 19) / 20);
			return Result.fail("gui.burmaldaholic.error.cooldown".equals(err)
				? Component.translatable(err, Texts.plural("unit.burmaldaholic.second", secs)) : Component.translatable(err));
		}
		sayTaunt(s, m, p, line, now);
		return new Result<>(null, null);
	}

	private void sayTaunt(MinecraftServer s, PvpMatch m, Participant p, int line, long now) {
		var t = CasinoConfig.pvp().taunts;
		if (Taunts.check(t.enabled, p.taunts(), t.maxPerMatch, p.lastTaunt, now, t.cooldownTicks) != null) {
			return;
		}
		p.countTaunt();
		p.lastTaunt = now;
		tell(s, m, Component.translatable("msg.burmaldaholic.pvp.taunt.say", PvpText.name(p, mode(m)), Component.translatable(Taunts.key(line))), true);
		presenter().sound(m, Taunts.friendly(line) ? "minecraft:entity.villager.yes" : "minecraft:entity.villager.no", p.index);
	}

	// =====================================================================================================
	// queries, settings, admin
	// =====================================================================================================

	@Override
	public Optional<PvpMatch> matchOf(UUID player) {
		PvpMatch best = null;
		for (PvpMatch m : matches.values()) {
			if (participantOf(m, player) != null && m.phase != Phase.CLOSED) {
				if (best == null || m.phase != Phase.HISTORY) {
					best = m;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	@Override
	public Optional<PvpMatch> get(String matchId) {
		return Optional.ofNullable(matches.get(matchId));
	}

	@Override
	public List<PvpMatch> lobbiesNear(ServerPlayer player, @Nullable String mode) {
		List<PvpMatch> out = new ArrayList<>();
		double r = CasinoConfig.pvp().joinRadius;
		for (PvpMatch m : matches.values()) {
			if (m.phase != Phase.LOBBY || (mode != null && !mode.equals(m.mode)) || m.seating.policy() == SeatPolicy.BOTS_ONLY) {
				continue;
			}
			if (m.inviteOnly && participantOf(m, player.getUUID()) == null && !m.guests.contains(player.getUUID())) {
				continue;
			}
			if (!player.level().dimension().equals(m.anchor.dimension())
					|| player.position().distanceTo(Vec3.atCenterOf(m.anchor.pos())) > r) {
				continue;
			}
			out.add(m);
		}
		return out;
	}

	@Override
	public List<PvpMatch> invitesFor(UUID player) {
		List<PvpMatch> out = new ArrayList<>();
		for (PvpMatch m : matches.values()) {
			if (m.phase == Phase.INVITE && player.equals(m.invitee)) {
				out.add(m);
			}
		}
		return out;
	}

	@Override
	public HeadToHead record(UUID a, UUID b) {
		return server == null ? HeadToHead.EMPTY : rec(server, a).vs(b);
	}

	@Override
	public List<Rival> rivals(UUID player, int max) {
		if (server == null) {
			return List.of();
		}
		List<Rival> out = new ArrayList<>();
		for (Map.Entry<UUID, PvpPlayerRecord.Rival> e : rec(server, player).rivals().entrySet()) {
			if (out.size() >= max) {
				break;
			}
			out.add(new Rival(e.getKey(), e.getValue().name(), e.getValue().record()));
		}
		return out;
	}

	@Override
	public Stats stats(UUID player) {
		if (server == null) {
			return new Stats(0, 0, 0, 0, null);
		}
		PvpPlayerRecord r = rec(server, player);
		return new Stats(r.wins, r.losses, r.net, r.streak, r.nemesis());
	}

	@Override
	public boolean acceptsInvites(UUID player) {
		return server == null || rec(server, player).acceptInvites;
	}

	@Override
	public void setAcceptInvites(UUID player, boolean accept) {
		if (server != null) {
			PvpPlayerRecord r = rec(server, player);
			r.acceptInvites = accept;
			saveRec(server, player, r);
		}
	}

	@Override
	public List<PvpMatch> all() {
		return new ArrayList<>(matches.values());
	}

	@Override
	public void cancel(String matchId) {
		PvpMatch m = matches.get(matchId);
		MinecraftServer s = server;
		if (m == null || s == null) {
			return;
		}
		switch (m.phase) {
			case INVITE -> {
				notifyInviteEnd(s, m, "msg.burmaldaholic.pvp.invite.withdrawn");
				close(s, m, MatchState.CANCELLED);
			}
			case LOBBY, NO_MORE_BETS -> cancelLobby(s, m);
			case REVEAL -> settle(s, m, Reason.ADMIN);
			case OFFER_LOSER, OFFER_WINNER, REMATCH -> m.phase = Phase.HISTORY;
			default -> {
			}
		}
	}

	// =====================================================================================================
	// eligibility (§3.2, BOTS.md §5.5)
	// =====================================================================================================

	enum Role {
		/** Creates an invite: rules 1–9 + pending invites (nothing escrowed yet). */
		CHALLENGER,
		/** Invite target: distance from the challenger, accepts challenges, decline cooldown; errors name the target. */
		TARGET,
		/** Money moves now for this player (accept / lobby create / rematch / chain link). */
		MONEY,
		/** The other duelist at accept (errors name them). */
		MONEY_OTHER,
		JOINER,
		TOP_UP
	}

	/**
	 * First failing rule as a translated error, or null.
	 *
	 * @param challenger the challenger (TARGET role only)
	 * @param botsTakePart house bots take part (sulking players are refused)
	 * @param onlyHouseBots every counterparty is a house-funded bot (debtor exception)
	 */
	private @Nullable Component eligibility(ServerPlayer p, PvpMode<?, ?> md, PvpMatch m, @Nullable OwnedTable owned, long stake, Role role,
			@Nullable ServerPlayer challenger, boolean botsTakePart, boolean onlyHouseBots) {
		MinecraftServer s = server(p);
		PvpConfig c = CasinoConfig.pvp();
		UUID id = p.getUUID();
		GlobalPos from = role == Role.TARGET && challenger != null ? GlobalPos.of(challenger.level().dimension(), challenger.blockPosition()) : m.anchor;
		boolean sameDim = p.level().dimension().equals(from.dimension());
		double dist = sameDim ? p.position().distanceTo(Vec3.atCenterOf(from.pos())) : Double.MAX_VALUE;
		boolean checkDistance = role == Role.TARGET || role == Role.JOINER || (role == Role.MONEY && m.anchorKind != AnchorKind.NONE && !m.duel);
		long tierMax = CoreServices.vip().maxBet(s, id);
		long balance = Economies.get().balance(p);
		boolean owes = CoreServices.debt().owed(s, id) > 0 || CoreServices.debt().inDefault(s, id);
		boolean houseBots = botsTakePart && m.bankroll.isEmpty();
		int pending = 0;
		if (role == Role.CHALLENGER) {
			for (PvpMatch x : matches.values()) {
				if (x.phase == Phase.INVITE && id.equals(x.host)) {
					pending++;
				}
			}
		}
		boolean declinedRecently = role == Role.TARGET && challenger != null
			&& declined.getOrDefault(id + "|" + challenger.getUUID(), Long.MIN_VALUE) > now(s);
		Eligibility.Facts f = new Eligibility.Facts(CasinoMode.isEnabled(p), c.enabled && md.enabled(), p.isSpectator(), owes, false,
			onlyHouseBots && m.bankroll.isEmpty(), CasinoConfig.bots().debtorsMayPlay, isBusy(s, id, m), sameDim || !checkDistance,
			checkDistance ? dist : 0, c.joinRadius, role == Role.TOP_UP ? 0 : stake, c.minStake, tierMax, balance, false, true,
			owned == null || owned.open(), owned != null && owned.owner().equals(id),
			role != Role.TARGET || rec(s, id).acceptInvites, declinedRecently, pending >= c.maxPendingInvites,
			houseBots && BotLedger.sulking(s, id));
		String key = Eligibility.check(f);
		if (key == null && role == Role.TOP_UP && stake > balance) {
			key = "gui.burmaldaholic.error.insufficient_funds";
		}
		if (key == null) {
			BlockPos table = m.anchorKind != AnchorKind.NONE && sameDim ? m.anchor.pos() : null;
			Component veto = Wagers.check(p, new WagerVeto.Context(GAME, Stake.Kind.CHIPS, table, true));
			return veto;
		}
		return error(key, p, role, s, tierMax, balance, c);
	}

	private Component error(String key, ServerPlayer p, Role role, MinecraftServer s, long tierMax, long balance, PvpConfig c) {
		boolean other = role == Role.TARGET || role == Role.MONEY_OTHER;
		Component name = p.getDisplayName();
		Component radius = Texts.plural("unit.burmaldaholic.block", c.joinRadius);
		return switch (key) {
			case "gui.burmaldaholic.pvp.error.debt", "gui.burmaldaholic.pvp.error.spectator" -> other
				? Component.translatable("gui.burmaldaholic.pvp.error.target_unavailable", name) : Component.translatable(key);
			case "gui.burmaldaholic.error.busy" -> other ? Component.translatable("gui.burmaldaholic.pvp.error.busy_target", name) : Component.translatable(key);
			case "gui.burmaldaholic.pvp.error.too_far" -> other ? Component.translatable(key, name, radius)
				: Component.translatable("gui.burmaldaholic.pvp.error.too_far_anchor", radius);
			case "gui.burmaldaholic.pvp.error.other_dimension" -> Component.translatable(key, name);
			case "gui.burmaldaholic.pvp.error.stake_min" -> Component.translatable(key, Texts.chips(c.minStake));
			case "gui.burmaldaholic.error.bet_too_high" -> other
				? Component.translatable("gui.burmaldaholic.pvp.error.over_target_max", name, Texts.chips(tierMax))
				: Component.translatable(key, Texts.chips(tierMax), dev.nezo.burmaldaholic.core.service.VipTiers.name(CoreServices.vip().tier(s, p.getUUID())));
			case "gui.burmaldaholic.error.insufficient_funds" -> other
				? Component.translatable("gui.burmaldaholic.pvp.error.cant_afford_target", name) : Component.translatable(key, Texts.chips(balance));
			case "gui.burmaldaholic.pvp.error.no_invites", "gui.burmaldaholic.pvp.error.cooldown_target" -> Component.translatable(key, name);
			case "gui.burmaldaholic.bots.error.capped" -> {
				long left = 24000 - Math.floorMod(now(s), 24000L);
				yield Component.translatable(key, Texts.plural("unit.burmaldaholic.minute", Math.max(1, left / 1200)));
			}
			default -> Component.translatable(key);
		};
	}

	/** In another live match or lobby (not the rematch window / history), or busy per a registered check. */
	private boolean isBusy(MinecraftServer s, UUID id, PvpMatch self) {
		for (PvpMatch m : matches.values()) {
			if (m == self || participantOf(m, id) == null) {
				continue;
			}
			if (m.phase == Phase.LOBBY || m.phase == Phase.NO_MORE_BETS || m.phase == Phase.REVEAL || m.state == MatchState.STARTING) {
				return true;
			}
			if ((m.phase == Phase.OFFER_LOSER || m.phase == Phase.OFFER_WINNER) && (self == null || !self.chainOf.equals(m.chainOf.isEmpty() ? m.id : m.chainOf))) {
				return true;
			}
		}
		for (BiPredicate<MinecraftServer, UUID> check : busyChecks) {
			try {
				if (check.test(s, id)) {
					return true;
				}
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP busy check failed", e);
			}
		}
		return false;
	}

	// =====================================================================================================
	// money
	// =====================================================================================================

	/**
	 * Escrows {@code ps} in ONE batch: humans debited, bankroll-bot stakes from the bankroll, all credited to the
	 * bank; bank bots move nothing. Sets ALL-IN for humans left with 0.
	 */
	private boolean escrow(MinecraftServer s, PvpMatch m, List<Participant> ps) {
		Economy.Batch batch = Economies.get().batch(s);
		long toHouse = 0;
		Map<String, Long> fromBankroll = new LinkedHashMap<>();
		for (Participant p : ps) {
			UUID h = p.humanId();
			if (h != null) {
				batch.debit(AccountId.player(h), p.stake());
				toHouse += p.stake();
			} else if (p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL) {
				fromBankroll.merge(b.purse().bankrollId(), p.stake(), Long::sum);
				toHouse += p.stake();
			}
		}
		if (toHouse == 0) {
			return true;
		}
		fromBankroll.forEach((id, amount) -> batch.debit(AccountId.bankroll(id), amount));
		batch.credit(AccountId.HOUSE, toHouse);
		if (!batch.commit(Transaction.bet(GAME)).ok()) {
			return false;
		}
		for (Participant p : ps) {
			UUID h = p.humanId();
			if (h != null && Economies.get().balance(s, h) == 0 && p.stake() > 0) {
				p.setAllIn(true);
				ServerPlayer sp = online(s, h);
				tell(s, m, Component.translatable("msg.burmaldaholic.pvp.match.all_in", sp != null ? sp.getDisplayName() : Texts.raw(p.occupant.name()))
					.withStyle(ChatFormatting.GOLD), false);
			}
		}
		return true;
	}

	/** Returns one entry from the bank escrow (humans, bankroll bots; bank bots: nothing moves). */
	private void refund(MinecraftServer s, PvpMatch m, Participant p, boolean note, boolean casinoOff) {
		UUID h = p.humanId();
		AccountId to = h != null ? AccountId.player(h)
			: p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL ? AccountId.bankroll(b.purse().bankrollId()) : null;
		if (to == null || p.stake() <= 0) {
			return;
		}
		Economies.get().batch(s).debit(AccountId.HOUSE, p.stake()).credit(to, p.stake()).commit(Transaction.refund(GAME));
		if (h != null) {
			ServerPlayer sp = online(s, h);
			if (sp != null && !note) {
				sp.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.lobby.refunded", Texts.chips(p.stake())).withStyle(ChatFormatting.GRAY));
			} else {
				note(s, h, new PvpPlayerRecord.Note("refunded", m.mode, p.stake()));
			}
		}
	}

	/** Refunds every entry in ONE batch. */
	private void refundAll(MinecraftServer s, PvpMatch m, boolean note, boolean casinoOff) {
		Economy.Batch batch = Economies.get().batch(s);
		long total = 0;
		for (Participant p : m.participants) {
			UUID h = p.humanId();
			if (h != null) {
				batch.credit(AccountId.player(h), p.stake());
				total += p.stake();
			} else if (p.occupant instanceof SeatOccupant.Bot b && b.purse().kind() == Purse.Kind.BANKROLL) {
				batch.credit(AccountId.bankroll(b.purse().bankrollId()), p.stake());
				total += p.stake();
			}
		}
		if (total > 0) {
			batch.debit(AccountId.HOUSE, total).commit(Transaction.refund(GAME));
		}
		for (Participant p : m.participants) {
			UUID h = p.humanId();
			if (h == null || p.stake() <= 0) {
				continue;
			}
			ServerPlayer sp = online(s, h);
			if (sp != null && !note) {
				sp.sendSystemMessage(Component.translatable("msg.burmaldaholic.pvp.lobby.refunded", Texts.chips(p.stake())).withStyle(ChatFormatting.GRAY));
			} else {
				note(s, h, new PvpPlayerRecord.Note("refunded", m.mode, p.stake()));
			}
		}
		for (Participant p : m.participants) {
			p.addStake(-p.stake());
		}
	}

	// =====================================================================================================
	// helpers
	// =====================================================================================================

	/** Gives the match's bots back to the world budget. */
	private static void releaseBots(PvpMatch m) {
		if (m.botsAcquired > 0) {
			Bots.release(m.botsAcquired);
			m.botsAcquired = 0;
		}
	}

	private void close(MinecraftServer s, PvpMatch m, MatchState state) {
		releaseBots(m);
		m.state = state;
		m.phase = Phase.CLOSED;
		matches.remove(m.id);
		chatter.remove(m.id);
		PvpMatchData.get(s).remove(m.id);
		presenter().lobbyChanged(m);
	}

	private void persist(MinecraftServer s, PvpMatch m) {
		if (m.state.persisted()) {
			PvpMatchData.get(s).put(m.id, MatchJson.encode(m));
		} else {
			PvpMatchData.get(s).remove(m.id);
		}
	}

	private void renumber(PvpMatch m, Participant removed) {
		List<Participant> rest = new ArrayList<>();
		for (Participant p : m.participants) {
			if (p != removed) {
				Participant q = new Participant(rest.size(), p.occupant, p.stake());
				q.setAllIn(p.allIn());
				q.botActAt = p.botActAt;
				rest.add(q);
			}
		}
		m.participants.clear();
		m.participants.addAll(rest);
	}

	private static List<Settlement.Seat> seats(PvpMatch m) {
		List<Settlement.Seat> out = new ArrayList<>();
		for (Participant p : m.participants) {
			if (p.occupant instanceof SeatOccupant.Bot b) {
				out.add(b.purse().kind() == Purse.Kind.BANKROLL ? Settlement.Seat.bankrollBot(p.stake()) : Settlement.Seat.bankBot(p.stake()));
			} else {
				out.add(Settlement.Seat.human(p.stake()));
			}
		}
		return out;
	}

	private static long[] stakes(PvpMatch m) {
		return m.participants.stream().mapToLong(Participant::stake).toArray();
	}

	private static @Nullable Participant participantOf(PvpMatch m, UUID id) {
		String key = id.toString();
		for (Participant p : m.participants) {
			if (p.occupant.key().equals(key)) {
				return p;
			}
		}
		return null;
	}

	private static String nameIn(PvpMatch m, UUID id) {
		Participant p = participantOf(m, id);
		return p == null ? "?" : p.occupant.name();
	}

	private Component hostName(MinecraftServer s, PvpMatch m) {
		ServerPlayer h = online(s, m.host);
		return h != null ? h.getDisplayName() : Texts.raw(m.host == null ? "?" : nameIn(m, m.host));
	}

	private static SeatOccupant.Human human(ServerPlayer p) {
		return new SeatOccupant.Human(p.getUUID(), p.getName().getString());
	}

	private static @Nullable PvpMode<?, ?> mode(PvpMatch m) {
		return PvpModes.get(m.mode).orElse(null);
	}

	private static boolean equalStakes(PvpMatch m) {
		PvpMode<?, ?> md = mode(m);
		return md == null || md.equalStakes();
	}

	private static int maxPlayers(PvpMode<?, ?> md) {
		return Math.max(2, md.maxPlayers());
	}

	/** Wheel Party cap C from the params ({@code "cap"}), else {@code fallback}. */
	private static long cap(PvpMatch m, long fallback) {
		PvpMode<?, ?> md = mode(m);
		long modeCap = md == null ? Long.MAX_VALUE : ModeCalls.stakeCap(md, m.params);
		if (modeCap != Long.MAX_VALUE) {
			return modeCap;
		}
		if (m.params != null && m.params.isJsonObject() && m.params.getAsJsonObject().has("cap")) {
			try {
				return m.params.getAsJsonObject().get("cap").getAsLong();
			} catch (RuntimeException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	private @Nullable OwnedTable owned(MinecraftServer s, PvpMatch m) {
		if (m.anchorKind == AnchorKind.NONE) {
			return null;
		}
		ServerLevel level = s.getLevel(m.anchor.dimension());
		return level == null ? null : CoreServices.tableOwnership().owner(level, m.anchor.pos()).orElse(null);
	}

	private BotRng rng(PvpMatch m) {
		if (m.botRng == null) {
			m.botRng = Bots.newRng();
		}
		return m.botRng;
	}

	private static @Nullable ServerPlayer online(MinecraftServer s, @Nullable UUID id) {
		if (id == null) {
			return null;
		}
		ServerPlayer p = s.getPlayerList().getPlayer(id);
		return p == null || p.hasDisconnected() ? null : p;
	}

	private List<ServerPlayer> onlineHumans(MinecraftServer s, PvpMatch m) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Participant p : m.participants) {
			ServerPlayer sp = online(s, p.humanId());
			if (sp != null) {
				out.add(sp);
			}
		}
		return out;
	}

	/** Players (not participants) within {@code radius} of the anchor. */
	private List<ServerPlayer> near(MinecraftServer s, PvpMatch m, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		Vec3 c = Vec3.atCenterOf(m.anchor.pos());
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (participantOf(m, p.getUUID()) == null && p.level().dimension().equals(m.anchor.dimension()) && p.position().distanceTo(c) <= radius) {
				out.add(p);
			}
		}
		return out;
	}

	private List<ServerPlayer> viewers(MinecraftServer s, PvpMatch m) {
		List<ServerPlayer> out = onlineHumans(s, m);
		out.addAll(near(s, m, CasinoConfig.pvp().announceRadius));
		return out;
	}

	/** Chat to the participants (and, with {@code radius}, spectators within {@code pvp.announceRadius}). */
	private void tell(MinecraftServer s, PvpMatch m, Component msg, boolean radius) {
		for (ServerPlayer p : radius ? viewers(s, m) : onlineHumans(s, m)) {
			p.sendSystemMessage(msg);
		}
	}

	private void note(MinecraftServer s, UUID id, PvpPlayerRecord.Note note) {
		PvpPlayerRecord r = rec(s, id);
		if (r.notes.size() < 32) {
			r.notes.add(note);
		}
		saveRec(s, id, r);
	}

	private static PvpPlayerRecord rec(MinecraftServer s, @Nullable UUID id) {
		return id == null ? new PvpPlayerRecord() : PvpPlayerRecord.fromJson(PvpRecordData.get(s).json(id));
	}

	private static void saveRec(MinecraftServer s, UUID id, PvpPlayerRecord r) {
		PvpRecordData.get(s).put(id, r.toJson());
	}

	private static PvpPresenter presenter() {
		return Pvp.presenter();
	}

	private static void fire(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP listener failed", e);
		}
	}

	private MinecraftServer server(ServerPlayer p) {
		MinecraftServer s = p.level().getServer();
		server = s;
		return s;
	}

	private static long now(MinecraftServer s) {
		return s.overworld().getGameTime();
	}

	private String newId() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		String id;
		do {
			StringBuilder b = new StringBuilder(8);
			for (int i = 0; i < 8; i++) {
				b.append(Character.forDigit(r.nextInt(36), 36));
			}
			id = b.toString();
		} while (matches.containsKey(id));
		return id;
	}

	/** Params with the mode's config snapshot (see {@link ModeCalls#normalize}); the input when the mode can't. */
	private static JsonElement snapshot(PvpMode<?, ?> md, JsonElement params) {
		try {
			return ModeCalls.normalize(md, params);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("PvP: mode {} could not normalize its params; kept as sent", md.id(), e);
			return params;
		}
	}

	private static <T> Result<T> fail(String key) {
		return Result.fail(Component.translatable(key));
	}

	/** Fair game randomness for tapes (never odds-adjusted, never the streak re-draw, never the bot rng). */
	static PvpRng fairRng() {
		CasinoRng r = OddsService.get().fair();
		return new PvpRng() {
			@Override
			public int nextInt(int bound) {
				return r.nextInt(bound);
			}

			@Override
			public long nextLong(long bound) {
				if (bound <= 0) {
					throw new IllegalArgumentException("bound must be positive");
				}
				if (bound <= Integer.MAX_VALUE) {
					return r.nextInt((int) bound);
				}
				long range = 1L << 60;
				long limit = range - range % bound;
				long bits;
				do {
					bits = ((long) r.nextInt(1 << 30) << 30) | r.nextInt(1 << 30);
				} while (bits >= limit);
				return bits % bound;
			}

			@Override
			public boolean nextBoolean() {
				return r.nextInt(2) == 1;
			}
		};
	}
}
