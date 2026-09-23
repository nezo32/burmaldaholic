package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
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

/** Diamond Casino Card (§12 Diamond cosmetic): replaces the Casino Card of Diamond+ VIPs and opens the Casino Menu. */
public class DiamondCasinoCardItem extends Item {
	public DiamondCasinoCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			if (CasinoMode.isEnabled(sp)) {
				VipService.send(sp, true, true);
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
