package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;

/**
 * PREVIEW FIXTURE (lane J-L9): the three default machines of SLOTS.md §3 / Appendix A as {@link MachineDef}s, so the
 * slot screen, its preview mode and the fidelity tests can run before the v2 config (lane J-L8, S-J4) exists. The
 * real game builds its defs from {@code slots.<m>.*} config and sends them to the client with the machine state;
 * nothing here is used to settle money.
 */
public final class PreviewMachines {
	private static final SymbolRole[] ROLES_BN = {SymbolRole.WILD, SymbolRole.SCATTER, SymbolRole.BONUS, SymbolRole.PAY, SymbolRole.PAY,
		SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY};
	private static final SymbolRole[] ROLES_CN = {SymbolRole.WILD, SymbolRole.SCATTER, SymbolRole.COIN, SymbolRole.PAY, SymbolRole.PAY,
		SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY, SymbolRole.PAY};

	private static final String[] OW_CODES = {"WD", "SC", "BN", "DI", "EM", "GO", "IR", "AP", "CA", "WH", "BE"};
	private static final String[] NE_CODES = {"WD", "SC", "CN", "SK", "BR", "MC", "QZ", "NW", "CF", "WF", "GD"};
	private static final String[] END_CODES = {"WD", "SC", "BN", "DH", "EL", "SS", "CH", "EP", "PU", "ER", "ES"};

	private static final String[] OW_STRIPS = {
		"AP CA GO BE BE AP AP CA IR CA AP SC DI BN GO WH GO BN BE CA BE EM IR DI EM EM AP BN BE BE WH CA IR WH DI SC WH IR GO WH",
		"CA AP CA BE WH BE WH GO WH GO IR SC GO CA EM AP EM BE BE IR IR WD WD WH WH CA EM DI DI CA IR WD WD BE AP DI AP AP GO BE",
		"AP EM BE IR WH WH BN DI CA DI DI BE GO EM WD WD IR CA BE AP GO AP CA IR CA WD WD GO WH IR WH BE GO AP EM SC AP BE CA BN",
		"AP WH BE AP CA EM BE WH GO AP GO BE WD WD AP WD WD AP CA GO CA IR GO BE EM WH IR IR DI DI CA BE WH DI SC CA WH BE EM IR",
		"BE WH WH BE BE WH EM GO CA BN BE AP IR CA IR AP SC DI EM BE AP DI AP BN GO CA CA BE AP WH GO DI BN EM IR IR SC GO WH CA"};
	private static final String[] NE_STRIPS = {
		"NW BR BR WF CF SC NW WF GD BR CF CF MC QZ NW NW MC QZ GD QZ CF WF WF MC GD CN CN NW GD SK SK CF",
		"SK SC BR MC MC WF CF NW WF NW MC NW QZ CF GD GD BR WF WD WF QZ CF GD WD NW SK QZ CN CN CF WF GD",
		"WF NW MC MC CF WF CN QZ QZ NW CF WD CF WF WD GD BR CF SK NW GD CN CN SC GD MC GD WF SK NW QZ BR",
		"CF BR NW QZ NW WF BR QZ CF MC SK CF NW SC WF GD MC SK WD CN CN WF WD WF CF GD WF GD GD QZ MC NW",
		"SK WF CF CN CN SK GD NW WF NW BR CF MC NW BR GD QZ BR CF MC GD QZ WF QZ GD MC NW CF NW CF SC WF"};
	private static final String[] END_STRIPS = {
		"EL PU ES CH SS PU EP CH ES ES PU SC EP EP SS ER PU EP ER CH CH EP ER ER ES DH DH PU EL EP CH ES PU EP ER SS ER ER PU EL ER ES SS ES ES",
		"ER EP ES SS PU PU ER SS ER PU PU CH EL CH ES EP ER EL PU ES ES BN PU ES ES EP ER SS EL DH DH CH ES EP EP BN ES ER CH EP CH WD SC SS ER",
		"CH ER ES ER EP BN PU PU EP CH PU ES EP ES BN ER SS EP EL PU ES CH SS DH DH PU ER ES PU ES BN ES EP WD PU SS EL CH EL EP ER SS ER SC ER",
		"DH DH PU PU CH EL SS PU ES ES ER EP ES ER EL ES SS SS PU EL ES ER ES ER BN CH ER BN SS ER EP PU CH EP EP CH PU CH SC ER ES EP WD ES EP",
		"DH DH PU ER PU CH SC SS PU ER SS EP PU ES EP SS ER CH ER CH ER EP SS EL CH EP EL EP PU ER CH PU ES EP EP ES EL ES ES ER ES PU ES ES ER"};

	/** Pays in fifths of the bet for 3 / 4 / 5 (SLOTS.md §3; ×bet × 5). */
	private static final int[][] OW_PAYS = {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {4, 10, 20}, {3, 6, 12}, {2, 4, 8}, {2, 4, 8}, {1, 2, 4}, {1, 2, 4},
		{1, 2, 4}, {1, 2, 4}};
	private static final int[][] NE_PAYS = {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {4, 10, 40}, {3, 6, 20}, {2, 4, 10}, {2, 4, 8}, {1, 2, 3}, {1, 2, 3},
		{1, 1, 3}, {1, 1, 3}};
	private static final int[][] END_PAYS = {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {10, 40, 150}, {5, 20, 60}, {4, 10, 30}, {3, 8, 25}, {2, 3, 10},
		{2, 3, 10}, {1, 3, 6}, {1, 3, 6}};

	/** Dragon Wheel wedges clockwise from the pointer at rest (SLOTS.md §3.3); positive = ×bet, negative = jackpot tier, 0 = UP. */
	public static final int[] WHEEL_OUTER = {10, 0, 12, 15, -1, 10, 20, 12, 25, 10, 40, 15, 0, 12, 20, -1, 15, 10, 75, 25};
	public static final int[] WHEEL_MIDDLE = {30, 50, -2, 75, 30, 100, 50, 0, 30, 75, -2, 50, 100, 30, 75, 50};
	public static final int[] WHEEL_CORE = {150, -3, 250, 150, -4, 250, -3, 150, 500, 250, -3, 150};

	/** Wheel ring wedges by ring index 0 outer, 1 middle, 2 core. */
	public static int[] wheelRing(int ring) {
		return switch (ring) {
			case 0 -> WHEEL_OUTER;
			case 1 -> WHEEL_MIDDLE;
			default -> WHEEL_CORE;
		};
	}

	/** Treasure Hunt contents and weights (SLOTS.md §3.1): value codes 1,2,3,5,10,25, -1..-4, 0 = Creeper. */
	public static final int[] HUNT_VALUES = {1, 2, 3, 5, 10, 25, -1, -2, -3, -4, 0};
	public static final int[] HUNT_WEIGHTS = {30000, 22000, 14000, 9000, 3500, 800, 600, 150, 20, 3, 22000};
	/** Piglin coin values and weights ×10 (SLOTS.md §3.2). */
	public static final int[] COIN_VALUES = {1, 2, 3, 5, 10, 25, -1, -2, -3};
	public static final int[] COIN_WEIGHTS = {4000, 2500, 1500, 1000, 500, 120, 80, 20, 3};

	private static final MachineDef[] DEFS = {
		new MachineDef(Machine.OVERWORLD, OW_CODES, ROLES_BN, strips(OW_CODES, OW_STRIPS), OW_PAYS, new int[] {5, 50, 250}, 0b10101,
			new int[] {8, 10, 15}, 8, 50, 2, new int[0], new int[0], 500, 0),
		new MachineDef(Machine.NETHER, NE_CODES, ROLES_CN, strips(NE_CODES, NE_STRIPS), NE_PAYS, new int[] {0, 0, 0}, 0,
			new int[] {12, 15, 20}, 5, 60, 1, new int[] {1, 2, 3, 5}, new int[] {2, 4, 6, 10}, 2000, 92),
		new MachineDef(Machine.END, END_CODES, ROLES_BN, strips(END_CODES, END_STRIPS), END_PAYS, new int[] {10, 50, 250}, 0b01110,
			new int[] {9, 11, 14}, 4, 40, 1, new int[0], new int[0], 5000, 545)};

	private PreviewMachines() {}

	public static MachineDef def(Machine m) {
		return DEFS[m.ordinal()];
	}

	private static int[][] strips(String[] codes, String[] rows) {
		int[][] out = new int[rows.length][];
		for (int r = 0; r < rows.length; r++) {
			String[] cells = rows[r].trim().split(" +");
			out[r] = new int[cells.length];
			for (int i = 0; i < cells.length; i++) out[r][i] = indexOf(codes, cells[i]);
		}
		return out;
	}

	private static int indexOf(String[] codes, String code) {
		for (int i = 0; i < codes.length; i++) if (codes[i].equals(code)) return i;
		throw new IllegalArgumentException("unknown symbol code " + code);
	}
}
