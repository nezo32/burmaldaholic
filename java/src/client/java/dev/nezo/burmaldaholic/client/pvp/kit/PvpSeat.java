package dev.nezo.burmaldaholic.client.pvp.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One seat of a PvP match as the plates draw it, read from a participant of the pvp module's match view
 * ({@code PvpMatchView}: {@code index, key, name, bot, level, stake, allIn, pressed, rematch, host, you, wins?, losses?}).
 */
public record PvpSeat(int index, String key, String name, boolean bot, String level, long stake, boolean allIn, boolean pressed, boolean rematch,
		boolean host, boolean you, long wins, long losses, boolean hasRecord) {

	public static PvpSeat of(JsonObject p) {
		return new PvpSeat((int) num(p, "index", 0), str(p, "key", ""), str(p, "name", "?"), bool(p, "bot"), str(p, "level", ""),
			num(p, "stake", 0), bool(p, "allIn"), bool(p, "pressed"), bool(p, "rematch"), bool(p, "host"), bool(p, "you"), num(p, "wins", 0),
			num(p, "losses", 0), p.has("wins"));
	}

	/** Seats of a match view in index order. */
	public static List<PvpSeat> all(JsonObject state) {
		List<PvpSeat> out = new ArrayList<>();
		JsonArray a = state == null ? null : state.getAsJsonArray("participants");
		if (a != null) {
			for (JsonElement e : a) {
				if (e.isJsonObject()) out.add(of(e.getAsJsonObject()));
			}
		}
		return out;
	}

	/** Plate name: "You", the player's name, or the bot's translated name (the difficulty is a badge). */
	public Component plateName() {
		if (you) return Component.translatable("gui.burmaldaholic.common.you");
		return bot ? Component.translatable(name) : Texts.raw(name);
	}

	/** Name for lines and banners ("[BOT] Name" for bots, as the existing strings do). */
	public Component displayName() {
		return bot ? Component.translatable("gui.burmaldaholic.bots.display", Component.translatable(name)) : Texts.raw(name);
	}

	static String str(JsonObject o, String k, String d) {
		JsonElement e = o == null ? null : o.get(k);
		return e == null || !e.isJsonPrimitive() ? d : e.getAsString();
	}

	static long num(JsonObject o, String k, long d) {
		JsonElement e = o == null ? null : o.get(k);
		try {
			return e == null || !e.isJsonPrimitive() ? d : e.getAsLong();
		} catch (NumberFormatException ex) {
			return d;
		}
	}

	static boolean bool(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}
}
