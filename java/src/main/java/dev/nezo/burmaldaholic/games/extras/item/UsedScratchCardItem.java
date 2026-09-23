package dev.nezo.burmaldaholic.games.extras.item;

import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
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

/** A fully scratched card: junk (stack 64). Using it opens the scratch screen (buy another basic card). */
public class UsedScratchCardItem extends Item {
	public UsedScratchCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ScratchGame.use(sp, ItemStack.EMPTY, Scratch.Kind.BASIC);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.burmaldaholic.scratch_card_used").withStyle(ChatFormatting.GRAY));
	}
}
