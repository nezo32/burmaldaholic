package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * Translation keys of the table UI v2 kit (docs/design/STRINGS.md, core › "Table UI v2 kit"; docs/architecture/ui-v2.md
 * §10). Flows and screens use these constants so a typo is a compile error; every key exists in EN and RU (unit test).
 */
public final class V2Keys {
	private V2Keys() {}

	public static final String P = "gui.burmaldaholic.core.v2.";

	// ---- dock / readout
	public static final String BET = P + "bet";
	public static final String ON_TABLE = P + "on_table";
	/** Plural base: {@code spots.p1 … .p5}, arg = count. */
	public static final String SPOTS = P + "spots";
	public static final String LIMITS = P + "limits";
	public static final String SEATS = P + "seats";
	public static final String NET_PLUS = P + "net.plus";
	public static final String NET_MINUS = P + "net.minus";
	public static final String CHIP = P + "chip";
	public static final String SIT = P + "sit";

	// ---- tools (narration / tooltips; the buttons show icons)
	public static final String TOOL_UNDO = P + "tool.undo";
	public static final String TOOL_CLEAR = P + "tool.clear";
	public static final String TOOL_REBET = P + "tool.rebet";
	public static final String TOOL_DOUBLE = P + "tool.double";

	// ---- top bar icons
	public static final String TOP_RULES = P + "top.rules";
	public static final String TOP_SETTINGS = P + "top.settings";
	public static final String TOP_LEAVE = P + "top.leave";

	// ---- primary labels
	public static final String PRIMARY_SIT = P + "primary.sit";
	public static final String PRIMARY_DEAL = P + "primary.deal";
	public static final String PRIMARY_REBET = P + "primary.rebet";
	public static final String PRIMARY_LEAVE = P + "primary.leave";
	public static final String PRIMARY_WAIT = P + "primary.wait";

	// ---- reasons (why a gate is disabled)
	public static final String REASON_PLACE_BET = P + "reason.place_bet";
	public static final String REASON_SIT_TO_BET = P + "reason.sit_to_bet";
	public static final String REASON_BELOW_MIN = P + "reason.below_min";
	public static final String REASON_ABOVE_MAX = P + "reason.above_max";
	public static final String REASON_NOT_ENOUGH = P + "reason.not_enough";
	public static final String REASON_NOT_ENOUGH_MIN = P + "reason.not_enough_min";
	public static final String REASON_TABLE_FULL = P + "reason.table_full";
	public static final String REASON_BETS_CLOSED = P + "reason.bets_closed";
	public static final String REASON_BETS_OPEN_IN = P + "reason.bets_open_in";
	public static final String REASON_ROUND_IN_PROGRESS = P + "reason.round_in_progress";
	public static final String REASON_WAITING_FOR = P + "reason.waiting_for";
	public static final String REASON_NO_ANSWER = P + "reason.no_answer";
	public static final String REASON_SPINNING = P + "reason.spinning";
	public static final String REASON_ROLLING = P + "reason.rolling";
	public static final String REASON_DEALING = P + "reason.dealing";
	public static final String REASON_NOTHING_TO_UNDO = P + "reason.nothing_to_undo";
	public static final String REASON_NOTHING_TO_CLEAR = P + "reason.nothing_to_clear";
	public static final String REASON_NO_REBET = P + "reason.no_rebet";
	public static final String REASON_CANNOT_DOUBLE = P + "reason.cannot_double";

	// ---- status hints
	public static final String STATUS_PICK_SEAT = P + "status.pick_seat";
	public static final String STATUS_CLICK_LAYOUT = P + "status.click_layout";
	public static final String STATUS_CHIPS_ADD = P + "status.chips_add";
	public static final String STATUS_NEXT_ROUND_IN = P + "status.next_round_in";
	public static final String STATUS_PENDING = P + "status.pending";

	// ---- stamps and banner
	public static final String STAMP_WIN = P + "stamp.win";
	public static final String STAMP_LOSE = P + "stamp.lose";
	public static final String STAMP_PUSH = P + "stamp.push";
	public static final String STAMP_BUST = P + "stamp.bust";
	public static final String STAMP_BLACKJACK = P + "stamp.blackjack";
	public static final String STAMP_NATURAL = P + "stamp.natural";
	public static final String STAMP_SURRENDER = P + "stamp.surrender";
	public static final String BANNER_YOU_WIN = P + "banner.you_win";
	public static final String BANNER_YOU_LOST = P + "banner.you_lost";
	public static final String BANNER_PUSH = P + "banner.push";

	/** Every key above (the EN / RU presence test iterates it). */
	public static final String[] ALL = {BET, ON_TABLE, SPOTS + ".p1", SPOTS + ".p21", SPOTS + ".p2", SPOTS + ".p5", LIMITS, SEATS, NET_PLUS,
		NET_MINUS, CHIP, SIT, TOOL_UNDO, TOOL_CLEAR, TOOL_REBET, TOOL_DOUBLE, TOP_RULES, TOP_SETTINGS, TOP_LEAVE, PRIMARY_SIT, PRIMARY_DEAL,
		PRIMARY_REBET, PRIMARY_LEAVE, PRIMARY_WAIT, REASON_PLACE_BET, REASON_SIT_TO_BET, REASON_BELOW_MIN, REASON_ABOVE_MAX, REASON_NOT_ENOUGH,
		REASON_NOT_ENOUGH_MIN, REASON_TABLE_FULL, REASON_BETS_CLOSED, REASON_BETS_OPEN_IN, REASON_ROUND_IN_PROGRESS, REASON_WAITING_FOR,
		REASON_NO_ANSWER, REASON_SPINNING, REASON_ROLLING, REASON_DEALING, REASON_NOTHING_TO_UNDO, REASON_NOTHING_TO_CLEAR, REASON_NO_REBET,
		REASON_CANNOT_DOUBLE, STATUS_PICK_SEAT, STATUS_CLICK_LAYOUT, STATUS_CHIPS_ADD, STATUS_NEXT_ROUND_IN, STATUS_PENDING, STAMP_WIN, STAMP_LOSE,
		STAMP_PUSH, STAMP_BUST, STAMP_BLACKJACK, STAMP_NATURAL, STAMP_SURRENDER, BANNER_YOU_WIN, BANNER_YOU_LOST, BANNER_PUSH};
}
