package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.RateBudget;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
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

	/** Per-id budget under the overall 20/s cap (a busy id never starves the others). */
	private static synchronized boolean allow(String id) {
		return BUDGET.tryAcquire(id, Util.getMillis());
	}
}
