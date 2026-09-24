package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFxPlan;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * World FX of the slot cabinets (slots.md §5.3, JS15): the module-owned particle types ({@code ember_burst},
 * {@code void_motes}; Java ids {@code burmaldaholic:slots/…} because the asset ownership check requires the
 * module folder) and the server scheduler that plays a published {@link CabinetSync}'s {@link CabinetFxPlan} at
 * {@code startTick + ceil(atMs / 50)}, so the particles burst when the cabinet reels every spectator sees reach
 * the beat. Budgets: ≤ 60 particles per burst (≤ 150 for a Grand), ≤ 3 {@code sendParticles} per event,
 * positional sounds on {@link SoundSource#BLOCKS}.
 *
 * <p>Core-owned shared particles ({@code coin_burst}, {@code confetti}, {@code jackpot_burst}, {@code sparkle},
 * lane J-L1) and the slot sound ids ({@code slots.*}, lane J-L9) are looked up at play time; until they are
 * registered a vanilla stand-in is used, so this class works on the current skeleton.
 */
public final class SlotsFx {
	/** Registry paths (particle JSON at {@code particles/slots/<name>.json}). */
	public static final String EMBER_BURST = "slots/ember_burst";
	public static final String VOID_MOTES = "slots/void_motes";

	private static @Nullable SimpleParticleType emberBurst;
	private static @Nullable SimpleParticleType voidMotes;

	/** Queued events; server thread only. */
	private static final List<Pending> QUEUE = new ArrayList<>();

	private record Pending(ResourceKey<Level> dim, BlockPos pos, Direction facing, CabinetSync sync, long tick, CabinetFxPlan.Event event) {}

	private SlotsFx() {}

	/** Called once from {@code SlotsModule.register}. */
	public static void register(ModuleContext ctx) {
		emberBurst = Registry.register(BuiltInRegistries.PARTICLE_TYPE, ctx.id(EMBER_BURST), FabricParticleTypes.simple());
		voidMotes = Registry.register(BuiltInRegistries.PARTICLE_TYPE, ctx.id(VOID_MOTES), FabricParticleTypes.simple());
		ServerTickEvents.END_SERVER_TICK.register(SlotsFx::tick);
		// singleplayer: the next world is a new server with the same dimension keys; never replay the old world's FX there
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	public static @Nullable SimpleParticleType emberBurst() {
		return emberBurst;
	}

	public static @Nullable SimpleParticleType voidMotes() {
		return voidMotes;
	}

	/**
	 * Schedules the world FX of a freshly published sync. Anything still queued for the same cabinet is dropped
	 * first (a new spin, or a republished step of the same spin, replaces it); events whose tick already passed are
	 * skipped, so a republish never replays a burst.
	 */
	public static void play(ServerLevel level, BlockPos pos, Direction facing, CabinetSync sync) {
		cancel(level, pos);
		long now = level.getGameTime();
		for (CabinetFxPlan.Event e : CabinetFxPlan.events(sync)) {
			long tick = CabinetFxPlan.tickOf(sync, e);
			if (tick < now) continue;
			QUEUE.add(new Pending(level.dimension(), pos.immutable(), facing, sync, tick, e));
		}
	}

	/** Events still queued (GameTests). */
	public static int queued() {
		return QUEUE.size();
	}

	/** Drops every queued event of the cabinet at {@code pos} (machine broken, reset). */
	public static void cancel(ServerLevel level, BlockPos pos) {
		QUEUE.removeIf(p -> p.dim == level.dimension() && p.pos.equals(pos));
	}

	private static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		List<Pending> due = null;
		for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext();) {
			Pending p = it.next();
			ServerLevel level = server.getLevel(p.dim);
			if (level == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= p.tick) {
				it.remove();
				if (due == null) due = new ArrayList<>();
				due.add(p);
			}
		}
		if (due == null) return;
		for (Pending p : due) {
			ServerLevel level = server.getLevel(p.dim);
			if (level == null || !level.isLoaded(p.pos)) continue;
			try {
				run(level, p);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("slots world FX failed", e);
			}
		}
	}

	// ---- one event --------------------------------------------------------------------------------

	private static void run(ServerLevel level, Pending p) {
		Vec3 face = face(p.pos, p.facing, 1.45);
		Vec3 top = face(p.pos, p.facing, 2.05);
		int arg = p.event.arg();
		switch (p.event.kind()) {
			case SPIN -> sound(level, face, "slots.spin_loop", CoreSounds.SLOT_SPIN, 0.3f, 1f);
			case FREE_SPINS -> {
				burst(level, particle("sparkle", ParticleTypes.END_ROD), top, 20, 0.6, 0.15, 0.02);
				sound(level, face, "slots.fs_intro", SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1f);
			}
			case TUMBLE -> burst(level, emberBurst != null ? emberBurst : ParticleTypes.FLAME, face, 8, 0.3, 0.2, 0.02);
			case STICKY -> {
				Vec3 reel = reel(p.pos, p.facing, arg);
				burst(level, voidMotes != null ? voidMotes : ParticleTypes.REVERSE_PORTAL, reel, 6, 0.04, 0.12, 0.01);
			}
			case TIER -> tier(level, face, top, arg);
			case JACKPOT -> jackpot(level, face, top, arg, p.sync);
		}
	}

	private static void tier(ServerLevel level, Vec3 face, Vec3 top, int arg) {
		if (arg == CabinetFxPlan.MAX_WIN_ARG || arg >= WinTier.EPIC.ordinal()) {
			burst(level, coin(), top, 60, 0.4, 0.3, 0.25);
			burst(level, ParticleTypes.FIREWORK, top.add(0, 0.6, 0), 36, 0.9, 0.5, 0.08);
			String stem = arg == CabinetFxPlan.MAX_WIN_ARG ? "slots.max_win" : "slots.epic_win";
			sound(level, face, stem, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
			sound(level, face, null, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6f, 1f);
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
	private static void jackpot(ServerLevel level, Vec3 face, Vec3 top, int tier, CabinetSync sync) {
		int coins = switch (tier) {
			case 1 -> 20;
			case 2 -> 30;
			case 3 -> 40;
			default -> 60;
		};
		burst(level, coin(), top, coins, 0.4, 0.3, 0.25);
		if (tier >= 3) burst(level, ParticleTypes.FIREWORK, top.add(0, 0.8, 0), tier == 4 ? 60 : 36, 1.0, 0.6, 0.1);
		if (tier == 4) burst(level, particle("jackpot_burst", ParticleTypes.TOTEM_OF_UNDYING), top, 30, 0.2, 0.2, 0.5);
		float pitch = tier == 1 ? 1.3f : tier == 2 ? 1.15f : 1f;
		sound(level, face, "jackpot", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, pitch);
		if (tier == 4) sound(level, face, "slots.epic_win", SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.6f, 1f);
		if (tier == 4 && sync.machine() == Machine.END)
			sound(level, face, null, SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 1.6f);
	}

	// ---- helpers ----------------------------------------------------------------------------------

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

	private static void burst(ServerLevel level, SimpleParticleType type, Vec3 at, int count, double spreadXZ, double spreadY, double speed) {
		level.sendParticles(type, at.x, at.y, at.z, Math.min(count, 60), spreadXZ, spreadY, spreadXZ, speed);
	}

	private static void sound(ServerLevel level, Vec3 at, @Nullable String id, @Nullable SoundEvent fallback, float volume, float pitch) {
		SoundEvent e = id == null ? null : CasinoSounds.get(id);
		if (e == null) e = fallback;
		if (e == null) return;
		level.playSound(null, at.x, at.y, at.z, e, SoundSource.BLOCKS, volume, pitch);
	}

	/** Point on the cabinet front at height {@code y} (the reel face is the upper block, slots.md §5.1). */
	static Vec3 face(BlockPos pos, Direction facing, double y) {
		return new Vec3(pos.getX() + 0.5 + facing.getStepX() * 0.55, pos.getY() + y, pos.getZ() + 0.5 + facing.getStepZ() * 0.55);
	}

	/** Centre of reel {@code r} on the face (reel 0 on the viewer's left). */
	static Vec3 reel(BlockPos pos, Direction facing, int r) {
		Direction left = facing.getClockWise();
		double off = (r - 2) * 0.16;
		Vec3 f = face(pos, facing, 1.45);
		return f.add(left.getStepX() * -off, 0, left.getStepZ() * -off);
	}
}
