package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.SoundMixer;

/**
 * Motion events → {@link FxSounds} (PHASE2B §3.2; PL §6, RO §6, DI §7 DiceAudio, CS §7). Per frame:
 *
 * <pre>{@code
 * sounds.crowd(ballsInFlight, leadSlot);            // optional lead-object voicing
 * for (Ball b : balls) sounds.advance(b.cursor, now - b.startMs, b.startMs, voicing, b::visual);
 * sounds.flush();                                   // voices the merged batch
 * }</pre>
 *
 * <p>Each {@link EventCursor} fires its events once (the hitch rule makes late ones silent: they reach the visual
 * listener but are never voiced). The {@link Voicing} maps an event to a sound id, volume and pitch (or declines it);
 * the {@link SoundMixer} applies the 30 ms merge window, per-id limits and lead-object voicing; {@link #flush} plays
 * the survivors through {@link FxSounds#play} / {@link FxSounds#playAt} (which add the 20/s overall and 15/s per-id
 * caps and the {@code anim.volume} setting). Allocation-free per frame; one instance per screen / renderer.
 */
public final class SoundCursor implements EventCursor.Listener {
	/** Maps an event to a sound; return false to stay silent. */
	@FunctionalInterface
	public interface Voicing {
		boolean voice(MotionEvent e, Cue out);
	}

	/** The sound of one event (mutable, reused). */
	public static final class Cue {
		public String id;
		public float volume;
		public float pitch;
		/** True for a world sound at (x, y, z). */
		public boolean positional;
		public double x, y, z;

		/** Sets a personal (GUI) sound. */
		public Cue set(String id, float volume, float pitch) {
			this.id = id;
			this.volume = volume;
			this.pitch = pitch;
			this.positional = false;
			return this;
		}

		/** Sets a positional sound. */
		public Cue at(String id, float volume, float pitch, double x, double y, double z) {
			set(id, volume, pitch);
			this.positional = true;
			this.x = x;
			this.y = y;
			this.z = z;
			return this;
		}
	}

	private final SoundMixer mixer;
	private final Cue cue = new Cue();
	private final SoundMixer.Voice out = SoundCursor::emit;
	private Voicing voicing;
	private EventCursor.Listener visual;
	private double offsetMs;
	private boolean muted;

	/** A cursor with the default mixer (30 ms merge, 15/s per id). */
	public SoundCursor() {
		this(new SoundMixer());
	}

	/** A cursor with a configured mixer ({@link SoundMixer#limit}, {@link SoundMixer#always}, lead voicing). */
	public SoundCursor(SoundMixer mixer) {
		this.mixer = mixer;
	}

	/** The mixer (configure limits / always-ids / lead voicing). */
	public SoundMixer mixer() {
		return mixer;
	}

	/** Objects in flight and the lead object for this frame (see {@link SoundMixer#crowd}). */
	public void crowd(int objectsInFlight, int leadObj) {
		mixer.crowd(objectsInFlight, leadObj);
	}

	/** Mutes voicing (e.g. {@code viewingOwnScreen} for a BER): events still reach the visual listener. */
	public void muted(boolean muted) {
		this.muted = muted;
	}

	/**
	 * Advances {@code cursor} to {@code nowMs} (its motion's clock); voiced events are offered to the mixer at
	 * {@code offsetMs + e.tMs} on the common clock.
	 *
	 * @param visual receives every event (silent or not) for visual state; may be null
	 */
	public void advance(EventCursor cursor, double nowMs, double offsetMs, Voicing voicing, EventCursor.Listener visual) {
		this.voicing = voicing;
		this.visual = visual;
		this.offsetMs = offsetMs;
		cursor.advance(nowMs, this);
		this.voicing = null;
		this.visual = null;
	}

	@Override
	public void fire(MotionEvent e, boolean silent) {
		if (visual != null) visual.fire(e, silent);
		if (silent || muted || voicing == null) return;
		cue.id = null;
		if (!voicing.voice(e, cue) || cue.id == null) return;
		double t = offsetMs + e.tMs();
		if (cue.positional) mixer.offerAt(cue.id, cue.volume, cue.pitch, e.obj(), t, cue.x, cue.y, cue.z);
		else mixer.offer(cue.id, cue.volume, cue.pitch, e.obj(), t);
	}

	/** Plays the merged batch of this frame; returns the number of sounds voiced. */
	public int flush() {
		return mixer.flush(out);
	}

	private static void emit(String id, float volume, float pitch, int obj, boolean positional, double x, double y, double z) {
		if (positional) FxSounds.playAt(id, x, y, z, volume, pitch);
		else FxSounds.play(id, volume, pitch);
	}
}
