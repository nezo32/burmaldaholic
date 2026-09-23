package dev.nezo.burmaldaholic.vip.mixin;

import dev.nezo.burmaldaholic.vip.Contracts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The {@code smelt} contract: items taken from a furnace / blast furnace / smoker output (§3.4.4). */
@Mixin(FurnaceResultSlot.class)
abstract class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void burmaldaholic$vipSmelt(ItemStack stack, CallbackInfo ci) {
		if (!player.level().isClientSide()) {
			Contracts.onSmelted(player, removeCount);
		}
	}
}
