package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

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
		for (String id : role.entityIds()) {
			Identifier key = Identifier.parse(id);
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
				return BuiltInRegistries.ENTITY_TYPE.getOptional(key);
			}
		}
		return Optional.empty();
	}

	/** Spawns the NPC standing in block {@code stand}; {@code null} if its type is absent. */
	public static Entity spawn(ServerLevelAccessor level, NpcRole role, BlockPos stand, float yaw) {
		Optional<EntityType<?>> type = type(role);
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
		level.addFreshEntityWithPassengers(entity);
		return entity;
	}
}
