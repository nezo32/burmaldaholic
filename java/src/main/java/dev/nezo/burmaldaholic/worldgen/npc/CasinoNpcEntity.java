package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
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
import net.minecraft.world.level.Level;

/**
 * Casino staff of the generated casinos (GAME_DESIGN §16): the Croupier (village casino, villager model,
 * sells scratch cards and Lucky Coins), the Piglin Dealer (Piglin Parlor, piglin model with a vest; seats
 * you at the nearest table — not a vanilla piglin: never zombifies, never takes items, neutral) and the
 * Shulker Croupier (High Roller Lounge, shulker model, sells gold scratch cards). They stand at their
 * post (no movement), never despawn and are respawned by worldgen at their home when killed.
 * One class for the three entity types; the role follows from the type.
 */
public class CasinoNpcEntity extends PathfinderMob {
	public CasinoNpcEntity(EntityType<? extends CasinoNpcEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	public NpcRole role() {
		if (getType() == NpcContent.PIGLIN_DEALER) {
			return NpcRole.PIGLIN_DEALER;
		}
		return getType() == NpcContent.SHULKER_CROUPIER ? NpcRole.SHULKER_CROUPIER : NpcRole.CROUPIER;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			NpcInteractions.interact(sp, this);
		}
		return InteractionResult.SUCCESS;
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
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return switch (role()) {
			case PIGLIN_DEALER -> SoundEvents.PIGLIN_AMBIENT;
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_AMBIENT;
			default -> SoundEvents.VILLAGER_AMBIENT;
		};
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return switch (role()) {
			case PIGLIN_DEALER -> SoundEvents.PIGLIN_HURT;
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_HURT;
			default -> SoundEvents.VILLAGER_HURT;
		};
	}

	@Override
	protected SoundEvent getDeathSound() {
		return switch (role()) {
			case PIGLIN_DEALER -> SoundEvents.PIGLIN_DEATH;
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_DEATH;
			default -> SoundEvents.VILLAGER_DEATH;
		};
	}
}
