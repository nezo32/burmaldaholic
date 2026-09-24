package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.Locale;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * "Client effects" (global.md §2.8): reduce motion, screen flashes, animation speed, win celebrations, effects
 * volume. Opened from Mod Menu (config screen button) and from the Casino Menu → Settings rows (lane J-L2 adds
 * the button there with {@code new FxSettingsScreen(parent)}). Every change is saved at once
 * ({@link FxSettings#update}); the information itself (words, amounts, timers) is never behind a toggle.
 */
public final class FxSettingsScreen extends Screen {
	private static final int W = 210;
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public FxSettingsScreen(Screen parent) {
		super(Component.translatable("config.burmaldaholic.section.client_fx"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addToHeader(new StringWidget(title, font));
		LinearLayout col = layout.addToContents(LinearLayout.vertical().spacing(4));
		FxSettings.Data d = FxSettings.get();
		col.addChild(CycleButton.onOffBuilder(d.reduceMotion).create(0, 0, W, 20, Component.translatable("gui.burmaldaholic.menu.settings.reduce_motion"),
			(b, v) -> FxSettings.update(s -> s.reduceMotion = v)));
		col.addChild(CycleButton.onOffBuilder(d.flashes).create(0, 0, W, 20, Component.translatable("gui.burmaldaholic.menu.settings.flashes"),
			(b, v) -> FxSettings.update(s -> s.flashes = v)));
		col.addChild(CycleButton.<FxSettings.Speed>builder(FxSettingsScreen::speedLabel, FxSettings.speed()).withValues(FxSettings.Speed.values())
			.create(0, 0, W, 20, Component.translatable("gui.burmaldaholic.menu.settings.anim_speed"), (b, v) -> FxSettings.update(s -> s.speed = v)));
		col.addChild(CycleButton.<FxSettings.Celebrations>builder(FxSettingsScreen::celebrationsLabel, FxSettings.celebrations())
			.withValues(FxSettings.Celebrations.values())
			.create(0, 0, W, 20, Component.translatable("gui.burmaldaholic.menu.settings.celebrations"),
				(b, v) -> FxSettings.update(s -> s.celebrations = v)));
		col.addChild(new VolumeSlider(W, d.volume));
		layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(W).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private static Component speedLabel(FxSettings.Speed s) {
		return Component.translatable("gui.burmaldaholic.menu.settings.anim_speed." + s.name().toLowerCase(Locale.ROOT));
	}

	private static Component celebrationsLabel(FxSettings.Celebrations c) {
		return Component.translatable("gui.burmaldaholic.menu.settings.celebrations." + c.name().toLowerCase(Locale.ROOT));
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** 0–100 % in steps of 5. */
	private static final class VolumeSlider extends AbstractSliderButton {
		VolumeSlider(int width, int volume) {
			super(0, 0, width, 20, Component.empty(), volume / 100.0);
			updateMessage();
		}

		private int percent() {
			return (int) Math.round(value * 20) * 5;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("gui.burmaldaholic.menu.settings.fx_volume", Texts.number(percent())));
		}

		@Override
		protected void applyValue() {
			int p = percent();
			FxSettings.update(s -> s.volume = p);
		}
	}
}
