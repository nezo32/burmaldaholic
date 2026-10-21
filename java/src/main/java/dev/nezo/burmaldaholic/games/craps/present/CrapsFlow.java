package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import dev.nezo.burmaldaholic.games.craps.present.CrapsView.BetPub;

/**
 * The craps v2 screen's decisions as pure functions of a {@link CrapsView} (ui-v2.md §5): the PrimaryButton (Sit down /
 * Roll, always with a reason when it cannot be pressed), the chips, Undo / Clear, the two status lines and what a click
 * on a felt spot does. A click never does nothing: it places a flat bet, adds odds, or names the reason.
 */
public final class CrapsFlow {
	public static final String K = "gui.burmaldaholic.craps.v2.";

	private CrapsFlow() {}

	/** A translatable line (key + plain arguments: numbers and names). */
	public record Line(String key, Object... args) {}

	/** The two status lines of the Dock (line 1 gold, line 2 muted). */
	public record Status(Line line1, Line line2) {}

	/** What a felt click does: send {@code action} with its arguments, or show {@code errorKey}. */
	public record Click(String action, String kind, int betId, long amount, String errorKey, Object[] errorArgs) {
		public static Click bet(String kind, long amount) {
			return new Click("bet", kind, -1, amount, null, new Object[0]);
		}

		public static Click odds(int betId, long amount) {
			return new Click("odds", "", betId, amount, null, new Object[0]);
		}

		public static Click error(String key, Object... args) {
			return new Click("", "", -1, 0, key, args);
		}

		public boolean sends() {
			return !action.isEmpty();
		}
	}

	public static Gate primary(CrapsView v) {
		if (!v.enabled()) return Gate.off("roll", K + "primary.roll", K + "reason.disabled");
		if (!v.seated()) {
			if (!v.seatFree()) return Gate.off(BetGates.SIT, V2Keys.PRIMARY_SIT, V2Keys.REASON_TABLE_FULL);
			if (v.balance() < v.min()) return Gate.off(BetGates.SIT, V2Keys.PRIMARY_SIT, V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
			return Gate.on(BetGates.SIT, V2Keys.PRIMARY_SIT);
		}
		if (v.rolling()) return Gate.off("roll", K + "primary.roll", V2Keys.REASON_ROLLING);
		boolean hasLine = v.mine().stream().anyMatch(b -> b.kind().equals("pass") || b.kind().equals("dont_pass"));
		if (v.comeOut() && !hasLine && !v.canRoll() && v.balance() < v.min()) {
			return Gate.off("roll", K + "primary.roll", V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
		}
		if (v.youShoot()) {
			// D5: the chips and the stickman finish before the next throw (skip with Space / a click)
			if (v.canRoll()) return v.settling() ? Gate.off("roll", K + "primary.roll", K + "reason.wait_settling") : Gate.on("roll", K + "primary.roll");
			if (v.needLine()) return Gate.off("roll", K + "primary.roll", K + "reason.need_line");
			if (v.windowSeconds() >= 0) return Gate.off("roll", K + "primary.roll", K + "reason.wait_roll", v.windowSeconds());
			return Gate.off("roll", K + "primary.roll", K + "reason.wait_settling");
		}
		if (v.shooter().isEmpty()) {
			if (v.balance() < v.min()) return Gate.off("roll", K + "primary.roll", V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
			return Gate.off("roll", K + "primary.roll", K + "reason.no_shooter");
		}
		return Gate.off("roll", K + "primary.roll", K + "reason.waiting_for", v.shooter());
	}

	/** The chip tray: a chip can be put down now (a felt click places it or says why the spot is closed). */
	public static Gate chips(CrapsView v) {
		if (!v.enabled()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, K + "reason.disabled");
		if (!v.seated() && !v.seatFree()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_TABLE_FULL);
		boolean flat = v.balance() >= v.min() && !v.allowed().isEmpty();
		boolean odds = v.balance() > 0 && !v.oddsTargets().isEmpty();
		if (flat || odds) return Gate.on(BetGates.CHIP, V2Keys.CHIP);
		if (v.balance() < v.min()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
		return Gate.off(BetGates.CHIP, V2Keys.CHIP, K + "reason.all_down");
	}

	/** Undo ({@code tool.undo}) and Clear ({@code tool.clear}): only chips put down since the last roll. */
	public static Gate tool(String id, CrapsView v) {
		String label = BetGates.CLEAR.equals(id) ? V2Keys.TOOL_CLEAR : V2Keys.TOOL_UNDO;
		if (!BetGates.UNDO.equals(id) && !BetGates.CLEAR.equals(id)) return Gate.off(id, label, K + "reason.no_tool");
		// R-CDE M5: nothing is taken back while the throw is shown (no chip can go down then either: the felt is closed)
		if (v.rolling()) return Gate.off(id, label, V2Keys.REASON_ROLLING);
		if (v.canUndo()) return Gate.on(id, label);
		return Gate.off(id, label, "gui.burmaldaholic.craps.nothing_to_undo");
	}

	/** The kit's view of this flow (the screen's chrome and {@code FlowCheck}). */
	public static final Flow<CrapsView> FLOW = new Flow<>() {
		@Override
		public Gate primary(CrapsView v) {
			return CrapsFlow.primary(v);
		}

		@Override
		public BetView bet(CrapsView v) {
			Map<String, Long> spots = new LinkedHashMap<>();
			for (BetPub b : v.mine()) spots.merge(b.kind() + (b.point() != 0 ? "@" + b.point() : ""), b.staked(), Long::sum);
			long secs = v.windowSeconds() >= 0 ? v.windowSeconds() : v.rollSeconds();
			return BetView.builder().seated(v.seated()).canSit(v.seatFree()).phase(BetView.Phase.BETTING).bettingOpen(CrapsFlow.chips(v).enabled())
				.limits(v.min(), v.max()).balance(v.balance()).myTotal(v.myTotal()).spots(spots).canUndo(v.canUndo()).canClear(v.canUndo())
				.waitingFor(v.youShoot() ? "" : v.shooter()).secondsLeft((int) Math.min(Integer.MAX_VALUE, secs)).build();
		}

		@Override
		public Gate chip(long denom, CrapsView v) {
			Gate g = CrapsFlow.chips(v);
			if (!g.enabled()) return g;
			// odds take any chip the balance covers (snapped to their unit on the click)
			if (denom > v.balance()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_NOT_ENOUGH);
			return new Gate(BetGates.CHIP, V2Keys.CHIP, new Object[] {denom}, true, null, null);
		}

		@Override
		public Gate tool(String id, CrapsView v) {
			return CrapsFlow.tool(id, v);
		}
	};

	public static Status status(CrapsView v) {
		Line one = v.point() == 0 ? new Line(K + "status.come_out") : new Line(K + "status.point", v.point());
		Line two;
		if (!v.seated()) two = new Line(K + "status.sit_or_bet");
		else if (v.rolling()) two = new Line(K + "status.rolling", v.shooter());
		else if (v.youShoot() && v.needLine()) two = new Line(K + "reason.need_line");
		else if (v.windowSeconds() >= 0) two = new Line(K + "status.bets_close_in", v.windowSeconds());
		else if (v.youShoot() && v.canRoll() && v.rollSeconds() >= 0) two = new Line(K + "status.roll_in", v.rollSeconds());
		else if (v.youShoot() && v.canRoll()) two = new Line(K + "status.your_roll");
		else if (!v.shooter().isEmpty() && !v.youShoot()) two = new Line(K + "reason.waiting_for", v.shooter());
		else if (v.comeOut() && v.mine().isEmpty()) two = new Line(K + "status.bet_pass");
		else two = new Line(K + "status.click_layout");
		return new Status(one, two);
	}

	/** What clicking the felt spot {@code spotId} with the {@code chip} selected does. */
	public static Click click(CrapsView v, String spotId, long chip) {
		if (!v.enabled()) return Click.error(K + "reason.disabled");
		// R-CDE M5: the felt is closed while the dice fly (the bets drawn then are the ones the roll saw)
		if (v.rolling()) return Click.error(V2Keys.REASON_ROLLING);
		String kind = CrapsLayout.kindOf(spotId);
		if ("point".equals(kind)) {
			int p = CrapsLayout.pointOf(spotId);
			BetPub target = null;
			for (BetPub b : v.oddsTargets()) {
				if (b.point() == p && ("come".equals(b.kind()) || "dont_come".equals(b.kind()))) {
					if (target == null || "come".equals(b.kind())) target = b;
				}
			}
			if (target != null) return odds(target, chip, v);
			return Click.error(v.comeOut() ? K + "reason.point_box_come_out" : K + "reason.point_box");
		}
		if ("pass_odds".equals(kind)) {
			BetPub target = lineBet(v, "pass");
			if (target != null && target.room() > 0) return odds(target, chip, v);
			// R-CDE M6: the odds lane never places the Pass line itself (on the come-out it names the reason)
			if (target != null && v.point() != 0) return Click.error(K + "reason.odds_full");
			return Click.error(v.comeOut() ? K + "reason.odds_need_point" : K + "reason.odds_need_pass");
		}
		if (v.allowed(kind)) return flat(v, kind, chip);
		// the flat bet is down already (or the line is closed): odds behind it
		BetPub target = null;
		for (BetPub b : v.oddsTargets()) {
			if (b.kind().equals(kind) && b.point() == 0) target = b;
		}
		if (target != null) return odds(target, chip, v);
		return switch (kind) {
			case "pass", "dont_pass" -> v.point() != 0 ? Click.error("gui.burmaldaholic.craps.line_only_come_out") : Click.error(K + "reason.already_placed");
			case "come", "dont_come" -> v.point() == 0 ? Click.error(K + "reason.come_needs_point") : Click.error(K + "reason.already_placed");
			case "field" -> Click.error(K + "reason.already_placed");
			default -> Click.error("gui.burmaldaholic.error.invalid_bet_position");
		};
	}

	private static Click flat(CrapsView v, String kind, long chip) {
		if (v.balance() < v.min() || v.balance() < chip) return Click.error(K + "reason.not_enough", Math.max(v.min(), chip));
		long amount = Math.max(v.min(), chip);
		if (v.max() > 0 && amount > v.max()) amount = v.max();
		return Click.bet(kind, amount);
	}

	/** Odds: the selected chip snapped to the bet's unit (at least one unit), never more than the room. */
	private static Click odds(BetPub b, long chip, CrapsView v) {
		long unit = Math.max(1, b.unit());
		long amount = Math.max(unit, chip / unit * unit);
		amount = Math.min(amount, b.room() / unit * unit);
		if (amount <= 0) return Click.error(K + "reason.odds_full");
		if (amount > v.balance()) return Click.error(K + "reason.not_enough", amount);
		return Click.odds(b.id(), amount);
	}

	private static BetPub lineBet(CrapsView v, String kind) {
		for (BetPub b : v.mine()) {
			if (b.kind().equals(kind) && b.point() == 0) return b;
		}
		return null;
	}

	/**
	 * T-D2 P-1: the line flashed with a flat bet sent for a chip under the table minimum (the bet goes down at the
	 * minimum: say so); null for any other click.
	 */
	public static @Nullable Line minRaised(CrapsView v, Click c, long chip) {
		if (!c.sends() || !"bet".equals(c.action()) || chip >= v.min()) return null;
		return new Line(K + "reason.min_raised", v.min());
	}

	/** Hover readout for a spot (status line while hovering): the bet's name and what it pays. */
	public static Line hover(String spotId, CrapsView v) {
		String kind = CrapsLayout.kindOf(spotId);
		return switch (kind) {
			case "point" -> new Line(K + "hover.point", CrapsLayout.pointOf(spotId));
			case "pass_odds" -> new Line(K + "hover.pass_odds");
			default -> new Line(K + "hover." + kind);
		};
	}
}
