package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Slot sounds on the player's screen (slots.md §8, SLOTS.md §10.7; JS4). Ids are {@code burmaldaholic:slots.*} from
 * {@code src/main/sounds/slots/sounds.json}; the MUST phase uses vanilla composites where the second ("+") layer is
 * played here in the same tick. Every sound is scaled by {@code anim.volume} and kept under 20 per second; loops
 * (spin whirr, anticipation ramp, free-spin music) are {@link Loop} instances that fade in and out. Loss is silent.
 */
public final class SlotSounds {
	private static final SoundPlan.Budget BUDGET = new SoundPlan.Budget(SoundPlan.MAX_PER_SECOND);

	private SlotSounds() {}

	/** The event of a slots id (registered by the module when it is, else resolved by id from sounds.json). */
	public static SoundEvent event(String id) {
		SoundEvent registered = CasinoSounds.get(id);
		return registered != null ? registered : SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("burmaldaholic", id));
	}

	private static boolean allowed() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && FxSettings.volume() > 0 && BUDGET.tryFire(net.minecraft.util.Util.getMillis());
	}

	public static void play(String id, float pitch, float volume) {
		if (!allowed()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(id), pitch, volume * FxSettings.volume()));
	}

	public static void vanilla(SoundEvent sound, float pitch, float volume) {
		if (!allowed()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume * FxSettings.volume()));
	}

	public static void vanilla(Holder<SoundEvent> sound, float pitch, float volume) {
		vanilla(sound.value(), pitch, volume);
	}

	// ---- composites (slots.md §8) -------------------------------------------------------------------------------

	public static void reelStop(int reel, float volume) {
		play("slots.reel_stop", 1f, volume);
		vanilla(SoundEvents.NOTE_BLOCK_PLING, SoundPlan.reelStop(reel), 0.35f * volume);
	}

	public static void scatterLand(int nth) {
		play("slots.scatter_land", 1f, 1f);
		vanilla(SoundEvents.NOTE_BLOCK_BELL, SoundPlan.scatter(nth), 0.6f);
	}

	public static void bonusLand(Machine m, float volume) {
		switch (m) {
			case NETHER -> vanilla(SoundEvents.CHAIN_PLACE, 1.8f, volume);
			case END -> vanilla(SoundEvents.END_PORTAL_FRAME_FILL, 1.2f, volume);
			default -> play("slots.bonus_land", 1f, volume);
		}
	}

	public static void winSmall(float pitch, float volume) {
		play("slots.win_small", pitch, volume);
		vanilla(SoundEvents.NOTE_BLOCK_CHIME, 1.5f * pitch, 0.4f * volume);
	}

	/** C–E–G arpeggio (the two later notes are delayed by the caller's cue list, 80 ms apart). */
	public static void winNice() {
		play("slots.win_nice", 1f, 1f);
		vanilla(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
	}

	public static void niceArpeggio(int note) {
		vanilla(SoundEvents.NOTE_BLOCK_CHIME, note == 1 ? 1.26f : 1.5f, 0.8f);
	}

	public static void bigWin() {
		play("slots.big_win", 1f, 1f);
		vanilla(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
	}

	public static void megaWin() {
		play("slots.mega_win", 1f, 1f);
		vanilla(SoundEvents.BELL_BLOCK, 1.5f, 0.4f);
	}

	public static void epicWin() {
		play("slots.epic_win", 1f, 1f);
		vanilla(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 0.5f);
		vanilla(SoundEvents.BELL_BLOCK, 1f, 0.6f);
	}

	public static void maxWin() {
		play("slots.max_win", 1f, 1f);
		vanilla(SoundEvents.ANVIL_LAND, 1.2f, 0.3f);
	}

	public static void fsIntro(float pitch, float volume) {
		play("slots.fs_intro", pitch, volume);
		vanilla(SoundEvents.AMETHYST_BLOCK_RESONATE, pitch, volume);
	}

	public static void fsOutro() {
		play("slots.fs_outro", 1f, 1f);
		vanilla(SoundEvents.NOTE_BLOCK_CHIME, 1f, 0.8f);
	}

	public static void wildStick() {
		play("slots.wild_stick", 1f, 1f);
		vanilla(SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 0.8f);
	}

	public static void tumble() {
		play("slots.tumble", 1f, 1f);
		vanilla(SoundEvents.FIRECHARGE_USE, 1f, 0.3f);
	}

	public static void multUp(int step) {
		play("slots.mult_up", SoundPlan.multUp(step), 1f);
		vanilla(SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1f, 0.8f);
	}

	public static void coinLand(float pitch) {
		play("slots.coin_land", pitch, 1f);
		vanilla(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 0.4f);
	}

	public static void jackpot(float pitch, float volume) {
		SoundEvent core = CasinoSounds.get("jackpot");
		vanilla(core != null ? core : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, pitch, volume);
	}

	public static void click() {
		vanilla(SoundEvents.UI_BUTTON_CLICK, 1f, 0.5f);
	}

	public static void deny() {
		SoundEvent core = CasinoSounds.get("ui_deny");
		vanilla(core != null ? core : SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
	}

	// ---- loops ------------------------------------------------------------------------------------------------

	/**
	 * A looping, tickable UI sound whose volume and pitch the screen drives every frame ({@link #target}); it fades out
	 * and stops itself after {@link #release}.
	 */
	public static final class Loop extends AbstractTickableSoundInstance {
		private float targetVolume;
		private float targetPitch;
		private final float fadePerTick;
		private boolean released;

		private Loop(SoundEvent event, float fadePerTick) {
			super(event, SoundSource.UI, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.001f;
			this.pitch = 1f;
			this.fadePerTick = fadePerTick;
		}

		/** Starts a loop; {@code fadeMs} = fade in / out time. */
		public static Loop start(String id, float volume, float pitch, int fadeMs) {
			Loop l = new Loop(event(id), Math.max(0.02f, 50f / Math.max(50, fadeMs)));
			l.targetVolume = volume;
			l.targetPitch = pitch;
			if (FxSettings.volume() > 0) Minecraft.getInstance().getSoundManager().play(l);
			return l;
		}

		public void target(float volume, float pitch) {
			this.targetVolume = volume;
			this.targetPitch = pitch;
		}

		public void release() {
			released = true;
		}

		public boolean released() {
			return released;
		}

		@Override
		public void tick() {
			float goal = released ? 0 : targetVolume * FxSettings.volume();
			float step = fadePerTick * Math.max(0.05f, targetVolume);
			if (volume < goal) volume = Math.min(goal, volume + step);
			else volume = Math.max(goal, volume - step);
			pitch += (targetPitch - pitch) * 0.5f;
			if (released && volume <= 0.001f) stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
