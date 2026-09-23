package dev.nezo.burmaldaholic.core.registry;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** One shared creative tab; items are appended by {@link ModRegistrar#item}. */
public final class CasinoCreativeTab {
	private static final List<ItemLike> ITEMS = new ArrayList<>();
	private static ItemLike icon = Items.EMERALD;

	private CasinoCreativeTab() {}

	static void add(ItemLike item) {
		ITEMS.add(item);
	}

	/** Core only: the tab icon (e.g. the chip item once it exists). */
	public static void setIcon(ItemLike item) {
		icon = item;
	}

	public static void register() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.burmaldaholic.main"))
			.icon(() -> new ItemStack(icon))
			.displayItems((params, output) -> ITEMS.forEach(output::accept))
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Burmaldaholic.id("main"), tab);
	}
}
