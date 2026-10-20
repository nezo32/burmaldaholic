package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.worldgen.logic.NpcLook;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Casino staff of the generated casinos (GAME_DESIGN §16): the Croupier (village casino, villager model,
 * sells scratch cards and Lucky Coins), the Piglin Dealer (Piglin Parlor, piglin model with a vest; seats
 * you at the nearest table — not a vanilla piglin: never zombifies, never takes items, neutral) and the
 * Shulker Croupier (High Roller Lounge, shulker model, sells gold scratch cards). They stand at their
 * post (no movement), never despawn and are respawned by worldgen at their home when killed.
 * One class for every staff type; the role follows from the type. DL adds the Ender Croupier (End), the Pit Boss
 * (Parlor) and the Merchant beside every cashier (villager / piglin / ender per dimension, its store registered by EC
 * through {@link HouseNpcRoles}); every type wears one skin of its pool ({@link HasLook}).
 */
public class CasinoNpcEntity extends PathfinderMob implements HasLook {
	private static final EntityDataAccessor<Byte> LOOK = SynchedEntityData.defineId(CasinoNpcEntity.class, EntityDataSerializers.BYTE);

	public CasinoNpcEntity(EntityType<? extends CasinoNpcEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setLook(NpcLook.pick(lookPool(), getUUID())); // spawn eggs; worldgen overrides it per casino and stand
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	public NpcRole role() {
		EntityType<?> t = getType();
		if (t == NpcContent.PIGLIN_DEALER) {
			return NpcRole.PIGLIN_DEALER;
		}
		if (t == NpcContent.SHULKER_CROUPIER) {
			return NpcRole.SHULKER_CROUPIER;
		}
		if (t == NpcContent.ENDER_CROUPIER) {
			return NpcRole.ENDER_CROUPIER;
		}
		if (t == NpcContent.PIT_BOSS) {
			return NpcRole.PIT_BOSS;
		}
		if (t == NpcContent.MERCHANT || t == NpcContent.PIGLIN_MERCHANT || t == NpcContent.ENDER_MERCHANT) {
			return NpcRole.MERCHANT;
		}
		return NpcRole.CROUPIER;
	}

	/** Model family of this NPC (picks the renderer's skin pool and the sounds). */
	public NpcLook.Species species() {
		EntityType<?> t = getType();
		if (t == NpcContent.PIGLIN_DEALER || t == NpcContent.PIT_BOSS || t == NpcContent.PIGLIN_MERCHANT) {
			return NpcLook.Species.PIGLIN;
		}
		if (t == NpcContent.ENDER_CROUPIER || t == NpcContent.ENDER_MERCHANT) {
			return NpcLook.Species.ENDER;
		}
		return NpcLook.Species.HUMAN;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LOOK, (byte) 0);
	}

	@Override
	public int look() {
		return Math.floorMod(entityData.get(LOOK), Math.max(1, lookPool()));
	}

	@Override
	public void setLook(int look) {
		entityData.set(LOOK, (byte) Math.floorMod(look, Math.max(1, lookPool())));
	}

	@Override
	public int lookPool() {
		EntityType<?> t = getType();
		if (t == NpcContent.SHULKER_CROUPIER || t == NpcContent.PIT_BOSS) {
			return 1;
		}
		return switch (species()) {
			case PIGLIN -> NpcLook.PIGLIN;
			case ENDER -> NpcLook.ENDER;
			case HUMAN -> NpcLook.VILLAGER;
		};
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putByte("look", (byte) look());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setLook(input.getByteOr("look", (byte) NpcLook.pick(lookPool(), getUUID())));
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
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_AMBIENT;
			case PIT_BOSS -> SoundEvents.PIGLIN_BRUTE_AMBIENT;
			default -> switch (species()) {
				case PIGLIN -> SoundEvents.PIGLIN_AMBIENT;
				case ENDER -> SoundEvents.ENDERMAN_AMBIENT;
				case HUMAN -> SoundEvents.VILLAGER_AMBIENT;
			};
		};
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return switch (role()) {
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_HURT;
			case PIT_BOSS -> SoundEvents.PIGLIN_BRUTE_HURT;
			default -> switch (species()) {
				case PIGLIN -> SoundEvents.PIGLIN_HURT;
				case ENDER -> SoundEvents.ENDERMAN_HURT;
				case HUMAN -> SoundEvents.VILLAGER_HURT;
			};
		};
	}

	@Override
	protected SoundEvent getDeathSound() {
		return switch (role()) {
			case SHULKER_CROUPIER -> SoundEvents.SHULKER_DEATH;
			case PIT_BOSS -> SoundEvents.PIGLIN_BRUTE_DEATH;
			default -> switch (species()) {
				case PIGLIN -> SoundEvents.PIGLIN_DEATH;
				case ENDER -> SoundEvents.ENDERMAN_DEATH;
				case HUMAN -> SoundEvents.VILLAGER_DEATH;
			};
		};
	}
}
