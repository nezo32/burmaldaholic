package dev.nezo.burmaldaholic.multiplayer.block;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.multiplayer.Ownership;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Casino Charter (§18.2). Placing it claims a casino (license fee from the balance, refused if the claim
 * would overlap another casino / spawn or the player owns the maximum); right-click opens the charter screen
 * for the owner (and operators). Break protection and closing live in {@link Ownership}.
 */
public class CharterBlock extends Block {
	public CharterBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
			Component error = Ownership.claimError(player, level, context.getClickedPos());
			if (error != null) {
				player.sendSystemMessage(error);
				return null;
			}
		}
		return defaultBlockState();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) {
			Ownership.claim(placer instanceof ServerPlayer p ? p : null, serverLevel, pos);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!CasinoMode.isEnabled(level)) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			Ownership.onCharterUse(serverPlayer, serverLevel, pos);
		}
		return InteractionResult.CONSUME;
	}
}
