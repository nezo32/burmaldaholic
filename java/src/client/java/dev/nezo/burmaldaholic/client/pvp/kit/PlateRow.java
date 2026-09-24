package dev.nezo.burmaldaholic.client.pvp.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The versus layout of visual/extras.md §7.1: seat plates left to right in seat order (you always first), three per
 * row (x = 10, 145, 280 panel-local, 110 wide), a second row for 4–6 seats; ready ticks above the plates of seats that
 * pressed the step button, taunt bubbles pointing at their sender's plate.
 */
public final class PlateRow {
	public static final int PLATE_W = 110;
	public static final int[] COL_X = {10, 145, 280};
	public static final int ROW_PITCH = 34;

	private PlateRow() {}

	/** Seats in display order: you first, then the others in seat order. */
	public static List<PvpSeat> ordered(List<PvpSeat> seats) {
		List<PvpSeat> out = new java.util.ArrayList<>();
		for (PvpSeat s : seats) if (s.you()) out.add(s);
		for (PvpSeat s : seats) if (!s.you()) out.add(s);
		return out;
	}

	/** Panel-local x of the plate at display position {@code k}. */
	public static int plateX(int k) {
		return COL_X[k % 3];
	}

	public static int plateY(int k, int y0) {
		return y0 + (k / 3) * ROW_PITCH;
	}

	/** Display position of a seat index (−1 when absent). */
	public static int position(List<PvpSeat> seats, int index) {
		List<PvpSeat> o = ordered(seats);
		for (int k = 0; k < o.size(); k++) if (o.get(k).index() == index) return k;
		return -1;
	}

	/**
	 * Draws the plates at panel origin (left, …) from row top {@code y}. {@code score} gives each plate's second line;
	 * {@code winners} (may be null) swap the plates to winner / loser.
	 */
	public static void draw(GuiGraphicsExtractor g, Font font, JsonObject state, List<PvpSeat> seats, int left, int y,
			Function<PvpSeat, @Nullable Component> score) {
		draw(g, font, state, seats, left, y, score, null);
	}

	public static void draw(GuiGraphicsExtractor g, Font font, JsonObject state, List<PvpSeat> seats, int left, int y,
			Function<PvpSeat, @Nullable Component> score, int @Nullable [] winners) {
		boolean grudge = PvpSeat.bool(state, "grudge");
		List<PvpSeat> o = ordered(seats);
		for (int k = 0; k < o.size() && k < 6; k++) {
			PvpSeat s = o.get(k);
			int x = left + plateX(k);
			int py = plateY(k, y);
			PvpDraw.PlateKind kind = PvpDraw.kindOf(s, grudge);
			if (winners != null) {
				boolean won = false;
				for (int w : winners) won |= w == s.index();
				kind = won ? PvpDraw.PlateKind.WINNER : PvpDraw.PlateKind.LOSER;
			}
			PvpDraw.plate(g, font, s, kind, x, py, PLATE_W, score.apply(s));
			if (s.pressed()) PvpDraw.readyTick(g, x + PLATE_W - 26, py - 12);
		}
	}

	/** Taunt bubbles of the match view's {@code taunts} over the sender's plate (drawn above the widgets). */
	public static void bubbles(GuiGraphicsExtractor g, Font font, JsonObject state, List<PvpSeat> seats, int left, int y) {
		bubblesAt(g, font, state, seats, left, y, COL_X);
	}

	/** The same with custom plate x positions (panel-local, by display position). */
	public static void bubblesAt(GuiGraphicsExtractor g, Font font, JsonObject state, List<PvpSeat> seats, int left, int y, int[] xs) {
		JsonArray taunts = state.getAsJsonArray("taunts");
		if (taunts == null) return;
		double extra = RevealState.ticksSinceState();
		for (JsonElement e : taunts) {
			if (!e.isJsonObject()) continue;
			JsonObject t = e.getAsJsonObject();
			int seat = (int) PvpSeat.num(t, "seat", -1);
			int k = position(seats, seat);
			if (k < 0) continue;
			double age = PvpSeat.num(t, "age", 0) + extra;
			int x = left + (xs == COL_X ? plateX(k) : xs[Math.min(k, xs.length - 1)]) + 24;
			PvpDraw.bubble(g, font, (int) PvpSeat.num(t, "line", 0), x, xs == COL_X ? plateY(k, y) - 2 : y - 2, 150, age);
		}
	}
}
