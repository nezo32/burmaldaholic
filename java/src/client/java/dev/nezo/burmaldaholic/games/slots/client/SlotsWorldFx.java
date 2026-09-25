package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsFx;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFxPlan;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Client player of the cabinet world FX (slots.md §5.3, JS15): when a cabinet receives a new {@link CabinetSync} its
 * {@link CabinetFxPlan} events are queued and played at {@code startTick + ceil(atMs / 50)} of the client level's game
 * time, so the bursts land on the beat every spectator sees. Everything here follows this viewer's FX settings: the
 * positional sounds are scaled by the Effects volume, reduce motion halves the particles, and flashes off drops the
 * firework flashes. Budgets: ≤ 60 particles per burst, ≤ 3 bursts per event.
 */
public final class SlotsWorldFx {
	private record Pending(BlockPos pos, Direction facing, CabinetSync sync, long tick, CabinetFxPlan.Event event) {}

	private static final List<Pending> QUEUE = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private SlotsWorldFx() {}

	public static void register() {
		SlotsFx.clientSync = SlotsWorldFx::onSync;
		ClientTickEvents.END_CLIENT_TICK.register(SlotsWorldFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> QUEUE.clear());
	}

	/** Events still queued (GameTests). */
	public static int queued() {
		return QUEUE.size();
	}

	/**
	 * A cabinet's new sync: anything still queued for it is dropped (a new spin, or a republished step of the same spin,
	 * replaces it); events whose tick already passed are skipped, so a republish never replays a burst.
	 */
	static void onSync(SlotMachineBlockEntity be) {
		CabinetSync sync = be.cabinetSync();
		if (!(be.getLevel() instanceof ClientLevel level) || sync == null) return;
		BlockPos pos = be.getBlockPos().immutable();
		QUEUE.removeIf(p -> p.pos.equals(pos));
		BlockState st = be.getBlockState();
		Direction facing = st.hasProperty(CasinoTableBlock.FACING) ? st.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		long now = level.getGameTime();
		for (CabinetFxPlan.Event e : CabinetFxPlan.events(sync)) {
			long tick = CabinetFxPlan.tickOf(sync, e);
			if (tick < now) continue;
			QUEUE.add(new Pending(pos, facing, sync, tick, e));
		}
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (QUEUE.isEmpty()) return;
		if (level == null) {
			QUEUE.clear();
			return;
		}
		List<Pending> due = null;
		for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (level.getGameTime() >= p.tick) {
				it.remove();
				if (due == null) due = new ArrayList<>();
				due.add(p);
			}
		}
		if (due == null) return;
		for (Pending p : due) {
			if (!level.isLoaded(p.pos)) continue;
			try {
				run(level, p);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("slots world FX failed", e);
			}
		}
	}

	private static void run(ClientLevel level, Pending p) {
		Vec3 face = SlotsFx.face(p.pos, p.facing, 1.45);
		Vec3 top = SlotsFx.face(p.pos, p.facing, 2.05);
		int arg = p.event.arg();
		switch (p.event.kind()) {
			case SPIN -> sound(level, face, "slots.spin_loop", CoreSounds.SLOT_SPIN, 0.3f, 1f);
			case FREE_SPINS -> {
				burst(level, particle("sparkle", ParticleTypes.END_ROD), top, 20, 0.6, 0.15, 0.02);
				sound(level, face, "slots.fs_intro", SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1f);
			}
			case TUMBLE -> {
				SimpleParticleType ember = SlotsFx.emberBurst();
				burst(level, ember != null ? ember : ParticleTypes.FLAME, face, 8, 0.3, 0.2, 0.02);
			}
			case STICKY -> {
				SimpleParticleType motes = SlotsFx.voidMotes();
				burst(level, motes != null ? motes : ParticleTypes.REVERSE_PORTAL, SlotsFx.reel(p.pos, p.facing, arg), 6, 0.04, 0.12, 0.01);
			}
			case TIER -> tier(level, face, top, arg);
			case JACKPOT -> jackpot(level, face, top, arg, p.sync);
		}
	}

	private static void tier(ClientLevel level, Vec3 face, Vec3 top, int arg) {
		if (arg == CabinetFxPlan.MAX_WIN_ARG || arg >= WinTier.EPIC.ordinal()) {
			burst(level, coin(), top, 60, 0.4, 0.3, 0.25);
			fireworks(level, top.add(0, 0.6, 0), 36, 0.9, 0.5, 0.08);
			String stem = arg == CabinetFxPlan.MAX_WIN_ARG ? "slots.max_win" : "slots.epic_win";
			sound(level, face, stem, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
			if (FxSettings.flashes()) sound(level, face, null, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6f, 1f);
		} else if (arg == WinTier.MEGA.ordinal()) {
			burst(level, coin(), top, 60, 0.4, 0.3, 0.22);
			burst(level, particle("confetti", ParticleTypes.HAPPY_VILLAGER), top.add(0, 0.4, 0), 30, 0.8, 0.4, 0.05);
			sound(level, face, "slots.mega_win", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1f);
		} else if (arg == WinTier.BIG.ordinal()) {
			burst(level, coin(), top, 40, 0.35, 0.25, 0.2);
			sound(level, face, "slots.big_win", SoundEvents.PLAYER_LEVELUP, 0.5f, 1f);
		} else if (arg == WinTier.NICE.ordinal()) {
			burst(level, particle("sparkle", ParticleTypes.END_ROD), top, 12, 0.45, 0.1, 0.01);
			sound(level, face, "slots.win_nice", SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.2f);
		}
	}

	/** slots.md §4.11 row "World": Mini 20 coins … Grand 60 + jackpot burst + fireworks. */
	private static void jackpot(ClientLevel level, Vec3 face, Vec3 top, int tier, CabinetSync sync) {
		int coins = switch (tier) {
			case 1 -> 20;
			case 2 -> 30;
			case 3 -> 40;
			default -> 60;
		};
		burst(level, coin(), top, coins, 0.4, 0.3, 0.25);
		if (tier >= 3) fireworks(level, top.add(0, 0.8, 0), tier == 4 ? 60 : 36, 1.0, 0.6, 0.1);
		if (tier == 4) burst(level, particle("jackpot_burst", ParticleTypes.TOTEM_OF_UNDYING), top, 30, 0.2, 0.2, 0.5);
		float pitch = tier == 1 ? 1.3f : tier == 2 ? 1.15f : 1f;
		sound(level, face, "jackpot", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, pitch);
		if (tier == 4) sound(level, face, "slots.epic_win", SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.6f, 1f);
		if (tier == 4 && sync.machine() == Machine.END) sound(level, face, null, SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 1.6f);
	}

	private static SimpleParticleType coin() {
		return particle("coin_burst", ParticleTypes.WAX_ON);
	}

	/** A core-owned particle when registered (lane J-L1), else the vanilla stand-in. */
	private static SimpleParticleType particle(String coreId, SimpleParticleType fallback) {
		return BuiltInRegistries.PARTICLE_TYPE.getOptional(Burmaldaholic.id(coreId))
			.filter(t -> t instanceof SimpleParticleType)
			.map(t -> (SimpleParticleType) t)
			.orElse(fallback);
	}

	/** Firework sparks are a flash: dropped when this viewer turned flashes off (or reduce motion). */
	private static void fireworks(ClientLevel level, Vec3 at, int count, double spreadXZ, double spreadY, double speed) {
		if (!FxSettings.flashes()) return;
		burst(level, ParticleTypes.FIREWORK, at, count, spreadXZ, spreadY, speed);
	}

	/** Same distribution as {@code ServerLevel.sendParticles}; reduce motion halves the count. */
	private static void burst(ClientLevel level, SimpleParticleType type, Vec3 at, int count, double spreadXZ, double spreadY, double speed) {
		int n = Math.min(count, 60);
		if (FxSettings.reduceMotion()) n = n / 2;
		for (int i = 0; i < n; i++) {
			double x = at.x + RANDOM.nextGaussian() * spreadXZ;
			double y = at.y + RANDOM.nextGaussian() * spreadY;
			double z = at.z + RANDOM.nextGaussian() * spreadXZ;
			level.addParticle(type, x, y, z, RANDOM.nextGaussian() * speed, RANDOM.nextGaussian() * speed, RANDOM.nextGaussian() * speed);
		}
	}

	/** Positional sound at the Effects volume of this viewer. */
	private static void sound(ClientLevel level, Vec3 at, @Nullable String id, @Nullable SoundEvent fallback, float volume, float pitch) {
		SoundEvent e = id == null ? null : CasinoSounds.get(id);
		if (e == null) e = fallback;
		float v = volume * FxSettings.volume();
		if (e == null || v <= 0) return;
		level.playLocalSound(at.x, at.y, at.z, e, SoundSource.BLOCKS, v, pitch, false);
	}
}
