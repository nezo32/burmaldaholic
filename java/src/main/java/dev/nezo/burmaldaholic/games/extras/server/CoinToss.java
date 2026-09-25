package dev.nezo.burmaldaholic.games.extras.server;

import com.mojang.math.Transformation;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.anim.CoinTossKeys;
import dev.nezo.burmaldaholic.games.extras.mixin.TossDisplayAccessor;
import dev.nezo.burmaldaholic.games.extras.mixin.TossItemDisplayAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Lucky Coin toss spectators see (extras-pvp.md §1.3): a transient vanilla {@link Display.ItemDisplay} thrown from
 * the player's hand, keyed by {@link CoinTossKeys} — every client renders it (no mod code on the viewing side), culled
 * by the display's view range (half the default: ~32 blocks). The item shows the edge-on {@code spin} model until the
 * landing tick and only then the true face ({@code heads} / {@code tails} through the item model's
 * {@code custom_model_data} select), so the result never reaches a spectator before the coin lands. One toss per
 * player (a new flip replaces the old one); tossed coins are never saved (tagged, discarded on load and on stop).
 */
public final class CoinToss {
	/** Tag of toss displays (a display saved with its chunk is discarded when it loads again). */
	public static final String TAG = "burmaldaholic_coin_toss";

	private record Toss(ServerLevel level, UUID entity, long start, boolean heads) {}

	private static final Map<UUID, Toss> TOSSES = new HashMap<>();

	private CoinToss() {}

	/** Throws the coin for {@code player}; {@code heads} is the settled face (shown from the landing only). */
	public static void toss(ServerPlayer player, boolean heads) {
		if (!(player.level() instanceof ServerLevel level) || ExtrasModule.LUCKY_COIN == null) {
			return;
		}
		remove(player.getUUID());
		Vec3 look = player.getLookAngle();
		Vec3 at = player.getEyePosition().add(look.scale(0.6)).add(0, -0.25, 0);
		Display.ItemDisplay d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		d.setPos(at.x, at.y, at.z);
		d.setYRot(player.getYRot() + 180f);
		d.addTag(TAG);
		d.setNoGravity(true);
		((TossItemDisplayAccessor) d).burmaldaholic$extrasItem(coin("spin"));
		TossDisplayAccessor a = (TossDisplayAccessor) d;
		a.burmaldaholic$extrasViewRange(0.5f);
		a.burmaldaholic$extrasTransformation(transform(0f, 0f, CoinTossKeys.SCALE));
		if (!level.addFreshEntity(d)) {
			return;
		}
		TOSSES.put(player.getUUID(), new Toss(level, d.getUUID(), level.getGameTime(), heads));
	}

	/** The coin item with the item model's variant ({@code spin}, {@code heads}, {@code tails}). */
	static ItemStack coin(String variant) {
		ItemStack s = new ItemStack(ExtrasModule.LUCKY_COIN);
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(variant), List.of()));
		return s;
	}

	private static Transformation transform(float y, float rollDeg, float scale) {
		return new Transformation(new Vector3f(0f, y, 0f), new Quaternionf().rotationX((float) Math.toRadians(rollDeg)),
			new Vector3f(scale, scale, scale), null);
	}

	/** Server tick: applies the due keyframes, lands the coin, removes finished tosses. */
	public static void tick(MinecraftServer server) {
		if (TOSSES.isEmpty()) {
			return;
		}
		for (var it = TOSSES.entrySet().iterator(); it.hasNext();) {
			Toss t = it.next().getValue();
			Entity e = t.level().getEntity(t.entity());
			int tick = (int) (t.level().getGameTime() - t.start());
			if (!(e instanceof Display.ItemDisplay d) || tick >= CoinTossKeys.REMOVE_TICK || tick < 0) {
				if (e != null) {
					e.discard();
				}
				it.remove();
				continue;
			}
			CoinTossKeys.Key k = CoinTossKeys.at(tick);
			if (k == null) {
				continue;
			}
			TossDisplayAccessor a = (TossDisplayAccessor) d;
			if (k.face() && tick == CoinTossKeys.LAND_TICK) {
				((TossItemDisplayAccessor) d).burmaldaholic$extrasItem(coin(t.heads() ? "heads" : "tails"));
				t.level().sendParticles(ParticleTypes.CRIT, d.getX(), d.getY(), d.getZ(), 8, 0.12, 0.12, 0.12, 0.2);
				SoundEvent land = CasinoSounds.get("coin_land");
				t.level().playSound(null, d.getX(), d.getY(), d.getZ(), land != null ? land : SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1f);
			}
			a.burmaldaholic$extrasTransformation(transform(k.y(), k.rollDeg(), k.scale()));
			a.burmaldaholic$extrasInterpolationDuration(k.interpolation());
			a.burmaldaholic$extrasInterpolationDelay(0);
		}
	}

	/** The running toss display of {@code player}, or {@code null} (tests). */
	public static Display.ItemDisplay displayOf(ServerPlayer player) {
		Toss t = TOSSES.get(player.getUUID());
		return t != null && t.level().getEntity(t.entity()) instanceof Display.ItemDisplay d ? d : null;
	}

	private static void remove(UUID player) {
		Toss t = TOSSES.remove(player);
		if (t != null) {
			Entity e = t.level().getEntity(t.entity());
			if (e != null) {
				e.discard();
			}
		}
	}

	/** A toss display loaded from a save (its chunk was saved mid-toss) is not ours any more. */
	public static void onEntityLoad(Entity entity) {
		if (entity.entityTags().contains(TAG)) {
			for (Toss t : TOSSES.values()) {
				if (t.entity().equals(entity.getUUID())) {
					return;
				}
			}
			entity.discard();
		}
	}

	public static void forget(UUID player) {
		remove(player);
	}

	public static void clear() {
		for (UUID p : List.copyOf(TOSSES.keySet())) {
			remove(p);
		}
		TOSSES.clear();
	}
}
