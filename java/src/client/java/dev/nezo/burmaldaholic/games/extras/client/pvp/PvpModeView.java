package dev.nezo.burmaldaholic.games.extras.client.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Client model of one PvP match as the extras mode screens (Plinko Battle, Scratch Showdown) read it from
 * the pvp module's match sync ({@code client.pvp.PvpScreens.ModeScreen#update}). The sync carries only
 * public state and revealed steps (tape confidentiality, PVP.md §3.6). Expected fields (aliases accepted):
 * <pre>
 * { "id": "k3j9x0aa", "mode": "plinko", "state": "DRAWN" | "SETTLED",
 *   "you": 0,                                     // viewer's participant index, -1 = spectator ("self")
 *   "pot": 600, "params": { …mode params… },
 *   "participants": [ {"name": "Alex", "bot": false, "allIn": false}, {"name": "gui.burmaldaholic.bots.name.creeper42", "bot": true, "level": "hard"} ],
 *   "steps": [ {"kind", "ticks", "round", "waitForAll", "data"} … ]   // every revealed step so far, or
 *   "step": {…}                                   // only the newest one (appended here)
 *   "outcome": {"points": [], "rankOrder": [], "winners": [], "events": [{"kind", "seat", "round", "data"}]},  // SETTLED only
 *   "payouts": [] }
 * </pre>
 */
public final class PvpModeView {
	public record Seat(Component name, boolean bot, boolean allIn) {}

	/** A revealed step and the client tick it arrived at (animations run from there). */
	public record StepView(String kind, int ticks, int round, boolean waitForAll, JsonObject data, int arrived) {}

	private final List<Seat> seats = new ArrayList<>();
	private final List<StepView> steps = new ArrayList<>();
	private JsonObject params = new JsonObject();
	private @Nullable JsonObject outcome;
	private int outcomeArrived;
	private int you = -1;
	private long pot;
	private String id = "";
	private JsonObject raw = new JsonObject();

	/** The last match view as received (plates, taunts, placings read it). */
	public JsonObject raw() {
		return raw;
	}

	public boolean grudge() {
		return raw.has("grudge") && raw.get("grudge").isJsonPrimitive() && raw.get("grudge").getAsBoolean();
	}

	/** Seats as the plates draw them. */
	public List<dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat> plateSeats() {
		return dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat.all(raw);
	}

	/** Final Reveal placings shown so far: {place, seat, points, events[]}. */
	public List<JsonObject> placings() {
		List<JsonObject> out = new ArrayList<>();
		if (raw.has("placings") && raw.get("placings").isJsonArray()) {
			for (JsonElement e : raw.getAsJsonArray("placings")) {
				if (e.isJsonObject()) out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	/** The revealed placing of a seat, or null. */
	public @Nullable JsonObject placing(int seat) {
		for (JsonObject p : placings()) {
			if (p.has("seat") && p.get("seat").getAsInt() == seat) return p;
		}
		return null;
	}

	/** An outcome event of one kind for a seat, from its revealed placing (Final Reveal cue) or the settled outcome. */
	public @Nullable JsonObject finalEvent(String kind, int seat) {
		JsonObject p = placing(seat);
		if (p != null && p.has("events") && p.get("events").isJsonArray()) {
			for (JsonElement e : p.getAsJsonArray("events")) {
				JsonObject o = e.getAsJsonObject();
				if (kind.equals(str(o, "kind", "")) && seat(o) == seat) return o;
			}
		}
		for (JsonObject o : events(kind)) {
			if (seat(o) == seat) return o;
		}
		return null;
	}

	public void update(JsonObject state, int now) {
		raw = state;
		id = str(state, "id", id);
		you = state.has("you") ? state.get("you").getAsInt() : state.has("self") ? state.get("self").getAsInt() : you;
		if (state.has("pot")) {
			pot = state.get("pot").getAsLong();
		}
		if (state.has("params") && state.get("params").isJsonObject()) {
			params = state.getAsJsonObject("params");
		}
		JsonArray ps = state.has("participants") ? state.getAsJsonArray("participants") : state.has("seats") ? state.getAsJsonArray("seats") : null;
		if (ps != null) {
			seats.clear();
			for (JsonElement e : ps) {
				JsonObject p = e.getAsJsonObject();
				boolean bot = p.has("bot") && p.get("bot").getAsBoolean();
				String name = str(p, "name", "?");
				Component shown = bot ? Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(name)) : Texts.raw(name);
				seats.add(new Seat(shown, bot, p.has("allIn") && p.get("allIn").getAsBoolean()));
			}
		}
		if (state.has("steps")) {
			JsonArray arr = state.getAsJsonArray("steps");
			for (int i = 0; i < arr.size(); i++) {
				StepView s = parse(arr.get(i).getAsJsonObject(), now);
				if (i < steps.size()) {
					StepView old = steps.get(i);
					if (!old.kind().equals(s.kind()) || old.round() != s.round()) {
						steps.set(i, s);
					}
				} else {
					steps.add(s);
				}
			}
		} else if (state.has("step") && state.get("step").isJsonObject()) {
			StepView s = parse(state.getAsJsonObject("step"), now);
			StepView last = last();
			if (last == null || !last.kind().equals(s.kind()) || last.round() != s.round()) {
				steps.add(s);
			}
		}
		if (outcome == null && state.has("outcome") && state.get("outcome").isJsonObject()) {
			outcome = state.getAsJsonObject("outcome");
			outcomeArrived = now;
		}
	}

	private static StepView parse(JsonObject o, int now) {
		JsonObject data = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : new JsonObject();
		boolean wait = o.has("waitForAll") ? o.get("waitForAll").getAsBoolean() : o.has("wait") && o.get("wait").getAsBoolean();
		return new StepView(str(o, "kind", ""), o.has("ticks") ? o.get("ticks").getAsInt() : 0, o.has("round") ? o.get("round").getAsInt() : -1,
			wait, data, now);
	}

	public static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	public String id() {
		return id;
	}

	public int you() {
		return you;
	}

	public long pot() {
		return pot;
	}

	public JsonObject params() {
		return params;
	}

	public List<Seat> seats() {
		return seats;
	}

	public int size() {
		return seats.size();
	}

	public Component name(int seat) {
		return seat >= 0 && seat < seats.size() ? seats.get(seat).name() : Texts.raw("?");
	}

	public List<StepView> steps() {
		return steps;
	}

	public @Nullable StepView last() {
		return steps.isEmpty() ? null : steps.get(steps.size() - 1);
	}

	public @Nullable StepView lastOf(String kind) {
		for (int i = steps.size() - 1; i >= 0; i--) {
			if (steps.get(i).kind().equals(kind)) {
				return steps.get(i);
			}
		}
		return null;
	}

	public boolean settled() {
		return outcome != null;
	}

	public int outcomeArrived() {
		return outcomeArrived;
	}

	public long[] outcomePoints() {
		return outcome == null ? new long[0] : longs(outcome, "points");
	}

	public int[] winners() {
		return outcome == null ? new int[0] : ints(outcome, "winners");
	}

	public int[] rankOrder() {
		return outcome == null ? new int[0] : ints(outcome, "rankOrder");
	}

	/** Outcome events of one kind: {@code {kind, seat, round, data}}. */
	public List<JsonObject> events(String kind) {
		List<JsonObject> out = new ArrayList<>();
		if (outcome != null && outcome.has("events")) {
			for (JsonElement e : outcome.getAsJsonArray("events")) {
				JsonObject o = e.getAsJsonObject();
				if (kind.equals(str(o, "kind", ""))) {
					out.add(o);
				}
			}
		}
		return out;
	}

	public static long[] longs(JsonObject o, String key) {
		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return new long[0];
		}
		JsonArray a = o.getAsJsonArray(key);
		long[] out = new long[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsLong();
		}
		return out;
	}

	public static int[] ints(JsonObject o, String key) {
		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return new int[0];
		}
		JsonArray a = o.getAsJsonArray(key);
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	/** {@code data.<key>} of an event (numbers are stored under {@code data}). */
	public static long eventLong(JsonObject event, String key, long fallback) {
		JsonObject d = event.has("data") && event.get("data").isJsonObject() ? event.getAsJsonObject("data") : event;
		return d.has(key) ? d.get(key).getAsLong() : fallback;
	}

	public static int seat(JsonObject event) {
		return event.has("seat") ? event.get("seat").getAsInt() : -1;
	}
}
