package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.LoanSquads;
import dev.nezo.burmaldaholic.loan.logic.SquadRules;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Accountant: 28 HP, squad leader (speaks the dialogue), no melee, no vexes. "Audit": a line of 5
 * fangs towards the target, 6 damage each, every 100 ticks; a hit also gives Weakness I for 200 ticks
 * (applied by {@link LoanSquads#onDamage}).
 */
public class AccountantEntity extends SquadMob {
	public static final Unit UNIT = Unit.ACCOUNTANT;
	private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(AccountantEntity.class, EntityDataSerializers.BOOLEAN);

	public AccountantEntity(EntityType<? extends AccountantEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public Unit unit() {
		return UNIT;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(CASTING, false);
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(3, new KeepDistanceGoal(5.0, 10.0));
		this.goalSelector.addGoal(4, new AuditGoal());
	}

	public boolean isCasting() {
		return this.entityData.get(CASTING);
	}

	@Override
	public IllagerArmPose getArmPose() {
		return isCasting() ? IllagerArmPose.SPELLCASTING : IllagerArmPose.CROSSED;
	}

	/** Accountant: 1–2 emeralds + Ledger Page. */
	@Override
	protected List<ItemStack> loot(ServerLevel level) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.EMERALD, 1 + random.nextInt(2)));
		out.add(new ItemStack(LoanContent.LEDGER_PAGE));
		return out;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.EVOKER_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.EVOKER_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.EVOKER_HURT;
	}

	/** Cast (20 ticks raised arms) then a line of fangs to the target; 100-tick cooldown. */
	private final class AuditGoal extends Goal {
		private int cooldown;
		private int castTicks;

		AuditGoal() {
			setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (cooldown > 0) {
				cooldown--;
				return false;
			}
			LivingEntity t = getTarget();
			return t != null && t.isAlive() && distanceTo(t) <= SquadRules.AUDIT_RANGE && hasLineOfSight(t);
		}

		@Override
		public void start() {
			castTicks = 20;
			entityData.set(CASTING, true);
			playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.2F);
		}

		@Override
		public boolean canContinueToUse() {
			return castTicks > 0 && getTarget() != null;
		}

		@Override
		public void tick() {
			LivingEntity t = getTarget();
			if (t != null) {
				getLookControl().setLookAt(t, 10.0F, getMaxHeadXRot());
			}
			if (--castTicks == 0 && t != null) {
				audit(t);
			}
		}

		@Override
		public void stop() {
			entityData.set(CASTING, false);
			cooldown = SquadRules.AUDIT_COOLDOWN;
		}
	}

	private void audit(LivingEntity target) {
		double minY = Math.min(target.getY(), getY());
		double maxY = Math.max(target.getY(), getY()) + 1.0;
		float angle = (float) Mth.atan2(target.getZ() - getZ(), target.getX() - getX());
		double dist = Math.max(1.5, Math.sqrt(Mth.square(target.getX() - getX()) + Mth.square(target.getZ() - getZ())));
		for (int i = 0; i < SquadRules.AUDIT_FANGS; i++) {
			double reach = dist * (i + 1) / SquadRules.AUDIT_FANGS;
			fang(getX() + Mth.cos(angle) * reach, getZ() + Mth.sin(angle) * reach, minY, maxY, angle, i * 2);
		}
		if (random.nextFloat() < 0.35F) {
			LoanSquads.auditLine(this);
		}
	}

	private void fang(double x, double z, double minY, double maxY, float angle, int delay) {
		BlockPos pos = BlockPos.containing(x, maxY, z);
		double top = 0;
		boolean ok = false;
		do {
			BlockPos below = pos.below();
			if (level().getBlockState(below).isFaceSturdy(level(), below, Direction.UP)) {
				if (!level().isEmptyBlock(pos)) {
					BlockState state = level().getBlockState(pos);
					VoxelShape shape = state.getCollisionShape(level(), pos);
					if (!shape.isEmpty()) {
						top = shape.max(Direction.Axis.Y);
					}
				}
				ok = true;
				break;
			}
			pos = pos.below();
		} while (pos.getY() >= Mth.floor(minY) - 1);
		if (ok) {
			level().addFreshEntity(new EvokerFangs(level(), x, pos.getY() + top, z, angle, delay, this));
		}
	}
}
