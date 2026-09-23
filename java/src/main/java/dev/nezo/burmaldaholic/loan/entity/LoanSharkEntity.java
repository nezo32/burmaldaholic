package dev.nezo.burmaldaholic.loan.entity;

import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.LoanShark;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Loan Shark / Piglin Moneylender (GAME_DESIGN.md §5.1): villager-sized humanoid, 40 HP, neutral,
 * never despawns, can't be leashed or traded with. Right-click opens the Loan screen; invulnerable to
 * players while a loan screen of his is open. Killing him does not touch any debt; he drops 1 emerald
 * and respawns at his home after {@code worldgen.loanSharkRespawnTicks}. Both entity types share this
 * class; the piglin one is only a different skin/voice.
 */
public class LoanSharkEntity extends PathfinderMob {
	private @Nullable BlockPos home;

	public LoanSharkEntity(EntityType<? extends LoanSharkEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.5);
	}

	public boolean isPiglin() {
		return getType() == LoanContent.PIGLIN_MONEYLENDER;
	}

	/** Where he respawns (first position he was seen at). */
	public BlockPos home() {
		return home != null ? home : blockPosition();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (home == null && !level().isClientSide() && onGround()) {
			home = blockPosition();
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			LoanShark.open(sp, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player && LoanShark.isBusy(this)) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			LoanShark.onDeath(level, this);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		spawnAtLocation(level, new ItemStack(Items.EMERALD));
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isPiglin() ? SoundEvents.PIGLIN_AMBIENT : SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return isPiglin() ? SoundEvents.PIGLIN_HURT : SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return isPiglin() ? SoundEvents.PIGLIN_DEATH : SoundEvents.VILLAGER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("burmaldaholic_home", BlockPos.CODEC, home);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		home = input.read("burmaldaholic_home", BlockPos.CODEC).orElse(null);
	}
}
