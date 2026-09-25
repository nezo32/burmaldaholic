package dev.nezo.burmaldaholic.games.extras.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The in-world Lucky Coin toss: the item of an item display (private in vanilla). */
@Mixin(Display.ItemDisplay.class)
public interface TossItemDisplayAccessor {
	@Invoker("setItemStack")
	void burmaldaholic$extrasItem(ItemStack stack);
}
