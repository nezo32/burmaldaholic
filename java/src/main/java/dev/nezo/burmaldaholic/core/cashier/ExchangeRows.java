package dev.nezo.burmaldaholic.core.cashier;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The cashier's exchange rows for a dimension ({@code economy.exchange}, EC-SPEC §2) and the inventory side of an
 * exchange: counting, removing and inserting into the 36 main slots only (never the armour / off-hand slots, never
 * dropped at the feet).
 */
public final class ExchangeRows {
	/** The main inventory (hotbar + 27). */
	public static final int MAIN_SLOTS = 36;

	private ExchangeRows() {}

	/** A resolved row. */
	public record Row(String id, Item item, int toChips, int fromChips, int buyMinTier) {}

	/** Rows offered in dimension {@code dim} ({@code O/N/E/?}), in config order; unknown items and empty rows skipped. */
	public static List<Row> rows(String dim) {
		List<Row> out = new ArrayList<>();
		EconomyConfig c = CasinoConfig.economy();
		c.exchange.forEach((id, r) -> {
			if (r.toChips <= 0 && r.fromChips <= 0) return;
			if (!r.dims.isEmpty() && !r.dims.contains(dim)) return;
			item(r.item).ifPresent(item -> out.add(new Row(id, item, r.toChips, r.fromChips, r.buyMinTier)));
		});
		return out;
	}

	public static Optional<Row> row(String dim, String id) {
		return rows(dim).stream().filter(r -> r.id().equals(id)).findFirst();
	}

	/** The item of a registry id, or empty (air / unknown / malformed). */
	public static Optional<Item> item(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) return Optional.empty();
		return BuiltInRegistries.ITEM.getOptional(key).filter(i -> i != Items.AIR);
	}

	/** Plain {@code item} stacks in the main slots (stacks with extra components, e.g. renamed, are not money). */
	public static long count(Inventory inv, Item item) {
		long n = 0;
		for (int i = 0; i < MAIN_SLOTS; i++) {
			ItemStack s = inv.getItem(i);
			if (plain(s, item)) n += s.getCount();
		}
		return n;
	}

	private static boolean plain(ItemStack s, Item item) {
		return !s.isEmpty() && s.is(item) && s.getComponentsPatch().isEmpty();
	}

	/** Removes up to {@code n} plain {@code item}s from the main slots; returns how many were actually removed. */
	public static long remove(Inventory inv, Item item, long n) {
		long left = n;
		for (int i = 0; i < MAIN_SLOTS && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (plain(s, item)) {
				int take = (int) Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		return n - left;
	}

	/** The main slots as {@link ExchangeMath.Slot}s for inserting {@code proto}. */
	public static ExchangeMath.Slot[] slots(Inventory inv, ItemStack proto) {
		ExchangeMath.Slot[] out = new ExchangeMath.Slot[MAIN_SLOTS];
		for (int i = 0; i < MAIN_SLOTS; i++) {
			ItemStack s = inv.getItem(i);
			out[i] = s.isEmpty() ? ExchangeMath.Slot.emptySlot()
				: new ExchangeMath.Slot(false, ItemStack.isSameItemSameComponents(s, proto), s.getCount(), s.getMaxStackSize());
		}
		return out;
	}

	/** How many items like {@code proto} fit into the main slots. */
	public static long fit(Inventory inv, ItemStack proto) {
		return ExchangeMath.fitCount(slots(inv, proto), proto.getMaxStackSize());
	}

	/**
	 * Inserts {@code n} copies of {@code proto} into the main slots (partial stacks first, then empty slots, the same
	 * order as {@link ExchangeMath#fitCount}); returns how many went in (the rest is the caller's to refund).
	 */
	public static long insert(Inventory inv, ItemStack proto, long n) {
		int max = proto.getMaxStackSize();
		long left = n;
		for (int i = 0; i < MAIN_SLOTS && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, proto) && s.getCount() < Math.min(max, s.getMaxStackSize())) {
				int add = (int) Math.min(left, Math.min(max, s.getMaxStackSize()) - s.getCount());
				s.grow(add);
				left -= add;
			}
		}
		for (int i = 0; i < MAIN_SLOTS && left > 0; i++) {
			if (inv.getItem(i).isEmpty()) {
				int add = (int) Math.min(left, max);
				inv.setItem(i, proto.copyWithCount(add));
				left -= add;
			}
		}
		inv.setChanged();
		return n - left;
	}
}
