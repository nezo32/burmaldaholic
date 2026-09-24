package dev.nezo.burmaldaholic.core.mixin.client;

import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Holds the Create World "Casino Mode" choice next to the other creation settings (client thread). */
@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin implements CasinoModeCreationState {
	@Unique
	private boolean burmaldaholic$casinoMode = CasinoMode.DEFAULT;

	@Override
	public boolean burmaldaholic$casinoMode() {
		return burmaldaholic$casinoMode;
	}

	@Override
	public void burmaldaholic$setCasinoMode(boolean enabled) {
		burmaldaholic$casinoMode = enabled;
	}
}
