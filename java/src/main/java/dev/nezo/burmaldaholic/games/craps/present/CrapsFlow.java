package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
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
			if (!v.seatFree()) return Gate.off("sit", K + "primary.sit", K + "reason.table_full");
			if (v.balance() < v.min()) return Gate.off("sit", K + "primary.sit", K + "reason.not_enough", v.min());
			return Gate.on("sit", K + "primary.sit");
		}
		if (v.rolling()) return Gate.off("roll", K + "primary.roll", K + "reason.rolling");
		if (v.youShoot()) {
			if (v.canRoll()) return Gate.on("roll", K + "primary.roll");
			if (v.needLine()) return Gate.off("roll", K + "primary.roll", K + "reason.need_line");
			if (v.windowSeconds() >= 0) return Gate.off("roll", K + "primary.roll", K + "reason.roll_in", v.windowSeconds());
			return Gate.off("roll", K + "primary.roll", K + "reason.settling");
		}
		if (v.shooter().isEmpty()) return Gate.off("roll", K + "primary.roll", K + "reason.no_shooter");
		return Gate.off("roll", K + "primary.roll", K + "reason.waiting_for", v.shooter());
	}

	/** The chip tray: a chip can be put down now (a felt click places it or says why the spot is closed). */
	public static Gate chips(CrapsView v) {
		if (!v.enabled()) return Gate.off("", K + "bet", K + "reason.disabled");
		if (!v.seated() && !v.seatFree()) return Gate.off("", K + "bet", K + "reason.table_full");
		boolean flat = v.balance() >= v.min() && !v.allowed().isEmpty();
		boolean odds = v.balance() > 0 && !v.oddsTargets().isEmpty();
		if (flat || odds) return Gate.on("", K + "bet");
		if (v.balance() < v.min()) return Gate.off("", K + "bet", K + "reason.not_enough", v.min());
		return Gate.off("", K + "bet", K + "reason.all_down");
	}

	/** Undo ({@code tool.undo}) and Clear ({@code tool.clear}): only chips put down since the last roll. */
	public static Gate tool(String id, CrapsView v) {
		String label = K + "tool." + id;
		if (v.canUndo() && !v.rolling()) return Gate.on(id, label);
		return Gate.off(id, label, v.rolling() ? K + "reason.rolling" : "gui.burmaldaholic.craps.nothing_to_undo");
	}

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
			if (v.allowed("pass")) return flat(v, "pass", chip);
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
