package dev.nezo.burmaldaholic.client.module;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Client-side registration helpers for a module. */
public final class ClientModuleContext {
	private final String moduleId;

	public ClientModuleContext(String moduleId) {
		this.moduleId = moduleId;
	}

	public String moduleId() {
		return moduleId;
	}

	@FunctionalInterface
	public interface TableScreenFactory<S extends CasinoTableScreen> {
		S create(CasinoTableMenu menu, Inventory inventory, Component title);
	}

	/** Binds your table's menu type to your screen class. */
	public <S extends CasinoTableScreen> void tableScreen(TableType<?> table, TableScreenFactory<S> factory) {
		MenuScreens.register(table.menuType(), factory::create);
	}
}
