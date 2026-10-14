package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * One call registers a table: block {@code burmaldaholic:<name>}, its item, block entity type
 * and menu type. Example (BlackjackModule.register):
 *
 * <pre>
 * TABLE = ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new);
 * </pre>
 *
 * and in BlackjackClientModule: {@code ctx.tableScreen(BlackjackModule.TABLE, BlackjackScreen::new);}
 */
public final class TableRegistrar {
	private final ModuleContext ctx;

	public TableRegistrar(ModuleContext ctx) {
		this.ctx = ctx;
	}

	@FunctionalInterface
	public interface BlockEntityFactory<BE extends CasinoTableBlockEntity> {
		BE create(TableType<BE> type, BlockPos pos, net.minecraft.world.level.block.state.BlockState state);
	}

	public static BlockBehaviour.Properties defaultProperties() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.WOOD).noOcclusion();
	}

	/**
	 * MT §2: multi-block table / machine properties — solid for fluids and mob spawning rules, never a spawn spot.
	 * Pistons: every part is a state of a block-entity block, which vanilla never pushes (pushReaction is NOT set:
	 * {@code PushReaction.BLOCK} was renamed {@code IMMOVEABLE} in 26.3, so naming it would break one of the two
	 * versions). Suffocation / view blocking stay vanilla-default (not a full cube → false; {@code isViewBlocking} also
	 * changed its lambda type in 26.3).
	 */
	public static BlockBehaviour.Properties multiBlockProperties(BlockBehaviour.Properties props) {
		return props.forceSolidOn().isValidSpawn((state, level, pos, entityType) -> false);
	}

	public <BE extends CasinoTableBlockEntity> TableType<BE> register(String name, BlockEntityFactory<BE> beFactory) {
		return register(name, beFactory, defaultProperties());
	}

	public <BE extends CasinoTableBlockEntity> TableType<BE> register(String name, BlockEntityFactory<BE> beFactory,
			BlockBehaviour.Properties props) {
		dev.nezo.burmaldaholic.core.table.parts.TableGame game = dev.nezo.burmaldaholic.core.table.parts.TableGame.ofBlockName(name);
		if (game != null) {
			return register(name, beFactory, multiBlockProperties(props), (p, t) -> new MultiBlockTableBlock(p, t, game));
		}
		dev.nezo.burmaldaholic.core.table.parts.MachineKind machine = dev.nezo.burmaldaholic.core.table.parts.MachineKind.ofBlockName(name);
		if (machine != null) {
			return register(name, beFactory, multiBlockProperties(props), (p, t) -> MultiBlockTableBlock.machine(p, t, machine));
		}
		return register(name, beFactory, props, CasinoTableBlock::new);
	}

	/** Custom block class (e.g. a different shape); it must extend {@link CasinoTableBlock}. */
	@FunctionalInterface
	public interface BlockFactory {
		CasinoTableBlock create(BlockBehaviour.Properties props, TableType<?> type);
	}

	public <BE extends CasinoTableBlockEntity> TableType<BE> register(String name, BlockEntityFactory<BE> beFactory,
			BlockBehaviour.Properties props, BlockFactory blockFactory) {
		TableType<BE> type = new TableType<>(name, ctx.moduleId());
		CasinoTableBlock block = ctx.registry().blockWithItem(name, p -> blockFactory.create(p, type), props);
		BlockEntityType<BE> beType = ctx.registry().blockEntity(name, (pos, state) -> beFactory.create(type, pos, state), block);
		ExtendedMenuType<CasinoTableMenu, BlockPos> menuType = ctx.registry().menu(name,
			(syncId, inventory, pos) -> new CasinoTableMenu(type, syncId, inventory, pos), BlockPos.STREAM_CODEC);
		Item item = block.asItem();
		type.bind(block, item, beType, menuType);
		return type;
	}
}
