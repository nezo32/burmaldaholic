package dev.nezo.burmaldaholic.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpViewContributors;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import dev.nezo.burmaldaholic.pvp.logic.RevealOrder;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Builds the public JSON state of a match for one viewer (the payload contract of {@code PvpSyncPayload};
 * mode screens registered with {@code client.pvp.PvpScreens} receive the same object):
 *
 * <pre>
 * { "id", "mode", "state": "LOBBY|DRAWN|SETTLED|…" (MatchState name), "you": index | -1, "host": index | -1,
 *   "spectator": bool, "pot", "rake", "rakeBp": 300, "rakePercent": "3", "entry", "min", "max", "equalStakes", "grudge", "link", "chainOf",
 *   "policy": "humans_only|mixed|bots_only", "botsToFill", "ticksLeft" (lobby timer; -1 = none), "balance",
 *   "anchorKind", "anchorName": translation key of the anchor block ("" = none), "params": {mode params},
 *   "participants": [{ "index", "key", "name" (bots: name key), "bot", "level" (bots: easy|normal|hard), "tagKey", "stake", "allIn", "pressed", "rematch", "host",
 *                      "you", "wins", "losses" (viewer vs this human; absent otherwise) }],
 *   "steps": [{ "kind", "ticks", "round", "waitForAll", "data" }]   (revealed so far, in order),
 *   "final": place being revealed (-1 = not in the Final Reveal),
 *   "result": { "order": [...], "points": [...], "winners": [...], "payouts": [...], "places": [...] }, "payouts" (settled),
 *   + fields added by {@link PvpViewContributors} (engine / modes: "decision", "chain", countdown "ticksLeft", …) }
 * </pre>
 */
public final class PvpMatchView {
	private PvpMatchView() {}

	public static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static String modeKey(String mode) {
		return "gui.burmaldaholic.pvp.game." + mode;
	}

	public static int indexOf(PvpMatch m, UUID player) {
		String key = player.toString();
		for (Participant p : m.participants()) {
			if (p.occupant.key().equals(key)) {
				return p.index;
			}
		}
		return -1;
	}

	public static int hostIndex(PvpMatch m) {
		UUID host = m.host();
		return host == null ? -1 : indexOf(m, host);
	}

	public static @Nullable Participant participant(PvpMatch m, int index) {
		for (Participant p : m.participants()) {
			if (p.index == index) {
				return p;
			}
		}
		return null;
	}

	/** Bot tag shown after a bot's name: its Style where decisions exist (coin, wheel), none for luck-only modes. */
	public static String tagKey(PvpMatch m, Participant p) {
		if (!(p.occupant instanceof SeatOccupant.Bot bot)) {
			return "";
		}
		boolean decisions = PvpModes.get(m.mode).map(PvpMode::hasDecisions).orElse(false);
		return decisions ? bot.profile().level().styleKey() : "";
	}

	public static long entry(PvpMatch m) {
		int host = hostIndex(m);
		Participant p = participant(m, host >= 0 ? host : 0);
		return p == null ? 0 : p.stake();
	}

	public static int maxPlayers(PvpMatch m) {
		return PvpModes.get(m.mode).map(mode -> {
			try {
				return mode.maxPlayers();
			} catch (RuntimeException e) {
				return m.participants().size();
			}
		}).orElse(m.participants().size());
	}

	/** Seats the engine will fill with bots at the start (MIXED lobbies; BOTS.md §3.4). */
	public static int botsToFill(PvpMatch m) {
		if (m.seating.policy() != SeatPolicy.MIXED) {
			return 0;
		}
		int size = Math.min(maxPlayers(m), m.seating.count() + 1);
		return Math.max(0, size - m.participants().size());
	}

	public static long lobbyTicksLeft(MinecraftServer server, PvpMatch m) {
		if (m.state() == MatchState.LOBBY) {
			return PvpText.ticksLeft(m.createdTick, CasinoConfig.pvp().lobbyTimeoutTicks, now(server));
		}
		if (m.state() == MatchState.INVITED) {
			return PvpText.ticksLeft(m.createdTick, CasinoConfig.pvp().inviteTimeoutTicks, now(server));
		}
		return -1;
	}

	/** The anchor block's name key ("" for anchor-less matches or unloaded anchors). */
	public static String anchorNameKey(MinecraftServer server, PvpMatch m) {
		if (m.anchorKind == dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind.NONE) {
			return "";
		}
		ServerLevel level = server.getLevel(m.anchor.dimension());
		if (level == null || !level.isLoaded(m.anchor.pos())) {
			return "";
		}
		return level.getBlockState(m.anchor.pos()).getBlock().getDescriptionId();
	}

	/** Payouts per participant index: the settle event's, else recomputed like the engine (PvpMath.split). */
	public static long[] payouts(PvpMatch m, Outcome o) {
		long[] cached = PvpUi.cachedPayouts(m.id);
		int n = m.participants().size();
		if (cached != null && cached.length == n) {
			return cached;
		}
		return PvpMath.split(m.pot() - m.rake(), o.winners(), o.seatOrder(), n);
	}

	public static JsonObject build(MinecraftServer server, @Nullable ServerPlayer viewer, PvpMatch m) {
		JsonObject o = new JsonObject();
		o.addProperty("id", m.id);
		o.addProperty("mode", m.mode);
		o.addProperty("state", m.state().name());
		int you = viewer == null ? -1 : indexOf(m, viewer.getUUID());
		o.addProperty("you", you);
		o.addProperty("spectator", you < 0);
		o.addProperty("host", hostIndex(m));
		o.addProperty("pot", m.pot());
		o.addProperty("rake", m.rake());
		o.addProperty("rakeBp", CasinoConfig.pvp().rakeBasisPoints);
		o.addProperty("rakePercent", PvpText.percent(CasinoConfig.pvp().rakeBasisPoints));
		o.addProperty("entry", entry(m));
		o.addProperty("min", PvpModes.get(m.mode).map(PvpMode::minPlayers).orElse(2));
		o.addProperty("max", maxPlayers(m));
		o.addProperty("equalStakes", PvpModes.get(m.mode).map(PvpMode::equalStakes).orElse(true));
		o.addProperty("grudge", m.grudge());
		o.addProperty("link", m.link);
		o.addProperty("chainOf", m.chainOf);
		o.addProperty("policy", m.seating.policy().id());
		o.addProperty("botsToFill", botsToFill(m));
		o.addProperty("ticksLeft", lobbyTicksLeft(server, m));
		o.addProperty("balance", viewer == null ? 0 : Economies.get().balance(viewer));
		o.addProperty("anchorKind", m.anchorKind.name().toLowerCase(Locale.ROOT));
		o.addProperty("anchorName", anchorNameKey(server, m));
		o.add("params", m.params == null ? new JsonObject() : m.params.deepCopy());
		int host = hostIndex(m);
		JsonArray ps = new JsonArray();
		for (Participant p : m.participants()) {
			JsonObject j = new JsonObject();
			j.addProperty("index", p.index);
			j.addProperty("key", p.occupant.key());
			j.addProperty("name", p.occupant.name());
			j.addProperty("bot", p.isBot());
			j.addProperty("tagKey", tagKey(m, p));
			j.addProperty("level", p.occupant instanceof SeatOccupant.Bot b ? b.profile().level().id() : "");
			j.addProperty("stake", p.stake());
			j.addProperty("allIn", p.allIn());
			j.addProperty("pressed", p.pressed());
			j.addProperty("rematch", p.wantsRematch());
			j.addProperty("host", p.index == host);
			j.addProperty("you", p.index == you);
			if (viewer != null && p.occupant instanceof SeatOccupant.Human h && !h.id().equals(viewer.getUUID())) {
				HeadToHead r = Pvp.service().record(viewer.getUUID(), h.id());
				j.addProperty("wins", r.wins());
				j.addProperty("losses", r.losses());
			}
			ps.add(j);
		}
		o.add("participants", ps);
		JsonArray steps = new JsonArray();
		for (JsonObject s : PvpUi.steps(m.id)) {
			steps.add(s.deepCopy());
		}
		o.add("steps", steps);
		o.addProperty("final", PvpUi.finalPlace(m.id));
		// places the Final Reveal has shown so far (never ahead of the cue): seat, points and that seat's outcome
		// events (the final Plinko bin / scratch cell of the place cue, extras-pvp.md §6.2, §8.2)
		JsonArray placings = new JsonArray();
		for (int place = m.participants().size(); place >= 1; place--) {
			var shown = m.placing(place);
			if (shown.isEmpty()) {
				continue;
			}
			JsonObject pl = new JsonObject();
			pl.addProperty("place", place);
			pl.addProperty("seat", shown.get().participant());
			pl.addProperty("points", shown.get().points());
			pl.add("events", events(m.revealedEvents(place)));
			placings.add(pl);
		}
		o.add("placings", placings);
		JsonArray taunts = new JsonArray();
		for (long[] t : PvpUi.taunts(m.id, now(server))) {
			JsonObject tj = new JsonObject();
			tj.addProperty("seat", t[0]);
			tj.addProperty("line", t[1]);
			tj.addProperty("age", t[2]);
			taunts.add(tj);
		}
		o.add("taunts", taunts);
		Outcome out = m.outcome();
		if (out != null) {
			JsonObject r = new JsonObject();
			r.add("order", ints(out.rankOrder()));
			r.add("points", longs(out.points()));
			r.add("winners", ints(out.winners()));
			r.add("payouts", longs(payouts(m, out)));
			o.add("payouts", longs(payouts(m, out)));
			r.add("places", ints(RevealOrder.places(out.rankOrder(), out.points(), out.winners())));
			o.add("result", r);
			// the same outcome in the shape the mode screens read (games.*.client.pvp: PvpModeView / MatchView)
			JsonObject oc = new JsonObject();
			oc.add("points", longs(out.points()));
			oc.add("rankOrder", ints(out.rankOrder()));
			oc.add("winners", ints(out.winners()));
			oc.add("seatOrder", ints(out.seatOrder()));
			oc.add("events", events(out.events()));
			o.add("outcome", oc);
			o.add("points", longs(out.points()));
		}
		PvpViewContributors.apply(m, viewer, o);
		return o;
	}

	static JsonArray events(List<dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent> list) {
		JsonArray events = new JsonArray();
		for (var e : list) {
			JsonObject ev = new JsonObject();
			ev.addProperty("kind", e.kind());
			ev.addProperty("seat", e.seat());
			ev.addProperty("round", e.round());
			JsonObject d = new JsonObject();
			e.data().forEach(d::addProperty);
			ev.add("data", d);
			events.add(ev);
		}
		return events;
	}

	static JsonArray ints(int[] a) {
		JsonArray j = new JsonArray();
		for (int v : a) {
			j.add(v);
		}
		return j;
	}

	static JsonArray longs(long[] a) {
		JsonArray j = new JsonArray();
		for (long v : a) {
			j.add(v);
		}
		return j;
	}

	/** Online human participants. */
	public static List<ServerPlayer> onlineHumans(MinecraftServer server, PvpMatch m) {
		return m.participants().stream()
			.filter(p -> p.occupant instanceof SeatOccupant.Human)
			.map(p -> server.getPlayerList().getPlayer(((SeatOccupant.Human) p.occupant).id()))
			.filter(p -> p != null && !p.hasDisconnected())
			.toList();
	}

	/** Display name of a participant as a component (players: name; bots: tagged translated name). */
	public static net.minecraft.network.chat.Component name(PvpMatch m, Participant p) {
		if (p.occupant instanceof SeatOccupant.Bot) {
			net.minecraft.network.chat.Component n = net.minecraft.network.chat.Component.translatable("gui.burmaldaholic.bots.display",
				net.minecraft.network.chat.Component.translatable(p.occupant.name()));
			String tag = tagKey(m, p);
			return tag.isEmpty() ? n : net.minecraft.network.chat.Component.translatable("gui.burmaldaholic.pvp.bots.tagged", n,
				net.minecraft.network.chat.Component.translatable(tag));
		}
		return dev.nezo.burmaldaholic.core.text.Texts.raw(p.occupant.name());
	}
}
