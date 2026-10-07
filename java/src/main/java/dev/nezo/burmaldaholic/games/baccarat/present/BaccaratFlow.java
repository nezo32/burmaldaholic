package dev.nezo.burmaldaholic.games.baccarat.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure gates of the baccarat / chemin de fer v2 screen (docs/architecture/ui-v2.md §5), exhaustively tested by
 * {@code BaccaratFlowTest}.
 *
 * <p>Action ids: {@code sit}, {@code deal} (ready), {@code box.<id>} (a felt box: add the armed chip), {@code rebet},
 * {@code punt} (the draft), {@code banco}, {@code take_bank} (the draft or the minimum bank), {@code keep_bank},
 * {@code pass}.
 */
public final class BaccaratFlow {
	private BaccaratFlow() {}

	public static final String V2 = "gui.burmaldaholic.core.v2.";
	public static final String BAC = "gui.burmaldaholic.baccarat.";
	public static final String BAC2 = "gui.burmaldaholic.baccarat.v2.";

	public record Line(String key, Object... args) {}

	public static Gate primary(BaccaratView v) {
		if (!v.seated()) {
			if (!v.canSit()) return Gate.off("sit", V2 + "primary.sit", V2 + "reason.table_full");
			return Gate.on("sit", V2 + "primary.sit");
		}
		if (v.pending()) return Gate.off("deal", V2 + "primary.deal", V2 + "reason.waiting_answer");
		return v.chemmy() ? chemmy(v) : house(v);
	}

	private static Gate house(BaccaratView v) {
		String label = V2 + "primary.deal";
		if (v.houseOpen()) {
			if (v.ready()) return Gate.off("deal", label, BAC2 + "reason.ready_waiting");
			if (v.total() == 0) {
				if (v.canRebet()) return Gate.on("rebet", BAC2 + "rebet");
				return v.balance() < v.min() ? Gate.off("deal", label, V2 + "reason.not_enough", v.min()) : Gate.off("deal", label, V2 + "reason.place_bet");
			}
			if (v.total() < v.minTotal()) return Gate.off("deal", label, V2 + "reason.below_min", v.minTotal());
			return Gate.on("deal", label);
		}
		return switch (v.phase()) {
			case "result" -> v.secondsLeft() >= 0 ? Gate.off("deal", label, BAC2 + "reason.next_coup_in", v.secondsLeft())
				: Gate.off("deal", label, V2 + "reason.round_in_progress");
			case "shuffle" -> Gate.off("deal", label, BAC2 + "reason.shuffling");
			default -> Gate.off("deal", label, V2 + "reason.dealing");
		};
	}

	private static Gate chemmy(BaccaratView v) {
		switch (v.phase()) {
			case "bank_offer" -> {
				if (!v.offerYou()) return Gate.off("take_bank", BAC2 + "take_bank", BAC2 + "reason.bank_offered", v.banker());
				if (v.offerKeep()) return Gate.on("keep_bank", BAC2 + "keep_bank");
				long amount = Math.max(v.draft(), v.minBank());
				if (v.balance() < amount) return Gate.off("take_bank", BAC2 + "take_bank", V2 + "reason.not_enough", amount);
				return Gate.on("take_bank", BAC2 + "take_bank_n", amount);
			}
			case "betting" -> {
				if (v.youBank()) return Gate.off("punt", BAC2 + "punt", BAC2 + "reason.you_bank");
				if (!v.puntOpen()) return Gate.off("punt", BAC2 + "punt", v.bancoTaken() ? BAC2 + "reason.banco_called" : V2 + "reason.round_in_progress");
				if (v.myPunt() > 0 && v.draft() == 0) {
					return v.ready() ? Gate.off("deal", V2 + "primary.deal", BAC2 + "reason.ready_waiting") : Gate.on("deal", V2 + "primary.deal");
				}
				if (v.draft() == 0) return Gate.off("punt", BAC2 + "punt", V2 + "reason.place_bet");
				if (v.draft() < v.min()) return Gate.off("punt", BAC2 + "punt", V2 + "reason.below_min", v.min());
				if (v.draft() > v.open()) return Gate.off("punt", BAC2 + "punt", BAC2 + "reason.bank_covers", v.open());
				if (v.draft() > v.balance()) return Gate.off("punt", BAC2 + "punt", V2 + "reason.not_enough", v.draft());
				return Gate.on("punt", BAC2 + "punt_n", v.draft());
			}
			case "waiting", "idle" -> {
				return Gate.off("deal", V2 + "primary.deal", BAC2 + "reason.waiting_players");
			}
			case "result" -> {
				return v.secondsLeft() >= 0 ? Gate.off("deal", V2 + "primary.deal", BAC2 + "reason.next_coup_in", v.secondsLeft())
					: Gate.off("deal", V2 + "primary.deal", V2 + "reason.round_in_progress");
			}
			default -> {
				return Gate.off("deal", V2 + "primary.deal", V2 + "reason.dealing");
			}
		}
	}

	public static List<Gate> actions(BaccaratView v) {
		List<Gate> out = new ArrayList<>();
		if (!v.seated() || v.pending() || !v.chemmy()) return out;
		if ("bank_offer".equals(v.phase()) && v.offerYou()) {
			out.add(Gate.on("pass", BAC2 + "pass"));
		} else if ("betting".equals(v.phase()) && !v.youBank()) {
			out.add(v.puntOpen() && v.open() > 0 && v.open() <= v.balance() + v.myPunt() ? Gate.on("banco", BAC2 + "banco", v.open())
				: Gate.off("banco", BAC2 + "banco", v.bancoTaken() ? BAC2 + "reason.banco_called"
					: v.open() > v.balance() + v.myPunt() ? V2 + "reason.not_enough" : V2 + "reason.round_in_progress", v.open()));
		}
		return out;
	}

	/** A felt box ({@link BaccaratPub#BOX_IDS} index): takes the armed chip {@code d}. */
	public static Gate box(int box, long d, BaccaratView v) {
		String label = BAC + BaccaratPub.BOX_IDS[box];
		Gate closed = closed(v, "box", label);
		if (closed != null) return closed;
		if (v.chemmy()) return Gate.off("box", label, BAC2 + "reason.chemmy_boxes");
		if (box >= BaccaratPub.T + 1 && !v.pairs()) return Gate.off("box", label, BAC2 + "reason.no_pairs");
		if (d > v.balance()) return Gate.off("box", label, V2 + "reason.not_enough", d);
		if (v.max() > 0 && v.bets()[box] + d > v.max()) return Gate.off("box", label, V2 + "reason.above_max", v.max());
		return Gate.on("box", label);
	}

	public static Gate chip(long d, BaccaratView v) {
		String label = V2 + "bet";
		Gate closed = closed(v, "chip", label);
		if (closed != null) return closed;
		long room = v.chemmy() ? Math.min(v.open(), v.balance()) - v.draft() : v.balance();
		if (d > room) return Gate.off("chip", label, v.chemmy() && d > v.open() - v.draft() ? BAC2 + "reason.bank_covers" : V2 + "reason.not_enough",
			v.chemmy() ? v.open() : d);
		return Gate.on("chip", label);
	}

	public static Gate tool(String id, BaccaratView v) {
		String label = V2 + "tool." + id;
		Gate closed = closed(v, id, label);
		if (closed != null) return closed;
		boolean any = v.chemmy() ? v.draft() > 0 || v.myPunt() > 0 : v.total() > 0;
		return switch (id) {
			case "undo", "clear" -> any ? Gate.on(id, label) : Gate.off(id, label, V2 + "reason.place_bet");
			case "rebet" -> v.chemmy() ? Gate.off(id, label, BAC2 + "reason.chemmy_boxes")
				: v.canRebet() ? Gate.on(id, label) : Gate.off(id, label, BAC2 + "reason.no_last_bet");
			case "double" -> !any ? Gate.off(id, label, V2 + "reason.place_bet")
				: (v.chemmy() ? v.draft() : v.total()) * 2 > v.balance() + (v.chemmy() ? 0 : v.total())
					? Gate.off(id, label, V2 + "reason.not_enough", (v.chemmy() ? v.draft() : v.total()) * 2)
				: Gate.on(id, label);
			default -> throw new IllegalArgumentException(id);
		};
	}

	private static Gate closed(BaccaratView v, String id, String label) {
		if (!v.seated()) return Gate.off(id, label, V2 + "reason.sit_to_bet");
		if (v.pending()) return Gate.off(id, label, V2 + "reason.waiting_answer");
		if (v.chemmy()) {
			if ("bank_offer".equals(v.phase()) && v.offerYou()) return null; // chips build the bank
			if (v.youBank()) return Gate.off(id, label, BAC2 + "reason.you_bank");
			if (!v.puntOpen()) return Gate.off(id, label, v.bancoTaken() ? BAC2 + "reason.banco_called" : V2 + "reason.round_in_progress");
			return null;
		}
		if (!v.houseOpen()) return Gate.off(id, label, V2 + "reason.bets_closed");
		if (v.ready()) return Gate.off(id, label, BAC2 + "reason.ready_waiting");
		return null;
	}

	public static Line[] status(BaccaratView v) {
		if (!v.seated()) return new Line[] {new Line(V2 + "status.pick_seat"), new Line("")};
		Line timer = v.secondsLeft() >= 0 ? new Line(BAC2 + "status.seconds_left", v.secondsLeft()) : new Line("");
		if (v.chemmy()) {
			return switch (v.phase()) {
				case "bank_offer" -> v.offerYou() ? new Line[] {new Line(BAC2 + "status.bank_yours"), timer}
					: new Line[] {new Line(BAC2 + "reason.bank_offered", v.banker()), timer};
				case "betting" -> v.youBank() ? new Line[] {new Line(BAC2 + "status.you_bank"), timer}
					: new Line[] {new Line(BAC2 + "status.punt", v.open()), timer};
				case "reveal", "no_more_bets" -> new Line[] {new Line(V2 + "reason.dealing"), new Line("")};
				default -> new Line[] {new Line(BAC2 + "reason.waiting_players"), timer};
			};
		}
		if (v.houseOpen()) {
			return v.ready() ? new Line[] {new Line(BAC2 + "reason.ready_waiting"), timer}
				: new Line[] {new Line(V2 + "status.click_layout"), timer};
		}
		if ("result".equals(v.phase())) return new Line[] {new Line(""), timer};
		return new Line[] {new Line(BAC + "no_more_bets"), new Line("")};
	}
}
