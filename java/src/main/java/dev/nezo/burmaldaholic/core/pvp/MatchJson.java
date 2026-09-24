package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * JSON form of a persisted match (docs/architecture/pvp-bots.md §5.1; Java adds {@code duel},
 * {@code lobbyEnd} and {@code settledTick}). Only what is needed to refund a LOBBY, settle a DRAWN match
 * from its tape and keep a SETTLED one for the history.
 */
final class MatchJson {
	private MatchJson() {}

	static String encode(PvpMatch m) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("id", m.id);
		o.addProperty("mode", m.mode);
		o.addProperty("state", m.state.name());
		o.add("params", m.params);
		JsonObject a = new JsonObject();
		a.addProperty("kind", m.anchorKind.name().toLowerCase(Locale.ROOT));
		a.addProperty("dim", m.anchor.dimension().identifier().toString());
		a.addProperty("x", m.anchor.pos().getX());
		a.addProperty("y", m.anchor.pos().getY());
		a.addProperty("z", m.anchor.pos().getZ());
		o.add("anchor", a);
		o.addProperty("bankroll", m.bankroll);
		JsonObject s = new JsonObject();
		s.addProperty("policy", m.seating.policy().name());
		s.addProperty("count", m.seating.count());
		s.addProperty("difficulty", m.seating.difficulty().name());
		s.addProperty("keepFree", m.seating.keepFree());
		s.addProperty("chatter", m.seating.chatter());
		s.addProperty("speed", m.seating.speed().name());
		o.add("seating", s);
		o.addProperty("inviteOnly", m.inviteOnly);
		o.addProperty("host", m.host == null ? "" : m.host.toString());
		o.addProperty("createdTick", m.createdTick);
		o.addProperty("chainOf", m.chainOf);
		o.addProperty("link", m.link);
		JsonArray ps = new JsonArray();
		for (Participant p : m.participants) {
			JsonObject po = new JsonObject();
			po.addProperty("index", p.index);
			po.add("occupant", occupant(p.occupant));
			po.addProperty("stake", p.stake());
			po.addProperty("allIn", p.allIn());
			ps.add(po);
		}
		o.add("participants", ps);
		o.add("tape", m.tape == null ? JsonNull.INSTANCE : m.tape);
		o.addProperty("drawnTick", m.drawnTick);
		JsonArray pay = new JsonArray();
		for (long x : m.payouts) {
			pay.add(x);
		}
		o.add("payouts", pay);
		o.addProperty("rake", m.rake);
		o.addProperty("grudge", m.grudge);
		o.addProperty("duel", m.duel);
		o.addProperty("lobbyEnd", m.lobbyEnd);
		o.addProperty("settledTick", m.settledTick);
		return o.toString();
	}

	static JsonObject occupant(SeatOccupant occ) {
		JsonObject o = new JsonObject();
		switch (occ) {
			case SeatOccupant.Human h -> {
				o.addProperty("kind", "human");
				o.addProperty("id", h.id().toString());
				o.addProperty("name", h.name());
			}
			case SeatOccupant.Bot b -> {
				o.addProperty("kind", "bot");
				JsonObject pr = new JsonObject();
				pr.addProperty("id", b.profile().id());
				pr.addProperty("nameId", b.profile().nameId());
				pr.addProperty("level", b.profile().level().name());
				pr.addProperty("personality", b.profile().personality().name());
				o.add("profile", pr);
				o.addProperty("role", b.role().name());
				JsonObject pu = new JsonObject();
				pu.addProperty("kind", b.purse().kind().name());
				if (!b.purse().bankrollId().isEmpty()) {
					pu.addProperty("id", b.purse().bankrollId());
				}
				o.add("purse", pu);
			}
		}
		return o;
	}

	static SeatOccupant occupant(JsonObject o) {
		if ("bot".equals(str(o, "kind", ""))) {
			JsonObject pr = o.getAsJsonObject("profile");
			BotDifficulty level = BotDifficulty.byId(str(pr, "level", "NORMAL"), BotDifficulty.NORMAL);
			if (level == BotDifficulty.MIXED) {
				level = BotDifficulty.NORMAL;
			}
			BotProfile profile = new BotProfile(str(pr, "id", "b0000000"), str(pr, "nameId", "lucky_steve"), level,
				Personality.byId(str(pr, "personality", "TAG")));
			JsonObject pu = o.getAsJsonObject("purse");
			Purse purse = "BANKROLL".equals(str(pu, "kind", "BANK")) ? Purse.bankroll(str(pu, "id", "")) : Purse.BANK;
			return new SeatOccupant.Bot(profile, BotRole.MONEY, purse);
		}
		return new SeatOccupant.Human(UUID.fromString(str(o, "id", "")), str(o, "name", "?"));
	}

	/** @throws RuntimeException on a corrupt record (the caller logs and drops it) */
	static PvpMatch decode(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		JsonObject a = o.getAsJsonObject("anchor");
		Identifier dim = Identifier.tryParse(str(a, "dim", "minecraft:overworld"));
		GlobalPos anchor = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim == null ? Identifier.withDefaultNamespace("overworld") : dim),
			new BlockPos(a.get("x").getAsInt(), a.get("y").getAsInt(), a.get("z").getAsInt()));
		AnchorKind kind = AnchorKind.NONE;
		for (AnchorKind k : AnchorKind.values()) {
			if (k.name().equalsIgnoreCase(str(a, "kind", "none"))) {
				kind = k;
			}
		}
		JsonObject s = o.getAsJsonObject("seating");
		BotSettings seating = new BotSettings(SeatPolicy.byId(str(s, "policy", "HUMANS_ONLY"), SeatPolicy.HUMANS_ONLY),
			s.has("count") ? s.get("count").getAsInt() : 0, BotDifficulty.byId(str(s, "difficulty", "NORMAL"), BotDifficulty.NORMAL),
			!s.has("keepFree") || s.get("keepFree").getAsBoolean(), !s.has("chatter") || s.get("chatter").getAsBoolean(),
			speed(str(s, "speed", "NORMAL")));
		MatchState state = MatchState.valueOf(str(o, "state", "LOBBY"));
		PvpMatch m = new PvpMatch(str(o, "id", ""), str(o, "mode", ""), o.get("params"), kind, anchor, str(o, "bankroll", ""), seating,
			o.has("inviteOnly") && o.get("inviteOnly").getAsBoolean(), o.get("createdTick").getAsLong(), str(o, "chainOf", ""),
			o.has("link") ? o.get("link").getAsInt() : 0, state);
		String host = str(o, "host", "");
		m.host = host.isEmpty() ? null : UUID.fromString(host);
		for (JsonElement e : o.getAsJsonArray("participants")) {
			JsonObject po = e.getAsJsonObject();
			Participant p = new Participant(po.get("index").getAsInt(), occupant(po.getAsJsonObject("occupant")), po.get("stake").getAsLong());
			p.setAllIn(po.has("allIn") && po.get("allIn").getAsBoolean());
			m.participants.add(p);
		}
		JsonElement tape = o.get("tape");
		m.tape = tape == null || tape.isJsonNull() ? null : tape;
		m.drawnTick = o.has("drawnTick") ? o.get("drawnTick").getAsLong() : 0;
		if (o.has("payouts")) {
			JsonArray pay = o.getAsJsonArray("payouts");
			m.payouts = new long[pay.size()];
			for (int i = 0; i < pay.size(); i++) {
				m.payouts[i] = pay.get(i).getAsLong();
			}
		}
		m.rake = o.has("rake") ? o.get("rake").getAsLong() : 0;
		m.grudge = o.has("grudge") && o.get("grudge").getAsBoolean();
		m.duel = o.has("duel") && o.get("duel").getAsBoolean();
		m.lobbyEnd = o.has("lobbyEnd") ? o.get("lobbyEnd").getAsLong() : 0;
		m.settledTick = o.has("settledTick") ? o.get("settledTick").getAsLong() : 0;
		return m;
	}

	private static BotSpeed speed(String id) {
		for (BotSpeed s : BotSpeed.values()) {
			if (s.name().equalsIgnoreCase(id)) {
				return s;
			}
		}
		return BotSpeed.NORMAL;
	}

	private static String str(JsonObject o, String k, String def) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}
}
