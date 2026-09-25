package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.LoanSquads;
import dev.nezo.burmaldaholic.loan.logic.SquadRules;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.State;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base class of the Debt Collector squad units (GAME_DESIGN.md §5.5): custom illagers that are never
 * raid members, never pick anything up, never grief, are immune to their own squad's fire, and follow
 * the squad state machine owned by {@link LoanSquads}:
 * APPROACH (walk to the debtor) → NEGOTIATE (stand still, look at him) → HOSTILE (attack only the
 * debtor and whoever hurts them) → LEAVING (walk away, then despawn).
 *
 * <p>Spawned from a spawn egg (no squad) they are "free": they wander and only retaliate when hit.
 * A unit whose squad no longer exists (server restart) removes itself.
 */
public abstract class SquadMob extends AbstractIllager {
	/** Scoreboard tag on every squad member (for other modules, e.g. Hardcore Last Chance rules, §5.7). */
	public static final String TAG = "burmaldaholic.debt_squad";
	private @Nullable UUID squadId;
	private @Nullable UUID debtorId;

	protected SquadMob(EntityType<? extends SquadMob> type, Level level) {
		super(type, level);
		this.xpReward = 5;
		this.getNavigation().setCanOpenDoors(true);
		this.setCanPickUpLoot(false);
	}

	public abstract Unit unit();

	public static Unit statsOf(Unit unit) {
		return unit;
	}

	public static AttributeSupplier.Builder createAttributes(Unit unit) {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, unit.health)
			.add(Attributes.MOVEMENT_SPEED, unit.speed)
			.add(Attributes.ATTACK_DAMAGE, Math.max(1, unit.melee))
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	// ---- squad membership -------------------------------------------------------------------------

	public void joinSquad(UUID squad, UUID debtor) {
		this.squadId = squad;
		this.debtorId = debtor;
		this.addTag(TAG);
		this.setPersistenceRequired();
	}

	public @Nullable UUID squadId() {
		return squadId;
	}

	public @Nullable UUID debtorId() {
		return debtorId;
	}

	/** Squad state (server side); null = free unit (spawn egg). */
	public @Nullable State squadState() {
		return squadId == null ? null : LoanSquads.stateOf(squadId);
	}

	protected boolean isHostileState() {
		State s = squadState();
		return squadId == null || s == State.HOSTILE;
	}

	/** The debtor (online, same level) or null. */
	public @Nullable ServerPlayer debtor() {
		if (debtorId == null || !(level() instanceof ServerLevel server)) {
			return null;
		}
		ServerPlayer p = server.getServer().getPlayerList().getPlayer(debtorId);
		return p != null && p.level() == level() ? p : null;
	}

	// ---- AI -----------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		// Deliberately NOT calling super: no raid goals (banner pickup, raid pathing, celebration, patrols).
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new OpenDoorGoal(this, false));
		this.goalSelector.addGoal(2, new LeaveGoal());
		this.goalSelector.addGoal(2, new HoldGoal());
		this.goalSelector.addGoal(3, new ApproachGoal());
		registerCombatGoals();
		this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return squadId == null && super.canUse();
			}
		});
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0F, 1.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, SquadMob.class) {
			@Override
			public boolean canUse() {
				return isHostileState() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new DebtorTargetGoal());
	}

	/** Unit attack goals at priority 4 (they only run with a target, which only exists while HOSTILE). */
	protected abstract void registerCombatGoals();

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (!dev.nezo.burmaldaholic.core.earnings.Earnings.isNoReward(this)) {
			dev.nezo.burmaldaholic.core.earnings.Earnings.markNoReward(this); // collectors never pay kill rewards (§3.4.2)
		}
		if (squadId != null) {
			State s = LoanSquads.stateOf(squadId);
			if (s == null || s == State.DONE) {
				discard(); // orphan (restart) or finished squad
				return;
			}
			if (s != State.HOSTILE && getTarget() != null) {
				setTarget(null);
				setAggressive(false);
			}
		}
		super.customServerAiStep(level);
	}

	@Override
	public boolean canJoinRaid() {
		return false;
	}

	@Override
	public void setCanJoinRaid(boolean canJoinRaid) {
		super.setCanJoinRaid(false);
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return squadId == null && super.removeWhenFarAway(distSqr);
	}

	@Override
	public boolean requiresCustomPersistence() {
		return squadId != null || super.requiresCustomPersistence();
	}

	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		if (other instanceof SquadMob mob && Objects.equals(mob.squadId, squadId)) {
			return true;
		}
		return super.considersEntityAsAlly(other);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		if (attacker instanceof SquadMob other && Objects.equals(other.squadId, squadId)) {
			return false; // no friendly fire inside a squad
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && squadId != null && attacker != null) {
			LoanSquads.onMemberHurt(this, attacker);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide() && squadId != null) {
			LoanSquads.onMemberDeath(this);
		}
	}

	/** Squad melee kills read "was collected by …" ({@code death.attack.burmaldaholic.debt_collection}). */
	@Override
	public DamageSource createDamageSource() {
		return new DamageSource(level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LoanContent.DEBT_COLLECTION), this);
	}

	/** Never trample farmland, whatever {@code mobGriefing} says. */
	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		if (onGround && onState.is(Blocks.FARMLAND)) {
			resetFallDistance();
		}
		super.checkFallDamage(ya, onGround, onState, pos);
	}

	/** No equipment drops (no crossbow/axe farming); unit loot only. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		for (ItemStack stack : loot(level)) {
			spawnAtLocation(level, stack);
		}
	}

	/** §5.5 drops (Collector/Repo Man: 0–1 emerald, 10 % Overdue Notice). Units override. */
	protected java.util.List<ItemStack> loot(ServerLevel level) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		int emeralds = random.nextInt(2);
		if (emeralds > 0) {
			out.add(new ItemStack(Items.EMERALD, emeralds));
		}
		if (random.nextFloat() < 0.10F) {
			out.add(new ItemStack(LoanContent.OVERDUE_NOTICE));
		}
		return out;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		populateDefaultEquipmentSlots(random, difficulty);
		setCanJoinRaid(false);
		dev.nezo.burmaldaholic.core.earnings.Earnings.markNoReward(this);
		return data;
	}

	@Override
	public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
	}

	@Override
	public SoundEvent getCelebrateSound() {
		return SoundEvents.VINDICATOR_CELEBRATE;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("burmaldaholic_squad", UUIDUtil.CODEC, squadId);
		output.storeNullable("burmaldaholic_debtor", UUIDUtil.CODEC, debtorId);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		squadId = input.read("burmaldaholic_squad", UUIDUtil.CODEC).orElse(null);
		debtorId = input.read("burmaldaholic_debtor", UUIDUtil.CODEC).orElse(null);
		setCanPickUpLoot(false);
	}

	// ---- goals --------------------------------------------------------------------------------------

	/** APPROACH: walk to the debtor (non-hostile). */
	private final class ApproachGoal extends Goal {
		private int repath;

		ApproachGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return squadState() == State.APPROACH && debtor() != null;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void tick() {
			ServerPlayer d = debtor();
			if (d == null) {
				return;
			}
			getLookControl().setLookAt(d, 30.0F, 30.0F);
			if (--repath <= 0) {
				repath = 20;
				if (distanceToSqr(d) > 9) {
					getNavigation().moveTo(d, 1.0);
				} else {
					getNavigation().stop();
				}
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** NEGOTIATE: stand still and stare at the debtor. */
	private final class HoldGoal extends Goal {
		HoldGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return squadState() == State.NEGOTIATE;
		}

		@Override
		public void start() {
			getNavigation().stop();
		}

		@Override
		public void tick() {
			ServerPlayer d = debtor();
			if (d != null) {
				getLookControl().setLookAt(d, 30.0F, 30.0F);
			}
		}
	}

	/** LEAVING: walk away from the debtor (despawn is done by the squad after 100 ticks). */
	private final class LeaveGoal extends Goal {
		private int repath;

		LeaveGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return squadState() == State.LEAVING;
		}

		@Override
		public void start() {
			repath = 0;
			setTarget(null);
			setAggressive(false);
		}

		@Override
		public void tick() {
			if (--repath > 0) {
				return;
			}
			repath = 40;
			ServerPlayer d = debtor();
			Vec3 from = d != null ? d.position() : position();
			Vec3 away = DefaultRandomPos.getPosAway(SquadMob.this, 16, 7, from);
			if (away != null) {
				getNavigation().moveTo(away.x, away.y, away.z, 1.0);
			}
		}
	}

	/** HOSTILE: the debtor is the target (unless someone who hurt the squad already is). */
	private final class DebtorTargetGoal extends Goal {
		DebtorTargetGoal() {
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (squadState() != State.HOSTILE) {
				return false;
			}
			ServerPlayer d = debtor();
			return d != null && valid(d) && getTarget() == null;
		}

		@Override
		public void start() {
			setTarget(debtor());
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity t = getTarget();
			return squadState() == State.HOSTILE && t != null && t.isAlive();
		}

		private boolean valid(ServerPlayer p) {
			return p.isAlive() && !p.isCreative() && !p.isSpectator();
		}
	}

	/**
	 * Keeps the target between {@code min} and {@code max} blocks away (Repo Man 8–12, Accountant);
	 * {@code max ≤ 0} = never closes in (another goal does).
	 */
	protected final class KeepDistanceGoal extends Goal {
		private final double min;
		private final double max;
		private int cooldown;

		public KeepDistanceGoal(double min) {
			this(min, 0);
		}

		public KeepDistanceGoal(double min, double max) {
			this.min = min;
			this.max = max;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = getTarget();
			if (t == null || --cooldown > 0) {
				return false;
			}
			double d = distanceToSqr(t);
			return d < min * min || (max > 0 && d > max * max);
		}

		@Override
		public void start() {
			cooldown = 20;
			LivingEntity t = getTarget();
			if (t == null) {
				return;
			}
			if (distanceToSqr(t) < min * min) {
				Vec3 away = DefaultRandomPos.getPosAway(SquadMob.this, (int) min + 2, 4, t.position());
				if (away != null) {
					getNavigation().moveTo(away.x, away.y, away.z, 1.1);
				}
			} else {
				getNavigation().moveTo(t, 1.0);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return !getNavigation().isDone();
		}
	}

	/** Pathfinder helper for subclasses. */
	protected PathfinderMob self() {
		return this;
	}
}
