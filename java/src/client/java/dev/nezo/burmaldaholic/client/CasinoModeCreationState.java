package dev.nezo.burmaldaholic.client;

/**
 * Duck interface on {@code WorldCreationUiState} ({@code core.mixin.client.WorldCreationUiStateMixin}): the
 * Create World "Casino Mode" button value of this screen. Default OFF; not copied by Re-Create. The
 * screen hands it to the new world when it is created ({@code CreateWorldScreenMixin}).
 */
public interface CasinoModeCreationState {
	boolean burmaldaholic$casinoMode();

	void burmaldaholic$setCasinoMode(boolean enabled);
}
