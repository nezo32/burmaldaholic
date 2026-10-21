package dev.nezo.burmaldaholic.client.anim;

import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.core.anim.sync.SmoothClockCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The client's ONE shared animation clock (SY-SPEC §1): a smoothed estimate of the server's level time in ms that is
 * monotonic within an {@link #epoch()}, corrects drift by changing speed (±10 %) and hard-snaps forward only when more
 * than 500 ms behind. {@link AnimClock#levelMs}, {@link MotionClock#levelMs} and every {@code TableClock} read it, so
 * the screen, the in-world renderer and every viewer sample the same time, and time packets / bunched state packets no
 * longer make animations jump.
 *
 * <p>Inputs: the level's game time at END_CLIENT_TICK (covers the server's time packets), table state packets'
 * {@code game_time} ({@link #observe}), {@code level.tickRateManager().millisecondsPerTick()} and the pause state. A new
 * level (rejoin, dimension change) resets the clock and bumps the epoch; no level clears it.
 *
 * <p>Render-thread static singleton. The partial tick is used only before the first observation and never under
 * {@link Test#freezeLevelMs}.
 */
public final class SmoothClock {
	private static final SmoothClockCore CORE = new SmoothClockCore();
	private static ClientLevel lastLevel;
	private static boolean registered;
	/** Server tick offset added to every observation by {@link Test#jump} (ms → applied in ticks with fraction). */
	private static double jumpMs;
	/** Wall nanos of the last core advance (per-frame cache, T-SY m2). */
	private static long lastAdvance = Long.MIN_VALUE;

	private SmoothClock() {}

	/** Registers the tick observation (CoreClientModule; idempotent). */
	public static synchronized void register() {
		if (registered) return;
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
	}

	/** END_CLIENT_TICK: follows the level (reset on a new one, clear on none) and observes its game time. */
	static synchronized void tick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		TimeSource src = Test.source;
		if (level != lastLevel) {
			lastLevel = level;
			if (level == null) {
				CORE.clear();
			} else {
				Long t = src != null ? src.serverTick() : Long.valueOf(level.getGameTime());
				if (t != null) CORE.reset(shift(t), wall());
				else CORE.clear();
			}
			return;
		}
		// T-SY D2: advance (or account the pause) every tick too, so a pause with no reader never counts as running time
		advanceNow(mc, true);
		Long t = src != null ? src.serverTick() : level == null ? null : Long.valueOf(level.getGameTime());
		if (t != null) CORE.observe(shift(t), wall());
	}

	/** Advances the core to now; {@code force} = always (the tick), else skipped within 1 ms of the last advance (m2). */
	private static void advanceNow(Minecraft mc, boolean force) {
		long now = wall();
		if (!force && lastAdvance != Long.MIN_VALUE && now >= lastAdvance && now - lastAdvance < 1_000_000L) return;
		lastAdvance = now;
		double msPerTick = mc.level != null ? mc.level.tickRateManager().millisecondsPerTick() : 50.0;
		TimeSource src = Test.source;
		boolean paused = src != null ? src.paused() : mc.isPaused() || (mc.level != null && !mc.level.tickRateManager().runsNormally());
		CORE.advance(now, msPerTick, paused);
	}

	/** A server game time observed outside the tick (a table state packet's {@code game_time}). */
	public static synchronized void observe(long serverTick) {
		if (serverTick <= 0) return;
		CORE.observe(shift(serverTick), wall());
	}

	/** The shared clock now, in ms of server level time (the partial tick only matters before the first observation). */
	public static synchronized double nowMs(float partialTick) {
		double frozen = Test.frozenLevelMs();
		if (!Double.isNaN(frozen)) return frozen;
		Minecraft mc = Minecraft.getInstance();
		if (!CORE.started()) {
			return mc.level == null ? 0 : (mc.level.getGameTime() + (double) partialTick) * 50.0;
		}
		advanceNow(mc, false);
		return CORE.nowMs();
	}

	/** {@link #nowMs(float)} without a partial tick. */
	public static double nowMs() {
		return nowMs(0f);
	}

	/** Bumped by every reset (rejoin, dimension change, no level): anchors of an older epoch are stale. */
	public static synchronized int epoch() {
		return CORE.epoch();
	}

	/** True once the clock observed the server (else {@link #nowMs} is the raw level time + partial tick). */
	public static synchronized boolean started() {
		return CORE.started();
	}

	/** Rate multiplier and snap flag of the last frame (diagnostics, tests). */
	public static synchronized double lastRate() {
		return CORE.lastRate();
	}

	public static synchronized boolean lastSnap() {
		return CORE.lastSnap();
	}

	/** Wall clock in ms on the same source as the clock (actor anchors: {@code ResultAnchor.tMs(wallMs(), nowMs())}). */
	public static double wallMs() {
		return wall() / 1_000_000.0;
	}

	private static long wall() {
		TimeSource src = Test.source;
		return src != null ? src.wallNanos() : System.nanoTime();
	}

	private static long shift(long tick) {
		return tick + Math.round(jumpMs / 50.0);
	}

	/** What the clock reads in tests (null fields of {@link Test#source} = the live source). */
	public interface TimeSource {
		long wallNanos();

		/** The server tick to observe at this client tick, or null for none. */
		Long serverTick();

		boolean paused();
	}

	/** Client GameTest hooks (null / NaN in production). */
	public static final class Test {
		static volatile TimeSource source;
		private static volatile double frozenLevelMs = Double.NaN;

		private Test() {}

		/** Replaces the wall clock, the observed server tick and the pause state; null restores the live inputs. */
		public static void source(TimeSource s) {
			source = s;
		}

		/** Freezes every client clock reader at level time {@code levelMs} (NaN releases it). */
		public static void freezeLevelMs(double levelMs) {
			frozenLevelMs = levelMs;
		}

		/** Simulates a server time jump of {@code ms}: every later observation is shifted by it. */
		public static void jump(double ms) {
			synchronized (SmoothClock.class) {
				jumpMs += ms;
			}
		}

		/** Restarts the clock from the current level (new epoch) and drops the jump offset. */
		public static void reset() {
			synchronized (SmoothClock.class) {
				jumpMs = 0;
				lastAdvance = Long.MIN_VALUE;
				lastLevel = null;
				CORE.clear();
				tick();
			}
		}

		/** The frozen level time every reader sees ({@link #freezeLevelMs}), or NaN. */
		public static double frozenLevelMs() {
			return frozenLevelMs;
		}
	}
}
