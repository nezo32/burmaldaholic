package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * The "impossible to get stuck" assertions of docs/architecture/ui-v2.md §5.3, for each game's {@code <G>FlowTest}:
 * enumerate the view's phases × seated × balance × bet × betting open and call {@link #problems} on each.
 *
 * <ol>
 *   <li>at least one gate is enabled (primary, an action, a chip or a tool) <b>or</b> the primary's reason is a waiting
 *       reason with a countdown ≥ 0 or a {@code waitingFor} name, or a round-in-progress / closed-bets reason;</li>
 *   <li>a disabled gate always has a reason (guaranteed by {@link Gate}; re-checked for safety);</li>
 *   <li>seated + betting open + balance ≥ min ⇒ at least one chip is enabled.</li>
 * </ol>
 */
public final class FlowCheck {
	private FlowCheck() {}

	/** Reasons that mean "nothing to press, but you are told what is happening". */
	public static final List<String> WAITING_REASONS = List.of(V2Keys.REASON_WAITING_FOR, V2Keys.REASON_BETS_OPEN_IN,
		V2Keys.REASON_ROUND_IN_PROGRESS, V2Keys.REASON_BETS_CLOSED, V2Keys.REASON_SPINNING, V2Keys.REASON_ROLLING, V2Keys.REASON_DEALING,
		V2Keys.REASON_TABLE_FULL, V2Keys.REASON_NOT_ENOUGH_MIN);

	public static <V> List<String> problems(Flow<V> flow, V view) {
		List<String> out = new ArrayList<>();
		List<Gate> all = new ArrayList<>();
		Gate primary = flow.primary(view);
		if (primary == null) {
			out.add("primary is null");
			return out;
		}
		all.add(primary);
		all.addAll(flow.actions(view));
		BetView bet = flow.bet(view);
		boolean chipOn = false;
		if (bet != null) {
			for (long d : bet.chips()) {
				Gate g = flow.chip(d, view);
				all.add(g);
				chipOn |= g.enabled();
			}
			for (String t : BetGates.TOOLS) all.add(flow.tool(t, view));
		}
		for (Gate g : all) {
			if (!g.enabled() && (g.reasonKey() == null || g.reasonKey().isEmpty())) out.add("disabled without reason: " + g.labelKey());
		}
		boolean anyOn = all.stream().anyMatch(Gate::enabled);
		if (!anyOn) {
			String r = primary.reasonKey();
			if (r == null || !(WAITING_REASONS.contains(r) || isGameWaitingReason(r))) {
				out.add("stuck: nothing enabled and the primary's reason '" + r + "' is not a waiting reason");
			}
		}
		long[] offered = bet == null ? new long[0] : bet.chips();
		if (bet != null && bet.seated() && bet.bettingOpen() && bet.balance() >= bet.min() && !chipOn
			&& (offered.length == 0 || offered[0] <= bet.roomUnderMax())) {
			out.add("seated, betting open, balance " + bet.balance() + " ≥ min " + bet.min() + " but no chip is enabled");
		}
		return out;
	}

	/** Game-specific waiting reasons follow the naming rule {@code …reason.waiting…} / {@code …reason.wait_…}. */
	private static boolean isGameWaitingReason(String key) {
		int i = key.lastIndexOf(".reason.");
		return i >= 0 && key.substring(i + 8).startsWith("wait");
	}
}
