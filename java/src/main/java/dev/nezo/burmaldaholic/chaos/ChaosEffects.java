package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules.EffectSpec;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules.RolledEffect;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules.Weather;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.earnings.Earnings;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The nine event effects of GAME_DESIGN.md §13.2 (Golden Hour lives in {@link GoldenHour}). */
final class ChaosEffects {
	/** Scoreboard tag on chaos-wave mobs (§13.2 "mobs tagged burmaldaholic:chaos"). */
	static final String WAVE_TAG = "burmaldaholic:chaos";
	private static final int RAIN_TICKS = 100;
	private static final int FOUNTAIN_TICKS = 60;
	private static final int FOUNTAIN_BURSTS = 6;

	/** Game time at which a chaos-wave mob is removed (persistent, so reloaded chunks are handled too). */
	static AttachmentType<Long> waveUntil;

	private ChaosEffects() {}

	static void register(ModuleContext ctx) {
		waveUntil = AttachmentRegistry.createPersistent(ctx.id("chaos_wave_until"), Codec.LONG);
	}

	private static ChaosConfig cfg() {
		return ChaosEngine.cfg();
	}

	/** Runs the (already safety-checked) event; false = nothing happened (skip silently). */
	static boolean run(ServerPlayer p, ChaosEvent e, String source) {
		return switch (e) {
			case CHIP_SHOWER -> chipShower(p);
			case LUCKY_BUFF -> effect(p, ChaosRules.BUFFS, cfg().buff.minTicks, cfg().buff.maxTicks, true, "big_win".equals(source));
			case CURSE -> effect(p, ChaosRules.CURSES, cfg().curse.minTicks, cfg().curse.maxTicks, false, false);
			case DIAMOND_RAIN -> diamondRain(p);
			case XP_FOUNTAIN -> xpFountain(p);
			case MOB_WAVE -> mobWave(p);
			case RANDOM_TELEPORT -> teleport(p);
			case WEATHER_CHANGE -> weather(p);
			case GOLDEN_HOUR -> GoldenHour.start(p.level().getServer(), null, source) == dev.nezo.burmaldaholic.chaos.logic.TriggerResult.STARTED;
		};
	}

	// ---- announcements -----------------------------------------------------------------------

	static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		if (subtitle != null) {
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	private static void announce(ServerPlayer p, String titleKey, Component subtitle) {
		title(p, Component.translatable(titleKey), subtitle, 5, 50, 15);
	}

	private static void sound(ServerPlayer p, SoundEvent sound, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
	}

	/** "2 minutes" / "45 seconds" in the accusative ("на 2 минуты", "for 45 seconds"). */
	static MutableComponent duration(long ticks) {
		ChaosRules.Duration d = ChaosRules.duration(ticks);
		return Texts.plural(d.minutes() ? "unit.burmaldaholic.minute_acc" : "unit.burmaldaholic.second_acc", d.amount());
	}

	// ---- chip shower -------------------------------------------------------------------------

	private static boolean chipShower(ServerPlayer p) {
		CasinoRng rng = ChaosEngine.rng();
		int amount = ChaosRules.rollRange(rng, cfg().chipShower.min, cfg().chipShower.max);
		if (amount <= 0) {
			return false;
		}
		int[] split = ChaosRules.chipShowerSplit(rng, amount);
		ServerLevel level = p.level();
		Vec3 origin = p.position();
		List<ItemStack> piles = new ArrayList<>();
		addPiles(piles, 5, split[0]);
		addPiles(piles, 1, split[1]);
		for (ItemStack stack : piles) {
			int[] o = ChaosRules.randomOffset(rng, 0.5, 2);
			spawnItem(level, stack, ChaosWorld.dropSpot(level, origin, o[0], o[1], 1));
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, origin.x, origin.y + 1, origin.z, 30, 1.0, 0.5, 1.0, 0.3);
		sound(p, SoundEvents.PLAYER_LEVELUP, 1.4F);
		announce(p, "msg.burmaldaholic.chaos.chip_shower.title", Component.translatable("msg.burmaldaholic.chaos.chip_shower.subtitle"));
		p.sendSystemMessage(Component.translatable("msg.burmaldaholic.chaos.chip_shower.chat", Texts.chips(amount)));
		return true;
	}

	/** Splits {@code count} chips of {@code value} into small piles (≈4 per pile, max 6 piles). */
	private static void addPiles(List<ItemStack> out, int value, int count) {
		if (count <= 0) {
			return;
		}
		int parts = Math.min(6, Math.max(1, (count + 3) / 4));
		for (int n : ChaosRules.splitEven(count, parts)) {
			int left = n;
			while (left > 0) {
				int k = Math.min(64, left);
				out.add(new ItemStack(Chips.item(value), k));
				left -= k;
			}
		}
	}

	private static void spawnItem(ServerLevel level, ItemStack stack, Vec3 at) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	// ---- buffs / curses ----------------------------------------------------------------------

	static Optional<Holder.Reference<MobEffect>> effectHolder(String id) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(id));
	}

	/** Vanilla effect name with a roman potency ("Speed II"), exactly like potion tooltips. */
	static Component effectName(Holder<MobEffect> effect, int amplifier) {
		Component name = effect.value().getDisplayName();
		return amplifier > 0 ? Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + amplifier)) : name;
	}

	private static boolean effect(ServerPlayer p, List<EffectSpec> pool, int minTicks, int maxTicks, boolean buff, boolean bigWin) {
		RolledEffect rolled = ChaosRules.rollEffect(ChaosEngine.rng(), pool, minTicks, maxTicks);
		EffectSpec spec = rolled.effect();
		if (ChaosRules.LETHAL_EFFECTS.contains(spec.id())) {
			return false; // never (§13.4); guards against a future pool edit
		}
		Optional<Holder.Reference<MobEffect>> holder = effectHolder(spec.id());
		if (holder.isEmpty()) {
			ChaosEngine.LOG.warn("chaos: unknown effect {}", spec.id());
			return false;
		}
		if (!p.addEffect(new MobEffectInstance(holder.get(), rolled.ticks(), spec.amplifier(), false, true))) {
			return false;
		}
		Component name = effectName(holder.get(), spec.amplifier());
		String prefix = buff ? "msg.burmaldaholic.chaos.lucky_buff" : "msg.burmaldaholic.chaos.curse";
		announce(p, prefix + ".title", Component.translatable(prefix + ".subtitle", name, duration(rolled.ticks())));
		sound(p, buff ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WITCH_AMBIENT, buff ? 1.8F : 1.0F);
		if (bigWin) {
			p.sendSystemMessage(Component.translatable("msg.burmaldaholic.chaos.big_win_buff", name));
		}
		return true;
	}

	// ---- diamond rain ------------------------------------------------------------------------

	private static boolean diamondRain(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		boolean hard = server.isHardcore() || p.level().getDifficulty() == Difficulty.HARD;
		CasinoRng rng = ChaosEngine.rng();
		int count = ChaosRules.diamondRainCount(rng, cfg().diamondRain.min, cfg().diamondRain.max, hard);
		if (count <= 0) {
			return false;
		}
		UUID id = p.getUUID();
		for (int i = 0; i < count; i++) {
			int delay = Math.max(1, i * RAIN_TICKS / count);
			ChaosEngine.schedule(server, delay, () -> {
				ServerPlayer q = server.getPlayerList().getPlayer(id);
				if (q == null) {
					return;
				}
				int[] o = ChaosRules.randomOffset(ChaosEngine.rng(), 0, 3);
				Vec3 at = ChaosWorld.dropSpot(q.level(), q.position(), o[0], o[1], 6);
				spawnItem(q.level(), new ItemStack(Items.DIAMOND), at);
				q.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
			});
		}
		for (int k = 0; k < RAIN_TICKS; k += 10) {
			ChaosEngine.schedule(server, k + 1, () -> {
				ServerPlayer q = server.getPlayerList().getPlayer(id);
				if (q != null) {
					q.level().sendParticles(ParticleTypes.END_ROD, q.getX(), q.getY() + 5, q.getZ(), 12, 2.5, 1.0, 2.5, 0.02);
				}
			});
		}
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
		announce(p, "msg.burmaldaholic.chaos.diamond_rain.title", Component.translatable("msg.burmaldaholic.chaos.diamond_rain.subtitle"));
		return true;
	}

	// ---- XP fountain -------------------------------------------------------------------------

	private static boolean xpFountain(ServerPlayer p) {
		int total = ChaosRules.rollRange(ChaosEngine.rng(), cfg().xpFountain.min, cfg().xpFountain.max);
		if (total <= 0) {
			return false;
		}
		MinecraftServer server = p.level().getServer();
		UUID id = p.getUUID();
		int[] bursts = ChaosRules.splitEven(total, FOUNTAIN_BURSTS);
		for (int i = 0; i < bursts.length; i++) {
			int xp = bursts[i];
			ChaosEngine.schedule(server, 1 + i * (FOUNTAIN_TICKS / FOUNTAIN_BURSTS), () -> {
				ServerPlayer q = server.getPlayerList().getPlayer(id);
				if (q == null || xp <= 0) {
					return;
				}
				ExperienceOrb.award(q.level(), q.position().add(0, 1.5, 0), xp);
				q.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, q.getX(), q.getY() + 1.5, q.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
			});
		}
		sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F);
		announce(p, "msg.burmaldaholic.chaos.xp_fountain.title", Component.translatable("msg.burmaldaholic.chaos.xp_fountain.subtitle"));
		return true;
	}

	// ---- mob wave ----------------------------------------------------------------------------

	private static boolean mobWave(ServerPlayer p) {
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		ChaosConfig.MobWave mw = cfg().mobWave;
		ChaosRules.Difficulty diff = switch (level.getDifficulty()) {
			case PEACEFUL -> ChaosRules.Difficulty.PEACEFUL;
			case EASY -> ChaosRules.Difficulty.EASY;
			case NORMAL -> ChaosRules.Difficulty.NORMAL;
			case HARD -> ChaosRules.Difficulty.HARD;
		};
		int n = ChaosRules.mobWaveSize(diff, server.isHardcore(), mw.easy, mw.normal, mw.hard);
		if (n <= 0) {
			return false;
		}
		CasinoRng rng = ChaosEngine.rng();
		List<BlockPos> spots = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			BlockPos spot = null;
			for (int a = 0; a < 16 && spot == null; a++) {
				int[] o = ChaosRules.randomOffset(rng, mw.minDistance, mw.maxDistance);
				spot = ChaosWorld.findSpawnSpot(level, p.getBlockX() + o[0], p.getBlockZ() + o[1], p.getBlockY());
				if (spot != null && CoreServices.claims().isClaimed(level, spot)) {
					spot = null; // never inside a player casino (§13.4)
				}
			}
			if (spot == null) {
				return false; // fewer than N valid spots: skip the whole wave (§13.4)
			}
			spots.add(spot);
		}
		long until = ChaosEngine.now(server) + mw.despawnTicks;
		List<String> types = ChaosRules.waveComposition(rng, ChaosWorld.dimension(level), n);
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(types.get(i)));
			if (type == null) {
				continue;
			}
			Entity mob = type.spawn(level, spots.get(i), EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			if (mob instanceof Slime cube) {
				cube.setSize(2, true); // magma cube size 2 (§13.2)
			}
			mob.addTag(WAVE_TAG);
			mob.setAttached(waveUntil, until);
			Earnings.markNoReward(mob);
			if (mob instanceof Mob m) {
				m.setTarget(p);
			}
			BlockPos s = spots.get(i);
			level.sendParticles(ParticleTypes.POOF, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.05);
			spawned++;
		}
		if (spawned == 0) {
			return false;
		}
		sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F);
		announce(p, "msg.burmaldaholic.chaos.mob_wave.title", Component.translatable("msg.burmaldaholic.chaos.mob_wave.subtitle"));
		p.sendSystemMessage(Component.translatable("msg.burmaldaholic.chaos.mob_wave.chat"));
		return true;
	}

	/** Removes chaos-wave mobs whose lifetime is over (§13.2: despawn after chaos.mobWave.despawnTicks). */
	static void sweepWaves(MinecraftServer server) {
		long now = ChaosEngine.now(server);
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> expired = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e == null || !e.entityTags().contains(WAVE_TAG)) {
					continue;
				}
				Long until = e.getAttached(waveUntil);
				// a clock that jumped far back (e.g. world copied) must not keep them forever
				if (until == null || now >= until || now < until - 10L * 72000) {
					expired.add(e);
				}
			}
			for (Entity e : expired) {
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
				e.discard();
			}
		}
	}

	// ---- random teleport ---------------------------------------------------------------------

	private static boolean teleport(ServerPlayer p) {
		ServerLevel level = p.level();
		ChaosConfig.Teleport tp = cfg().teleport;
		CasinoRng rng = ChaosEngine.rng();
		Vec3 from = p.position();
		boolean mayLoad = true; // max 1 synchronous chunk load per event (§13.4)
		for (int a = 0; a < tp.attempts; a++) {
			int[] o = ChaosRules.randomOffset(rng, tp.minDistance, tp.maxDistance);
			int x = p.getBlockX() + o[0];
			int z = p.getBlockZ() + o[1];
			boolean loaded = level.hasChunk(x >> 4, z >> 4);
			int y = ChaosWorld.findLanding(level, x, z, mayLoad);
			if (!loaded) {
				mayLoad = false;
			}
			if (y == Integer.MIN_VALUE || CoreServices.claims().isClaimed(level, new BlockPos(x, y, z))) {
				continue; // never into a player casino (§13.4)
			}
			double tx = x + 0.5;
			double ty = y + 1;
			double tz = z + 0.5;
			level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.5, 1.0, 0.5, 0.2);
			if (!p.teleportTo(level, tx, ty, tz, Set.of(), p.getYRot(), p.getXRot(), true)) {
				return false;
			}
			p.fallDistance = 0;
			effectHolder("resistance").ifPresent(h -> p.addEffect(new MobEffectInstance(h, 60, 4, false, false)));
			level.playSound(null, tx, ty, tz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
			long dist = Math.round(Math.hypot(tx - from.x, tz - from.z));
			announce(p, "msg.burmaldaholic.chaos.random_teleport.title",
				Component.translatable("msg.burmaldaholic.chaos.random_teleport.subtitle", Texts.plural("unit.burmaldaholic.block", dist)));
			p.sendSystemMessage(Component.translatable("msg.burmaldaholic.chaos.random_teleport.chat", Texts.raw(x + ", " + (y + 1) + ", " + z)));
			CasinoAdvancements.grant(p, "beam_me_up");
			return true;
		}
		return false; // no safe spot: skip silently (§13.4)
	}

	// ---- weather -----------------------------------------------------------------------------

	private static boolean weather(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		var wd = server.getWeatherData();
		Weather next = Weather.of(wd.isRaining(), wd.isThundering()).next();
		int dur = cfg().weather.durationTicks;
		switch (next) {
			case CLEAR -> server.setWeatherParameters(dur, 0, false, false);
			case RAIN -> server.setWeatherParameters(0, dur, true, false);
			case THUNDER -> server.setWeatherParameters(0, dur, true, true);
		}
		Component line = Component.translatable("msg.burmaldaholic.chaos.weather." + next.id());
		announce(p, "msg.burmaldaholic.chaos.weather.title", line);
		for (ServerPlayer o : server.overworld().players()) {
			if (o != p) {
				o.sendSystemMessage(line);
			}
		}
		return true;
	}

	/** Used by GameTests: whether an entity is a chaos-wave mob. */
	static boolean isWaveMob(Entity e) {
		return e.entityTags().contains(WAVE_TAG);
	}
}
