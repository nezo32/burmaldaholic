package dev.nezo.burmaldaholic.games.poker.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Hold'em v2 Dock as pure gates (docs/architecture/ui-v2.md §5.1, REDESIGN.md §7.3): the primary button, the
 * action row and the amount stepper for every state of {@link PokerView}. A disabled gate always carries its reason
 * (the status line shows it); there is always either an enabled gate or a reason naming what the table waits for.
 *
 * <p>Gate actions: table actions sent as is ({@code buy_in}, {@code sit_in}, {@code sit_out}, {@code stand_up},
 * {@code top_up}; the decision {@code fold|check|call|raise} goes out as {@code act {kind, to, seq}}, a raise at the
 * maximum as {@code all_in}); local actions start with {@link #LOCAL} ({@code ~pick:<level>}, {@code ~back},
 * {@code ~top_up_form}).
 */
public final class PokerFlow {
	public static final String P = "gui.burmaldaholic.poker.";
	public static final String V2 = P + "v2.";
	public static final String LOCAL = "~";

	private PokerFlow() {}

	/** The amount stepper left of the primary (buy-in, raise, top-up). */
	public record Stepper(String id, long value, long min, long max, long step) {
		public boolean canMinus() {
			return value > min;
		}

		public boolean canPlus() {
			return value < max;
		}

		/** One step down / up, snapped to the step grid above {@code min} and clamped. */
		public long next(int dir, int multiplier) {
			long d = step * Math.max(1, multiplier);
			long v;
			if (dir > 0) {
				v = value >= max ? max : min + ((value - min) / d + 1) * d;
			} else {
				long off = value - min;
				v = off <= 0 ? min : min + ((off - 1) / d) * d;
			}
			return Math.max(min, Math.min(max, v));
		}
	}

	public static Gate primary(PokerView v) {
		if (!v.enabled()) return Gate.off("", V2 + "primary.wait", "gui.burmaldaholic.error.disabled");
		switch (v.seat()) {
			case QUEUED -> {
				return Gate.off("", P + "sit_down", "msg.burmaldaholic.bots.seat_after_round");
			}
			case NONE -> {
				PokerView.Level l = v.level();
				if (l == null) return Gate.off("buy_in", V2 + "primary.buy_in", V2 + "reason.pick_stakes");
				if (!l.vipOk()) return Gate.off("buy_in", V2 + "primary.buy_in", V2 + "reason.vip_stakes");
				if (!l.affordable()) return Gate.off("buy_in", V2 + "primary.buy_in", V2 + "reason.buy_in_min", Math.max(l.buyMin(), l.bb()));
				return Gate.on("buy_in", V2 + "primary.buy_in_amount", clamp(v.buyIn(), l.buyMin(), l.buyMax()));
			}
			default -> {
			}
		}
		PokerView.Turn t = v.turn();
		if (t != null) {
			if (t.canRaise()) {
				if (t.raiseIsAllIn()) return Gate.on("all_in", P + "all_in", t.maxTo());
				return Gate.on("raise", t.isBet() ? V2 + "bet_to" : P + "raise_to", clamp(t.raiseTo(), t.minTo(), t.maxTo()));
			}
			if (t.canCheck()) return Gate.on("check", P + "check");
			if (t.callIsAllIn()) return Gate.on("call", P + "all_in", t.myBet() + t.myStack());
			return Gate.on("call", P + "call", t.toCall());
		}
		if (v.topUpForm()) {
			if (v.canTopUp() && v.topMax() > 0) return Gate.on("top_up", V2 + "primary.top_up", clamp(v.topUp(), 1, v.topMax()));
			return Gate.off("top_up", P + "top_up", V2 + "reason.no_top_up");
		}
		if (v.sittingOut()) return Gate.on("sit_in", P + "sit_in");
		return Gate.off("", V2 + "primary.wait", waitReason(v), waitArgs(v));
	}

	/** Why the seated viewer waits (the status line). */
	public static String waitReason(PokerView v) {
		if (v.leaving()) return "msg.burmaldaholic.poker.leaving_after_hand";
		if (v.players() < 2) return "gui.burmaldaholic.common.waiting_players";
		if (v.live() && !v.waitingFor().isEmpty()) return V2 + "reason.waiting_for";
		if (v.presenting()) return P + "showdown";
		if (v.nextHandIn() >= 0) return P + "next_hand_in";
		return P + "waiting_hand";
	}

	private static Object[] waitArgs(PokerView v) {
		String key = waitReason(v);
		if (key.endsWith("waiting_for")) return new Object[] {v.waitingFor()};
		if (key.endsWith("next_hand_in")) return new Object[] {(long) v.nextHandIn()};
		return new Object[0];
	}

	/** The action row, left to right (the primary is not part of it). */
	public static List<Gate> actions(PokerView v) {
		return actions(v, false);
	}

	/** Whether the Dock offers the stake levels to pick (the widest action row of the table). */
	public static boolean pickingStakes(PokerView v) {
		return v.enabled() && v.seat() == PokerView.Seat.NONE && v.level() == null;
	}

	/**
	 * The action row; {@code shortStakes}: the stake buttons say only the level ("Micro"), for a Dock too narrow for
	 * "Micro (1/2)" × 4 (T-K2 D4: at 427 × 240 RU the row ran off the left edge). The screen lays out the full labels
	 * first and falls back to the short ones when the Dock reports an overflow.
	 */
	public static List<Gate> actions(PokerView v, boolean shortStakes) {
		List<Gate> out = new ArrayList<>();
		if (!v.enabled()) return out;
		switch (v.seat()) {
			case QUEUED -> out.add(Gate.on("stand_up", V2 + "leave_queue"));
			case NONE -> {
				if (v.level() == null) {
					for (PokerView.Level l : v.levels()) {
						Object[] args = {l.sb(), l.bb()};
						String label = shortStakes ? V2 + "stakes_short." + l.id() : P + "stakes." + l.id();
						if (!l.vipOk()) out.add(new Gate(LOCAL + "pick:" + l.id(), label, args, false, V2 + "reason.vip_stakes", null));
						else if (!l.affordable()) out.add(new Gate(LOCAL + "pick:" + l.id(), label, args, false, V2 + "reason.buy_in_min",
							new Object[] {Math.max(l.buyMin(), l.bb())}));
						else out.add(new Gate(LOCAL + "pick:" + l.id(), label, args, true, null, null));
					}
				} else if (!v.fixedStake()) {
					out.add(Gate.on(LOCAL + "back", "gui.burmaldaholic.common.back"));
				}
			}
			case SEATED -> {
				PokerView.Turn t = v.turn();
				if (t != null) {
					if (!t.canCheck()) out.add(Gate.on("fold", P + "fold"));
					if (t.canRaise()) {
						if (t.canCheck()) out.add(Gate.on("check", P + "check"));
						else if (t.callIsAllIn()) out.add(Gate.on("call", P + "all_in", t.myBet() + t.myStack()));
						else out.add(Gate.on("call", P + "call", t.toCall()));
					}
				} else if (v.topUpForm()) {
					out.add(Gate.on(LOCAL + "back", "gui.burmaldaholic.common.back"));
				} else {
					if (!v.sittingOut()) out.add(Gate.on("sit_out", P + "sit_out"));
					if (v.canTopUp() && v.topMax() > 0) out.add(Gate.on(LOCAL + "top_up_form", P + "top_up"));
					else out.add(Gate.off(LOCAL + "top_up_form", P + "top_up", V2 + "reason.no_top_up"));
					if (v.leaving()) out.add(Gate.off("stand_up", P + "stand_up", "msg.burmaldaholic.poker.leaving_after_hand"));
					else out.add(Gate.on("stand_up", P + "stand_up"));
				}
			}
		}
		return out;
	}

	/** Gate actions of the kit stepper's − / + (R-AB M3: the kit's {@code Stepper}, HitMap {@code stepper.minus/plus}). */
	public static final String STEP_MINUS = LOCAL + "step_minus", STEP_PLUS = LOCAL + "step_plus";

	/** The stepper's −: off at the minimum, with the reason. */
	public static Gate stepMinus(Stepper s) {
		return s.canMinus() ? Gate.on(STEP_MINUS, V2 + "step_minus") : Gate.off(STEP_MINUS, V2 + "step_minus", V2 + "reason.stepper_min", s.min());
	}

	/** The stepper's +: off at the maximum, with the reason. */
	public static Gate stepPlus(Stepper s) {
		return s.canPlus() ? Gate.on(STEP_PLUS, V2 + "step_plus") : Gate.off(STEP_PLUS, V2 + "step_plus", V2 + "reason.stepper_max", s.max());
	}

	/** The amount stepper (between the action row and the primary), if the state has an amount to choose. */
	public static Optional<Stepper> stepper(PokerView v) {
		if (!v.enabled()) return Optional.empty();
		if (v.seat() == PokerView.Seat.NONE && v.level() != null && v.level().vipOk() && v.level().affordable()) {
			PokerView.Level l = v.level();
			return Optional.of(new Stepper("buy_in", clamp(v.buyIn(), l.buyMin(), l.buyMax()), l.buyMin(), l.buyMax(), Math.max(1, l.bb())));
		}
		if (!v.seated()) return Optional.empty();
		PokerView.Turn t = v.turn();
		if (t != null) {
			if (!t.canRaise()) return Optional.empty();
			return Optional.of(new Stepper("raise", clamp(t.raiseTo(), t.minTo(), t.maxTo()), t.minTo(), t.maxTo(), Math.max(1, t.bb())));
		}
		if (v.topUpForm() && v.canTopUp() && v.topMax() > 0) {
			long step = v.level() == null ? 1 : Math.max(1, v.level().bb());
			return Optional.of(new Stepper("top_up", clamp(v.topUp(), 1, v.topMax()), 1, v.topMax(), step));
		}
		return Optional.empty();
	}

	/**
	 * Raise presets the stepper's value cycles through on click (½ pot, ¾ pot, pot, all-in; the old sizing panel's
	 * buttons), clamped to the legal range, ascending, distinct, starting with the minimum raise.
	 */
	public static long[] raisePresets(PokerView.Turn t) {
		double[] f = {0.5, 0.75, 1.0};
		java.util.TreeSet<Long> set = new java.util.TreeSet<>();
		set.add(t.minTo());
		for (double x : f) {
			long to = t.currentBet() == 0 ? Math.round(t.pot() * x) : Math.round(t.currentBet() + x * (t.pot() + t.toCall()));
			set.add(clamp(to, t.minTo(), t.maxTo()));
		}
		set.add(t.maxTo());
		return set.stream().mapToLong(Long::longValue).toArray();
	}

	/** The preset after {@code value} (wrapping to the first). */
	public static long nextPreset(PokerView.Turn t, long value) {
		long[] p = raisePresets(t);
		for (long x : p) if (x > value) return x;
		return p[0];
	}

	/** Danger style (red): giving up the hand or the seat. */
	public static boolean danger(String action) {
		return action.equals("fold") || action.equals("stand_up");
	}

	/** Keyboard letter shown on the button's key plate (and handled by the screen), or "". */
	public static String key(String action) {
		return switch (action) {
			case "fold" -> "F";
			case "check", "call" -> "C";
			case "raise", "all_in" -> "R";
			default -> "";
		};
	}

	static long clamp(long v, long min, long max) {
		return Math.max(min, Math.min(Math.max(min, max), v));
	}
}
