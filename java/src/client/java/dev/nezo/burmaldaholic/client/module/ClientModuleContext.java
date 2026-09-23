package dev.nezo.burmaldaholic.client.module;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.hud.CasinoHud;
import dev.nezo.burmaldaholic.client.hud.HudSegment;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.core.module.Namespaces;
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

	/**
	 * Adds lines to the casino HUD panel. {@code name} must be owned by the module (e.g. "loan_hud");
	 * {@code order}: core uses 0 balance, 100 streak/VIP, 200 loan, 300 Golden Hour.
	 */
	public void hudSegment(String name, int order, HudSegment segment) {
		if (!Namespaces.get().owns(moduleId, name)) {
			throw new IllegalArgumentException("Module '" + moduleId + "' does not own the HUD segment name '" + name + "'");
		}
		CasinoHud.register(Burmaldaholic.id(name), order, segment);
	}
}
