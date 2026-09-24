package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of the match JSON the pvp module sends to a mode screen ({@code PvpScreens.ModeScreen#update},
 * from {@code PvpMatchSyncPayload}). Every field is optional; missing ones read as empty / 0, so the screens
 * degrade instead of failing when the engine sends less. Expected shape (public state + revealed steps only):
 * <pre>
 * { "id", "mode", "state": "LOBBY|DRAWN|SETTLED|CANCELLED|CLOSED", "params": {mode params}, "you": index|-1,
 *   "participants": [{"index", "name", "bot", "level", "stake", "allIn", "host"}],   // bots: name = name key
 *   "steps": [{"kind", "ticks", "round", "data": {…}}],   // revealed so far, oldest first
 *   "pot", "rake", "rakeBp", "payouts": [..] (after SETTLE), "ticksLeft" (lobby / countdown timer),
 *   "decision": {"id": "coin.don_offer|coin.let_it_ride", "ticksLeft", "blocked": key|null, "blockedArg",
 *                "deficit", "called": 1|2} (a decision waiting for the viewer),
 *   "chain": {"link", "loser": index|-1, "deficit"} }
 * </pre>
 */
public final class MatchView {
	public record Seat(int index, Component name, boolean bot, String level, long stake, boolean allIn, boolean host) {}

	public record StepView(String kind, int ticks, int round, JsonObject data) {}

	public final JsonObject json;

	public MatchView(JsonObject json) {
		this.json = json == null ? new JsonObject() : json;
	}

	/** Seats as the plates draw them. */
	public List<dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat> seatsFull() {
		return dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat.all(json);
	}

	public String id() {
		return str(json, "id", "");
	}

	public String state() {
		return str(json, "state", "");
	}

	public boolean settled() {
		return "SETTLED".equals(state()) || "CLOSED".equals(state());
	}

	public boolean lobby() {
		return "LOBBY".equals(state());
	}

	public int you() {
		return integer(json, "you", -1);
	}

	public JsonObject params() {
		return obj(json, "params");
	}

	public long pot() {
		long pot = lng(json, "pot", -1);
		if (pot >= 0) {
			return pot;
		}
		long sum = 0;
		for (Seat s : seats()) {
			sum += s.stake();
		}
		return sum;
	}

	public long rake() {
		return lng(json, "rake", 0);
	}

	public int rakeBasisPoints() {
		return integer(json, "rakeBp", 300);
	}

	public int ticksLeft() {
		return integer(json, "ticksLeft", -1);
	}

	public List<Seat> seats() {
		List<Seat> out = new ArrayList<>();
		JsonArray arr = arr(json, "participants");
		for (int i = 0; i < arr.size(); i++) {
			JsonObject p = arr.get(i).isJsonObject() ? arr.get(i).getAsJsonObject() : new JsonObject();
			boolean bot = bool(p, "bot", false);
			String name = str(p, "name", "?");
			Component display = bot ? Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(name)) : Texts.raw(name);
			out.add(new Seat(integer(p, "index", i), display, bot, str(p, "level", ""), lng(p, "stake", 0), bool(p, "allIn", false),
				bool(p, "host", false)));
		}
		return out;
	}

	public @Nullable Seat seat(int index) {
		for (Seat s : seats()) {
			if (s.index() == index) {
				return s;
			}
		}
		return null;
	}

	public Component name(int index) {
		Seat s = seat(index);
		return s == null ? Texts.raw("?") : s.name();
	}

	public List<StepView> steps() {
		List<StepView> out = new ArrayList<>();
		JsonArray arr = arr(json, "steps");
		for (int i = 0; i < arr.size(); i++) {
			if (arr.get(i).isJsonObject()) {
				JsonObject s = arr.get(i).getAsJsonObject();
				out.add(new StepView(str(s, "kind", ""), integer(s, "ticks", 0), integer(s, "round", -1), obj(s, "data")));
			}
		}
		return out;
	}

	public @Nullable StepView step(String kind) {
		StepView found = null;
		for (StepView s : steps()) {
			if (s.kind().equals(kind)) {
				found = s;
			}
		}
		return found;
	}

	public long payout(int index) {
		JsonArray arr = arr(json, "payouts");
		return index >= 0 && index < arr.size() ? arr.get(index).getAsLong() : -1;
	}

	/** The decision waiting for the viewer (null = none). */
	public @Nullable JsonObject decision() {
		JsonObject d = obj(json, "decision");
		return d.has("id") ? d : null;
	}

	public JsonObject chain() {
		return obj(json, "chain");
	}

	// ---- tolerant JSON access ---------------------------------------------------------------------------

	public static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	public static JsonArray arr(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	public static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	public static long lng(JsonObject o, String key, long fallback) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	public static int integer(JsonObject o, String key, int fallback) {
		return (int) lng(o, key, fallback);
	}

	public static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
	}
}
