package dev.nezo.burmaldaholic.games.extras.item;

import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
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

/** Lucky Coin (GAME_DESIGN.md §11.1): usable anywhere, not consumed; opens the Coin Flip screen. */
public class LuckyCoinItem extends Item {
	public LuckyCoinItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			CoinFlipGame.open(sp);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.burmaldaholic.lucky_coin").withStyle(ChatFormatting.GRAY));
	}
}
