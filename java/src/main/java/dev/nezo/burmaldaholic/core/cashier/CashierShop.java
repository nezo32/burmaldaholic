package dev.nezo.burmaldaholic.core.cashier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Cashier "Shop" tab (UI.md §3: scratch cards, lucky coin): items bought for chips from the balance.
 * Modules register offers in {@code register}; the cashier shows them, checks the VIP tier and
 * funds, debits and hands out the item.
 *
 * <pre>
 * CashierShop.add(new CashierShop.Offer("scratch_card", () -&gt; new ItemStack(SCRATCH_CARD), () -&gt; price, 0, () -&gt; enabled));
 * </pre>
 */
public final class CashierShop {
	/**
	 * @param id      stable id (owned name, e.g. "scratch_card_gold")
	 * @param item    what the player gets (one purchase)
	 * @param price   chips (read when shown / bought)
	 * @param minTier VIP tier required (0 = everyone)
	 * @param enabled offer currently available (e.g. the game's config switch)
	 */
	public record Offer(String id, Supplier<ItemStack> item, LongSupplier price, int minTier, BooleanSupplier enabled) {}

	private static final List<Offer> OFFERS = new CopyOnWriteArrayList<>();

	private CashierShop() {}

	public static void add(Offer offer) {
		OFFERS.removeIf(o -> o.id().equals(offer.id()));
		OFFERS.add(offer);
	}

	/** Offers currently enabled. */
	public static List<Offer> offers() {
		return OFFERS.stream().filter(o -> {
			try {
				return o.enabled().getAsBoolean();
			} catch (RuntimeException e) {
				return false;
			}
		}).toList();
	}

	public static @Nullable Offer byId(String id) {
		for (Offer o : offers()) {
			if (o.id().equals(id)) {
				return o;
			}
		}
		return null;
	}
}
