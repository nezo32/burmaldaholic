package dev.nezo.burmaldaholic.core.chips;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The five chip items {@code burmaldaholic:chip_<value>} (§3.1). */
public final class Chips {
	private static final Map<Integer, ChipItem> ITEMS = new LinkedHashMap<>();

	private Chips() {}

	public static void register(ModuleContext ctx) {
		register(ctx, 1, Rarity.COMMON);
		register(ctx, 5, Rarity.COMMON);
		register(ctx, 25, Rarity.UNCOMMON);
		register(ctx, 100, Rarity.RARE);
		register(ctx, 500, Rarity.EPIC);
	}

	private static void register(ModuleContext ctx, int value, Rarity rarity) {
		ITEMS.put(value, ctx.registry().item("chip_" + value, p -> new ChipItem(p, value), new Item.Properties().rarity(rarity)));
	}

	/** The chip item worth {@code value} (1, 5, 25, 100 or 500). */
	public static ChipItem item(int value) {
		ChipItem item = ITEMS.get(value);
		if (item == null) {
			throw new IllegalArgumentException("No chip worth " + value);
		}
		return item;
	}
}
