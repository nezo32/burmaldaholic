package dev.nezo.burmaldaholic.core.pvp.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's PvP record (pvp-bots.md §5.2, PVP.md §3.8): totals vs humans, the PvP win streak, the
 * "accept challenges" setting, up to {@link #MAX_RIVALS} head-to-head rows (most recent first, LRU), the
 * separate "vs bots" statistics and (Java only) notes to show on the next join. Mutable; JSON codec
 * shared with Bedrock's {@code burmaldaholic:pvp_record} (unknown fields are ignored).
 */
public final class PvpPlayerRecord {
	public static final int MAX_RIVALS = 50;

	public record Rival(String name, HeadToHead record) {}

	/** Offline note: {@code refunded} (amount) or {@code offline} / {@code casino_off} (mode, net). */
	public record Note(String type, String mode, long amount) {}

	public int wins;
	public int losses;
	public long net;
	public int streak;
	public boolean acceptInvites = true;
	public int botRounds;
	public long botNet;
	/** Most recent first. */
	private final LinkedHashMap<UUID, Rival> rivals = new LinkedHashMap<>();
	public final List<Note> notes = new ArrayList<>();

	public HeadToHead vs(UUID other) {
		Rival r = rivals.get(other);
		return r == null ? HeadToHead.EMPTY : r.record();
	}

	/** Stores a row and moves it to the front (drops the oldest beyond {@link #MAX_RIVALS}). */
	public void putRival(UUID other, String name, HeadToHead record) {
		rivals.remove(other);
		LinkedHashMap<UUID, Rival> copy = new LinkedHashMap<>();
		copy.put(other, new Rival(name, record));
		copy.putAll(rivals);
		rivals.clear();
		Iterator<Map.Entry<UUID, Rival>> it = copy.entrySet().iterator();
		while (it.hasNext() && rivals.size() < MAX_RIVALS) {
			Map.Entry<UUID, Rival> e = it.next();
			rivals.put(e.getKey(), e.getValue());
		}
	}

	/** Rivals, most recent first. */
	public Map<UUID, Rival> rivals() {
		return java.util.Collections.unmodifiableMap(rivals);
	}

	/** Nemesis (PVP.md §3.8): the rival with the worst net for this player, if that rival leads by ≥ 3 matches. */
	public UUID nemesis() {
		UUID best = null;
		long worst = 0;
		for (Map.Entry<UUID, Rival> e : rivals.entrySet()) {
			HeadToHead h = e.getValue().record();
			if (h.wins() - h.losses() <= -3 && (best == null || h.net() < worst)) {
				best = e.getKey();
				worst = h.net();
			}
		}
		return best;
	}

	// ---- JSON ----------------------------------------------------------------------------------------

	public static PvpPlayerRecord fromJson(String json) {
		PvpPlayerRecord r = new PvpPlayerRecord();
		if (json == null || json.isBlank()) {
			return r;
		}
		try {
			JsonElement el = JsonParser.parseString(json);
			if (!el.isJsonObject()) {
				return r;
			}
			JsonObject o = el.getAsJsonObject();
			r.wins = intOf(o, "wins");
			r.losses = intOf(o, "losses");
			r.net = longOf(o, "net");
			r.streak = intOf(o, "streak");
			r.acceptInvites = !o.has("acceptInvites") || o.get("acceptInvites").getAsBoolean();
			r.botRounds = intOf(o, "botRounds");
			r.botNet = longOf(o, "botNet");
			if (o.has("rivals") && o.get("rivals").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("rivals")) {
					JsonObject ro = e.getAsJsonObject();
					try {
						UUID id = UUID.fromString(ro.get("id").getAsString());
						JsonObject h = ro.getAsJsonObject("r");
						HeadToHead hh = new HeadToHead(intOf(h, "wins"), intOf(h, "losses"), longOf(h, "net"), intOf(h, "run"));
						if (r.rivals.size() < MAX_RIVALS) {
							r.rivals.put(id, new Rival(ro.has("name") ? ro.get("name").getAsString() : "", hh));
						}
					} catch (RuntimeException ignored) {
						// skip a corrupt row
					}
				}
			}
			if (o.has("notes") && o.get("notes").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("notes")) {
					JsonObject n = e.getAsJsonObject();
					r.notes.add(new Note(n.get("type").getAsString(), n.has("mode") ? n.get("mode").getAsString() : "", longOf(n, "amount")));
				}
			}
		} catch (RuntimeException e) {
			return new PvpPlayerRecord();
		}
		return r;
	}

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("wins", wins);
		o.addProperty("losses", losses);
		o.addProperty("net", net);
		o.addProperty("streak", streak);
		o.addProperty("acceptInvites", acceptInvites);
		JsonArray rs = new JsonArray();
		rivals.forEach((id, rv) -> {
			JsonObject ro = new JsonObject();
			ro.addProperty("id", id.toString());
			ro.addProperty("name", rv.name());
			JsonObject h = new JsonObject();
			h.addProperty("wins", rv.record().wins());
			h.addProperty("losses", rv.record().losses());
			h.addProperty("net", rv.record().net());
			h.addProperty("run", rv.record().run());
			ro.add("r", h);
			rs.add(ro);
		});
		o.add("rivals", rs);
		o.addProperty("botRounds", botRounds);
		o.addProperty("botNet", botNet);
		if (!notes.isEmpty()) {
			JsonArray ns = new JsonArray();
			for (Note n : notes) {
				JsonObject no = new JsonObject();
				no.addProperty("type", n.type());
				no.addProperty("mode", n.mode());
				no.addProperty("amount", n.amount());
				ns.add(no);
			}
			o.add("notes", ns);
		}
		return o.toString();
	}

	private static int intOf(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : 0;
	}

	private static long longOf(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0;
	}
}
