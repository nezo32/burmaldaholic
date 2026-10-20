package dev.nezo.burmaldaholic.pvp;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.pvp.logic.Taunts;
import dev.nezo.burmaldaholic.pvp.net.PvpActionPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the PvP screens: keeps what each client was sent, pushes {@link PvpSyncPayload}s (from the
 * presenter hooks and, as a safety net that works whatever the engine calls, from a 10-tick scan), sends the
 * clickable invites, and executes {@link PvpActionPayload}s through {@link PvpService}. Server thread only.
 */
public final class PvpUi {
	/** What a player was last sent: match id, state, change signature, spectator view. */
	private record Sent(String id, MatchState state, String signature, boolean spectator) {}

	private static final Map<UUID, Sent> SENT = new ConcurrentHashMap<>();
	/** What a player was last sent of their OWN match (spectator ticker pushes of other matches do not count). */
	private static final Map<UUID, Sent> OWN = new ConcurrentHashMap<>();
	/**
	 * B1 (owner playtest 1): the views ({@code <match id>/<state>}) each player was already sent with {@code open = true}.
	 * The scan never opens one of them again (an explicit "show" still does).
	 */
	private static final Map<UUID, Set<String>> OPENED = new ConcurrentHashMap<>();
	private static final Map<String, List<JsonObject>> STEPS = new ConcurrentHashMap<>();
	private static final Map<String, long[]> PAYOUTS = new ConcurrentHashMap<>();
	private static final Map<String, Integer> FINAL = new ConcurrentHashMap<>();
	private static final Set<String> NOTIFIED = ConcurrentHashMap.newKeySet();
	/** Recent taunts per match: {seat, line, gameTime} (bubbles on the plates, extras-pvp.md §9.7). */
	private static final Map<String, List<long[]>> TAUNTS = new ConcurrentHashMap<>();
	/** Taunt bubbles stay this long on the plates. */
	static final int TAUNT_TICKS = 60;

	private PvpUi() {}

	// ---- state kept for the views ------------------------------------------------------------

	static List<JsonObject> steps(String matchId) {
		return STEPS.getOrDefault(matchId, List.of());
	}

	static void addStep(String matchId, Step step) {
		JsonObject j = new JsonObject();
		j.addProperty("kind", step.kind());
		j.addProperty("ticks", step.ticks());
		j.addProperty("round", step.round());
		j.addProperty("waitForAll", step.waitForAll());
		j.add("data", step.data() == null ? new JsonObject() : step.data().deepCopy());
		STEPS.computeIfAbsent(matchId, k -> new ArrayList<>()).add(j);
	}

	/** Taunts said within {@link #TAUNT_TICKS} of {@code now}: {seat, line, age ticks}. */
	static List<long[]> taunts(String matchId, long now) {
		List<long[]> out = new ArrayList<>();
		for (long[] t : TAUNTS.getOrDefault(matchId, List.of())) {
			if (now - t[2] < TAUNT_TICKS && now >= t[2]) {
				out.add(new long[] {t[0], t[1], now - t[2]});
			}
		}
		return out;
	}

	static void addTaunt(String matchId, int seat, int line, long now) {
		List<long[]> list = TAUNTS.computeIfAbsent(matchId, k -> new ArrayList<>());
		list.removeIf(t -> t[0] == seat || now - t[2] >= TAUNT_TICKS);
		list.add(new long[] {seat, line, now});
	}

	static int finalPlace(String matchId) {
		return FINAL.getOrDefault(matchId, -1);
	}

	static void setFinalPlace(String matchId, int place) {
		FINAL.put(matchId, place);
	}

	static long @Nullable [] cachedPayouts(String matchId) {
		return PAYOUTS.get(matchId);
	}

	static void cachePayouts(String matchId, long[] payouts) {
		PAYOUTS.put(matchId, payouts.clone());
	}

	/** A match started (fresh tape): forget stale reveal data of the id. */
	static void resetMatch(String matchId) {
		STEPS.remove(matchId);
		FINAL.remove(matchId);
		TAUNTS.remove(matchId);
		PAYOUTS.remove(matchId);
	}

	static void clearAll() {
		SENT.clear();
		OWN.clear();
		OPENED.clear();
		STEPS.clear();
		PAYOUTS.clear();
		FINAL.clear();
		NOTIFIED.clear();
		TAUNTS.clear();
	}

	// ---- sending -----------------------------------------------------------------------------

	static String kindOf(MatchState state) {
		return switch (state) {
			case LOBBY -> "lobby";
			case STARTING, DRAWN -> "match";
			case SETTLED -> "result";
			default -> "clear";
		};
	}

	private static String signature(PvpMatch m) {
		StringBuilder b = new StringBuilder(m.state().name()).append('|').append(m.host()).append('|').append(steps(m.id).size())
			.append('|').append(finalPlace(m.id)).append('|').append(m.grudge()).append('|')
			.append(TAUNTS.getOrDefault(m.id, List.of()).stream().mapToLong(t -> t[2]).max().orElse(0));
		for (Participant p : m.participants()) {
			b.append('|').append(p.occupant.key()).append(':').append(p.stake()).append(p.allIn() ? 'A' : '-').append(p.pressed() ? 'P' : '-')
				.append(p.wantsRematch() ? 'R' : '-');
		}
		return b.toString();
	}

	private static boolean canSend(ServerPlayer p) {
		return PvpSyncPayload.TYPE != null && !p.hasDisconnected() && ServerPlayNetworking.canSend(p, PvpSyncPayload.TYPE);
	}

	/**
	 * Sends the viewer's state of {@code m}; {@code open} makes the client open the matching screen.
	 *
	 * <p>B1 (owner playtest 1: a duel's result window re-opened on both players every ~2 minutes): a closed / cancelled
	 * match clears only the views of THAT match ({@link #closed}), and a participant is never sent a match that is not
	 * their current one ({@code matchOf}), e.g. an older duel leaving its rematch window while a newer one is shown.
	 */
	public static void push(ServerPlayer p, PvpMatch m, boolean open) {
		String kind = kindOf(m.state());
		if (kind.equals("clear")) {
			closed(p, m.id);
			return;
		}
		boolean spectator = PvpMatchView.indexOf(m, p.getUUID()) < 0;
		if (!spectator) {
			Optional<PvpMatch> current = Pvp.service().matchOf(p.getUUID()).filter(x -> x.state() != MatchState.INVITED);
			if (current.isPresent() && !current.get().id.equals(m.id)) {
				return;
			}
		}
		JsonObject view = PvpMatchView.build(p.level().getServer(), p, m);
		Sent sent = new Sent(m.id, m.state(), signature(m), spectator);
		SENT.put(p.getUUID(), sent);
		if (!spectator) {
			OWN.put(p.getUUID(), sent);
		}
		if (open) {
			OPENED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(viewKey(m));
		}
		if (canSend(p)) {
			ServerPlayNetworking.send(p, new PvpSyncPayload(kind, open, view.toString()));
		}
	}

	private static String viewKey(PvpMatch m) {
		return m.id + "/" + m.state().name();
	}

	/** This view of {@code m} was already opened for the player (B1: the scan never opens it again). */
	static boolean opened(UUID player, PvpMatch m) {
		Set<String> s = OPENED.get(player);
		return s != null && s.contains(viewKey(m));
	}

	/**
	 * Match {@code matchId} closed / was cancelled: forgets what the player was sent of THAT match only, and clears the
	 * client's PvP state only when it holds that match (B1: an expiring older duel or invite wiped the bookkeeping of the
	 * newer match, so the scan re-opened its result).
	 */
	static void closed(ServerPlayer p, String matchId) {
		UUID id = p.getUUID();
		Sent own = OWN.get(id);
		if (own != null && own.id().equals(matchId)) {
			OWN.remove(id);
		}
		Sent sent = SENT.get(id);
		if (sent != null && sent.id().equals(matchId)) {
			SENT.remove(id);
			if (canSend(p)) {
				ServerPlayNetworking.send(p, new PvpSyncPayload("clear", false, "{}"));
			}
		}
	}

	public static void pushAll(MinecraftServer server, PvpMatch m, boolean open) {
		for (ServerPlayer p : PvpMatchView.onlineHumans(server, m)) {
			push(p, m, open);
		}
	}

	public static void clear(ServerPlayer p) {
		SENT.remove(p.getUUID());
		OWN.remove(p.getUUID());
		if (canSend(p)) {
			ServerPlayNetworking.send(p, new PvpSyncPayload("clear", false, "{}"));
		}
	}

	/** Red line in the open PvP screen + action bar. */
	public static void error(ServerPlayer p, Component error) {
		p.sendOverlayMessage(error.copy().withStyle(ChatFormatting.RED));
		if (!canSend(p)) {
			return;
		}
		JsonObject o = new JsonObject();
		ComponentSerialization.CODEC.encodeStart(p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE), error).result()
			.ifPresent(j -> o.add("error", j));
		ServerPlayNetworking.send(p, new PvpSyncPayload("error", false, o.toString()));
	}

	// ---- titles, sounds, particles -----------------------------------------------------------

	static void title(ServerPlayer p, @Nullable Component title, @Nullable Component subtitle, int stay) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(5, stay, 10));
		if (subtitle != null) {
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
		p.connection.send(new ClientboundSetTitleTextPacket(title == null ? Component.empty() : title));
	}

	/** Plays a sound event id (vanilla or {@code burmaldaholic:…}) to one player at their position. */
	static void sound(ServerPlayer p, String soundId, float volume, float pitch) {
		Identifier id = Identifier.tryParse(soundId);
		if (id == null) {
			return;
		}
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.get(id).<Holder<SoundEvent>>map(h -> h)
			.orElseGet(() -> Holder.direct(SoundEvent.createVariableRangeEvent(id)));
		p.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), volume, pitch,
			p.level().getRandom().nextLong()));
	}

	static void particles(ServerLevel level, String particleId, double x, double y, double z, int count) {
		Identifier id = Identifier.tryParse(particleId);
		if (id == null) {
			return;
		}
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
		if (type instanceof SimpleParticleType simple) {
			level.sendParticles(simple, x, y, z, count, 0.5, 0.6, 0.5, 0.1);
		}
	}

	// ---- periodic scan -----------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		if (!CasinoMode.isEnabled(server)) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (SENT.containsKey(p.getUUID())) {
					clear(p);
				}
			}
			return;
		}
		PvpService pvp = Pvp.service();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Optional<PvpMatch> match = pvp.matchOf(p.getUUID()).filter(m -> m.state() != MatchState.INVITED);
			Sent prev = SENT.get(p.getUUID());
			if (match.isPresent()) {
				PvpMatch m = match.get();
				if (kindOf(m.state()).equals("clear")) {
					if (prev != null) {
						clear(p);
					}
				} else if (prev == null || prev.spectator() || !prev.id().equals(m.id) || !prev.signature().equals(signature(m))) {
					// "not seen yet" is about the player's OWN view of this match: a spectator ticker push of a match
					// revealing nearby must not re-open a result the player already closed (v0.1.2)
					Sent own = OWN.get(p.getUUID());
					boolean newView = own == null || !own.id().equals(m.id) || own.state() != m.state();
					// a lobby, a match or a result the player has not seen yet opens its screen; never one already opened
					// and never a match in its history (B1: after the rematch window nothing pops up by itself)
					boolean open = newView && !opened(p.getUUID(), m) && !"history".equals(m.phase()) && !"closed".equals(m.phase());
					push(p, m, open);
				}
			} else if (prev != null) {
				Optional<PvpMatch> watched = pvp.get(prev.id());
				if (!prev.spectator() || watched.isEmpty() || watched.get().state() != MatchState.DRAWN) {
					clear(p);
				}
			}
			for (PvpMatch invite : pvp.invitesFor(p.getUUID())) {
				notifyInvite(server, p, invite);
			}
		}
		if (server.getTickCount() % 400 == 0) {
			Set<String> live = new HashSet<>();
			for (PvpMatch m : pvp.all()) {
				live.add(m.id);
			}
			NOTIFIED.removeIf(k -> !live.contains(k.substring(0, k.indexOf('/'))));
			STEPS.keySet().retainAll(live);
			FINAL.keySet().retainAll(live);
			PAYOUTS.keySet().retainAll(live);
			OPENED.values().forEach(s -> s.removeIf(k -> !live.contains(k.substring(0, k.indexOf('/')))));
			OPENED.values().removeIf(Set::isEmpty);
		}
	}

	// ---- invites (PVP.md §3.3.1: chat line with clickable [Accept] / [Decline]) ----------------

	static @Nullable Participant challenger(PvpMatch m) {
		int host = PvpMatchView.hostIndex(m);
		Participant p = PvpMatchView.participant(m, host);
		return p != null ? p : m.participants().isEmpty() ? null : m.participants().getFirst();
	}

	/** The side the TARGET plays in a Coin Flip Duel invite (params {@code heads} = the challenger's side), else null. */
	static @Nullable Component targetSide(PvpMatch m) {
		String key = m.params instanceof JsonObject o ? PvpHubPage.sideKey(o, m.mode) : null;
		if (key != null && ((JsonObject) m.params).get(key).isJsonPrimitive()) {
			boolean challengerHeads = ((JsonObject) m.params).get(key).getAsBoolean();
			return Component.translatable(challengerHeads ? "gui.burmaldaholic.extras.coin.tails" : "gui.burmaldaholic.extras.coin.heads");
		}
		return null;
	}

	/** Sends {@code target} the invite of {@code m} once (presenter at creation; the periodic scan as a safety net). */
	static void notifyInvite(MinecraftServer server, ServerPlayer target, PvpMatch m) {
		if (NOTIFIED.add(m.id + "/" + target.getUUID())) {
			sendInvite(server, target, m);
		}
	}

	public static void sendInvite(MinecraftServer server, ServerPlayer target, PvpMatch m) {
		Participant from = challenger(m);
		if (from == null) {
			return;
		}
		Component name = PvpMatchView.name(m, from);
		Component game = Component.translatable(PvpMatchView.modeKey(m.mode));
		MutableComponent line = Component.translatable("msg.burmaldaholic.pvp.invite.received", name, game, Texts.chips(from.stake()))
			.withStyle(ChatFormatting.GOLD);
		Component side = targetSide(m);
		if (side != null) {
			line.append(Texts.raw(" ")).append(Component.translatable("gui.burmaldaholic.pvp.invite.side", side).withStyle(ChatFormatting.YELLOW));
		}
		if (from.occupant instanceof SeatOccupant.Human h) {
			HeadToHead r = Pvp.service().record(target.getUUID(), h.id());
			Component rec = r.wins() + r.losses() == 0
				? Component.translatable("gui.burmaldaholic.pvp.invite.record_none", name)
				: Component.translatable("gui.burmaldaholic.pvp.invite.record", name, Texts.number(r.wins()), Texts.number(r.losses()));
			line.append(Texts.raw(" ")).append(rec.copy().withStyle(ChatFormatting.GRAY));
		}
		line.append(Texts.raw(" ")).append(button("gui.burmaldaholic.pvp.invite.accept_button", "accept", m.id, ChatFormatting.GREEN));
		line.append(Texts.raw(" ")).append(button("gui.burmaldaholic.pvp.invite.decline_button", "decline", m.id, ChatFormatting.RED));
		target.sendSystemMessage(line);
		target.sendOverlayMessage(Component.translatable("msg.burmaldaholic.pvp.invite.received", name, game, Texts.chips(from.stake())));
		sound(target, "minecraft:item.goat_horn.sound.0", 0.6f, 1.0f);
	}

	private static Component button(String key, String verb, String matchId, ChatFormatting color) {
		String command = "/casino pvp " + verb + " " + matchId;
		return Component.translatable(key).withStyle(s -> s.withColor(color).withBold(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Texts.raw(command))));
	}

	// ---- actions from the screens ------------------------------------------------------------

	static void handle(ServerPlayer p, PvpActionPayload a) {
		PvpService pvp = Pvp.service();
		MinecraftServer server = p.level().getServer();
		Component error = null;
		String id = a.matchId();
		try {
			switch (a.action()) {
				case "start" -> error = err(pvp.start(p, id));
				case "fill_bots" -> pvp.fillWithBots(p, id);
				case "leave" -> pvp.leave(p);
				case "press" -> pvp.press(p);
				case "decide" -> {
					// arg = "<decision>#<seq>" (the question the screen was opened for, review wave 2 m2); without #seq
					// the engine drops the answer (no unchecked form)
					String arg = a.arg();
					int hash = arg.indexOf('#');
					long seq = -1;
					if (hash >= 0) {
						try {
							seq = Long.parseLong(arg.substring(hash + 1));
						} catch (NumberFormatException e) {
							seq = Long.MAX_VALUE; // never matches
						}
						arg = arg.substring(0, hash);
					}
					pvp.decide(p, arg, a.value(), id.isEmpty() ? null : id, seq);
				}
				case "taunt" -> error = Taunts.valid((int) a.value()) ? err(pvp.taunt(p, (int) a.value())) : null;
				case "rematch" -> pvp.rematch(p, id);
				case "accept" -> error = err(pvp.accept(p, id));
				case "decline" -> pvp.decline(p, id);
				case "withdraw" -> pvp.withdraw(p, id);
				case "join" -> error = err(pvp.join(p, id, Math.max(0, a.value())));
				case "top_up" -> error = err(pvp.topUp(p, id, Math.max(0, a.value())));
				case "show" -> {
					Optional<PvpMatch> m = pvp.matchOf(p.getUUID());
					if (m.isPresent()) {
						push(p, m.get(), true);
					} else {
						clear(p);
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PvP action {} failed", a.action(), e);
		}
		if (error != null) {
			error(p, error);
		}
		// refresh at once (the engine may or may not call lobbyChanged)
		pvp.matchOf(p.getUUID()).filter(m -> m.state() != MatchState.INVITED).ifPresent(m -> {
			boolean joined = a.action().equals("join") || a.action().equals("accept");
			if (joined) {
				pushAll(server, m, false);
			}
			push(p, m, joined);
		});
	}

	private static @Nullable Component err(Result<?> r) {
		return r == null || r.isOk() ? null : r.error();
	}

	/** Error text of an engine result for commands / pages. */
	static @Nullable Component errorOf(Result<?> r) {
		return err(r);
	}

}
