package dev.nezo.burmaldaholic.core.chips;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Casino Card (§3.3). Given on first join. Using it opens the Casino Menu (UI.md §2) and shows the
 * balance in the action bar.
 */
public class CasinoCardItem extends Item {
	public CasinoCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			if (CasinoMode.isEnabled(sp)) {
				sp.sendOverlayMessage(Component.translatable("gui.burmaldaholic.common.balance", Texts.number(Economies.get().balance(sp))));
				CasinoMenu.open(sp, "");
			} else {
				sp.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.burmaldaholic.casino_card").withStyle(ChatFormatting.GRAY));
	}
}
