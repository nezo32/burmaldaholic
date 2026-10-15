package dev.nezo.burmaldaholic.games.blackjack.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackView.Phase;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure gates of the blackjack v2 screen (docs/architecture/ui-v2.md §5): what the primary, the action buttons, the
 * bet tools and the chips say and whether they can be pressed — and, when not, why. Exhaustively unit-tested
 * ({@code BlackjackFlowTest}) so the "can't place a bet" class of bugs cannot come back silently.
 *
 * <p>Action ids: {@code sit}, {@code bet} (the draft), {@code rebet} (local: arms the last bet for the next round),
 * {@code insurance}, {@code no_insurance}, {@code even_money}, {@code no_even_money}, {@code hit}, {@code stand},
 * {@code double}, {@code split}, {@code surrender}, {@code leave}.
 */
public final class BlackjackFlow implements Flow<BlackjackView> {
	public static final BlackjackFlow INSTANCE = new BlackjackFlow();

	private BlackjackFlow() {}

	public static final String V2 = "gui.burmaldaholic.core.v2.";
	public static final String BJ = "gui.burmaldaholic.blackjack.";
	public static final String BJ2 = "gui.burmaldaholic.blackjack.v2.";

	/** A status line: translation key + plain arguments. */
	public record Line(String key, Object... args) {}

	@Override
	public Gate primary(BlackjackView v) {
		if (!v.seated()) {
			if (!v.canSit()) return Gate.off("sit", V2 + "primary.sit", V2 + "reason.table_full");
			if (v.balance() < v.min()) return Gate.off("sit", V2 + "primary.sit", V2 + "reason.not_enough", v.min());
			return Gate.on("sit", V2 + "primary.sit");
		}
		if (v.pending()) return Gate.off("bet", V2 + "primary.deal", V2Keys.STATUS_PENDING);
		return switch (v.phase()) {
			case BETTING -> betting(v);
			case INSURANCE -> insurance(v);
			case TURNS -> v.myTurn() ? stand(v) : wait(v, "stand", BJ + "stand");
			case DEALER -> Gate.off("stand", BJ + "stand", V2 + "reason.dealing");
			case RESULT -> rebet(v);
		};
	}

	private static Gate betting(BlackjackView v) {
		if (v.placed() > 0) {
			return v.secondsLeft() >= 0 ? Gate.off("bet", V2 + "primary.deal", BJ2 + "reason.deal_in", v.secondsLeft())
				: Gate.off("bet", V2 + "primary.deal", BJ2 + "reason.others_betting");
		}
		if (v.draft() == 0) {
			if (v.lastBet() > 0 && v.lastBet() >= v.min() && v.lastBet() <= v.cap()) return Gate.on("rebet", V2 + "primary.rebet", v.lastBet());
			if (v.balance() < v.min()) return Gate.off("bet", V2 + "primary.deal", V2 + "reason.not_enough", v.min());
			return Gate.off("bet", V2 + "primary.deal", V2 + "reason.place_bet");
		}
		if (v.draft() < v.min()) return Gate.off("bet", V2 + "primary.deal", V2 + "reason.below_min", v.min());
		if (v.max() > 0 && v.draft() > v.max()) return Gate.off("bet", V2 + "primary.deal", V2 + "reason.above_max", v.max());
		if (v.draft() > v.balance()) return Gate.off("bet", V2 + "primary.deal", V2 + "reason.not_enough", v.draft());
		return Gate.on("bet", V2 + "primary.deal");
	}

	private static Gate insurance(BlackjackView v) {
		if ("insurance".equals(v.offer())) {
			return v.insuranceMax() > 0 ? Gate.on("insurance", BJ + "insure", v.insuranceMax())
				: Gate.off("insurance", BJ + "insure", V2 + "reason.not_enough", 1);
		}
		if ("even_money".equals(v.offer())) return Gate.on("even_money", BJ + "even_money");
		return wait(v, "insurance", BJ2 + "insurance");
	}

	private static Gate stand(BlackjackView v) {
		return v.legal().contains("stand") ? Gate.on("stand", BJ + "stand") : Gate.off("stand", BJ + "stand", BJ2 + "reason.not_now");
	}

	private static Gate rebet(BlackjackView v) {
		if (v.lastBet() <= 0) return Gate.off("rebet", V2 + "primary.deal", V2 + "reason.round_in_progress");
		// the label keeps its amount ("Rebet 50"; it showed "Rebet %1$s" while disabled)
		if (v.lastBet() > v.balance()) return new Gate("rebet", V2 + "primary.rebet", new Object[] {v.lastBet()}, false, V2 + "reason.not_enough",
			new Object[] {v.lastBet()});
		return Gate.on("rebet", V2 + "primary.rebet", v.lastBet());
	}

	private static Gate wait(BlackjackView v, String action, String label) {
		if (v.busy()) return Gate.off(action, label, V2 + "reason.dealing");
		if (!v.waitingFor().isEmpty()) return Gate.off(action, label, V2 + "reason.waiting_for", v.waitingFor());
		return Gate.off(action, label, V2 + "reason.round_in_progress");
	}

	/** Action row left of the primary (≤ 4): the decisions while playing, the decline of an insurance / even-money offer. */
	@Override
	public List<Gate> actions(BlackjackView v) {
		List<Gate> out = new ArrayList<>();
		if (!v.seated() || v.pending()) return out;
		if (v.phase() == Phase.INSURANCE && "insurance".equals(v.offer())) {
			out.add(Gate.on("no_insurance", BJ + "no_insurance"));
		} else if (v.phase() == Phase.INSURANCE && "even_money".equals(v.offer())) {
			out.add(Gate.on("no_even_money", BJ + "decline_even_money"));
		} else if (v.phase() == Phase.TURNS && v.inRound()) {
			for (String a : new String[] {"hit", "double", "split"}) out.add(decision(v, a));
			if (v.myTurn() && v.legal().contains("surrender")) out.add(Gate.on("surrender", BJ + "surrender"));
		} else if (v.phase() == Phase.RESULT) {
			out.add(Gate.on("leave", V2Keys.PRIMARY_LEAVE));
		}
		return out;
	}

	private static Gate decision(BlackjackView v, String a) {
		if (!v.myTurn()) {
			return v.busy() ? Gate.off(a, BJ + a, V2 + "reason.dealing")
				: !v.waitingFor().isEmpty() ? Gate.off(a, BJ + a, V2 + "reason.waiting_for", v.waitingFor())
				: Gate.off(a, BJ + a, BJ2 + "reason.not_now");
		}
		if (v.legal().contains(a)) return Gate.on(a, BJ + a);
		return Gate.off(a, BJ + a, switch (a) {
			case "double" -> BJ2 + "reason.cannot_double";
			case "split" -> BJ2 + "reason.cannot_split";
			default -> BJ2 + "reason.not_now";
		});
	}

	/** The bet model for the kit's BetBar (single spot: the local draft is the bet until the server holds one). */
	@Override
	public BetView bet(BlackjackView v) {
		BetView.Phase ph = switch (v.phase()) {
			case BETTING -> BetView.Phase.BETTING;
			case INSURANCE, TURNS -> BetView.Phase.ACTING;
			case DEALER, RESULT -> BetView.Phase.SETTLING;
		};
		long total = v.placed() > 0 ? v.placed() : v.draft();
		return BetView.builder().seated(v.seated()).canSit(v.canSit()).phase(ph).bettingOpen(v.bettingOpen() && !v.pending()).limits(v.min(), v.max())
			.balance(v.balance()).myTotal(total).canUndo(v.canUndo()).canClear(v.draft() > 0).rebet(v.lastBet() > 0, v.lastBet())
			.canDouble(v.draft() > 0).secondsLeft(v.secondsLeft()).waitingFor(v.waitingFor()).build();
	}

	/** Bet tools: {@code undo}, {@code clear}, {@code rebet}, {@code double}. */
	@Override
	public Gate tool(String id, BlackjackView v) {
		String label = V2 + "tool." + id;
		Gate closed = closedReason(v, id, label);
		if (closed != null) return closed;
		return switch (id) {
			case "undo" -> v.canUndo() ? Gate.on(id, label) : Gate.off(id, label, V2 + "reason.place_bet");
			case "clear" -> v.draft() > 0 ? Gate.on(id, label) : Gate.off(id, label, V2 + "reason.place_bet");
			case "rebet" -> v.lastBet() <= 0 ? Gate.off(id, label, BJ2 + "reason.no_last_bet")
				: v.lastBet() > v.cap() ? Gate.off(id, label, V2 + "reason.not_enough", v.lastBet())
				: v.draft() == v.lastBet() ? Gate.off(id, label, BJ2 + "reason.already_rebet")
				: Gate.on(id, label);
			case "double" -> v.draft() <= 0 ? Gate.off(id, label, V2 + "reason.place_bet")
				: v.draft() * 2 > v.cap() ? Gate.off(id, label, v.max() > 0 && v.draft() * 2 > v.max() ? V2 + "reason.above_max" : V2 + "reason.not_enough",
					v.max() > 0 && v.draft() * 2 > v.max() ? v.max() : v.draft() * 2)
				: Gate.on(id, label);
			default -> throw new IllegalArgumentException(id);
		};
	}

	/** A chip of the tray: adds {@code d} to the draft. */
	@Override
	public Gate chip(long d, BlackjackView v) {
		String label = V2 + "bet";
		Gate closed = closedReason(v, "chip", label);
		if (closed != null) return closed;
		if (v.chipFits(d)) return Gate.on("chip", label);
		if (v.max() > 0 && v.draft() + d > v.max()) return Gate.off("chip", label, V2 + "reason.above_max", v.max());
		return Gate.off("chip", label, V2 + "reason.not_enough", v.draft() + d);
	}

	/** Why the bet bar is closed (null = open). */
	private static Gate closedReason(BlackjackView v, String id, String label) {
		if (!v.seated()) return Gate.off(id, label, V2 + "reason.sit_to_bet");
		if (v.pending()) return Gate.off(id, label, V2Keys.STATUS_PENDING);
		if (v.phase() != Phase.BETTING) return Gate.off(id, label, V2 + "reason.round_in_progress");
		if (v.placed() > 0) return Gate.off(id, label, BJ2 + "reason.bet_placed", v.placed());
		return null;
	}

	/** The Dock's two status lines (the gate reason of a disabled primary is shown by the kit above these). */
	public static Line[] status(BlackjackView v) {
		if (!v.seated()) return new Line[] {new Line(V2 + "status.pick_seat"), new Line(BJ2 + "status.rules")};
		return switch (v.phase()) {
			case BETTING -> v.placed() > 0
				? new Line[] {new Line(BJ2 + "status.bet_placed", v.placed()), timer(v)}
				: new Line[] {new Line(V2 + "status.chips_add"), timer(v)};
			case INSURANCE -> "even_money".equals(v.offer())
				? new Line[] {new Line(BJ2 + "status.even_money"), timer(v)}
				: "insurance".equals(v.offer()) ? new Line[] {new Line(BJ2 + "status.insurance", v.insuranceMax()), timer(v)}
				: new Line[] {new Line(BJ2 + "status.insurance_others"), timer(v)};
			case TURNS -> v.myTurn()
				? new Line[] {new Line(BJ2 + "status.your_turn"), new Line(BJ2 + "status.hand", v.placed(), Math.max(1, v.hand()), Math.max(1, v.hands()))}
				: !v.waitingFor().isEmpty() ? new Line[] {new Line(V2 + "reason.waiting_for", v.waitingFor()), timer(v)}
				: new Line[] {new Line(V2 + "reason.dealing"), new Line("")};
			case DEALER -> new Line[] {new Line(BJ2 + "status.dealer_plays"), new Line("")};
			case RESULT -> new Line[] {new Line(""), v.secondsLeft() >= 0 ? new Line(V2 + "status.next_round_in", v.secondsLeft()) : new Line("")};
		};
	}

	private static Line timer(BlackjackView v) {
		if (v.secondsLeft() < 0) return new Line("");
		return switch (v.phase()) {
			case BETTING -> new Line(BJ2 + "status.deal_in", v.secondsLeft());
			default -> new Line(BJ2 + "status.seconds_left", v.secondsLeft());
		};
	}
}
