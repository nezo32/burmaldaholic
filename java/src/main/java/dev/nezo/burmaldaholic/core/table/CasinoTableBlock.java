package dev.nezo.burmaldaholic.core.table;

import com.mojang.serialization.MapCodec;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Generic table block. Right-click opens the table's menu (if casino mode is on). Games normally
 * do not subclass this — all game logic lives in the {@link CasinoTableBlockEntity} subclass.
 */
public class CasinoTableBlock extends BaseEntityBlock {
	private final TableType<?> type;

	public CasinoTableBlock(Properties properties, TableType<?> type) {
		super(properties);
		this.type = type;
	}

	public TableType<?> tableType() {
		return type;
	}

	/**
	 * 26.2 declares {@code BaseEntityBlock.codec()} abstract; 26.3 removed block codecs entirely.
	 * Deliberately WITHOUT {@code @Override} and without {@code simpleCodec} so the same source
	 * compiles and links on both versions. Copy this pattern for any custom BaseEntityBlock.
	 */
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return MapCodec.unit(this);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return type.blockEntityType().create(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!CasinoMode.isEnabled(level)) {
			player.sendOverlayMessage(Component.translatable("burmaldaholic.core.casino_disabled"));
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
			serverPlayer.openMenu(table);
			table.sendStateTo(serverPlayer);
		}
		return InteractionResult.CONSUME;
	}
}
