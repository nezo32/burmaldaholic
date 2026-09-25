package dev.nezo.burmaldaholic.core.table;

import com.mojang.serialization.MapCodec;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Generic table block: horizontal {@link #FACING} (faces the placer), server ticker for the block
 * entity, right-click opens the table's menu when casino mode is on (else
 * {@code gui.burmaldaholic.error.casino_off}). Games normally do not subclass this — all game logic
 * lives in the {@link CasinoTableBlockEntity} subclass. Blockstates need a {@code facing=} variant set.
 */
public class CasinoTableBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final TableType<?> type;

	public CasinoTableBlock(Properties properties, TableType<?> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return type.blockEntityType().create(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
		if (!(level instanceof ServerLevel) || beType != type.blockEntityType()) {
			return null;
		}
		return (lvl, pos, st, be) -> ((CasinoTableBlockEntity) be).tick((ServerLevel) lvl);
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
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
			serverPlayer.openMenu(table);
			table.sendStateTo(serverPlayer);
		}
		return InteractionResult.CONSUME;
	}
}
