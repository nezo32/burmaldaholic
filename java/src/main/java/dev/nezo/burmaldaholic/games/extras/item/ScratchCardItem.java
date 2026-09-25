package dev.nezo.burmaldaholic.games.extras.item;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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

/** Scratch card (basic / gold, GAME_DESIGN.md §11.3). Use → scratch screen. */
public class ScratchCardItem extends Item {
	private final Scratch.Kind kind;

	public ScratchCardItem(Properties properties, Scratch.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Scratch.Kind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ScratchGame.use(sp, player.getItemInHand(hand), kind);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		CompoundTag data = ScratchGame.data(stack);
		if (data != null) {
			builder.accept(Component.translatable("tooltip.burmaldaholic.scratch_card.progress",
				Texts.number(Scratch.revealedCount(data.getIntOr("mask", 0)))).withStyle(ChatFormatting.GRAY));
			return;
		}
		double[][] prizes = kind == Scratch.Kind.GOLD ? CasinoConfig.extras().scratch.gold.prizes : CasinoConfig.extras().scratch.basic.prizes;
		builder.accept(Component.translatable("tooltip.burmaldaholic.scratch_card", Texts.chips(Scratch.topPrize(Scratch.table(prizes))))
			.withStyle(ChatFormatting.GRAY));
	}
}
