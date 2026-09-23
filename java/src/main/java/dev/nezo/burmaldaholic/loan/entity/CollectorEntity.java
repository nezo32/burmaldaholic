package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/** Collector ({@code debt_collector}): 24 HP, melee 5 with an iron-axe-looking "bat", vindicator-like. */
public class CollectorEntity extends SquadMob {
	public static final Unit UNIT = Unit.DEBT_COLLECTOR;

	public CollectorEntity(EntityType<? extends CollectorEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public Unit unit() {
		return UNIT;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
	}

	/** The weapon is only a look: no attribute modifiers, so the hit stays at the §5.5 value. */
	static ItemStack prop(ItemStack stack) {
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return stack;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, prop(new ItemStack(Items.IRON_AXE)));
	}

	@Override
	public IllagerArmPose getArmPose() {
		return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.CROSSED;
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
