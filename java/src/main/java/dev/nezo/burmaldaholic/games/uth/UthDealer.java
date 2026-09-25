package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Hold'em Dealer NPC ({@code burmaldaholic:uth_dealer}, GAME_DESIGN §21.6): optional and cosmetic, same
 * behaviour as the Baccarat Dealer — stationary, invulnerable, persistent, looks at players. Using it opens
 * the nearest hold'em table (standard, High-Roller or player-banked) within {@link #TABLE_RADIUS} blocks, else
 * {@code msg.burmaldaholic.uth.no_table}. The table deals itself; the dealer is only its face.
 */
public class UthDealer extends PathfinderMob {
	public static final int TABLE_RADIUS = 3;

	public UthDealer(EntityType<? extends UthDealer> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f, 0.9f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (!CasinoMode.isEnabled(serverLevel)) {
			serverPlayer.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return InteractionResult.CONSUME;
		}
		if (!CasinoConfig.uth().enabled) {
			serverPlayer.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.disabled"));
			return InteractionResult.CONSUME;
		}
		UthTableBlockEntity table = nearestTable(serverLevel);
		if (table == null) {
			serverPlayer.sendOverlayMessage(Component.translatable("msg.burmaldaholic.uth.no_table"));
			return InteractionResult.CONSUME;
		}
		serverPlayer.openMenu(table);
		table.sendStateTo(serverPlayer);
		return InteractionResult.CONSUME;
	}

	/** Closest hold'em table within {@link #TABLE_RADIUS} blocks horizontally, one block up/down. */
	public UthTableBlockEntity nearestTable(Level level) {
		BlockPos center = blockPosition();
		UthTableBlockEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TABLE_RADIUS, -1, -TABLE_RADIUS), center.offset(TABLE_RADIUS, 1, TABLE_RADIUS))) {
			if (level.getBlockEntity(pos) instanceof UthTableBlockEntity be) {
				double d = pos.distSqr(center);
				if (d < bestDist) {
					bestDist = d;
					best = be;
				}
			}
		}
		return best;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.isCreativePlayer() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(net.minecraft.world.entity.Entity entity) {}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
