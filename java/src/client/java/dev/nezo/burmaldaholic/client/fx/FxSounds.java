package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.RateBudget;
import dev.nezo.burmaldaholic.core.anim.motion.LoopBudget;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

/**
 * Client playback of the casino sound catalog (global.md §2.7; docs/architecture/animation.md §2.4). Every sound
 * is multiplied by {@code anim.volume}, uses the catalog's source (PLAYERS personal, BLOCKS tables, AMBIENT;
 * never MASTER) and is limited to 20 per second. MUST-phase "+" composites: the catalog id plays its primary
 * sample from {@code sounds.json} and this class adds the vanilla layers in the same tick (or a few ticks later
 * for arpeggios / double knocks), so resource packs can still replace the primary id.
 */
public final class FxSounds {
	/** A vanilla layer of a composite: {@code event} (vanilla id), absolute pitch/volume, delay in ticks. */
	private record Layer(String event, float pitch, float volume, int delayTicks) {}

	private static final Map<String, List<Layer>> LAYERS = Map.ofEntries(
		Map.entry("win_small", List.of(new Layer("block.note_block.chime", 1.6f, 0.5f, 0))),
		Map.entry("win", List.of(new Layer("block.note_block.chime", 1.6f, 0.5f, 0))),
		Map.entry("win_nice", List.of(new Layer("block.note_block.chime", 1.26f, 1f, 2), new Layer("block.note_block.chime", 1.5f, 1f, 3),
			new Layer("entity.experience_orb.pickup", 1f, 0.6f, 3))),
		Map.entry("win_big", List.of(new Layer("block.amethyst_block.resonate", 1.2f, 1f, 0))),
		Map.entry("win_mega", List.of(new Layer("block.bell.use", 1.5f, 0.4f, 0))),
		Map.entry("jackpot", List.of(new Layer("entity.firework_rocket.twinkle", 1f, 1f, 0), new Layer("entity.firework_rocket.twinkle", 1.1f, 1f, 4),
			new Layer("block.bell.use", 1f, 1f, 0))),
		Map.entry("lose", List.of(new Layer("block.wool.fall", 1f, 0.4f, 0))),
		Map.entry("toast", List.of(new Layer("block.amethyst_block.chime", 1f, 1f, 0))),
		Map.entry("collector_arrive", List.of(new Layer("entity.ravager.roar", 1.6f, 0.25f, 0))),
		Map.entry("coin_land", List.of(new Layer("entity.experience_orb.pickup", 0.9f, 1f, 0))),
		Map.entry("attract_chime", List.of(new Layer("block.amethyst_block.chime", 1f, 0.15f, 0))),
		Map.entry("card_deal", List.of(new Layer("block.wool.hit", 1.6f, 0.3f, 0))),
		Map.entry("card_squeeze", List.of(new Layer("block.scaffolding.step", 1.4f, 0.2f, 0))),
		Map.entry("card_sting", List.of(new Layer("block.note_block.chime", 1.26f, 1f, 2), new Layer("block.note_block.chime", 1.5f, 1f, 3),
			new Layer("block.amethyst_block.chime", 1f, 0.4f, 0))),
		Map.entry("chip_push", List.of(new Layer("item.bundle.drop_contents", 1f, 0.5f, 0))),
		Map.entry("pot_win", List.of(new Layer("entity.experience_orb.pickup", 0.8f, 0.4f, 0))),
		Map.entry("table_knock", List.of(new Layer("block.wood.hit", 0.8f, 0.7f, 2))),
		Map.entry("wheel_stop", List.of(new Layer("block.wooden_button.click_off", 1f, 1f, 0))),
		Map.entry("burn", List.of(new Layer("entity.generic.explode", 1f, 0.3f, 20))));

	/** ≤ 20 casino sounds per second per player (docs/architecture/animation.md §2.4). */
	static final int MAX_PER_SECOND = 20;
	/** One id (e.g. the count-up tick) may use at most this many, so fanfares and toasts always get through. */
	static final int MAX_PER_ID_PER_SECOND = 15;

	private static final RandomSource RANDOM = RandomSource.create();
	private static final RateBudget BUDGET = new RateBudget(MAX_PER_ID_PER_SECOND, MAX_PER_SECOND, 1000);

	private FxSounds() {}

	/** Personal (non-positional) playback of catalog id {@code id} at volume 1. */
	public static void play(String id, float pitch) {
		play(id, 1f, pitch);
	}

	/** Personal (non-positional) playback of catalog id {@code id}; layers of composites are added. */
	public static void play(String id, float volume, float pitch) {
		SoundEvent e = CasinoSounds.get(id);
		if (e == null) return;
		SoundSource src = CasinoSounds.sourceOf(id);
		if (!allow(id) || !emit(e.location(), src, volume, pitch, 0, true, 0, 0, 0)) return;
		for (Layer l : LAYERS.getOrDefault(id, List.of()))
			emit(Identifier.withDefaultNamespace(l.event), src, volume * l.volume, l.pitch, l.delayTicks, true, 0, 0, 0);
	}

	/** Positional playback (tables, cabinets, nearby wins) at a world position. */
	public static void playAt(String id, double x, double y, double z, float volume, float pitch) {
		SoundEvent e = CasinoSounds.get(id);
		if (e == null) return;
		SoundSource src = CasinoSounds.sourceOf(id);
		if (!allow(id) || !emit(e.location(), src, volume, pitch, 0, false, x, y, z)) return;
		for (Layer l : LAYERS.getOrDefault(id, List.of()))
			emit(Identifier.withDefaultNamespace(l.event), src, volume * l.volume, l.pitch, l.delayTicks, false, x, y, z);
	}

	/** Plays a burmaldaholic sound that is not in the catalog (legacy ids), personal. */
	public static void playLegacy(String path, float volume, float pitch) {
		SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Burmaldaholic.id(path));
		if (e != null && allow(path)) emit(e.location(), SoundSource.PLAYERS, volume, pitch, 0, true, 0, 0, 0);
	}

	/**
	 * Additive hook (lane J-L3): plays any sound id (vanilla layers of world / meta FX, the Golden Hour loop voices) with
	 * an explicit source, × {@code anim.volume}, under the same 20/s budget; {@code at} null = personal (non-positional).
	 */
	public static void playRaw(Identifier sound, SoundSource source, float volume, float pitch, int delayTicks, net.minecraft.world.phys.Vec3 at) {
		if (sound == null || source == SoundSource.MASTER || !allow(sound.getPath())) return;
		if (at == null) emit(sound, source, volume, pitch, delayTicks, true, 0, 0, 0);
		else emit(sound, source, volume, pitch, delayTicks, false, at.x, at.y, at.z);
	}

	private static boolean emit(Identifier sound, SoundSource source, float volume, float pitch, int delayTicks, boolean relative, double x,
			double y, double z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getSoundManager() == null) return false;
		float v = volume * FxSettings.volume();
		if (v <= 0.001f) return false;
		SoundInstance inst = relative
			? new SimpleSoundInstance(sound, source, v, pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true)
			: new SimpleSoundInstance(sound, source, v, pitch, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
		if (delayTicks > 0) mc.getSoundManager().playDelayed(inst, delayTicks);
		else mc.getSoundManager().play(inst);
		return true;
	}

	// ---- loops (PHASE2B §3.2; RO §6 roulette_roll) --------------------------------------------------------------

	/**
	 * At most this many {@link #loop} sounds play at once on the WHOLE client (all screens and renderers share the
	 * budget). WORLD loops hold at most {@link #MAX_WORLD_LOOPS} of them and give their slot up to a SCREEN loop.
	 */
	public static final int MAX_LOOPS = 2;

	/** Slots WORLD (in-world renderer) loops may hold. */
	public static final int MAX_WORLD_LOOPS = 1;

	/** Who owns a loop; decides who gets a slot when the {@link #MAX_LOOPS} budget is full. */
	public enum LoopPriority {
		/**
		 * The player's open table screen. Takes a free slot, else evicts the WORLD loop; stops by itself when that screen
		 * closes (the screen open at {@link #loop} time must still be open).
		 */
		SCREEN,
		/** An in-world renderer (nearest table only). At most {@link #MAX_WORLD_LOOPS} slot; evicted by a SCREEN loop. */
		WORLD
	}

	private static final LoopBudget<Loop> LOOPS = new LoopBudget<>(MAX_LOOPS, MAX_WORLD_LOOPS);

	/** A running loop started by {@link #loop}. */
	public interface Handle {
		/** A handle of a loop that did not start (unknown id, loop budget spent, volume 0, no sound manager). */
		Handle NONE = new Handle() {
			@Override
			public boolean active() {
				return false;
			}

			@Override
			public void release() {}
		};

		/**
		 * True while the sound engine plays the loop. It turns false when the loop was released, its {@code alive}
		 * turned false, its screen closed, a SCREEN loop evicted it (WORLD), or the engine dropped it (disconnect,
		 * {@code /stopsound}, sound reload). A caller that still wants the sound requests a new loop.
		 */
		boolean active();

		/** Stops the loop now and frees its slot (idempotent). */
		void release();
	}

	/** {@link #loop(String, DoubleSupplier, DoubleSupplier, BooleanSupplier, LoopPriority)} as a SCREEN loop. */
	public static Handle loop(String id, DoubleSupplier pitch, DoubleSupplier volume, BooleanSupplier alive) {
		return loop(id, pitch, volume, alive, LoopPriority.SCREEN);
	}

	/**
	 * Starts a personal looping sound whose pitch and volume are read every tick (RO §6: {@code roulette_roll},
	 * pitch {@code 1.4 + 0.6·ω/ω0}). The volume is multiplied by {@code anim.volume} every tick
	 * ({@link FxSettings#volume()}); pitch is clamped to vanilla's 0.5–2.
	 *
	 * <p><b>Budget.</b> At most {@link #MAX_LOOPS} loops play at once CLIENT-WIDE. A SCREEN loop takes a free slot or
	 * evicts the WORLD loop; a WORLD loop takes a slot only when fewer than {@link #MAX_WORLD_LOOPS} WORLD loops play
	 * and a slot is free. Otherwise (and at {@code anim.volume} 0) the call returns {@link Handle#NONE}. A slot is
	 * freed as soon as its loop is released, stops, or is dropped by the sound engine.
	 *
	 * <p><b>Lifetime.</b> The loop stops by itself when {@code alive} turns false, when {@link Handle#release} is
	 * called, and (SCREEN) when the screen that was open at this call closes. {@code alive} should say "the sound is
	 * still wanted" (e.g. the ball is still on the track). <b>Re-request:</b> poll {@link Handle#active()} (e.g. each
	 * client tick); when it is false while the sound is still wanted, call {@code loop} again (eviction, a reload or a
	 * volume change can end a loop at any time).
	 *
	 * @param id       catalog sound id
	 * @param pitch    pitch supplier (read on the client tick)
	 * @param volume   volume supplier, 0–1 before {@code anim.volume}
	 * @param alive    keeps the loop playing while true
	 * @param priority SCREEN (table screen) or WORLD (in-world renderer)
	 */
	public static Handle loop(String id, DoubleSupplier pitch, DoubleSupplier volume, BooleanSupplier alive, LoopPriority priority) {
		SoundEvent e = CasinoSounds.get(id);
		Minecraft mc = Minecraft.getInstance();
		if (e == null || mc == null || mc.getSoundManager() == null || FxSettings.volume() <= 0 || !alive.getAsBoolean()) return Handle.NONE;
		boolean world = priority == LoopPriority.WORLD;
		Object owner = world ? null : mc.gui.screen();
		Loop l = new Loop(e, CasinoSounds.sourceOf(id), pitch, volume, alive, owner);
		if (!LOOPS.tryAdd(l, world, FxSounds::gone, Loop::release)) return Handle.NONE;
		mc.getSoundManager().play(l);
		if (gone(l)) {
			LOOPS.remove(l); // the engine refused it (no sound file, engine not loaded)
			return Handle.NONE;
		}
		return l;
	}

	/** Loops currently counted against {@link #MAX_LOOPS} (ended / engine-dropped loops are pruned first). */
	public static int activeLoops() {
		return LOOPS.size(FxSounds::gone);
	}

	/** A loop no longer holds its slot when released, stopped, or not played by the engine any more. */
	private static boolean gone(Loop l) {
		if (l.stopRequested || l.isStopped()) return true;
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.getSoundManager() == null || !mc.getSoundManager().isActive(l);
	}

	private static final class Loop extends AbstractTickableSoundInstance implements Handle {
		private final DoubleSupplier pitchFn;
		private final DoubleSupplier volumeFn;
		private final BooleanSupplier alive;
		/** The screen that must stay open (SCREEN loops started with a screen open); null = no screen check. */
		private final Object ownerScreen;
		volatile boolean stopRequested;

		Loop(SoundEvent event, SoundSource source, DoubleSupplier pitch, DoubleSupplier volume, BooleanSupplier alive, Object ownerScreen) {
			super(event, source == SoundSource.MASTER ? SoundSource.PLAYERS : source, SoundInstance.createUnseededRandom());
			this.pitchFn = pitch;
			this.volumeFn = volume;
			this.alive = alive;
			this.ownerScreen = ownerScreen;
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0;
			this.y = 0;
			this.z = 0;
			apply();
		}

		private void apply() {
			double v = volumeFn.getAsDouble();
			double p = pitchFn.getAsDouble();
			this.volume = (float) Math.max(0.001, Math.min(1, Double.isFinite(v) ? v : 0) * FxSettings.volume());
			this.pitch = (float) Math.max(0.5, Math.min(2.0, Double.isFinite(p) ? p : 1));
		}

		@Override
		public void tick() {
			if (stopRequested || !alive.getAsBoolean() || ownerScreen != null && Minecraft.getInstance().gui.screen() != ownerScreen) {
				stop();
				LOOPS.remove(this);
				return;
			}
			apply();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public boolean active() {
			return !gone(this);
		}

		@Override
		public void release() {
			if (stopRequested) return;
			stopRequested = true;
			LOOPS.remove(this);
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getSoundManager() != null) mc.getSoundManager().stop(this);
		}
	}

	/** Per-id budget under the overall 20/s cap (a busy id never starves the others). */
	private static synchronized boolean allow(String id) {
		return BUDGET.tryAcquire(id, Util.getMillis());
	}
}
