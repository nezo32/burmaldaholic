package dev.nezo.burmaldaholic.games.uth.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The UTH v2 Dock as pure gates (docs/architecture/ui-v2.md §5, REDESIGN.md §7.4): the BetBar (chips, tools, the
 * Ante / Trips spots on the felt), the primary, the decision row and the player-bank controls, for every state of
 * {@link UthView}. A disabled gate always carries its reason.
 *
 * <p>Changing the bet is always one obvious click (the v0.1.1 report "I couldn't change my UTH bet"): before Deal the
 * draft is edited freely (chips add to the armed spot, right click removes one, Undo / Clear / Rebet / ×2); after Deal
 * while others are still betting the primary is <b>Change bet</b> and any chip or tool click changes it too — the
 * screen sends {@code clear} (the server refunds) and keeps the amounts in the draft.
 *
 * <p>Gate actions: table actions as is ({@code bet}, {@code sit}, decisions, {@code take_bank}, {@code leave_bank});
 * local ones start with {@link #LOCAL} ({@code ~change}, {@code ~bank_form}, {@code ~back}, tools {@code ~undo …}).
 */
public final class UthFlow {
	public static final String U = "gui.burmaldaholic.uth.";
	public static final String V2 = U + "v2.";
	public static final String LOCAL = "~";
	public static final List<String> TOOLS = List.of("undo", "clear", "rebet", "double");
	/** Decisions, primary candidates first. */
	private static final List<String> BETS = List.of("bet_4x", "bet_2x", "bet_1x");

	private UthFlow() {}

	public record Stepper(String id, long value, long min, long max, long step) {}

	/** Why the BetBar (chips, tools, spots) is locked, or null when it is live. */
	public static String betLock(UthView v) {
		if (v.banker()) return V2 + "reason.you_bank";
		if (!v.seated()) return V2 + "reason.sit_to_bet";
		if (!v.betting()) return "gui.burmaldaholic.error.round_in_progress";
		return null;
	}

	/** The chip tray (one gate for all chips). */
	public static Gate chips(UthView v) {
		String lock = betLock(v);
		return lock == null ? Gate.on(LOCAL + "chip", V2 + "chips") : Gate.off(LOCAL + "chip", V2 + "chips", lock);
	}

	/** The felt spot {@code ante} or {@code trips}: click arms it and adds the selected chip. */
	public static Gate spot(String id, UthView v) {
		String label = V2 + "spot." + id;
		String lock = betLock(v);
		if (lock != null) return Gate.off(LOCAL + "spot." + id, label, lock);
		if (id.equals("trips") && !v.tripsOn()) return Gate.off(LOCAL + "spot.trips", label, U + "error.trips_off");
		return Gate.on(LOCAL + "spot." + id, label);
	}

	public static Gate tool(String id, UthView v) {
		String label = "gui.burmaldaholic.common." + id;
		if (id.equals("double") || id.equals("undo")) label = V2 + "tool." + id;
		String lock = betLock(v);
		if (lock != null) return Gate.off(LOCAL + id, label, lock);
		UthBetDraft d = v.draft();
		return switch (id) {
			case "undo" -> d.canUndo() ? Gate.on(LOCAL + id, label) : Gate.off(LOCAL + id, label, V2 + "reason.nothing_to_undo");
			case "clear" -> !d.isEmpty() ? Gate.on(LOCAL + id, label) : Gate.off(LOCAL + id, label, V2 + "reason.no_bet");
			case "rebet" -> v.lastAnte() <= 0 ? Gate.off(LOCAL + id, label, V2 + "reason.no_last_bet")
				: d.equalsBet(v.lastAnte(), v.lastTrips()) ? Gate.off(LOCAL + id, label, V2 + "reason.same_as_last")
				: Gate.on(LOCAL + id, label);
			case "double" -> d.isEmpty() ? Gate.off(LOCAL + id, label, V2 + "reason.no_bet")
				: problem(v, d.ante() * 2, d.trips() * 2) == UthLimits.Problem.WORST_CASE_MAX
					? Gate.off(LOCAL + id, label, U + "error.worst_case_max", v.maxW())
					: Gate.on(LOCAL + id, label);
			default -> throw new IllegalArgumentException(id);
		};
	}

	public static Gate primary(UthView v) {
		if (v.banker()) {
			return Gate.off("", V2 + "primary.wait", V2 + "reason.you_bank");
		}
		if (!v.seated()) return Gate.on("sit", V2 + "primary.sit");
		if (v.deciding()) {
			for (String b : BETS) {
				if (v.legal().contains(b)) return decision(b, v);
			}
			if (v.legal().contains("check")) return Gate.on("check", U + "check");
			return Gate.on("fold", U + "fold");
		}
		if (v.betting()) {
			if (v.bank() != null && v.bank().form()) {
				long a = v.bank().amount();
				if (a < v.bank().minBank()) return Gate.off("take_bank", V2 + "primary.bank", U + "error.min_bank", v.bank().minBank());
				if (a > v.balance()) return Gate.off("take_bank", V2 + "primary.bank", "gui.burmaldaholic.error.insufficient_funds", v.balance());
				return new Gate("take_bank", V2 + "primary.bank_amount", new Object[] {a}, true, null, null);
			}
			if (v.confirmed() != null) return Gate.on(LOCAL + "change", V2 + "primary.change");
			UthBetDraft d = v.draft();
			if (d.ante() <= 0) return Gate.off("bet", V2 + "primary.deal_plain", d.trips() > 0 ? U + "error.trips_needs_ante" : V2 + "reason.place_ante");
			UthLimits.Problem p = problem(v, d.ante(), d.trips());
			return switch (p) {
				case NONE -> Gate.on("bet", V2 + "primary.deal", d.cost());
				case INVALID -> Gate.off("bet", V2 + "primary.deal_plain", V2 + "reason.place_ante");
				case ANTE_MIN -> Gate.off("bet", V2 + "primary.deal_plain", U + "error.ante_min", v.minAnte());
				case TRIPS_OFF -> Gate.off("bet", V2 + "primary.deal_plain", U + "error.trips_off");
				case TRIPS_MIN -> Gate.off("bet", V2 + "primary.deal_plain", "gui.burmaldaholic.error.bet_too_low", v.tripsMin());
				case WORST_CASE_MAX -> Gate.off("bet", V2 + "primary.deal_plain", U + "error.worst_case_max", v.maxW());
				case INSUFFICIENT_FUNDS -> Gate.off("bet", V2 + "primary.deal_plain", "gui.burmaldaholic.error.insufficient_funds", v.balance());
				case KEEP_FOR_RIVER -> Gate.off("bet", V2 + "primary.deal_plain", U + "error.keep_for_river", d.ante());
			};
		}
		return Gate.off("", V2 + "primary.wait", waitReason(v), waitArgs(v));
	}

	public static String waitReason(UthView v) {
		if (!v.inRound()) return U + "waiting_next";
		if (!v.waitingFor().isEmpty()) return V2 + "reason.waiting_for";
		if (v.phase() == UthView.Phase.SHOWDOWN) return V2 + "reason.showdown";
		return V2 + "reason.dealing";
	}

	private static Object[] waitArgs(UthView v) {
		return waitReason(v).endsWith("waiting_for") ? new Object[] {v.waitingFor()} : new Object[0];
	}

	/** The action row: decisions other than the primary; player-bank controls while betting. */
	public static List<Gate> actions(UthView v) {
		List<Gate> out = new ArrayList<>();
		if (v.deciding()) {
			Gate p = primary(v);
			for (String id : List.of("fold", "check", "bet_3x", "bet_2x", "bet_1x", "bet_4x")) {
				if (!v.legal().contains(id) || id.equals(p.action())) continue;
				out.add(decision(id, v));
			}
			return out;
		}
		if (!v.betting() || v.bank() == null) return out;
		if (v.banker()) {
			out.add(v.bank().leaving() ? Gate.off("leave_bank", U + "pvp.leave_seat", U + "pvp.leaving_after_round") : Gate.on("leave_bank", U + "pvp.leave_seat"));
		} else if (v.bank().form()) {
			out.add(Gate.on(LOCAL + "back", "gui.burmaldaholic.common.back"));
		} else if (v.bank().canTake()) {
			out.add(Gate.on(LOCAL + "bank_form", U + "pvp.take_seat"));
		}
		return out;
	}

	/** The bank amount stepper while the take-the-bank form is open. */
	public static Optional<Stepper> stepper(UthView v) {
		if (!v.betting() || v.bank() == null || !v.bank().form() || v.banker()) return Optional.empty();
		long min = v.bank().minBank();
		long max = Math.max(min, v.balance());
		long step = Math.max(1, min / 10);
		return Optional.of(new Stepper("bank", Math.max(min, Math.min(max, v.bank().amount())), min, max, step));
	}

	private static Gate decision(String id, UthView v) {
		long mult = switch (id) {
			case "bet_4x" -> 4;
			case "bet_3x" -> 3;
			case "bet_2x" -> 2;
			case "bet_1x" -> 1;
			default -> 0;
		};
		if (mult == 0) return Gate.on(id, U + id);
		long amount = mult * v.myAnte();
		if (amount > v.balance()) return new Gate(id, U + id, new Object[] {amount}, false, U + "unaffordable", null);
		return new Gate(id, U + id, new Object[] {amount}, true, null, null);
	}

	private static UthLimits.Problem problem(UthView v, long ante, long trips) {
		return UthLimits.check(ante, trips, v.minAnte(), v.maxW(), v.tripsOn(), v.tripsMin(), v.balance());
	}

	public static boolean danger(String action) {
		return action.equals("fold") || action.equals("leave_bank");
	}

	public static String key(String action) {
		return switch (action) {
			case "fold" -> "F";
			case "check" -> "C";
			case "bet_3x" -> "3";
			default -> "";
		};
	}
}
