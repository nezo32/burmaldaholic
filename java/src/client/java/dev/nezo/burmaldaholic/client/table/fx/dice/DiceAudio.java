package dev.nezo.burmaldaholic.client.table.fx.dice;

import dev.nezo.burmaldaholic.client.fx.motion.SoundCursor;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.SoundMixer;

/**
 * Dice sounds from {@link DiceEvent}s (DI §7, CS §7) on the MK {@link SoundCursor}: a 30 ms merge window (two contacts
 * of one id in the same window play once, louder), ≤ 10 contact sounds per second per tray, REST always passes, and
 * events made late by a hitch (> 120 ms) stay silent. One instance per tray view (screen lane or block entity).
 *
 * <pre>{@code
 * audio.advance(cursor, nowMs, 0, null);   // per frame, after drawing; cursor = new EventCursor(roll.events(), 120)
 * }</pre>
 *
 * <p>Presets ({@link #gui} for screens, {@link #world} for positional block-entity sound):
 * RATTLE {@code dice_cup}; RELEASE (die 0) {@code dice_throw}; FLOOR {@code dice_bounce} (volume by impact, pitch
 * 1.15 / 1.25 per die, −0.04 per later landing); WALL {@code dice_wall}; BUMPER {@code dice_wall} at pitch 1.7;
 * CLACK {@code dice_clack}; TIP {@code dice_tip} (×0.75 volume per roll, pitch rising); TEETER {@code dice_teeter};
 * REST {@code dice_settle}.
 */
public final class DiceAudio {
	/** Hitch rule of DI §7: events more than this late fire silently. */
	public static final double LATE_MS = 120;

	private final SoundCursor cursor;
	private final SoundCursor.Voicing voicing;
	private double volume = 1;
	private double wx, wy, wz;
	private boolean positional;

	private DiceAudio(boolean positional) {
		SoundMixer mixer = new SoundMixer(30, 0.1f, 10);
		mixer.always("dice_settle");
		this.cursor = new SoundCursor(mixer);
		this.positional = positional;
		this.voicing = this::voice;
	}

	/** GUI (personal) dice sounds. */
	public static DiceAudio gui() {
		return new DiceAudio(false);
	}

	/** Positional dice sounds at a block position (0.7 × the GUI volume, CS §7). */
	public static DiceAudio world() {
		DiceAudio a = new DiceAudio(true);
		a.volume = 0.7;
		return a;
	}

	/** A fresh cursor over a roll's events with the dice hitch rule. */
	public static EventCursor cursor(MotionEvent[] events) {
		return new EventCursor(events, LATE_MS);
	}

	/** Sets the world position of a positional instance. */
	public DiceAudio at(double x, double y, double z) {
		this.wx = x;
		this.wy = y;
		this.wz = z;
		return this;
	}

	/** Volume multiplier (e.g. 0.7 for the opponent's cup, DI §7). */
	public DiceAudio volume(double v) {
		this.volume = v;
		return this;
	}

	/** Mutes voicing (the {@code viewingOwnScreen} rule); events still reach {@code visual}. */
	public void muted(boolean muted) {
		cursor.muted(muted);
	}

	/**
	 * Advances {@code events} to {@code nowMs} (the roll clock), voices what fires and flushes the batch.
	 *
	 * @param offsetMs where the roll clock starts on the caller's common clock (merge windows across rolls)
	 * @param visual   receives every event (silent or not) for dust / sparks / flashes; may be null
	 */
	public int advance(EventCursor events, double nowMs, double offsetMs, EventCursor.Listener visual) {
		cursor.advance(events, nowMs, offsetMs, voicing, visual);
		return cursor.flush();
	}

	/** The voicing table (package-visible for tests and game facades). */
	boolean voice(MotionEvent e, SoundCursor.Cue c) {
		float s = e.strength();
		float v = (float) volume;
		String id;
		float vol;
		float pitch;
		switch (e.kind()) {
			case DiceEvent.RATTLE -> {
				id = "dice_cup";
				vol = s;
				pitch = 1.6f + 0.1f * (e.aux() % 4);
			}
			case DiceEvent.RELEASE -> {
				if (e.obj() != 0) return false;
				id = "dice_throw";
				vol = 0.5f;
				pitch = 1.3f;
			}
			case DiceEvent.FLOOR -> {
				id = "dice_bounce";
				vol = 0.25f + 0.65f * s;
				pitch = (e.obj() == 0 ? 1.15f : 1.25f) - 0.04f * (e.aux() - 1);
			}
			case DiceEvent.WALL -> {
				id = "dice_wall";
				vol = 0.4f + 0.5f * s;
				pitch = 1.5f;
			}
			case DiceEvent.BUMPER -> {
				id = "dice_wall";
				vol = 0.4f + 0.5f * s;
				pitch = 1.7f;
			}
			case DiceEvent.CLACK -> {
				id = "dice_clack";
				vol = 0.6f;
				pitch = 1.8f;
			}
			case DiceEvent.TIP -> {
				id = "dice_tip";
				vol = (float) (0.9 * Math.pow(0.75, Math.max(0, e.aux() - 1)));
				pitch = 1.45f + 0.1f * e.aux();
			}
			case DiceEvent.TEETER -> {
				id = "dice_teeter";
				vol = 1;
				pitch = 1.9f;
			}
			case DiceEvent.REST -> {
				id = "dice_settle";
				vol = 1;
				pitch = e.obj() == 0 ? 1.4f : 1.5f;
			}
			default -> {
				return false;
			}
		}
		if (positional) c.at(id, vol * v, pitch, wx, wy, wz);
		else c.set(id, vol * v, pitch);
		return true;
	}
}
