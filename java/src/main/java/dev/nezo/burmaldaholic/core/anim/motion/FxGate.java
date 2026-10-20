package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The accessibility gate of motion effects (PL §3.8, RO §3.10, DI §9, CS §4.8), pure so it can be tested: reduced
 * motion turns off shakes, particles, streaks/after-images, pops and other decorative motion; flashes need both the
 * flashes setting and no reduced motion. The client facade {@code client.fx.motion.MotionFx} feeds it the player's
 * {@code FxSettings} and a test {@link Recorder} counts every effect that passed, so a test can assert "no shake or
 * particle calls under reduced motion" (PL §8.1-9, RO §9.1-10, DI §10.1-15).
 */
public final class FxGate {
	/** Effect kinds. */
	public enum Kind {
		/** Screen / board shake ({@code GuiShake}). */
		SHAKE,
		/** GUI or world particles (sparks, dust, coins). */
		PARTICLES,
		/** White flashes, flash frames, blinking bulbs. */
		FLASH,
		/** Trails, streaks and after-images. */
		STREAK,
		/** Other decorative motion: pops, wobble, bobbing, slides, loupe, focus zoom. */
		MOTION
	}

	/** Receives every effect that passed the gate (tests). */
	@FunctionalInterface
	public interface Recorder {
		void record(Kind kind, String what);
	}

	private FxGate() {}

	/** True when an effect of {@code kind} may run with the given settings. */
	public static boolean allows(Kind kind, boolean reduceMotion, boolean flashes) {
		if (reduceMotion) return false;
		return kind != Kind.FLASH || flashes;
	}

	/** Gate + record: returns {@link #allows} and reports a passed effect to {@code rec} (may be null). */
	public static boolean pass(Kind kind, String what, boolean reduceMotion, boolean flashes, Recorder rec) {
		boolean ok = allows(kind, reduceMotion, flashes);
		if (ok && rec != null) rec.record(kind, what);
		return ok;
	}

	/** A counting {@link Recorder} for tests (per kind and per {@code what}). Not thread-safe. */
	public static final class Counter implements Recorder {
		private final Map<Kind, Integer> byKind = new EnumMap<>(Kind.class);
		private final Map<String, Integer> byWhat = new HashMap<>();

		@Override
		public void record(Kind kind, String what) {
			byKind.merge(kind, 1, Integer::sum);
			byWhat.merge(what == null ? "" : what, 1, Integer::sum);
		}

		/** Calls of {@code kind}. */
		public int count(Kind kind) {
			return byKind.getOrDefault(kind, 0);
		}

		/** Calls named {@code what}. */
		public int count(String what) {
			return byWhat.getOrDefault(what, 0);
		}

		/** All calls. */
		public int total() {
			int t = 0;
			for (int v : byKind.values()) t += v;
			return t;
		}

		/** Forgets every count. */
		public void clear() {
			byKind.clear();
			byWhat.clear();
		}
	}
}
