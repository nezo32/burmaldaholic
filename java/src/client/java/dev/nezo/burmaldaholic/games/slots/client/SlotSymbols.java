package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Reel symbol icons: vanilla item renders (no text baked in, resource-pack friendly). */
final class SlotSymbols {
	private static Map<Symbol, ItemStack> icons;

	private SlotSymbols() {}

	static ItemStack icon(Symbol s) {
		if (icons == null) {
			Map<Symbol, ItemStack> m = new EnumMap<>(Symbol.class);
			m.put(Symbol.BERRIES, new ItemStack(Items.SWEET_BERRIES));
			m.put(Symbol.APPLE, new ItemStack(Items.APPLE));
			m.put(Symbol.GOLDEN_CARROT, new ItemStack(Items.GOLDEN_CARROT));
			m.put(Symbol.EMERALD, new ItemStack(Items.EMERALD));
			m.put(Symbol.DIAMOND, new ItemStack(Items.DIAMOND));
			m.put(Symbol.SEVEN, new ItemStack(Items.REDSTONE));
			m.put(Symbol.WILD, new ItemStack(Items.TOTEM_OF_UNDYING));
			m.put(Symbol.CREEPER, new ItemStack(Items.CREEPER_HEAD));
			m.put(Symbol.TNT, new ItemStack(Items.TNT));
			m.put(Symbol.PEARL, new ItemStack(Items.ENDER_PEARL));
			m.put(Symbol.CLOCK, new ItemStack(Items.CLOCK));
			m.put(Symbol.STAR, new ItemStack(Items.NETHER_STAR));
			icons = m;
		}
		return icons.get(s);
	}
}
