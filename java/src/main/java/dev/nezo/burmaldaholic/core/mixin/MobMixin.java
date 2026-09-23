package dev.nezo.burmaldaholic.core.mixin;

import dev.nezo.burmaldaholic.core.earnings.Earnings;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mobs from spawners / trial spawners never pay kill rewards (§3.4.2), unless economy.mob.spawnerRewards. */
@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void burmaldaholic$markSpawnerMob(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.SPAWNER || reason == EntitySpawnReason.TRIAL_SPAWNER) {
			Earnings.markNoReward((Mob) (Object) this);
		}
	}
}
