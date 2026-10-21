package dev.nezo.burmaldaholic.core.ui.v2;

import dev.nezo.burmaldaholic.core.wager.BetCap;

/**
 * The shared gates of the bet flow (docs/architecture/ui-v2.md §5): chips, the four bet tools, Sit down and the generic
 * "place your bet, then press the primary" button. Every game's {@code <G>Flow} starts from these so the reasons are the
 * same at every table; a game overrides only what its rules change. Pure.
 *
 * <p>Gate actions here are the kit's ids ({@code chip}, {@code undo}, {@code clear}, {@code rebet}, {@code double},
 * {@code sit}); what is actually sent is decided by the screen's {@code BetPort}.
 */
public final class BetGates {
	private BetGates() {}

	public static final String UNDO = "undo", CLEAR = "clear", REBET = "rebet", DOUBLE = "double", SIT = "sit", CHIP = "chip";
	/** Tool ids in the Dock's fixed order. */
	public static final String[] TOOLS = {UNDO, CLEAR, REBET, DOUBLE};

	/** Why the viewer cannot put chips down now (null = they can). */
	public static Gate closedReason(String action, String labelKey, BetView v) {
		if (!v.seated()) return Gate.off(action, labelKey, V2Keys.REASON_SIT_TO_BET);
		if (!v.bettingOpen()) return waiting(action, labelKey, v);
		if (v.balance() <= 0) return Gate.off(action, labelKey, V2Keys.REASON_NOT_ENOUGH);
		return null;
	}

	/** The "not now" gate: waiting for a named player, a countdown, or a round in progress. */
	public static Gate waiting(String action, String labelKey, BetView v) {
		if (!v.waitingFor().isEmpty()) return Gate.off(action, labelKey, V2Keys.REASON_WAITING_FOR, v.waitingFor());
		return switch (v.phase()) {
			case BETTING, IDLE -> v.secondsLeft() >= 0 ? Gate.off(action, labelKey, V2Keys.REASON_BETS_OPEN_IN, v.secondsLeft())
				: Gate.off(action, labelKey, V2Keys.REASON_BETS_CLOSED);
			case LOCKED -> Gate.off(action, labelKey, V2Keys.REASON_BETS_CLOSED);
			case ACTING, SETTLING -> Gate.off(action, labelKey, V2Keys.REASON_ROUND_IN_PROGRESS);
		};
	}

	/** A chip of the tray. */
	public static Gate chip(long denom, BetView v) {
		Gate closed = closedReason(CHIP, V2Keys.CHIP, v);
		if (closed != null) return closed;
		if (denom > v.balance()) return Gate.off(CHIP, V2Keys.CHIP, V2Keys.REASON_NOT_ENOUGH);
		if (denom > v.roomUnderMax()) return aboveMax(CHIP, V2Keys.CHIP, v);
		return new Gate(CHIP, V2Keys.CHIP, new Object[] {denom}, true, null, null);
	}

	/** One of {@link #TOOLS}. */
	public static Gate tool(String id, BetView v) {
		String label = switch (id) {
			case UNDO -> V2Keys.TOOL_UNDO;
			case CLEAR -> V2Keys.TOOL_CLEAR;
			case REBET -> V2Keys.TOOL_REBET;
			case DOUBLE -> V2Keys.TOOL_DOUBLE;
			default -> throw new IllegalArgumentException("unknown tool " + id);
		};
		Gate closed = closedReason(id, label, v);
		if (closed != null && !(closed.reasonKey().equals(V2Keys.REASON_NOT_ENOUGH) && (id.equals(UNDO) || id.equals(CLEAR)))) return closed;
		return switch (id) {
			case UNDO -> v.canUndo() ? Gate.on(id, label) : Gate.off(id, label, V2Keys.REASON_NOTHING_TO_UNDO);
			case CLEAR -> v.canClear() ? Gate.on(id, label) : Gate.off(id, label, V2Keys.REASON_NOTHING_TO_CLEAR);
			case REBET -> !v.canRebet() || v.rebetAmount() <= 0 ? Gate.off(id, label, V2Keys.REASON_NO_REBET)
				: v.rebetAmount() > v.balance() ? Gate.off(id, label, V2Keys.REASON_NOT_ENOUGH) : Gate.on(id, label, v.rebetAmount());
			default -> !v.canDouble() || v.myTotal() <= 0 ? Gate.off(id, label, V2Keys.REASON_CANNOT_DOUBLE)
				: v.myTotal() > v.balance() ? Gate.off(id, label, V2Keys.REASON_NOT_ENOUGH)
				: v.myTotal() * 2 > (v.max() <= 0 ? Long.MAX_VALUE : v.max()) ? aboveMax(id, label, v)
				: Gate.on(id, label);
		};
	}

	/**
	 * "Not more than the maximum", in the words of WHOSE maximum it is (owner playtest B5): the VIP tier and the tier that
	 * allows more, the table, the table's owner ({@link BetView#cap}); the plain "The maximum bet is N" when unknown.
	 */
	public static Gate aboveMax(String action, String labelKey, BetView v) {
		BetCap c = v.cap();
		if (c.source() == BetCap.Source.NONE) return Gate.off(action, labelKey, V2Keys.REASON_ABOVE_MAX, v.max());
		BetCap at = c.max() == v.max() || v.max() <= 0 ? c : c.withMax(v.max());
		return Gate.off(action, labelKey, at.reasonKey(), at.reasonArgs());
	}

	/** "Not less than the minimum": the table owner's minimum when it is theirs. */
	public static Gate belowMin(String action, String labelKey, BetView v) {
		return Gate.off(action, labelKey, v.cap().ownerMin() ? BetCap.KEY_OWNER_MIN : V2Keys.REASON_BELOW_MIN, v.min());
	}

	/** "Not more than you have": the balance. */
	public static Gate overBalance(String action, String labelKey, BetView v) {
		return Gate.off(action, labelKey, BetCap.KEY_BALANCE, Math.max(0, v.balance()));
	}

	/** The Sit down primary (viewer not seated, ui-v2.md §2.3). */
	public static Gate sit(BetView v) {
		if (!v.canSit()) return Gate.off(SIT, V2Keys.PRIMARY_SIT, V2Keys.REASON_TABLE_FULL);
		if (!v.affordsMin()) return Gate.off(SIT, V2Keys.PRIMARY_SIT, V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
		return Gate.on(SIT, V2Keys.PRIMARY_SIT);
	}

	/**
	 * The generic primary of a betting game: Sit down → (place a bet / reach the minimum) → {@code action} with
	 * {@code labelKey}; outside betting the waiting reason. Games with a rebet shortcut use {@link #betOrRebet}.
	 */
	public static Gate primary(String action, String labelKey, BetView v) {
		if (!v.seated()) return sit(v);
		if (!v.bettingOpen()) return waiting(action, labelKey, v);
		if (v.myTotal() <= 0) {
			return v.affordsMin() ? Gate.off(action, labelKey, V2Keys.REASON_PLACE_BET)
				: Gate.off(action, labelKey, V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
		}
		if (v.myTotal() < v.min()) return belowMin(action, labelKey, v);
		if (v.max() > 0 && v.myTotal() > v.max()) return aboveMax(action, labelKey, v);
		return Gate.on(action, labelKey);
	}

	/** {@link #primary}, except that with no bet down and a rebet available the primary is {@code Rebet <n>}. */
	public static Gate betOrRebet(String action, String labelKey, BetView v) {
		if (v.seated() && v.bettingOpen() && v.myTotal() <= 0) {
			Gate r = tool(REBET, v);
			if (r.enabled()) return new Gate(REBET, V2Keys.PRIMARY_REBET, new Object[] {v.rebetAmount()}, true, null, null);
		}
		return primary(action, labelKey, v);
	}

	/**
	 * The chip a felt click places when no chip is armed (§5.2-4, review R-CDE m1): the last used one when it is still
	 * offered, at least {@code min} and affordable; else the smallest offered chip of at least {@code min} the balance
	 * covers; else the smallest affordable one (a table whose minimum is above every chip); -1 = none affordable.
	 */
	public static long armDefault(long[] chips, long min, long balance, long lastUsed) {
		boolean offered = false;
		for (long c : chips) offered |= c == lastUsed;
		if (offered && lastUsed > 0 && lastUsed >= min && lastUsed <= balance) return lastUsed;
		long fallback = -1;
		for (long c : chips) {
			if (c > balance || c <= 0) continue;
			if (c >= min) return c;
			if (fallback < 0) fallback = c;
		}
		return offered && lastUsed > 0 && lastUsed <= balance ? lastUsed : fallback;
	}

	/** {@link #armDefault(long[], long, long, long)} with an unlimited balance (chips already filtered by it). */
	public static long armDefault(long[] chips, long min, long lastUsed) {
		return armDefault(chips, min, Long.MAX_VALUE, lastUsed);
	}
}
