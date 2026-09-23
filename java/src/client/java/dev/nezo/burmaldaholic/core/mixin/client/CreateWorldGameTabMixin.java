package dev.nezo.burmaldaholic.core.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds "Casino Mode: ON/OFF" to the Create World "Game" tab (GAME_DESIGN.md §2.1). It edits the same
 * {@code burmaldaholic:casino_mode} game rule as the Game Rules screen, so both stay in sync.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
abstract class CreateWorldGameTabMixin {
	@Unique
	private GridLayout.RowHelper burmaldaholic$rows;

	@ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"))
	private GridLayout.RowHelper burmaldaholic$captureRows(GridLayout.RowHelper rows) {
		this.burmaldaholic$rows = rows;
		return rows;
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void burmaldaholic$addCasinoToggle(CreateWorldScreen screen, CallbackInfo ci) {
		if (burmaldaholic$rows == null) {
			return;
		}
		WorldCreationUiState state = screen.getUiState();
		CycleButton<Boolean> button = CycleButton.onOffBuilder(state.getGameRules().get(CasinoMode.rule()))
			.withTooltip(value -> Tooltip.create(Component.translatable("gamerule.burmaldaholic.casino_mode.description")))
			.create(0, 0, 210, 20, Component.translatable("gamerule.burmaldaholic.casino_mode"), (b, value) -> {
				GameRules rules = state.getGameRules();
				rules.set(CasinoMode.rule(), value, null);
				state.setGameRules(rules);
			});
		burmaldaholic$rows.addChild(button, LayoutSettings.defaults().alignHorizontallyCenter());
		state.addListener(s -> button.setValue(s.getGameRules().get(CasinoMode.rule())));
		burmaldaholic$rows = null;
	}
}
