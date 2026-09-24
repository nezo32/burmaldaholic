package dev.nezo.burmaldaholic.core.mixin.client;

import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import dev.nezo.burmaldaholic.core.mode.PendingCasinoMode;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * When this screen creates its world, hands the "Casino Mode" choice to that world's
 * {@code LevelStorageAccess} — the object the integrated server is built with. The server stores it in
 * {@code data/burmaldaholic/mode.dat} on SERVER_STARTING and saves it right away ({@code CasinoMode}).
 */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin {
	@ModifyArg(method = "createNewWorld", index = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)V"))
	private LevelStorageSource.LevelStorageAccess burmaldaholic$handOffCasinoMode(LevelStorageSource.LevelStorageAccess access) {
		boolean enabled = ((CasinoModeCreationState) ((CreateWorldScreen) (Object) this).getUiState()).burmaldaholic$casinoMode();
		((PendingCasinoMode) access).burmaldaholic$setPendingCasinoMode(enabled);
		return access;
	}
}
