package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Repo Man: 24 HP, crossbow (vanilla bolts never break blocks), keeps 8–12 blocks away from the debtor. */
public class RepoManEntity extends SquadMob implements CrossbowAttackMob {
	public static final Unit UNIT = Unit.REPO_MAN;
	private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(RepoManEntity.class, EntityDataSerializers.BOOLEAN);

	public RepoManEntity(EntityType<? extends RepoManEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public Unit unit() {
		return UNIT;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(CHARGING, false);
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(3, new KeepDistanceGoal(8.0));
		this.goalSelector.addGoal(4, new RangedCrossbowAttackGoal<>(this, 1.0, 12.0F));
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
	}

	@Override
	public boolean canUseNonMeleeWeapon(ItemStack item) {
		return item.getItem() == Items.CROSSBOW;
	}

	public boolean isChargingCrossbow() {
		return this.entityData.get(CHARGING);
	}

	@Override
	public void setChargingCrossbow(boolean charging) {
		this.entityData.set(CHARGING, charging);
	}

	@Override
	public void onCrossbowAttackPerformed() {
		this.noActionTime = 0;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		this.performCrossbowAttack(this, 1.6F);
	}

	@Override
	public IllagerArmPose getArmPose() {
		if (isChargingCrossbow()) {
			return IllagerArmPose.CROSSBOW_CHARGE;
		}
		if (isHolding(Items.CROSSBOW)) {
			return IllagerArmPose.CROSSBOW_HOLD;
		}
		return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PILLAGER_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PILLAGER_HURT;
	}
}
