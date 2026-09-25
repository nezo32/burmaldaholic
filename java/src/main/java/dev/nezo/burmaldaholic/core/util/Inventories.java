package dev.nezo.burmaldaholic.core.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Inventory helpers that link on every supported Minecraft version (Player#drop changed in 26.3). */
public final class Inventories {
	private Inventories() {}

	/** Adds the stack to the inventory; whatever does not fit drops at the player's feet. Returns true if something dropped. */
	public static boolean giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		player.getInventory().add(stack);
		if (stack.isEmpty()) {
			return false;
		}
		dropAtFeet(player, stack);
		return true;
	}

	public static void dropAtFeet(ServerPlayer player, ItemStack stack) {
		ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy());
		entity.setNoPickUpDelay();
		player.level().addFreshEntity(entity);
		stack.setCount(0);
	}
}
