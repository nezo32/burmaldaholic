package dev.nezo.burmaldaholic.core.chips;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A physical chip worth {@link #value()} chips (§3.1). Does nothing on use; deposit at a Cashier. */
public class ChipItem extends Item {
	private final int value;

	public ChipItem(Properties properties, int value) {
		super(properties);
		this.value = value;
	}

	public int value() {
		return value;
	}

	/** Chip value of a stack (0 for non-chips). */
	public static long valueOf(ItemStack stack) {
		return stack.getItem() instanceof ChipItem chip ? (long) chip.value * stack.getCount() : 0;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.burmaldaholic.chip.value", Texts.chips(value)).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.burmaldaholic.chip.hint").withStyle(ChatFormatting.GRAY));
	}
}
