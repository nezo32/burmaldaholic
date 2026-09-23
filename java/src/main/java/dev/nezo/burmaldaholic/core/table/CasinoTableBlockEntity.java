package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative game state for one table. Subclass per game:
 *
 * <ul>
 *   <li>{@link #onAction} — validate & apply a player's input (bet, hit, spin...). Casino mode,
 *       distance and "has this table open" are already checked by core.</li>
 *   <li>{@link #writeClientState} — what {@code viewer} may see (hide other players' hole cards!).</li>
 *   <li>call {@link #syncViewers()} after every state change.</li>
 *   <li>persist long-lived state in {@code saveAdditional/loadAdditional} (ValueOutput/ValueInput).</li>
 * </ul>
 *
 * Keep the actual rules in a pure-Java class ({@code games/<name>/logic}) so they are unit-testable.
 */
public abstract class CasinoTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private final TableType<?> tableType;

	protected CasinoTableBlockEntity(TableType<?> tableType, BlockPos pos, BlockState state) {
		super(tableType.blockEntityType(), pos, state);
		this.tableType = tableType;
	}

	public TableType<?> tableType() {
		return tableType;
	}

	/** Handle a client action. {@code args} is untrusted input. */
	public abstract void onAction(ServerPlayer player, String action, CompoundTag args);

	/** Viewer-specific snapshot for the client screen. */
	public abstract CompoundTag writeClientState(ServerPlayer viewer);

	public void sendStateTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, new TableSyncPayload(worldPosition, writeClientState(player)));
	}

	/** Re-sends state to every player who currently has this table's screen open. */
	public void syncViewers() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		for (ServerPlayer player : serverLevel.players()) {
			if (player.containerMenu instanceof CasinoTableMenu menu && menu.pos().equals(worldPosition)) {
				sendStateTo(player);
			}
		}
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new CasinoTableMenu(tableType, syncId, inventory, worldPosition);
	}
}
