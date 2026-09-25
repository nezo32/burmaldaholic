package dev.nezo.burmaldaholic.core.pvp.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What a persisted match holds in the bank escrow, kept NEXT TO the match record (Java wave-2 re-review): if
 * the match record can't be decoded on load, every entry is still refunded; no chip may vanish with a corrupt
 * record. Pure, Gson only. Format: {@code {"mode":…, "e":[["p", uuid, amount], ["b", bankrollId, amount], …]}}
 * ({@code p} = a player, {@code b} = a bankroll bot's bankroll; bank bots moved nothing and are not listed).
 */
public final class EscrowRecord {
	private EscrowRecord() {}

	/** One escrowed entry: to a player ({@code bankroll == false}, id = UUID) or to a bankroll (id = bankroll id). */
	public record Entry(boolean bankroll, String id, long amount) {
		public Entry {
			if (amount < 0) {
				throw new IllegalArgumentException("negative escrow");
			}
			if (!bankroll) {
				UUID.fromString(id); // validates
			}
		}

		public static Entry player(UUID id, long amount) {
			return new Entry(false, id.toString(), amount);
		}

		public static Entry bankroll(String id, long amount) {
			return new Entry(true, id, amount);
		}
	}

	/** Result of reading escrow out of a damaged record. */
	public sealed interface Salvage {
		/** The record holds nothing in escrow (settled / cancelled / an invite). */
		record Nothing() implements Salvage {}

		/** The entries to refund. */
		record Refund(String mode, List<Entry> entries) implements Salvage {}

		/** Neither the state nor the stakes can be read: quarantine the raw record for an admin. */
		record Unknown(String why) implements Salvage {}
	}

	public static String encode(String mode, List<Entry> entries) {
		JsonObject o = new JsonObject();
		o.addProperty("mode", mode);
		JsonArray a = new JsonArray();
		for (Entry e : entries) {
			JsonArray t = new JsonArray();
			t.add(e.bankroll() ? "b" : "p");
			t.add(e.id());
			t.add(e.amount());
			a.add(t);
		}
		o.add("e", a);
		return o.toString();
	}

	/** @throws RuntimeException on a damaged escrow record */
	public static Salvage.Refund decode(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		List<Entry> out = new ArrayList<>();
		for (JsonElement el : o.getAsJsonArray("e")) {
			JsonArray t = el.getAsJsonArray();
			String kind = t.get(0).getAsString();
			if (!"p".equals(kind) && !"b".equals(kind)) {
				throw new IllegalArgumentException("escrow kind " + kind);
			}
			out.add(new Entry("b".equals(kind), t.get(1).getAsString(), t.get(2).getAsLong()));
		}
		return new Salvage.Refund(o.has("mode") ? o.get("mode").getAsString() : "", List.copyOf(out));
	}

	/**
	 * Best-effort read of the escrow from a match record that {@code MatchJson.decode} rejected: only the state
	 * and the participants' accounts and stakes are needed (packed {@code "p"} tuples or the full
	 * {@code "participants"} form). Anything unreadable → {@link Salvage.Unknown}.
	 */
	public static Salvage salvage(String rawMatch) {
		JsonObject o;
		try {
			o = JsonParser.parseString(rawMatch).getAsJsonObject();
		} catch (RuntimeException e) {
			return new Salvage.Unknown("not a JSON object");
		}
		String state = o.has("state") && o.get("state").isJsonPrimitive() ? o.get("state").getAsString().toUpperCase(Locale.ROOT) : "";
		switch (state) {
			case "SETTLED", "CANCELLED", "CLOSED", "INVITED" -> {
				return new Salvage.Nothing();
			}
			case "LOBBY", "STARTING", "DRAWN" -> {
			}
			default -> {
				return new Salvage.Unknown("unknown state '" + state + "'");
			}
		}
		String mode = o.has("mode") && o.get("mode").isJsonPrimitive() ? o.get("mode").getAsString() : "";
		try {
			List<Entry> out = new ArrayList<>();
			if (o.has("p") && o.get("p").isJsonArray()) {
				for (JsonElement el : o.getAsJsonArray("p")) {
					JsonArray t = el.getAsJsonArray();
					switch (t.get(0).getAsString()) {
						case "h" -> out.add(Entry.player(UUID.fromString(t.get(1).getAsString()), t.get(3).getAsLong()));
						case "b" -> {
							if ("BANKROLL".equals(t.get(6).getAsString())) {
								out.add(Entry.bankroll(t.get(7).getAsString(), t.get(8).getAsLong()));
							} else {
								t.get(8).getAsLong(); // a bank bot: nothing moved, but the tuple must be whole
							}
						}
						default -> throw new IllegalArgumentException("participant kind");
					}
				}
			} else {
				for (JsonElement el : o.getAsJsonArray("participants")) {
					JsonObject po = el.getAsJsonObject();
					JsonObject occ = po.getAsJsonObject("occupant");
					long stake = po.get("stake").getAsLong();
					if ("bot".equals(occ.get("kind").getAsString())) {
						JsonObject pu = occ.getAsJsonObject("purse");
						if ("BANKROLL".equals(pu.get("kind").getAsString())) {
							out.add(Entry.bankroll(pu.get("id").getAsString(), stake));
						}
					} else {
						out.add(Entry.player(UUID.fromString(occ.get("id").getAsString()), stake));
					}
				}
			}
			return new Salvage.Refund(mode, List.copyOf(out));
		} catch (RuntimeException e) {
			return new Salvage.Unknown("participants unreadable: " + e);
		}
	}
}
