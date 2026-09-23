package dev.nezo.burmaldaholic.games.extras.item;

import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Dice (GAME_DESIGN.md §11.5): used on air → duel the house; used on a player → challenge them. */
public class DiceItem extends Item {
	public DiceItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			DiceGame.open(sp, null);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof Player)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && target instanceof ServerPlayer other) {
			DiceGame.open(sp, other);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.burmaldaholic.dice").withStyle(ChatFormatting.GRAY));
	}
}
