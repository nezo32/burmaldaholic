package dev.nezo.burmaldaholic.client.pvp.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Client clock of the shared Final Reveal (PVP.md §3.11.4, extras-pvp.md §9.4) for one match: when "Final results…"
 * started (the {@code pvp.final} step arrived), which places the server has revealed ({@code placings} of the match
 * view, never ahead of the cue) and when each one arrived, so plaques flip on the server's cue ticks. Plays the
 * drumroll (the server plays its first hit; the other 7 accelerate here) and nothing else — sounds of the place cues
 * come from the server.
 */
public final class RevealState {
	/** A revealed place: seat, place (1 = winner), points, local arrival time, the seat's outcome events. */
	public record Placing(int seat, int place, long points, long arrivedMs, JsonArray events) {}

	private String matchId = "";
	private long finalStartMs = -1;
	private double drumTick = -1;
	private final Map<Integer, Placing> bySeat = new HashMap<>();
	private long countdownMs = -1;
	private int countdownTicks = 60;
	private boolean grudge;

	/** Ms since the {@code pvp.countdown} step arrived (−1 = none seen live: late open or no countdown). */
	public double countdownElapsedMs() {
		return countdownMs <= 0 ? -1 : Util.getMillis() - countdownMs;
	}

	public int countdownTicks() {
		return countdownTicks;
	}

	public boolean grudge() {
		return grudge;
	}

	private static final RevealState CURRENT = new RevealState();

	/** The pvp client module's clock of the player's current match (mode screens read it). */
	public static RevealState current() {
		return CURRENT;
	}

	/** Local time the last match view arrived (taunt bubble ages count from it). */
	private static long lastStateMs = Util.getMillis();

	/** Ticks since the last match view arrived. */
	public static double ticksSinceState() {
		return (Util.getMillis() - lastStateMs) / 50.0;
	}

	/** Feeds a new match view; returns true when a new place was revealed. */
	public boolean update(JsonObject state) {
		lastStateMs = Util.getMillis();
		String id = PvpSeat.str(state, "id", "");
		if (!id.equals(matchId)) {
			matchId = id;
			finalStartMs = -1;
			drumTick = -1;
			countdownMs = -1;
			bySeat.clear();
		}
		long now = Util.getMillis();
		if (finalStartMs < 0 && hasFinalStep(state)) {
			// opened late (the reveal is already past "Final results…"): no drumroll replay over plaques already shown
			boolean late = !lastStepIsFinal(state) || hasPlacings(state);
			finalStartMs = late ? now - (PvpMotion.DRUM_TICKS[PvpMotion.DRUM_TICKS.length - 1] + 4) * 50L : now;
			if (late) drumTick = PvpMotion.DRUM_TICKS[PvpMotion.DRUM_TICKS.length - 1] + 4;
		}
		JsonArray steps = state.getAsJsonArray("steps");
		if (countdownMs < 0 && steps != null && !steps.isEmpty() && steps.get(0).isJsonObject()) {
			JsonObject first = steps.get(0).getAsJsonObject();
			if ("pvp.countdown".equals(PvpSeat.str(first, "kind", ""))) {
				// the countdown is the first step; a late open (more steps already) shows no countdown
				countdownTicks = (int) PvpSeat.num(first, "ticks", 60);
				countdownMs = steps.size() == 1 ? now : 0;
			}
		}
		grudge = PvpSeat.bool(state, "grudge");
		boolean fresh = false;
		JsonArray pl = state.getAsJsonArray("placings");
		if (pl != null) {
			for (JsonElement e : pl) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				int seat = (int) PvpSeat.num(o, "seat", -1);
				if (seat < 0 || bySeat.containsKey(seat)) continue;
				JsonArray ev = o.has("events") && o.get("events").isJsonArray() ? o.getAsJsonArray("events") : new JsonArray();
				bySeat.put(seat, new Placing(seat, (int) PvpSeat.num(o, "place", 0), PvpSeat.num(o, "points", 0), now, ev));
				fresh = true;
			}
		}
		if (fresh && finalStartMs < 0) finalStartMs = now;
		return fresh;
	}

	private static boolean lastStepIsFinal(JsonObject state) {
		JsonArray steps = state.getAsJsonArray("steps");
		if (steps == null || steps.isEmpty() || !steps.get(steps.size() - 1).isJsonObject()) return false;
		return "pvp.final".equals(PvpSeat.str(steps.get(steps.size() - 1).getAsJsonObject(), "kind", ""));
	}

	private static boolean hasPlacings(JsonObject state) {
		JsonArray pl = state.getAsJsonArray("placings");
		return pl != null && !pl.isEmpty();
	}

	private static boolean hasFinalStep(JsonObject state) {
		JsonArray steps = state.getAsJsonArray("steps");
		if (steps == null) return false;
		for (JsonElement e : steps) {
			if (e.isJsonObject() && "pvp.final".equals(PvpSeat.str(e.getAsJsonObject(), "kind", ""))) return true;
		}
		return false;
	}

	/** In the Final Reveal (or after it). */
	public boolean active() {
		return finalStartMs >= 0;
	}

	/** Ticks since "Final results…". */
	public double ticks() {
		return finalStartMs < 0 ? -1 : (Util.getMillis() - finalStartMs) / 50.0;
	}

	public @Nullable Placing placing(int seat) {
		return bySeat.get(seat);
	}

	public int revealedCount() {
		return bySeat.size();
	}

	/** Advances the drumroll (call once per frame while the overlay shows). */
	public void drum() {
		double t = ticks();
		if (t < 0) return;
		if (drumTick < 0) drumTick = 0; // the server plays the first hit with the cue (a late open starts past the roll)
		int hits = PvpMotion.drumHitsBetween(drumTick, t);
		for (int i = 0; i < hits; i++) Kit.vanilla("block.note_block.basedrum", 0.8f, 1f);
		if (hits > 0 || t > drumTick) drumTick = t;
	}
}
