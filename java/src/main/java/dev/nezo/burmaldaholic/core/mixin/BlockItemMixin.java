package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.earnings.Earnings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player-placed ancient debris goes into the placed-debris ledger (§3.4.1 anti-exploit). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void burmaldaholic$trackPlacedDebris(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel level && cir.getReturnValue().consumesAction()) {
			Earnings.onBlockPlaced(level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
		}
	}
}
