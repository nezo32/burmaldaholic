package dev.nezo.burmaldaholic.core.table;

import com.mojang.serialization.MapCodec;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Generic table block: horizontal {@link #FACING} (faces the placer), server ticker for the block
 * entity, right-click opens the table's menu when casino mode is on (else
 * {@code gui.burmaldaholic.error.casino_off}). Games normally do not subclass this — all game logic
 * lives in the {@link CasinoTableBlockEntity} subclass. Blockstates need a {@code facing=} variant set.
 */
public class CasinoTableBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static volatile HouseTables houseTables = HouseTables.NONE;
	private final TableType<?> type;

	/**
	 * Which tables are "house" tables: game blocks a generated casino placed (worldgen's {@code HouseGames}, R-CB M9).
	 * A house table drops nothing (any break path: operators, {@code /setblock … destroy}, withers) and explosions
	 * leave it standing; the worldgen module also refuses non-operator players breaking one. Core only asks.
	 */
	public interface HouseTables {
		HouseTables NONE = new HouseTables() {
			@Override
			public boolean isHouseTable(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
				return false;
			}

			@Override
			public void placedByPlayer(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {}
		};

		/** True for a table at {@code pos} that a generated casino placed ({@code be}: its block entity, if known). */
		boolean isHouseTable(ServerLevel level, BlockPos pos, @Nullable BlockEntity be);

		/** A player placed this table (it is theirs even inside a generated casino). */
		void placedByPlayer(ServerLevel level, BlockPos pos, @Nullable BlockEntity be);
	}

	public static void setHouseTables(HouseTables tables) {
		houseTables = tables == null ? HouseTables.NONE : tables;
	}

	public static boolean isHouseTable(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
		return houseTables.isHouseTable(level, pos, be);
	}

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
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof Player) {
			houseTables.placedByPlayer(serverLevel, pos, level.getBlockEntity(pos));
		}
	}

	/** House tables drop nothing, whoever or whatever removes them (R-CB M9: no free tables from generated casinos). */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && houseTables.isHouseTable(params.getLevel(), BlockPos.containing(origin),
			params.getOptionalParameter(LootContextParams.BLOCK_ENTITY))) {
			return List.of();
		}
		return super.getDrops(state, params);
	}

	/** Explosions (TNT, creepers, fireballs) leave house tables standing; other tables break and drop as usual. */
	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
			java.util.function.BiConsumer<ItemStack, BlockPos> dropConsumer) {
		if (houseTables.isHouseTable(level, pos, level.getBlockEntity(pos))) {
			return;
		}
		super.onExplosionHit(state, level, pos, explosion, dropConsumer);
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
