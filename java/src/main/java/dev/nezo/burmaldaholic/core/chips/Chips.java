package dev.nezo.burmaldaholic.core.chips;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The chip items {@code burmaldaholic:chip_<value>}, one per {@link ChipMath#DENOMINATIONS} value (§3.1). */
public final class Chips {
	private static final Map<Integer, ChipItem> ITEMS = new LinkedHashMap<>();

	private Chips() {}

	public static void register(ModuleContext ctx) {
		for (int i = ChipMath.DENOMINATIONS.length - 1; i >= 0; i--) { // smallest first: creative tab / registry order
			int value = ChipMath.DENOMINATIONS[i];
			register(ctx, value, rarity(value));
		}
	}

	/** Item rarity by value: common up to 5, uncommon 25, rare 100, epic from 500 (the high-roller chips). */
	static Rarity rarity(int value) {
		return value >= 500 ? Rarity.EPIC : value >= 100 ? Rarity.RARE : value >= 25 ? Rarity.UNCOMMON : Rarity.COMMON;
	}

	private static void register(ModuleContext ctx, int value, Rarity rarity) {
		ITEMS.put(value, ctx.registry().item("chip_" + value, p -> new ChipItem(p, value), new Item.Properties().rarity(rarity)));
	}

	/** The chip item worth {@code value} (one of {@link ChipMath#DENOMINATIONS}). */
	public static ChipItem item(int value) {
		ChipItem item = ITEMS.get(value);
		if (item == null) {
			throw new IllegalArgumentException("No chip worth " + value);
		}
		return item;
	}
}
