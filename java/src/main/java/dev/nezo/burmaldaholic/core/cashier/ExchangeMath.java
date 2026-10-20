package dev.nezo.burmaldaholic.core.cashier;

/**
 * Pure maths of the cashier exchange and the store (EC-SPEC §1): how many items a request may move. The client never
 * computes "all"; the server does it here. Inventory slots are modelled as {@link Slot}s (the 36 main slots).
 */
public final class ExchangeMath {
	/** One request moves at most a full inventory: 36 × 64 items. */
	public static final int MAX_UNITS = 36 * 64;

	private ExchangeMath() {}

	/**
	 * One inventory slot: {@code same} = holds the same item with the same components as what is inserted,
	 * {@code count}, and {@code max} = the stack limit of what is in it (or of the inserted item when empty).
	 */
	public record Slot(boolean empty, boolean same, int count, int max) {
		public static Slot emptySlot() {
			return new Slot(true, false, 0, 0);
		}
	}

	/**
	 * How many items of a stack size {@code itemMax} fit into {@code slots}: free slots × max stack, plus the room left on
	 * partial stacks of the same item. Unstackables ({@code itemMax = 1}) only go into empty slots.
	 */
	public static long fitCount(Slot[] slots, int itemMax) {
		long fit = 0;
		int max = Math.max(1, itemMax);
		for (Slot s : slots) {
			if (s.empty()) {
				fit += max;
			} else if (s.same()) {
				fit += Math.max(0, Math.min(max, s.max() > 0 ? s.max() : max) - s.count());
			}
		}
		return fit;
	}

	/**
	 * Items handed in (item → chips): {@code min(qty | have, room / toChips, MAX_UNITS)}.
	 *
	 * @param qty     requested count (ignored when {@code all})
	 * @param have    matching items in the inventory
	 * @param room    chips left under the balance cap
	 */
	public static long countIn(long qty, boolean all, long have, long room, int toChips) {
		if (toChips <= 0) return 0;
		long n = all ? have : Math.min(Math.max(0, qty), have);
		n = Math.min(n, Math.max(0, room) / toChips);
		return Math.max(0, Math.min(n, MAX_UNITS));
	}

	/**
	 * Items taken out (chips → item): {@code min(qty | ∞, withdrawable / fromChips, fit, MAX_UNITS)}.
	 */
	public static long countOut(long qty, boolean all, long withdrawable, int fromChips, long fit) {
		if (fromChips <= 0) return 0;
		long n = all ? Long.MAX_VALUE : Math.max(0, qty);
		n = Math.min(n, Math.max(0, withdrawable) / fromChips);
		n = Math.min(n, Math.max(0, fit));
		return Math.max(0, Math.min(n, MAX_UNITS));
	}

	/**
	 * Store buy: {@code min(qty | ∞, balance / price, fit, dailyLeft)} purchases ({@code dailyLeft < 0} = no limit), each
	 * purchase being {@code countPer} items (fit is in items).
	 */
	public static long countBuy(long qty, boolean all, long balance, long price, long fitItems, int countPer, long dailyLeft) {
		if (price <= 0) return 0;
		long n = all ? Long.MAX_VALUE : Math.max(0, qty);
		n = Math.min(n, Math.max(0, balance) / price);
		n = Math.min(n, Math.max(0, fitItems) / Math.max(1, countPer));
		if (dailyLeft >= 0) n = Math.min(n, dailyLeft);
		return Math.max(0, Math.min(n, MAX_UNITS));
	}
}
