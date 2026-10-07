package dev.nezo.burmaldaholic.games.extras.present;

/**
 * Translation keys of the v2 extras and PvP screens (docs/design/STRINGS.md, extras › "Table UI v2 (lane E)" and pvp ›
 * "Table UI v2 (lane E)"). Flows and screens use these constants; {@code ExtrasFlowTest} asserts every key exists in EN
 * and RU.
 */
public final class ExtrasKeys {
	private ExtrasKeys() {}

	public static final String P = "gui.burmaldaholic.extras.v2.";

	// primary labels
	public static final String DROP = P + "primary.drop";
	public static final String SPIN = P + "primary.spin";
	public static final String FLIP = P + "primary.flip";
	public static final String ROLL = P + "primary.roll";
	public static final String SCRATCH_ALL = P + "primary.scratch_all";
	public static final String BUY = P + "primary.buy";
	public static final String NEXT_CARD = P + "primary.next_card";

	// reasons
	public static final String REASON_DROPPING = P + "reason.dropping";
	public static final String REASON_FLIPPING = P + "reason.flipping";
	public static final String REASON_SCRATCHING = P + "reason.scratching";
	public static final String REASON_PICK_SIDE = P + "reason.pick_side";
	public static final String REASON_NO_ITEM = P + "reason.no_item";
	public static final String REASON_STAKE_MIN = P + "reason.stake_min";
	public static final String REASON_STAKE_MAX = P + "reason.stake_max";
	public static final String REASON_SOUL = P + "reason.soul";

	// status hints
	public static final String HINT_CHIPS = P + "hint.chips";
	public static final String HINT_WIN_UP_TO = P + "hint.win_up_to";
	public static final String HINT_SCRATCH = P + "hint.scratch";
	public static final String HINT_STAKE = P + "hint.stake";

	// stake (non-chip) actions
	public static final String STAKE_MINUS = P + "stake.minus";
	public static final String STAKE_PLUS = P + "stake.plus";

	// play-area captions
	public static final String RISK = P + "risk";
	public static final String LAST_BALLS = P + "last_balls";
	public static final String TOP_PRIZE = P + "top_prize";
	public static final String SEGMENTS = P + "segments";
	public static final String YOUR_CALL = P + "your_call";
	public static final String LAST_FLIPS = P + "last_flips";
	public static final String PRIZES = P + "prizes";
	public static final String TICKET = P + "ticket";
	public static final String STAKE = P + "stake";
	public static final String CHALLENGE = P + "challenge";

	public static final String[] ALL = {DROP, SPIN, FLIP, ROLL, SCRATCH_ALL, BUY, NEXT_CARD, REASON_DROPPING, REASON_FLIPPING, REASON_SCRATCHING,
		REASON_PICK_SIDE, REASON_NO_ITEM, REASON_STAKE_MIN, REASON_STAKE_MAX, REASON_SOUL, HINT_CHIPS, HINT_WIN_UP_TO, HINT_SCRATCH, HINT_STAKE, STAKE_MINUS,
		STAKE_PLUS, RISK, LAST_BALLS, TOP_PRIZE, SEGMENTS, YOUR_CALL, LAST_FLIPS, PRIZES, TICKET, STAKE, CHALLENGE};
}
