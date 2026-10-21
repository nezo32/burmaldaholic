package dev.nezo.burmaldaholic.core.economy;

/**
 * The single source of the house-edge rebate caps (GAME_DESIGN §12 / §12.1, EC-SPEC §4.1). Pure.
 *
 * <ul>
 *   <li>Cashback rate: clamped to [0, 0.5].</li>
 *   <li>Comp-point rate: clamped so {@code comp + cashback ≤ 0.35} per tier (at least 65 % of the edge stays).</li>
 * </ul>
 * Used by the VIP perks, the VIP config validation and the comps runtime, so they can never disagree.
 */
public final class RebateCaps {
	public static final double MAX_CASHBACK = 0.5;
	public static final double MAX_COMP_PLUS_CASHBACK = 0.35;

	private RebateCaps() {}

	/** A configured cashback rate, clamped to [0, 0.5] (non-finite = 0). */
	public static double cashback(double rate) {
		return Double.isFinite(rate) ? Math.max(0, Math.min(MAX_CASHBACK, rate)) : 0;
	}

	/** A configured comp rate next to a tier's cashback rate, clamped to {@code [0, 0.35 − cashback]}. */
	public static double comp(double rate, double cashbackRate) {
		if (!Double.isFinite(rate) || rate <= 0) {
			return 0;
		}
		return Math.min(rate, Math.max(0, MAX_COMP_PLUS_CASHBACK - cashback(cashbackRate)));
	}
}
