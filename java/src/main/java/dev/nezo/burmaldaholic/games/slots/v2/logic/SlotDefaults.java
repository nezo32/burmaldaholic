package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The normative default machines of SLOTS.md (§2 symbols, §3 paytables and features, §5 jackpots, Appendix A
 * strips) and the validation of configured strips (SLOTS.md §12). Pure; the Bedrock twin is
 * {@code bedrock/src/games/slots/v2/logic/defaults.ts}.
 */
public final class SlotDefaults {
	private SlotDefaults() {}

	/** Symbol codes per machine in SLOTS.md §2 order (index = symbol id in strips and tapes). */
	public static String[] codes(Machine m) {
		return switch (m) {
			case OVERWORLD -> new String[] {"WD", "SC", "BN", "DI", "EM", "GO", "IR", "AP", "CA", "WH", "BE"};
			case NETHER -> new String[] {"WD", "SC", "CN", "SK", "BR", "MC", "QZ", "NW", "CF", "WF", "GD"};
			case END -> new String[] {"WD", "SC", "BN", "DH", "EL", "SS", "CH", "EP", "PU", "ER", "ES"};
		};
	}

	/** Symbol ids (lang {@code gui.burmaldaholic.slots.symbol.<id>}, config {@code slots.<m>.pays} keys), same order. */
	public static String[] symbolIds(Machine m) {
		return switch (m) {
			case OVERWORLD -> new String[] {"totem", "compass", "chest", "diamond", "emerald", "gold_ingot", "iron_ingot", "apple", "carrot",
				"wheat", "sweet_berries"};
			case NETHER -> new String[] {"lava_bucket", "ghast_tear", "piglin_coin", "wither_skull", "blaze_rod", "magma_cream", "quartz",
				"nether_wart", "crimson_fungus", "warped_fungus", "glowstone"};
			case END -> new String[] {"dragon_egg", "ender_eye", "end_crystal", "dragon_head", "elytra", "shulker_shell", "chorus_fruit",
				"ender_pearl", "purpur", "end_rod", "end_stone"};
		};
	}

	public static SymbolRole[] roles(Machine m) {
		SymbolRole third = m == Machine.NETHER ? SymbolRole.COIN : SymbolRole.BONUS;
		SymbolRole[] r = new SymbolRole[11];
		r[0] = SymbolRole.WILD;
		r[1] = SymbolRole.SCATTER;
		r[2] = third;
		for (int i = 3; i < 11; i++) r[i] = SymbolRole.PAY;
		return r;
	}

	/** Appendix A, one string of codes per reel. */
	public static String[] strips(Machine m) {
		return switch (m) {
			case OVERWORLD -> new String[] {
				"AP CA GO BE BE AP AP CA IR CA AP SC DI BN GO WH GO BN BE CA BE EM IR DI EM EM AP BN BE BE WH CA IR WH DI SC WH IR GO WH",
				"CA AP CA BE WH BE WH GO WH GO IR SC GO CA EM AP EM BE BE IR IR WD WD WH WH CA EM DI DI CA IR WD WD BE AP DI AP AP GO BE",
				"AP EM BE IR WH WH BN DI CA DI DI BE GO EM WD WD IR CA BE AP GO AP CA IR CA WD WD GO WH IR WH BE GO AP EM SC AP BE CA BN",
				"AP WH BE AP CA EM BE WH GO AP GO BE WD WD AP WD WD AP CA GO CA IR GO BE EM WH IR IR DI DI CA BE WH DI SC CA WH BE EM IR",
				"BE WH WH BE BE WH EM GO CA BN BE AP IR CA IR AP SC DI EM BE AP DI AP BN GO CA CA BE AP WH GO DI BN EM IR IR SC GO WH CA"};
			case NETHER -> new String[] {
				"NW BR BR WF CF SC NW WF GD BR CF CF MC QZ NW NW MC QZ GD QZ CF WF WF MC GD CN CN NW GD SK SK CF",
				"SK SC BR MC MC WF CF NW WF NW MC NW QZ CF GD GD BR WF WD WF QZ CF GD WD NW SK QZ CN CN CF WF GD",
				"WF NW MC MC CF WF CN QZ QZ NW CF WD CF WF WD GD BR CF SK NW GD CN CN SC GD MC GD WF SK NW QZ BR",
				"CF BR NW QZ NW WF BR QZ CF MC SK CF NW SC WF GD MC SK WD CN CN WF WD WF CF GD WF GD GD QZ MC NW",
				"SK WF CF CN CN SK GD NW WF NW BR CF MC NW BR GD QZ BR CF MC GD QZ WF QZ GD MC NW CF NW CF SC WF"};
			case END -> new String[] {
				"EL PU ES CH SS PU EP CH ES ES PU SC EP EP SS ER PU EP ER CH CH EP ER ER ES DH DH PU EL EP CH ES PU EP ER SS ER ER PU EL ER ES SS ES ES",
				"ER EP ES SS PU PU ER SS ER PU PU CH EL CH ES EP ER EL PU ES ES BN PU ES ES EP ER SS EL DH DH CH ES EP EP BN ES ER CH EP CH WD SC SS ER",
				"CH ER ES ER EP BN PU PU EP CH PU ES EP ES BN ER SS EP EL PU ES CH SS DH DH PU ER ES PU ES BN ES EP WD PU SS EL CH EL EP ER SS ER SC ER",
				"DH DH PU PU CH EL SS PU ES ES ER EP ES ER EL ES SS SS PU EL ES ER ES ER BN CH ER BN SS ER EP PU CH EP EP CH PU CH SC ER ES EP WD ES EP",
				"DH DH PU ER PU CH SC SS PU ER SS EP PU ES EP SS ER CH ER CH ER EP SS EL CH EP EL EP PU ER CH PU ES EP EP ES EL ES ES ER ES PU ES ES ER"};
		};
	}

	/** Pays per paying symbol (index 3…10), 3/4/5 of a kind, fifths of the bet (SLOTS.md §3 tables × 5). */
	public static int[][] pays(Machine m) {
		int[][] p = new int[11][3];
		int[][] rows = switch (m) {
			case OVERWORLD -> new int[][] {{4, 10, 20}, {3, 6, 12}, {2, 4, 8}, {2, 4, 8}, {1, 2, 4}, {1, 2, 4}, {1, 2, 4}, {1, 2, 4}};
			case NETHER -> new int[][] {{4, 10, 40}, {3, 6, 20}, {2, 4, 10}, {2, 4, 8}, {1, 2, 3}, {1, 2, 3}, {1, 1, 3}, {1, 1, 3}};
			case END -> new int[][] {{10, 40, 150}, {5, 20, 60}, {4, 10, 30}, {3, 8, 25}, {2, 3, 10}, {2, 3, 10}, {1, 3, 6}, {1, 3, 6}};
		};
		for (int i = 0; i < 8; i++) p[3 + i] = rows[i];
		return p;
	}

	private static final Map<Machine, MachineDef> DEFAULTS = new EnumMap<>(Machine.class);

	/** The default (SLOTS.md) definition of a machine. Shared instance: never mutate its arrays. */
	public static synchronized MachineDef def(Machine m) {
		return DEFAULTS.computeIfAbsent(m, SlotDefaults::build);
	}

	private static MachineDef build(Machine m) {
		String[] codes = codes(m);
		int[][] strips = new int[5][];
		String[] s = strips(m);
		for (int r = 0; r < 5; r++) strips[r] = parseStrip(codes, s[r]);
		return switch (m) {
			case OVERWORLD -> new MachineDef(m, codes, roles(m), strips, pays(m), new int[] {5, 50, 250}, 0b10101, new int[] {8, 10, 15}, 8, 50, 2,
				new int[0], new int[0], 500, 0, overworldFeatures());
			case NETHER -> new MachineDef(m, codes, roles(m), strips, pays(m), new int[] {0, 0, 0}, 0, new int[] {12, 15, 20}, 5, 60, 1,
				new int[] {1, 2, 3, 5}, new int[] {2, 4, 6, 10}, 2000, 92, netherFeatures());
			case END -> new MachineDef(m, codes, roles(m), strips, pays(m), new int[] {10, 50, 250}, 0b01110, new int[] {9, 11, 14}, 4, 40, 1,
				new int[0], new int[0], 5000, 545, endFeatures());
		};
	}

	/** Hunt contents order = config keys {@code x1 x2 x3 x5 x10 x25 mini minor major grand creeper}. */
	public static final String[] PICK_KEYS = {"x1", "x2", "x3", "x5", "x10", "x25", "mini", "minor", "major", "grand", "creeper"};
	public static final int[] PICK_VALUES = {1, 2, 3, 5, 10, 25, -1, -2, -3, -4, 0};
	public static final int[] PICK_WEIGHTS = {30000, 22000, 14000, 9000, 3500, 800, 600, 150, 20, 3, 22000};
	/** Hoard coins order = config keys {@code x1 x2 x3 x5 x10 x25 mini minor major}. */
	public static final String[] HOLD_KEYS = {"x1", "x2", "x3", "x5", "x10", "x25", "mini", "minor", "major"};
	public static final int[] HOLD_VALUES = {1, 2, 3, 5, 10, 25, -1, -2, -3};
	public static final int[] HOLD_WEIGHTS = {4000, 2500, 1500, 1000, 500, 120, 80, 20, 3};
	/** Jackpot tier keys (config maps), index = tier − 1. */
	public static final String[] TIER_KEYS = {"mini", "minor", "major", "grand"};

	/** Wedge orders of SLOTS.md §3.3 (tokens: integer multiples, MINI MINOR MAJOR GRAND, UP). */
	public static final String[] WHEEL_OUTER = "10 UP 12 15 MINI 10 20 12 25 10 40 15 UP 12 20 MINI 15 10 75 25".split(" ");
	public static final String[] WHEEL_MIDDLE = "30 50 MINOR 75 30 100 50 UP 30 75 MINOR 50 100 30 75 50".split(" ");
	public static final String[] WHEEL_CORE = "150 MAJOR 250 150 GRAND 250 MAJOR 150 500 250 MAJOR 150".split(" ");

	private static MachineDef.Features overworldFeatures() {
		return new MachineDef.Features(15, PICK_VALUES.clone(), PICK_WEIGHTS.clone(), 0, 0, 0, new int[0], new int[0], new int[0][],
			100, new int[] {10, 25, 100, 500}, new int[] {4000, 3000, 2000, 1000}, new int[] {10, 25, 100, 250});
	}

	private static MachineDef.Features netherFeatures() {
		return new MachineDef.Features(0, new int[0], new int[0], 6, 3, 40000, HOLD_VALUES.clone(), HOLD_WEIGHTS.clone(), new int[0][],
			500, new int[] {10, 30, 150, 1000}, new int[] {5000, 4000, 3500, 2500}, new int[] {10, 30, 150, 500});
	}

	private static MachineDef.Features endFeatures() {
		int[][] rings = {wheelTokens(WHEEL_OUTER), wheelTokens(WHEEL_MIDDLE), wheelTokens(WHEEL_CORE)};
		return new MachineDef.Features(0, new int[0], new int[0], 0, 0, 0, new int[0], new int[0], rings,
			5000, new int[] {15, 50, 250, 2500}, new int[] {5000, 5000, 6000, 9000}, new int[] {15, 50, 250, 1000});
	}

	/** Wheel tokens → value codes (multiple, -tier, 0 = UP). Throws on an unknown token. */
	public static int[] wheelTokens(String[] tokens) {
		int[] out = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			String t = tokens[i].trim();
			out[i] = switch (t) {
				case "UP" -> 0;
				case "MINI" -> -1;
				case "MINOR" -> -2;
				case "MAJOR" -> -3;
				case "GRAND" -> -4;
				default -> {
					int v = Integer.parseInt(t);
					if (v <= 0) throw new IllegalArgumentException("wheel multiple must be > 0: " + t);
					yield v;
				}
			};
		}
		return out;
	}

	/** Parses a strip of space-separated codes; throws on an unknown code. */
	public static int[] parseStrip(String[] codes, String strip) {
		String[] parts = strip.trim().split("\\s+");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			int idx = -1;
			for (int c = 0; c < codes.length; c++) if (codes[c].equals(parts[i])) idx = c;
			if (idx < 0) throw new IllegalArgumentException("unknown symbol code '" + parts[i] + "'");
			out[i] = idx;
		}
		return out;
	}

	/**
	 * Strip rules of SLOTS.md §12: known codes (checked by {@link #parseStrip}), Wild only on reels 2–4, the bonus
	 * symbol only on its reels, scatters (and bonus symbols) at cyclic spacing ≥ 3, at least 3 cells per reel.
	 *
	 * @return problems (empty = valid)
	 */
	public static List<String> validateStrips(Machine m, int[][] strips, int bonusReelsMask) {
		List<String> errors = new ArrayList<>();
		SymbolRole[] roles = roles(m);
		if (strips.length != 5) {
			errors.add("exactly 5 strips expected");
			return errors;
		}
		for (int r = 0; r < 5; r++) {
			int[] s = strips[r];
			if (s.length < 3 || s.length > 1000) {
				errors.add("reel " + (r + 1) + ": 3–1000 cells expected");
				continue;
			}
			for (int i = 0; i < s.length; i++) {
				SymbolRole role = roles[s[i]];
				if (role == SymbolRole.WILD && (r == 0 || r == 4)) errors.add("reel " + (r + 1) + ": Wild only on reels 2–4");
				if (role == SymbolRole.BONUS && (bonusReelsMask >> r & 1) == 0) errors.add("reel " + (r + 1) + ": bonus symbol not allowed");
				if (role == SymbolRole.SCATTER || role == SymbolRole.BONUS) {
					for (int d = 1; d < 3; d++) {
						if (s[(i + d) % s.length] == s[i] && s.length > d) errors.add("reel " + (r + 1) + ": " + codes(m)[s[i]] + " spacing < 3");
					}
				}
			}
		}
		return errors.stream().distinct().toList();
	}
}
