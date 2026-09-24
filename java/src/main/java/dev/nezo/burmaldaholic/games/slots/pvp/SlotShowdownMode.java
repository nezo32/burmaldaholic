package dev.nezo.burmaldaholic.games.slots.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.slots.SlotsMath;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Slot Showdown (PVP.md §5, mode id {@code slots}; task J-M3). Everyone gets N spins of the anchor's tier,
 * drawn exactly like a solo spin and evaluated with the solo line evaluator ({@link ShowdownScoring});
 * line wins become points; HOT symbol ×2, KABOOM halves, Pearl SWAP with the leader, TIME WARP, Underdog
 * Boost on the final spin. Pure apart from the config-backed production constructor.
 *
 * <p>The rule switches ({@code pvp.slots.hotSymbol/underdogBoost/kaboom/pearlSwap/starPoints}) and the auto-spin
 * interval are snapshotted into {@link Params} when the lobby opens, so a settle after a config change or a
 * restart scores exactly what was announced.
 */
public final class SlotShowdownMode implements PvpMode<SlotShowdownMode.Params, SlotShowdownMode.Tape> {
	public static final String ID = "slots";
	/** Reel animation of one round (PVP.md §5.3). */
	public static final int SPIN_TICKS = 40;
	/** Score step base length and per-event extra (KABOOM / SWAP / TIME WARP 20 t each). */
	public static final int SCORE_TICKS = 20;
	public static final int EVENT_TICKS = 20;
	public static final int UNDERDOG_TICKS = 20;

	/** Rule switches snapshotted at lobby creation (PVP.md §13.2). */
	public record Rules(boolean hotSymbol, boolean underdogBoost, boolean kaboom, boolean pearlSwap, int starPoints, int spinIntervalTicks) {
		public static final Rules DEFAULTS = new Rules(true, true, true, true, 500, 100);

		public Rules {
			starPoints = Math.max(0, starPoints);
			spinIntervalTicks = Math.max(1, spinIntervalTicks);
		}

		static Rules of(PvpConfig.Slots c) {
			return new Rules(c.hotSymbol, c.underdogBoost, c.kaboom, c.pearlSwap, c.starPoints, c.spinIntervalTicks);
		}
	}

	/** Host set-up: machine tier id ({@code copper|gold|netherite}), spins per player, rules snapshot. */
	public record Params(String tier, int spins, Rules rules) {
		public Params {
			if (rules == null) {
				rules = Rules.DEFAULTS;
			}
		}

		public Tier tierOrCopper() {
			Tier t = Tier.byId(tier);
			return t == null ? Tier.COPPER : t;
		}
	}

	/**
	 * Everything random, drawn at START (seat order included).
	 *
	 * @param seatOrder participant indices in seat order
	 * @param hot       per round: HOT symbol ordinal (one of the six regular symbols)
	 * @param grids     {@code grids[player][round][r * 3 + c]} = symbol ordinal
	 */
	public record Tape(int[] seatOrder, int[] hot, int[][][] grids) {}

	/** Live settings a production instance reads (config) and tests fix. */
	public record Settings(boolean enabled, int maxPlayers, int[] spinChoices, int minStake, Rules rules) {}

	private final Function<Tier, SlotTable> tables;
	private final Supplier<Settings> settings;

	/** Production: live config ({@code pvp.*}, {@code slots.<tier>.*}). */
	public SlotShowdownMode() {
		this(tier -> SlotsMath.machine(tier, false).table(), () -> {
			PvpConfig c = CasinoConfig.pvp();
			return new Settings(c.enabled && c.slots.enabled, c.slots.maxPlayers, c.slots.spinChoices.clone(), c.minStake, Rules.of(c.slots));
		});
	}

	/** Tests / tools. */
	public SlotShowdownMode(Function<Tier, SlotTable> tables, Supplier<Settings> settings) {
		this.tables = tables;
		this.settings = settings;
	}

	/** Defaults of GAME_DESIGN.md §8 and PVP.md §13 (no config needed). */
	public static SlotShowdownMode withDefaults() {
		return new SlotShowdownMode(SlotTable::defaults, () -> new Settings(true, 6, new int[] {3, 5, 10}, 10, Rules.DEFAULTS));
	}

	public SlotTable table(Tier tier) {
		return tables.apply(tier);
	}

	public Settings settings() {
		return settings.get();
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
		return settings.get().maxPlayers();
	}

	@Override
	public AnchorKind anchor() {
		return AnchorKind.SLOT_MACHINE;
	}

	@Override
	public boolean equalStakes() {
		return true;
	}

	@Override
	public boolean enabled() {
		return settings.get().enabled();
	}

	@Override
	public String validate(Params params) {
		if (params == null || Tier.byId(params.tier()) == null) {
			return "gui.burmaldaholic.error.disabled";
		}
		boolean allowed = false;
		for (int s : settings.get().spinChoices()) {
			allowed |= s == params.spins();
		}
		if (!allowed || params.spins() < 1 || params.spins() > 20) {
			return "gui.burmaldaholic.error.invalid_amount";
		}
		if (table(params.tierOrCopper()).empty()) {
			return "gui.burmaldaholic.error.disabled";
		}
		return null;
	}

	/** Copper, the middle spin choice, the current rules. The machine entry replaces the tier. */
	@Override
	public Params defaults() {
		return defaults(Tier.COPPER);
	}

	public Params defaults(Tier tier) {
		Settings s = settings.get();
		int[] choices = s.spinChoices();
		int spins = choices.length == 0 ? 5 : choices[(choices.length - 1) / 2];
		return new Params(tier.id(), spins, s.rules());
	}

	// ---- draw -----------------------------------------------------------------------------------

	/** Order: seat order, the N HOT symbols, then every player's N grids cell by cell (row-major). */
	@Override
	public Tape draw(PvpRng rng, int players, Params params) {
		SlotTable table = table(params.tierOrCopper());
		int spins = params.spins();
		int[] seatOrder = rng.permutation(players);
		int[] hot = new int[spins];
		for (int r = 0; r < spins; r++) {
			hot[r] = ShowdownScoring.HOT_CANDIDATES.get(rng.nextInt(ShowdownScoring.HOT_CANDIDATES.size())).ordinal();
		}
		int[][][] grids = new int[players][spins][9];
		for (int p = 0; p < players; p++) {
			for (int r = 0; r < spins; r++) {
				for (int cell = 0; cell < 9; cell++) {
					grids[p][r][cell] = drawCell(table, rng).ordinal();
				}
			}
		}
		return new Tape(seatOrder, hot, grids);
	}

	private static Symbol drawCell(SlotTable table, PvpRng rng) {
		int roll = rng.nextInt(table.totalWeight());
		Symbol last = null;
		for (Symbol s : table.present()) {
			last = s;
			roll -= table.weight(s);
			if (roll < 0) {
				return s;
			}
		}
		return last;
	}

	// ---- score / timeline -----------------------------------------------------------------------

	@Override
	public Outcome score(Tape tape, long[] stakes, Params params) {
		return ShowdownScoring.play(tape, table(params.tierOrCopper()), params.rules()).outcome();
	}

	@Override
	public List<Step> timeline(Tape tape, Outcome outcome, Params params) {
		ShowdownScoring.Match m = ShowdownScoring.play(tape, table(params.tierOrCopper()), params.rules());
		return ShowdownTimeline.build(tape, m, params);
	}

	// ---- JSON -----------------------------------------------------------------------------------

	@Override
	public JsonElement encodeParams(Params p) {
		JsonObject o = new JsonObject();
		o.addProperty("tier", p.tier());
		o.addProperty("spins", p.spins());
		Rules r = p.rules();
		o.addProperty("hot", r.hotSymbol());
		o.addProperty("underdog", r.underdogBoost());
		o.addProperty("kaboom", r.kaboom());
		o.addProperty("swap", r.pearlSwap());
		o.addProperty("star", r.starPoints());
		o.addProperty("interval", r.spinIntervalTicks());
		return o;
	}

	@Override
	public Params decodeParams(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		Rules d = Rules.DEFAULTS;
		Rules r = new Rules(bool(o, "hot", d.hotSymbol()), bool(o, "underdog", d.underdogBoost()), bool(o, "kaboom", d.kaboom()),
			bool(o, "swap", d.pearlSwap()), o.has("star") ? o.get("star").getAsInt() : d.starPoints(),
			o.has("interval") ? o.get("interval").getAsInt() : d.spinIntervalTicks());
		return new Params(o.has("tier") ? o.get("tier").getAsString() : Tier.COPPER.id(), o.has("spins") ? o.get("spins").getAsInt() : 5, r);
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		return o.has(k) ? o.get(k).getAsBoolean() : def;
	}

	private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

	/**
	 * Compact form (PVP.md §3.6: 6 players × 10 spins < 700 chars): one base-36 digit per symbol ordinal /
	 * seat. {@code {"s":"<seats>","h":"<hot per round>","n":<players>,"g":"<players×rounds×9 cells>"}}.
	 */
	@Override
	public JsonElement encodeTape(Tape t) {
		JsonObject o = new JsonObject();
		o.addProperty("s", digits(t.seatOrder()));
		o.addProperty("h", digits(t.hot()));
		StringBuilder g = new StringBuilder();
		for (int[][] player : t.grids()) {
			for (int[] grid : player) {
				g.append(digits(grid));
			}
		}
		o.addProperty("g", g.toString());
		return o;
	}

	@Override
	public Tape decodeTape(JsonElement json) {
		JsonObject o = json.getAsJsonObject();
		int[] seats = undigits(o.get("s").getAsString());
		int[] hot = undigits(o.get("h").getAsString());
		int[] cells = undigits(o.get("g").getAsString());
		int players = seats.length;
		int rounds = hot.length;
		if (cells.length != players * rounds * 9) {
			throw new IllegalArgumentException("slot showdown tape: " + cells.length + " cells for " + players + "×" + rounds);
		}
		int[][][] grids = new int[players][rounds][9];
		int k = 0;
		for (int p = 0; p < players; p++) {
			for (int r = 0; r < rounds; r++) {
				for (int c = 0; c < 9; c++) {
					grids[p][r][c] = cells[k++];
				}
			}
		}
		return new Tape(seats, hot, grids);
	}

	private static String digits(int[] v) {
		StringBuilder sb = new StringBuilder(v.length);
		for (int x : v) {
			if (x < 0 || x >= DIGITS.length()) {
				throw new IllegalArgumentException("tape value out of range: " + x);
			}
			sb.append(DIGITS.charAt(x));
		}
		return sb.toString();
	}

	private static int[] undigits(String s) {
		int[] out = new int[s.length()];
		for (int i = 0; i < s.length(); i++) {
			int d = DIGITS.indexOf(s.charAt(i));
			if (d < 0) {
				throw new IllegalArgumentException("bad tape digit '" + s.charAt(i) + "'");
			}
			out[i] = d;
		}
		return out;
	}

	/** Json int array helper for timeline data. */
	static JsonArray ints(int[] v) {
		JsonArray a = new JsonArray();
		for (int x : v) {
			a.add(x);
		}
		return a;
	}

	static JsonArray longs(long[] v) {
		JsonArray a = new JsonArray();
		for (long x : v) {
			a.add(x);
		}
		return a;
	}
}
