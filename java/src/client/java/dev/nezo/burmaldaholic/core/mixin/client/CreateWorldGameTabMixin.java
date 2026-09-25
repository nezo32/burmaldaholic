package dev.nezo.burmaldaholic.core.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds "Casino Mode: ON/OFF" to the Create World "Game" tab directly below "Difficulty" (GAME_DESIGN.md
 * §2.1). The value lives in the screen's {@code WorldCreationUiState} ({@link CasinoModeCreationState},
 * default OFF) and is saved with the new world in {@code data/burmaldaholic/mode.dat}
 * ({@code CreateWorldScreenMixin} -> {@code CasinoMode}). It is not a game rule.
 *
 * <p>{@code GameTab(CreateWorldScreen)} (identical bytecode in 26.2 and 26.3) adds, in order: the name
 * box, Game Mode, Difficulty (the three {@code RowHelper.addChild(element, settings)} calls, ordinals
 * 0..2), then Allow Commands via {@code addChild(element)}. We insert right after ordinal 2. If a future
 * version reshuffles the constructor, the TAIL fallback still adds the button (at the end of the grid)
 * and the client GameTest's "directly below Difficulty" check flags it.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
abstract class CreateWorldGameTabMixin {
	@Unique
	private static final String KEY = "gui.burmaldaholic.core.create_world.casino_mode";

	@Unique
	private boolean burmaldaholic$added;

	@Inject(method = "<init>", require = 0, at = @At(value = "INVOKE", ordinal = 2, shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private void burmaldaholic$addBelowDifficulty(CreateWorldScreen screen, CallbackInfo ci,
			@Local GridLayout.RowHelper helper, @Local LayoutSettings buttonSettings) {
		burmaldaholic$add(screen, helper, buttonSettings);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void burmaldaholic$fallback(CreateWorldScreen screen, CallbackInfo ci, @Local GridLayout.RowHelper helper) {
		burmaldaholic$add(screen, helper, helper.newCellSettings());
	}

	@Unique
	private void burmaldaholic$add(CreateWorldScreen screen, GridLayout.RowHelper helper, LayoutSettings settings) {
		if (burmaldaholic$added) {
			return;
		}
		burmaldaholic$added = true;
		CasinoModeCreationState state = (CasinoModeCreationState) screen.getUiState();
		CycleButton<Boolean> button = CycleButton.booleanBuilder(
				Component.translatable(KEY, Component.translatable("gui.burmaldaholic.common.on")),
				Component.translatable(KEY, Component.translatable("gui.burmaldaholic.common.off")),
				state.burmaldaholic$casinoMode())
			.displayOnlyValue()
			.withTooltip(value -> Tooltip.create(Component.translatable(KEY + ".tooltip")))
			// Same size as Game Mode / Difficulty / Allow Commands (210x20).
			.create(0, 0, 210, 20, Component.translatable("gui.burmaldaholic.core.setup.casino_mode"),
				(b, value) -> state.burmaldaholic$setCasinoMode(value));
		helper.addChild(button, settings);
	}
}
