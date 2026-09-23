package dev.nezo.burmaldaholic.multiplayer.mixin;

import dev.nezo.burmaldaholic.multiplayer.Ownership;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** §18.2: a casino table placed by the owner inside their claim links to the casino. */
@Mixin(BlockItem.class)
abstract class TablePlacementMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void burmaldaholic$linkOwnedTable(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level
			&& cir.getReturnValue().consumesAction()) {
			Ownership.onBlockPlaced(player, level, context.getClickedPos());
		}
	}
}
