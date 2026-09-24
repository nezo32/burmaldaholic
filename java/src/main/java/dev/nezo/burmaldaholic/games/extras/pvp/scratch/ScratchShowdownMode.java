package dev.nezo.burmaldaholic.games.extras.pvp.scratch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.extras.pvp.ModeRanking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Scratch Showdown (PVP.md §8, mode id {@code scratch}), task J-M5. Pure: no Minecraft types; test vectors
 * PVP.md §16.6 ({@code ScratchShowdownModeTest}). Duel (challenge one player / a bot) or open lobby
 * anchored at the host's position; no parameters besides the stake.
 *
 * <p>Tape (drawn at START from the fair rng, in this order): the seat order, then for every participant
 * 0…n−1 the cells 1…9 ({@link ShowdownCard#drawCell}: {@code nextInt(Σw)} over the weights in symbol
 * order). The symbol values are copied into the tape at draw time.
 *
 * <p>Timeline (§8.3), per cell c = 1…8: {@code cell_wait} (≤ {@code revealIntervalTicks}, waits for
 * Scratch!) → {@code cell} (20 t: everyone's symbol, running scores, multipliers, burned cells) → when a
 * Creeper or a Rabbit's Foot showed: {@code cell_events} (20 t). Cell 9: {@code cell_wait} only; it is
 * revealed by the engine's Final Reveal (outcome events {@code final_cell}).
 */
public final class ScratchShowdownMode implements PvpMode<ScratchShowdownMode.Params, ScratchShowdownMode.Tape> {
	public static final String ID = "scratch";
	public static final int REVEAL_TICKS = 20;
	public static final int EVENT_TICKS = 20;

	/** No set-up besides the stake. */
	public record Params() {}

	/**
	 * Everything random, drawn at START.
	 *
	 * @param seatOrder permutation of participant indices
	 * @param cells     [participant][cell 0…8] {@link ShowdownCard.Sym} ordinals
	 * @param values    symbol values (Coal … Star) at draw time
	 */
	public record Tape(int[] seatOrder, int[][] cells, int[] values) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Tape t && Arrays.equals(seatOrder, t.seatOrder) && Arrays.deepEquals(cells, t.cells) && Arrays.equals(values, t.values);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(seatOrder) * 31 + Arrays.deepHashCode(cells);
		}

		@Override
		public String toString() {
			return "Tape" + Arrays.toString(seatOrder) + Arrays.deepToString(cells);
		}
	}

	private final Supplier<PvpConfig> pvp;

	public ScratchShowdownMode() {
		this(CasinoConfig::pvp);
	}

	/** Tests / tools: explicit config source. */
	public ScratchShowdownMode(Supplier<PvpConfig> pvp) {
		this.pvp = pvp;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int minPlayers() {
		return 2;
	}

	@Override
	public int maxPlayers() {
		return pvp.get().scratch.maxPlayers;
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.NONE;
	}

	@Override
	public boolean equalStakes() {
		return true;
	}

	@Override
	public boolean enabled() {
		PvpConfig c = pvp.get();
		return c.enabled && c.scratch.enabled;
	}

	@Override
	public String validate(Params params) {
		return params == null ? "gui.burmaldaholic.error.invalid_bet_position" : null;
	}

	@Override
	public Params defaults() {
		return new Params();
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		PvpConfig.Scratch cfg = pvp.get().scratch;
		int[] weights = ShowdownCard.weights(cfg.weights);
		int[] seatOrder = rng.permutation(players);
		int[][] cells = new int[players][];
		for (int i = 0; i < players; i++) {
			cells[i] = ShowdownCard.drawCard(rng, weights);
		}
		return new Tape(seatOrder, cells, ShowdownCard.values(cfg.values));
	}

	public ShowdownCard.Evaluation[] evaluate(Tape tape, int revealed) {
		ShowdownCard.Evaluation[] out = new ShowdownCard.Evaluation[tape.cells().length];
		for (int i = 0; i < out.length; i++) {
			out[i] = ShowdownCard.evaluate(tape.cells()[i], tape.values(), revealed);
		}
		return out;
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		int n = tape.cells().length;
		ShowdownCard.Evaluation[] ev = evaluate(tape, ShowdownCard.CELLS);
		long[] points = new long[n];
		long[] best = new long[n];
		List<PvpEvent> events = new ArrayList<>();
		for (int c = 0; c < ShowdownCard.CELLS; c++) {
			for (int i = 0; i < n; i++) {
				events.addAll(cellEvents(ev[i], i, c));
			}
		}
		for (int i = 0; i < n; i++) {
			points[i] = ev[i].score();
			best[i] = ev[i].best();
			int last = ShowdownCard.CELLS - 1;
			events.add(new PvpEvent("final_cell", i, last, Map.of("symbol", (long) ev[i].cells()[last], "score", ev[i].score(),
				"mult", (long) ev[i].multiplier())));
			if (ev[i].feet() >= ShowdownCard.MAX_FEET_COUNTED) {
				events.add(new PvpEvent("lucky_feet", i, -1, Map.of("feet", (long) ev[i].feet())));
			}
		}
		ModeRanking.Ranked r = ModeRanking.rank(points, best, tape.seatOrder());
		return new Outcome(points, r.rankOrder(), r.winners(), tape.seatOrder().clone(), events);
	}

	/** Creeper burn / fizzle / foot at cell {@code c} of one card. */
	static List<PvpEvent> cellEvents(ShowdownCard.Evaluation e, int seat, int c) {
		int s = e.cells()[c];
		if (s == ShowdownCard.Sym.CREEPER.ordinal()) {
			int target = e.creeperBurns()[c];
			if (target < 0) {
				return List.of(PvpEvent.of("fizzle", seat, c));
			}
			return List.of(new PvpEvent("creeper", seat, c, Map.of("cell", (long) target, "symbol", (long) e.cells()[target])));
		}
		if (s == ShowdownCard.Sym.FOOT.ordinal()) {
			int feet = 0;
			for (int j = 0; j <= c; j++) {
				if (e.cells()[j] == ShowdownCard.Sym.FOOT.ordinal()) {
					feet++;
				}
			}
			return List.of(new PvpEvent("foot", seat, c, Map.of("feet", (long) feet, "mult", (long) ShowdownCard.multiplier(feet))));
		}
		return List.of();
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		int n = tape.cells().length;
		int wait = pvp.get().scratch.revealIntervalTicks;
		ShowdownCard.Evaluation[] ev = evaluate(tape, ShowdownCard.CELLS - 1);
		List<Step> steps = new ArrayList<>();
		for (int c = 0; c < ShowdownCard.CELLS; c++) {
			JsonObject w = new JsonObject();
			w.addProperty("cell", c + 1);
			w.addProperty("final", c == ShowdownCard.CELLS - 1);
			steps.add(new Step("cell_wait", wait, c, true, w));
			if (c == ShowdownCard.CELLS - 1) {
				break;
			}
			JsonObject d = new JsonObject();
			d.addProperty("cell", c + 1);
			JsonArray symbols = new JsonArray();
			JsonArray scores = new JsonArray();
			JsonArray mult = new JsonArray();
			JsonArray best = new JsonArray();
			JsonArray creepers = new JsonArray();
			JsonArray fizzles = new JsonArray();
			JsonArray feet = new JsonArray();
			long[] running = new long[n];
			long[] bestSoFar = new long[n];
			for (int i = 0; i < n; i++) {
				ShowdownCard.Evaluation prefix = ShowdownCard.evaluate(tape.cells()[i], tape.values(), c + 1);
				symbols.add(tape.cells()[i][c]);
				running[i] = ev[i].running()[c];
				bestSoFar[i] = prefix.best();
				scores.add(running[i]);
				mult.add(prefix.multiplier());
				best.add(prefix.best());
				for (PvpEvent e : cellEvents(ev[i], i, c)) {
					JsonObject x = new JsonObject();
					x.addProperty("seat", i);
					e.data().forEach(x::addProperty);
					switch (e.kind()) {
						case "creeper" -> creepers.add(x);
						case "fizzle" -> fizzles.add(x);
						default -> feet.add(x);
					}
				}
			}
			d.add("symbols", symbols);
			d.add("scores", scores);
			d.add("mult", mult);
			d.add("best", best);
			d.add("burns", creepers);
			d.add("standings", ints(ModeRanking.rank(running, bestSoFar, tape.seatOrder()).rankOrder()));
			steps.add(new Step("cell", REVEAL_TICKS, c, false, d));
			if (!creepers.isEmpty() || !fizzles.isEmpty() || !feet.isEmpty()) {
				JsonObject x = new JsonObject();
				x.addProperty("cell", c + 1);
				x.add("creepers", creepers);
				x.add("fizzles", fizzles);
				x.add("feet", feet);
				steps.add(new Step("cell_events", EVENT_TICKS, c, false, x));
			}
		}
		return steps;
	}

	private static JsonArray ints(int[] v) {
		JsonArray a = new JsonArray();
		for (int x : v) {
			a.add(x);
		}
		return a;
	}

	// ---- persistence ----

	@Override
	public JsonElement encodeParams(Params params) {
		return new JsonObject();
	}

	@Override
	public Params decodeParams(JsonElement json) {
		return new Params();
	}

	/** {@code {"s":[seat order],"c":["012345678" per participant],"v":[6 values]}}. */
	@Override
	public JsonElement encodeTape(Tape tape) {
		JsonObject o = new JsonObject();
		o.add("s", ints(tape.seatOrder()));
		JsonArray c = new JsonArray();
		for (int[] card : tape.cells()) {
			StringBuilder sb = new StringBuilder(ShowdownCard.CELLS);
			for (int x : card) {
				sb.append((char) ('0' + x));
			}
			c.add(sb.toString());
		}
		o.add("c", c);
		o.add("v", ints(tape.values()));
		return o;
	}

	@Override
	public Tape decodeTape(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		JsonArray s = o.getAsJsonArray("s");
		int[] seat = new int[s.size()];
		for (int i = 0; i < seat.length; i++) {
			seat[i] = s.get(i).getAsInt();
		}
		JsonArray c = o.getAsJsonArray("c");
		int[][] cells = new int[c.size()][ShowdownCard.CELLS];
		int symbols = ShowdownCard.Sym.values().length;
		for (int i = 0; i < cells.length; i++) {
			String card = c.get(i).getAsString();
			if (card.length() != ShowdownCard.CELLS) {
				throw new IllegalArgumentException("card " + card);
			}
			for (int k = 0; k < ShowdownCard.CELLS; k++) {
				int x = card.charAt(k) - '0';
				if (x < 0 || x >= symbols) {
					throw new IllegalArgumentException("card " + card);
				}
				cells[i][k] = x;
			}
		}
		int[] values = ShowdownCard.DEFAULT_VALUES.clone();
		if (o.has("v")) {
			JsonArray v = o.getAsJsonArray("v");
			for (int i = 0; i < Math.min(values.length, v.size()); i++) {
				values[i] = v.get(i).getAsInt();
			}
		}
		return new Tape(seat, cells, values);
	}
}
