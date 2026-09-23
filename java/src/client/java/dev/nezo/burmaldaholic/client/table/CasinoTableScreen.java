package dev.nezo.burmaldaholic.client.table;

import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Base screen for tables: renders {@link #state()} and sends input with {@link #sendAction}.
 * NEVER decide outcomes on the client. All text via Component.translatable.
 */
public abstract class CasinoTableScreen extends AbstractContainerScreen<CasinoTableMenu> {
	private CompoundTag state = new CompoundTag();

	protected CasinoTableScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.inventoryLabelY = -10_000; // slot-less menus: hide the "Inventory" label
	}

	@Override
	protected void init() {
		super.init();
		acceptState(ClientTableCache.get(menu.pos()));
	}

	/** Latest server state (never null). */
	protected CompoundTag state() {
		return state;
	}

	public final void acceptState(CompoundTag newState) {
		this.state = newState;
		onStateChanged(newState);
	}

	/** Rebuild widgets / animations here. */
	protected void onStateChanged(CompoundTag newState) {}

	protected void sendAction(String action, CompoundTag args) {
		ClientPlayNetworking.send(new TableActionPayload(menu.pos(), action, args));
	}

	protected void sendAction(String action) {
		sendAction(action, new CompoundTag());
	}
}
