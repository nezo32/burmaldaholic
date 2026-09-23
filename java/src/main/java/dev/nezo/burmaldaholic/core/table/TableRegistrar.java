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

	public <BE extends CasinoTableBlockEntity> TableType<BE> register(String name, BlockEntityFactory<BE> beFactory) {
		return register(name, beFactory, defaultProperties());
	}

	public <BE extends CasinoTableBlockEntity> TableType<BE> register(String name, BlockEntityFactory<BE> beFactory,
			BlockBehaviour.Properties props) {
		TableType<BE> type = new TableType<>(name);
		CasinoTableBlock block = ctx.registry().blockWithItem(name, p -> new CasinoTableBlock(p, type), props);
		BlockEntityType<BE> beType = ctx.registry().blockEntity(name, (pos, state) -> beFactory.create(type, pos, state), block);
		ExtendedMenuType<CasinoTableMenu, BlockPos> menuType = ctx.registry().menu(name,
			(syncId, inventory, pos) -> new CasinoTableMenu(type, syncId, inventory, pos), BlockPos.STREAM_CODEC);
		Item item = block.asItem();
		type.bind(block, item, beType, menuType);
		return type;
	}
}
