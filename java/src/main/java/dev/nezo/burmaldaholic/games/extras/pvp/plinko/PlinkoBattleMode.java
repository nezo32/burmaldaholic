package dev.nezo.burmaldaholic.games.extras.pvp.plinko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.pvp.ModeRanking;
import java.util.ArrayList;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Plinko Battle (PVP.md §7, mode id {@code plinko}), task J-M4. Pure: no Minecraft types; test vectors
 * PVP.md §16.5 ({@code PlinkoBattleModeTest}).
 *
 * <p>Tape (drawn at START from the fair rng, in this order): the seat order ({@link PvpRng#permutation}),
 * then for every participant 0…n−1 and every ball 0…B−1 one path = 12 × {@code nextInt(2)} (row 0 first,
 * exactly the solo drop). The points row of the chosen risk and the Underdog switch are copied into the
 * tape at draw time, so a config reload between START and SETTLE cannot change the result.
 *
 * <p>Timeline (§7.2), per ball b: {@code ball_wait} (≤ {@code roundIntervalTicks}, waits for Drop!) →
 * {@code drop} (48 t: 12 rows × 4 t; masks, bins, scored points) → {@code ball_score} (40 t; totals,
 * standings). Before the final ball: {@code underdog} (20 t) when someone is boosted. The final ball's
 * {@code drop} step shows only the first 11 rows ({@code rows} = 11); its landing is told by the engine's
 * Final Reveal (outcome events {@code final_ball}).
 */
public final class PlinkoBattleMode implements PvpMode<PlinkoBattleMode.Params, PlinkoBattleMode.Tape> {
	public static final String ID = "plinko";
	/** One row every 4 ticks (the solo animation speed). */
	public static final int ROW_TICKS = 4;
	public static final int DROP_TICKS = PlinkoBattle.ROWS * ROW_TICKS;
	public static final int SCORE_TICKS = 40;
	public static final int UNDERDOG_TICKS = 20;
	/** Rows shown of the final ball before the Final Reveal. */
	public static final int FINAL_ROWS_SHOWN = PlinkoBattle.ROWS - 1;

	/**
	 * Host set-up.
	 *
	 * @param risk  {@code low | medium | high} (one risk for everyone)
	 * @param balls balls per player, one of {@code pvp.plinko.ballChoices}
	 */
	public record Params(String risk, int balls, long @Nullable [] points, @Nullable Boolean underdogBoost) {
		public Params(String risk, int balls) {
			this(risk, balls, null, null);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Params p && risk.equals(p.risk) && balls == p.balls && Arrays.equals(points, p.points)
				&& java.util.Objects.equals(underdogBoost, p.underdogBoost);
		}

		@Override
		public int hashCode() {
			return risk.hashCode() * 31 + balls;
		}
	}

	/**
	 * Everything random, drawn at START.
	 *
	 * @param seatOrder     permutation of participant indices
	 * @param paths         [participant][ball] 12-bit path masks
	 * @param points        the risk's points row (13 bins) at draw time
	 * @param underdogBoost {@code pvp.plinko.underdogBoost} at draw time
	 */
	public record Tape(int[] seatOrder, int[][] paths, long[] points, boolean underdogBoost) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Tape t && Arrays.equals(seatOrder, t.seatOrder) && Arrays.deepEquals(paths, t.paths)
				&& Arrays.equals(points, t.points) && underdogBoost == t.underdogBoost;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(seatOrder) * 31 + Arrays.deepHashCode(paths);
		}

		@Override
		public String toString() {
			return "Tape" + Arrays.toString(seatOrder) + Arrays.deepToString(paths);
		}
	}

	private final Supplier<PvpConfig> pvp;
	private final Supplier<ExtrasConfig.Plinko> rows;

	public PlinkoBattleMode() {
		this(CasinoConfig::pvp, () -> CasinoConfig.extras().plinko);
	}

	/** Tests / tools: explicit config sources. */
	public PlinkoBattleMode(Supplier<PvpConfig> pvp, Supplier<ExtrasConfig.Plinko> rows) {
		this.pvp = pvp;
		this.rows = rows;
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
		return pvp.get().plinko.maxPlayers;
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.PLINKO_MACHINE;
	}

	@Override
	public boolean equalStakes() {
		return true;
	}

	@Override
	public boolean enabled() {
		PvpConfig c = pvp.get();
		return c.enabled && c.plinko.enabled;
	}

	/** Offered ball counts ({@code pvp.plinko.ballChoices}, sorted, unique). */
	public int[] ballChoices() {
		int[] c = pvp.get().plinko.ballChoices;
		return c == null || c.length == 0 ? new int[] {3} : Arrays.stream(c).distinct().sorted().toArray();
	}

	@Override
	public String validate(Params params) {
		if (params == null || Plinko.Risk.parse(params.risk()) == null) {
			return "gui.burmaldaholic.error.invalid_bet_position";
		}
		for (int c : ballChoices()) {
			if (c == params.balls()) {
				return null;
			}
		}
		return "gui.burmaldaholic.error.invalid_bet_position";
	}

	@Override
	public Params defaults() {
		int[] c = ballChoices();
		return new Params(Plinko.Risk.LOW.id(), c[c.length / 2]);
	}

	/** Live points row of a risk (§7.1: derived from {@code extras.plinko.<risk>}). */
	public long[] pointsRow(Plinko.Risk risk) {
		ExtrasConfig.Plinko cfg = rows.get();
		double[] configured = switch (risk) {
			case LOW -> cfg.low;
			case MEDIUM -> cfg.medium;
			case HIGH -> cfg.high;
		};
		return PlinkoBattle.pointsRow(Plinko.table(configured, risk));
	}

	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		Plinko.Risk risk = Plinko.Risk.parse(params.risk());
		if (risk == null) {
			throw new IllegalArgumentException("risk " + params.risk());
		}
		int[] seatOrder = rng.permutation(players);
		int[][] paths = new int[players][params.balls()];
		for (int i = 0; i < players; i++) {
			for (int b = 0; b < params.balls(); b++) {
				paths[i][b] = PlinkoBattle.drawPath(rng);
			}
		}
		// the points row / boost snapshot taken when the match was created (params), else the live config
		long[] row = params.points() != null && params.points().length == PlinkoBattle.ROWS + 1 ? params.points().clone() : pointsRow(risk);
		boolean boost = params.underdogBoost() != null ? params.underdogBoost() : pvp.get().plinko.underdogBoost;
		return new Tape(seatOrder, paths, row, boost);
	}

	public PlinkoBattle.Scored scored(Tape tape) {
		return PlinkoBattle.score(tape.paths(), tape.seatOrder(), tape.points(), tape.underdogBoost());
	}

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		PlinkoBattle.Scored s = scored(tape);
		return new Outcome(s.totals(), s.rankOrder(), s.winners(), tape.seatOrder().clone(), s.events());
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		PlinkoBattle.Scored s = scored(tape);
		int n = tape.paths().length;
		int balls = n == 0 ? 0 : tape.paths()[0].length;
		int wait = pvp.get().plinko.roundIntervalTicks;
		List<Step> steps = new ArrayList<>();
		long[] totals = new long[n];
		long[] best = new long[n];
		for (int b = 0; b < balls; b++) {
			boolean last = b == balls - 1;
			if (last && balls > 1) {
				JsonArray seats = new JsonArray();
				for (int i = 0; i < n; i++) {
					if (s.underdog()[i]) {
						seats.add(i);
					}
				}
				if (!seats.isEmpty()) {
					JsonObject d = new JsonObject();
					d.add("seats", seats);
					steps.add(new Step("underdog", UNDERDOG_TICKS, b, false, d));
				}
			}
			JsonObject w = new JsonObject();
			w.addProperty("ball", b + 1);
			w.addProperty("balls", balls);
			w.addProperty("final", last);
			w.add("row", longs(tape.points())); // bin labels (public: derived from the config)
			steps.add(new Step("ball_wait", wait, b, true, w));
			JsonObject drop = new JsonObject();
			JsonArray masks = new JsonArray();
			for (int i = 0; i < n; i++) {
				int mask = tape.paths()[i][b];
				masks.add(last ? mask & ((1 << FINAL_ROWS_SHOWN) - 1) : mask);
			}
			drop.addProperty("ball", b + 1);
			drop.addProperty("balls", balls);
			drop.add("masks", masks);
			if (last) {
				drop.addProperty("final", true);
				drop.addProperty("rows", FINAL_ROWS_SHOWN);
				steps.add(new Step("drop", FINAL_ROWS_SHOWN * ROW_TICKS, b, false, drop));
				break;
			}
			drop.addProperty("rows", PlinkoBattle.ROWS);
			JsonArray bins = new JsonArray();
			JsonArray points = new JsonArray();
			JsonArray edges = new JsonArray();
			for (int i = 0; i < n; i++) {
				int bin = PlinkoBattle.bin(tape.paths()[i][b]);
				bins.add(bin);
				points.add(s.ballPoints()[i][b]);
				if (PlinkoBattle.edge(bin)) {
					edges.add(i);
				}
				totals[i] += s.ballPoints()[i][b];
				best[i] = Math.max(best[i], s.ballPoints()[i][b]);
			}
			drop.add("bins", bins);
			drop.add("points", points);
			drop.add("edges", edges);
			steps.add(new Step("drop", DROP_TICKS, b, false, drop));
			JsonObject score = new JsonObject();
			score.addProperty("ball", b + 1);
			score.addProperty("balls", balls);
			score.add("totals", longs(totals));
			score.add("standings", ints(ModeRanking.rank(totals, best, tape.seatOrder()).rankOrder()));
			steps.add(new Step("ball_score", SCORE_TICKS, b, false, score));
		}
		return steps;
	}

	private static JsonArray longs(long[] v) {
		JsonArray a = new JsonArray();
		for (long x : v) {
			a.add(x);
		}
		return a;
	}

	private static JsonArray ints(int[] v) {
		JsonArray a = new JsonArray();
		for (int x : v) {
			a.add(x);
		}
		return a;
	}

	// ---- persistence ----

	/** {@code {"risk","balls","row":[13 points],"u":bool}}; the row / boost are snapshot from the config at creation. */
	@Override
	public JsonElement encodeParams(Params params) {
		JsonObject o = new JsonObject();
		o.addProperty("risk", params.risk());
		o.addProperty("balls", params.balls());
		Plinko.Risk risk = Plinko.Risk.parse(params.risk());
		long[] row = params.points() != null ? params.points() : risk == null ? null : pointsRow(risk);
		if (row != null) {
			o.add("row", longs(row));
		}
		o.addProperty("u", params.underdogBoost() != null ? params.underdogBoost() : pvp.get().plinko.underdogBoost);
		return o;
	}

	@Override
	public Params decodeParams(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		long[] row = null;
		if (o.has("row") && o.get("row").isJsonArray()) {
			JsonArray r = o.getAsJsonArray("row");
			row = new long[r.size()];
			for (int i = 0; i < row.length; i++) {
				row[i] = r.get(i).getAsLong();
			}
		}
		Boolean boost = o.has("u") ? o.get("u").getAsBoolean() : null; // older records: the live config
		return new Params(o.get("risk").getAsString(), o.get("balls").getAsInt(), row, boost);
	}

	/** {@code {"s":[seat order],"p":[[masks per ball] per participant],"r":[13 points],"u":true}}. */
	@Override
	public JsonElement encodeTape(Tape tape) {
		JsonObject o = new JsonObject();
		o.add("s", ints(tape.seatOrder()));
		JsonArray p = new JsonArray();
		for (int[] row : tape.paths()) {
			p.add(ints(row));
		}
		o.add("p", p);
		o.add("r", longs(tape.points()));
		o.addProperty("u", tape.underdogBoost());
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
		JsonArray p = o.getAsJsonArray("p");
		int[][] paths = new int[p.size()][];
		for (int i = 0; i < paths.length; i++) {
			JsonArray row = p.get(i).getAsJsonArray();
			paths[i] = new int[row.size()];
			for (int b = 0; b < row.size(); b++) {
				paths[i][b] = row.get(b).getAsInt() & PlinkoBattle.MASK;
			}
		}
		JsonArray r = o.getAsJsonArray("r");
		long[] points = new long[r.size()];
		for (int i = 0; i < points.length; i++) {
			points[i] = r.get(i).getAsLong();
		}
		return new Tape(seat, paths, points, o.has("u") && o.get("u").getAsBoolean());
	}
}
