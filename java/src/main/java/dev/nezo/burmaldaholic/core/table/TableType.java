package dev.nezo.burmaldaholic.core.table;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Everything registered for one kind of casino table. Created by {@link TableRegistrar#register};
 * keep it in a static field of your module, e.g. {@code BlackjackModule.TABLE}.
 */
public final class TableType<BE extends CasinoTableBlockEntity> {
	private final String name;
	private CasinoTableBlock block;
	private Item item;
	private BlockEntityType<BE> blockEntityType;
	private ExtendedMenuType<CasinoTableMenu, BlockPos> menuType;

	TableType(String name) {
		this.name = name;
	}

	void bind(CasinoTableBlock block, Item item, BlockEntityType<BE> beType, ExtendedMenuType<CasinoTableMenu, BlockPos> menuType) {
		this.block = block;
		this.item = item;
		this.blockEntityType = beType;
		this.menuType = menuType;
	}

	public String name() {
		return name;
	}

	public CasinoTableBlock block() {
		return block;
	}

	public Item item() {
		return item;
	}

	public BlockEntityType<BE> blockEntityType() {
		return blockEntityType;
	}

	public ExtendedMenuType<CasinoTableMenu, BlockPos> menuType() {
		return menuType;
	}
}
