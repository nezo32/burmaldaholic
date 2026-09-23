package dev.nezo.burmaldaholic.client.config;

import dev.nezo.burmaldaholic.core.config.ConfigManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Minimal translated config screen (opened from Mod Menu). J-core: replace with an auto-generated
 * field editor using keys {@code config.burmaldaholic.<module>.<field>}.
 */
public final class CasinoConfigScreen extends Screen {
	private final Screen parent;

	public CasinoConfigScreen(Screen parent) {
		super(Component.translatable("config.burmaldaholic.core.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("config.burmaldaholic.core.reload"), b -> ConfigManager.get().load())
			.bounds(width / 2 - 100, height / 2 - 24, 200, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(width / 2 - 100, height / 2 + 4, 200, 20).build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
