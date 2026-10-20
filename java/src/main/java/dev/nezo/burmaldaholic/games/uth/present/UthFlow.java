package dev.nezo.burmaldaholic.games.uth.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The UTH v2 Dock as pure gates (docs/architecture/ui-v2.md §5, REDESIGN.md §7.4): the BetBar (chips, tools, the
 * Ante / Trips spots on the felt), the primary, the decision row and the player-bank controls, for every state of
 * {@link UthView}. A disabled gate always carries its reason.
 *
 * <p>Changing the bet is always one obvious click (the v0.1.1 report "I couldn't change my UTH bet"): before Deal the
 * draft is edited freely (chips add to the armed spot, right click removes one, Undo / Clear / Rebet / ×2); after Deal
 * while others are still betting the primary is <b>Change bet</b> — only it sends {@code clear} (the server refunds and
 * the draft keeps the amounts); chips, tools and spots are locked with the reason "press Change bet" until then (R-AB M4:
 * a stray chip click must never withdraw a confirmed bet).
 *
 * <p>Limits (R-AB M5, the v0.1.2 hotfix behaviour): a chip that would take the armed spot over its cap is clamped to the
 * cap ({@link #add}) and the reason is shown; the Ante cap is {@code min((maxW − Trips) / 6, (balance − Trips) / 3)}
 * (6 × Ante + Trips ≤ max; Ante + Blind + a river bet + Trips ≤ balance), the Trips cap
 * {@code min(maxW − 6 × Ante, balance − 3 × Ante)}. Chips grey out when the armed spot is at its cap; Rebet clamps the
 * same way.
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

	/** Locked BetBar while a confirmed bet waits for the deal: press Change bet first (R-AB M4). */
	public static final String REASON_PRESS_CHANGE = V2 + "reason.press_change_bet";

	/**
	 * A draft edit and, when the amount was clamped (or nothing could be added), the refused gate carrying the reason to
	 * flash ({@code null} = applied as asked).
	 */
	public record Edit(UthBetDraft draft, @Nullable Gate refusal) {}

	/** Why the BetBar (chips, tools, spots) is locked, or null when it is live. */
	public static String betLock(UthView v) {
		if (v.banker()) return V2 + "reason.you_bank";
		if (!v.seated()) return V2 + "reason.sit_to_bet";
		if (!v.betting()) return "gui.burmaldaholic.error.round_in_progress";
		if (v.confirmed() != null) return REASON_PRESS_CHANGE;
		return null;
	}

	// ---- limits (R-AB M5; re-derived from the v0.1.2 hotfix UthScreen) ---------------------------------------------

	/** Largest Ante with {@code trips} on Trips: the worst-case limit and the balance (Ante + Blind + river bet + Trips). */
	public static long maxAnte(UthView v, long trips) {
		long t = Math.max(0, trips);
		return Math.max(0, Math.min(UthLimits.maxAnte(v.maxW(), t), (v.balance() - t) / 3));
	}

	/** Largest Trips with Ante {@code ante}. */
	public static long maxTrips(UthView v, long ante) {
		long a = Math.max(0, ante);
		return Math.max(0, Math.min(v.maxW() - 6 * a, v.balance() - 3 * a));
	}

	private static boolean toTrips(UthView v, UthBetDraft.Spot s) {
		return s == UthBetDraft.Spot.TRIPS && v.tripsOn();
	}

	/** The cap of spot {@code s} given the other spot of {@code d}. */
	public static long cap(UthView v, UthBetDraft d, UthBetDraft.Spot s) {
		return toTrips(v, s) ? maxTrips(v, d.ante()) : maxAnte(v, d.trips());
	}

	/** The reason a spot is capped at {@code cap} (the hotfix's {@code uth.error.max_ante*} / {@code max_trips}). */
	public static Gate limitGate(String action, String label, UthView v, UthBetDraft d, UthBetDraft.Spot s, long cap) {
		if (toTrips(v, s)) return Gate.off(action, label, U + "error.max_trips", cap);
		long t = d.trips();
		boolean byBalance = Math.max(0, (v.balance() - t) / 3) < UthLimits.maxAnte(v.maxW(), t);
		return byBalance ? Gate.off(action, label, U + "error.max_ante_balance", cap) : Gate.off(action, label, U + "error.max_ante", cap, v.maxW());
	}

	/** A tray chip of {@code denom}: live while the armed spot is under its cap (it then clamps), grey with the reason. */
	public static Gate chip(long denom, UthView v) {
		String lock = betLock(v);
		String label = V2 + "chips";
		if (lock != null) return Gate.off(LOCAL + "chip", label, lock);
		UthBetDraft d = v.draft();
		UthBetDraft.Spot s = toTrips(v, d.spot()) ? UthBetDraft.Spot.TRIPS : UthBetDraft.Spot.ANTE;
		long cur = s == UthBetDraft.Spot.TRIPS ? d.trips() : d.ante();
		long cap = cap(v, d, s);
		if (cap <= cur) return limitGate(LOCAL + "chip", label, v, d, s, cap);
		return new Gate(LOCAL + "chip", label, new Object[] {denom}, true, null, null);
	}

	/**
	 * One chip of {@code chip} onto spot {@code s} (arming it), clamped to the spot's cap: over the cap the amount
	 * becomes the cap and {@link Edit#refusal} says why; at the cap nothing is added.
	 */
	public static Edit add(UthView v, UthBetDraft.Spot s, long chip) {
		UthBetDraft d = v.draft();
		if (chip <= 0) return new Edit(d.arm(s), null);
		long cur = s == UthBetDraft.Spot.TRIPS ? d.trips() : d.ante();
		long cap = cap(v, d, s);
		long want = cur + chip;
		Gate refusal = null;
		if (want > cap) {
			refusal = limitGate(LOCAL + "chip", V2 + "chips", v, d, s, cap);
			if (cap <= cur) return new Edit(d.arm(s), refusal);
			want = cap;
		}
		return new Edit(d.add(s, want - cur), refusal);
	}

	/** Rebet: the last round's bet, clamped to today's limits / balance (Trips first, then the Ante under it). */
	public static Edit rebet(UthView v) {
		long trips = v.tripsOn() ? Math.min(v.lastTrips(), maxTrips(v, 0)) : 0;
		long cap = maxAnte(v, trips);
		long ante = v.lastAnte();
		Gate refusal = null;
		if (ante > cap) {
			UthBetDraft probe = new UthBetDraft(ante, trips, UthBetDraft.Spot.ANTE, java.util.List.of());
			refusal = limitGate(LOCAL + "rebet", V2Keys.TOOL_REBET, v, probe, UthBetDraft.Spot.ANTE, cap);
			ante = cap;
		}
		return new Edit(v.draft().load(ante, Math.max(0, trips)), refusal);
	}

	/**
	 * ×2: doubles the Ante and the Trips, clamped like a chip (R3: it used to grey out over the limit). The Ante comes
	 * first (up to its cap with today's Trips), then the Trips under it; a clamp says why. At the caps nothing changes and
	 * the refusal says why.
	 */
	public static Edit doubled(UthView v) {
		UthBetDraft d = v.draft();
		if (d.isEmpty()) return new Edit(d, Gate.off(LOCAL + "double", V2Keys.TOOL_DOUBLE, V2 + "reason.no_bet"));
		long anteCap = maxAnte(v, d.trips());
		long ante = Math.max(d.ante(), Math.min(d.ante() * 2, anteCap));
		long trips = v.tripsOn() ? Math.max(d.trips(), Math.min(d.trips() * 2, maxTrips(v, ante))) : d.trips();
		Gate refusal = null;
		if (ante < d.ante() * 2) {
			refusal = limitGate(LOCAL + "double", V2Keys.TOOL_DOUBLE, v, d, UthBetDraft.Spot.ANTE, anteCap);
		} else if (trips < d.trips() * 2) {
			refusal = limitGate(LOCAL + "double", V2Keys.TOOL_DOUBLE, v, d.load(ante, d.trips()), UthBetDraft.Spot.TRIPS, maxTrips(v, ante));
		}
		return new Edit(d.load(ante, trips), refusal);
	}

	/** The chip tray as a whole (live = chips may be put down now; per-denomination caps: {@link #chip}). */
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
		String label = switch (id) {
			case "undo" -> V2Keys.TOOL_UNDO;
			case "clear" -> V2Keys.TOOL_CLEAR;
			case "rebet" -> V2Keys.TOOL_REBET;
			default -> V2Keys.TOOL_DOUBLE;
		};
		String lock = betLock(v);
		if (lock != null) return Gate.off(LOCAL + id, label, lock);
		UthBetDraft d = v.draft();
		return switch (id) {
			case "undo" -> d.canUndo() ? Gate.on(LOCAL + id, label) : Gate.off(LOCAL + id, label, V2 + "reason.nothing_to_undo");
			case "clear" -> !d.isEmpty() ? Gate.on(LOCAL + id, label) : Gate.off(LOCAL + id, label, V2 + "reason.no_bet");
			case "rebet" -> v.lastAnte() <= 0 ? Gate.off(LOCAL + id, label, V2 + "reason.no_last_bet")
				: d.equalsBet(v.lastAnte(), v.lastTrips()) ? Gate.off(LOCAL + id, label, V2 + "reason.same_as_last")
				: Gate.on(LOCAL + id, label);
			case "double" -> {
				// live while doubling adds anything (it clamps to the caps and says why); grey with the reason at the caps
				Edit e = doubled(v);
				yield e.draft().equalsBet(d.ante(), d.trips()) && e.refusal() != null ? e.refusal() : Gate.on(LOCAL + id, label);
			}
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

	/** Gate actions of the kit stepper's − / + on the bank amount (R-AB M3; labels shared with poker's stepper). */
	public static final String STEP_MINUS = LOCAL + "step_minus", STEP_PLUS = LOCAL + "step_plus";
	private static final String PV2 = "gui.burmaldaholic.poker.v2.";

	public static Gate stepMinus(Stepper s) {
		return s.value() > s.min() ? Gate.on(STEP_MINUS, PV2 + "step_minus") : Gate.off(STEP_MINUS, PV2 + "step_minus", PV2 + "reason.stepper_min", s.min());
	}

	public static Gate stepPlus(Stepper s) {
		return s.value() < s.max() ? Gate.on(STEP_PLUS, PV2 + "step_plus") : Gate.off(STEP_PLUS, PV2 + "step_plus", PV2 + "reason.stepper_max", s.max());
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
