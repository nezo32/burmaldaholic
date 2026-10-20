package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.NpcLook;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.npc.HasLook;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Spawns casino NPCs. The staff types (croupier, piglin dealer, shulker croupier) are worldgen's own
 * ({@code npc/NpcContent}), the Loan Shark belongs to loan; all are looked up by registry id at spawn time,
 * and a role whose types are not registered is skipped (and retried by the respawn check later).
 */
public final class CasinoNpcs {
	/** Entity tag of every NPC spawned by worldgen. */
	public static final String NPC_TAG = "burmaldaholic.worldgen_npc";

	private CasinoNpcs() {}

	public static Optional<EntityType<?>> type(NpcRole role) {
		return type(role, "minecraft:overworld");
	}

	/** The role's entity type in {@code dimension} (house dealers and merchants: human / piglin / ender). */
	public static Optional<EntityType<?>> type(NpcRole role, String dimension) {
		for (String id : role.entityIds(dimension)) {
			Identifier key = Identifier.parse(id);
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
				return BuiltInRegistries.ENTITY_TYPE.getOptional(key);
			}
		}
		return Optional.empty();
	}

	/** Spawns the NPC standing in block {@code stand}; {@code null} if its type is absent. */
	public static Entity spawn(ServerLevelAccessor level, NpcRole role, BlockPos stand, float yaw) {
		return spawn(level, role, stand, yaw, null);
	}

	/**
	 * Spawns the NPC standing in block {@code stand}; with a {@code casinoId} its look is the casino's pick for that
	 * cell ({@link NpcLook}), so a respawned NPC looks the same. {@code null} if its type is absent.
	 */
	public static Entity spawn(ServerLevelAccessor level, NpcRole role, BlockPos stand, float yaw, @Nullable String casinoId) {
		Optional<EntityType<?>> type = type(role, level.getLevel().dimension().identifier().toString());
		if (type.isEmpty()) {
			return null;
		}
		Entity entity = type.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			return null;
		}
		entity.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, 0f);
		entity.setYHeadRot(yaw);
		entity.addTag(NPC_TAG);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), EntitySpawnReason.STRUCTURE, null);
		}
		if (entity instanceof HasLook look && casinoId != null) {
			look.setLook(NpcLook.pick(look.lookPool(), casinoId, stand.getX(), stand.getY(), stand.getZ()));
		}
		if (entity instanceof HouseDealer dealer) {
			dealer.setHome(stand, yaw);
		}
		level.addFreshEntityWithPassengers(entity);
		return entity;
	}

	/**
	 * Respawn of a missing NPC slot. Duplicate guard for house dealers: a live dealer already standing on the slot's
	 * cell (±1 block; e.g. one the migration spawned, or its entity reloaded under a new chunk) is adopted instead.
	 */
	public static @Nullable Entity respawn(ServerLevel level, CasinoRecord.NpcSlot slot, String casinoId) {
		BlockPos home = new BlockPos(slot.home().x(), slot.home().y(), slot.home().z());
		if (slot.role() == NpcRole.HOUSE_DEALER) {
			List<HouseDealer> near = level.getEntitiesOfClass(HouseDealer.class, new AABB(home).inflate(1), Entity::isAlive);
			if (!near.isEmpty()) {
				return near.getFirst();
			}
		}
		return spawn(level, slot.role(), home, slot.yaw(), casinoId);
	}
}
