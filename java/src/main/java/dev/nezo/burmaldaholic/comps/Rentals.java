package dev.nezo.burmaldaholic.comps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.util.Inventories;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Ride rentals (EC-SPEC §4.2): a tamed, saddled horse (Overworld) or a saddled strider plus a tagged warped fungus
 * on a stick (Nether), 10 minutes. The entity carries the persistent attachment {@code burmaldaholic:rental}
 * {owner, expiresAt}; it has no inventory access and no drops ({@code discard} on expiry, death or the owner's logout).
 */
public final class Rentals {
	public record Rental(UUID owner, long expiresAt) {
		static final Codec<Rental> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Rental::owner),
			Codec.LONG.fieldOf("expires_at").forGetter(Rental::expiresAt)
		).apply(i, Rental::new));
	}

	static AttachmentType<Rental> TYPE;
	private static final String STICK_TAG = "burmaldaholic_rental";
	private static final String STICK_UNTIL = "burmaldaholic_rental_until";
	/** Loaded rental entities: entity UUID → dimension. */
	private static final Map<UUID, ResourceKey<Level>> LIVE = new HashMap<>();

	private Rentals() {}

	static void register() {
		TYPE = AttachmentRegistry.create(Burmaldaholic.id("rental"), b -> b.persistent(Rental.CODEC));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Rental r = entity.getAttached(TYPE);
			if (r != null) {
				if (expired(level.getServer(), r) || level.getServer().getPlayerList().getPlayer(r.owner()) == null) {
					entity.discard();
				} else {
					LIVE.put(entity.getUUID(), level.dimension());
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LIVE.remove(entity.getUUID()));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity.getAttached(TYPE) != null) {
				end(entity, false);
				return false; // no drops
			}
			return true;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			Rental r = entity.getAttached(TYPE);
			if (r == null) {
				return InteractionResult.PASS;
			}
			// owner may ride; nobody opens the inventory or equips it
			if (!player.getUUID().equals(r.owner()) || player.isSecondaryUseActive() || !player.getItemInHand(hand).isEmpty()
				&& !player.getItemInHand(hand).is(Items.WARPED_FUNGUS_ON_A_STICK)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	static boolean expired(MinecraftServer server, Rental r) {
		return server.overworld().getGameTime() >= r.expiresAt();
	}

	/** Spawns the rental next to the player. @return false if it could not be spawned (nothing is charged). */
	static boolean spawn(ServerPlayer player, boolean strider, long ticks) {
		ServerLevel level = player.level();
		if (strider != (level.dimension() == Level.NETHER)) {
			return false;
		}
		EntityType<? extends Mob> type = strider ? net.minecraft.world.entity.EntityTypes.STRIDER : net.minecraft.world.entity.EntityTypes.HORSE;
		Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) {
			return false;
		}
		mob.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
		mob.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		mob.setDropChance(EquipmentSlot.SADDLE, 0);
		mob.setPersistenceRequired();
		if (mob instanceof AbstractHorse horse) {
			horse.tameWithName(player);
		}
		long until = level.getServer().overworld().getGameTime() + ticks;
		mob.setAttached(TYPE, new Rental(player.getUUID(), until));
		if (!level.addFreshEntity(mob)) {
			return false;
		}
		LIVE.put(mob.getUUID(), level.dimension());
		if (strider) {
			ItemStack stick = new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK);
			CompoundTag tag = new CompoundTag();
			tag.putBoolean(STICK_TAG, true);
			tag.putLong(STICK_UNTIL, until); // swept wherever a player carries it after this (T-EC2 L5)
			stick.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			Inventories.giveOrDrop(player, stick);
		}
		return true;
	}

	/** Every second: rentals past their time (or whose owner left) are removed. */
	static void tick(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, ResourceKey<Level>> e : List.copyOf(LIVE.entrySet())) {
			ServerLevel level = server.getLevel(e.getValue());
			Entity entity = level == null ? null : level.getEntity(e.getKey());
			if (entity == null) {
				LIVE.remove(e.getKey());
				continue;
			}
			Rental r = entity.getAttached(TYPE);
			if (r == null) {
				LIVE.remove(e.getKey());
			} else if (expired(server, r) || server.getPlayerList().getPlayer(r.owner()) == null) {
				end(entity, true);
			}
		}
	}

	/** Owner logged out: their rentals go. */
	static void onLogout(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (Map.Entry<UUID, ResourceKey<Level>> e : List.copyOf(LIVE.entrySet())) {
			ServerLevel level = server.getLevel(e.getValue());
			Entity entity = level == null ? null : level.getEntity(e.getKey());
			Rental r = entity == null ? null : entity.getAttached(TYPE);
			if (r != null && r.owner().equals(player.getUUID())) {
				end(entity, false);
			}
		}
		removeSticks(player);
	}

	private static void end(Entity entity, boolean tell) {
		Rental r = entity.getAttached(TYPE);
		LIVE.remove(entity.getUUID());
		entity.ejectPassengers();
		entity.discard();
		if (r != null && entity.level() instanceof ServerLevel level) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(r.owner());
			if (owner != null) {
				removeSticks(owner);
				if (tell) {
					owner.sendSystemMessage(Component.translatable("msg.burmaldaholic.comps.rental_expired").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	private static void removeSticks(ServerPlayer player) {
		removeSticks(player.getInventory(), Long.MAX_VALUE);
		removeSticks(player.getEnderChestInventory(), Long.MAX_VALUE);
	}

	/** Removes rental sticks from {@code c}: all of them ({@code now = MAX}), or those whose rental ended by {@code now}. */
	static int removeSticks(net.minecraft.world.Container c, long now) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			CustomData d = s.isEmpty() || !s.is(Items.WARPED_FUNGUS_ON_A_STICK) ? null : s.get(DataComponents.CUSTOM_DATA);
			if (d == null) {
				continue;
			}
			CompoundTag tag = d.copyTag();
			if (tag.getBooleanOr(STICK_TAG, false) && (now == Long.MAX_VALUE || tag.getLongOr(STICK_UNTIL, 0) <= now)) {
				c.setItem(i, ItemStack.EMPTY);
				n++;
			}
		}
		return n;
	}

	/**
	 * Every few seconds: rental sticks past their rental, wherever a player has them (inventory, ender chest, the open
	 * container — e.g. one taken back out of a chest), are removed (T-EC2 L5).
	 */
	static void sweepSticks(ServerPlayer player) {
		long now = player.level().getServer().overworld().getGameTime();
		removeSticks(player.getInventory(), now);
		removeSticks(player.getEnderChestInventory(), now);
		for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
			ItemStack s = slot.getItem();
			CustomData d = s.isEmpty() || !s.is(Items.WARPED_FUNGUS_ON_A_STICK) ? null : s.get(DataComponents.CUSTOM_DATA);
			if (d != null && d.copyTag().getBooleanOr(STICK_TAG, false) && d.copyTag().getLongOr(STICK_UNTIL, 0) <= now && slot.mayPickup(player)) {
				slot.set(ItemStack.EMPTY);
			}
		}
	}

	/** Tests: live rental count. */
	public static int liveCount() {
		return LIVE.size();
	}
}
