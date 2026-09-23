package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-less menu shared by all tables. It only carries the table position; state travels via
 * {@link dev.nezo.burmaldaholic.core.network.TableSyncPayload} and input via
 * {@link dev.nezo.burmaldaholic.core.network.TableActionPayload}.
 */
public class CasinoTableMenu extends AbstractContainerMenu {
	private final TableType<?> tableType;
	private final BlockPos pos;

	public CasinoTableMenu(TableType<?> tableType, int syncId, Inventory inventory, BlockPos pos) {
		super(tableType.menuType(), syncId);
		this.tableType = tableType;
		this.pos = pos;
	}

	public TableType<?> tableType() {
		return tableType;
	}

	public BlockPos pos() {
		return pos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		double max = CasinoConfig.multiplayer().tableLeaveDistance;
		return player.level().getBlockState(pos).is(tableType.block())
			&& player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
	}
}
