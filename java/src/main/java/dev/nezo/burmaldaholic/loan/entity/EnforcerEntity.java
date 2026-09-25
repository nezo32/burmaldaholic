package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.logic.SquadRules;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Enforcer: 80 HP, melee 12, knockback 1.5, knockback resistance 0.75, "big guy" (scale 1.25), bare fists. */
public class EnforcerEntity extends SquadMob {
	public static final Unit UNIT = Unit.ENFORCER;

	public EnforcerEntity(EntityType<? extends EnforcerEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createEnforcerAttributes() {
		return createAttributes(UNIT)
			.add(Attributes.ATTACK_KNOCKBACK, SquadRules.ENFORCER_KNOCKBACK)
			.add(Attributes.KNOCKBACK_RESISTANCE, SquadRules.ENFORCER_KNOCKBACK_RESISTANCE)
			.add(Attributes.SCALE, 1.25);
	}

	@Override
	public Unit unit() {
		return UNIT;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
	}

	@Override
	public IllagerArmPose getArmPose() {
		return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.CROSSED;
	}

	/** Enforcer: 2–4 emeralds, 25 % iron block. */
	@Override
	protected List<ItemStack> loot(ServerLevel level) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.EMERALD, 2 + random.nextInt(3)));
		if (random.nextFloat() < 0.25F) {
			out.add(new ItemStack(Items.IRON_BLOCK));
		}
		return out;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VINDICATOR_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VINDICATOR_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VINDICATOR_HURT;
	}
}
