package dev.nezo.burmaldaholic.loan.mixin;

import dev.nezo.burmaldaholic.core.wager.BetLimits;
import dev.nezo.burmaldaholic.loan.LoanService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Asset Freeze (GAME_DESIGN.md §5.6): a player in default without collectors (Peaceful, or collectors
 * disabled) cannot wager at all until the debt is repaid. Core has no "may wager" hook yet, so the loan
 * module vetoes chip bets at {@link BetLimits#validate} (used by {@code Stakes.chips} and every table).
 */
@Mixin(value = BetLimits.class, remap = false)
abstract class BetLimitsMixin {
	@Inject(method = "validate", at = @At("HEAD"), cancellable = true)
	private static void burmaldaholic$assetFreeze(ServerPlayer player, long amount, long min, long tableMax, CallbackInfoReturnable<Component> cir) {
		if (LoanService.frozen(player.level().getServer(), player.getUUID())) {
			cir.setReturnValue(Component.translatable("gui.burmaldaholic.error.in_default"));
		}
	}
}
