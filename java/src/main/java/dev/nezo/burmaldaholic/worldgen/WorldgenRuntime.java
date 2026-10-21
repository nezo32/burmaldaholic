package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.server.MinecraftServer;

/**
 * What world generation may do right now. Structure generation runs on worker threads without a level, so the
 * answer is a <b>snapshot</b> (R-CB M5): {@link #active()} / {@link #standaloneActive()} only read volatile fields.
 * They are recomputed on the server thread — casino mode lives in the server's {@code DataStorage} (a plain
 * HashMap), which worker threads must never touch:
 * <ul>
 *   <li>when the overworld loads (before spawn chunks are prepared), {@link #refresh};</li>
 *   <li>on every casino-mode switch ({@code CasinoMode.onChange}, server thread);</li>
 *   <li>after a config (re)load: at once on the server thread, else marked dirty for the next tick;</li>
 *   <li>every 20 server ticks (anything else, e.g. a test writing config fields directly);</li>
 *   <li>false once the server stopped.</li>
 * </ul>
 * The running server is remembered too (set on {@code SERVER_STARTING}, cleared on stop) for code that needs it.
 */
public final class WorldgenRuntime {
	private static volatile MinecraftServer server;
	private static volatile boolean active;
	private static volatile boolean standalone;
	private static volatile boolean dirty;

	private WorldgenRuntime() {}

	static void setServer(MinecraftServer s) {
		server = s;
		if (s == null) {
			active = false;
			standalone = false;
			dirty = false;
		}
	}

	public static MinecraftServer server() {
		return server;
	}

	/**
	 * Recomputes the snapshot. Server thread only (reads casino mode from the server's saved data); a no-op
	 * without a running server.
	 */
	public static void refresh() {
		MinecraftServer s = server;
		if (s == null) {
			active = false;
			standalone = false;
			return;
		}
		dirty = false;
		boolean a;
		boolean st;
		try {
			a = CasinoConfig.worldgen().enabled && CasinoMode.isEnabled(s);
			st = a && CasinoConfig.worldgen().standalone.enabled;
		} catch (RuntimeException e) {
			a = false; // config / casino mode not ready
			st = false;
		}
		active = a;
		standalone = st;
	}

	/** Config changed (any thread): refresh now on the server thread, else on the next server tick. */
	static void configChanged() {
		MinecraftServer s = server;
		if (s != null && s.isSameThread()) {
			refresh();
		} else {
			dirty = true;
		}
	}

	/** END_SERVER_TICK. */
	static void tick(MinecraftServer s) {
		if (dirty || s.getTickCount() % 20 == 0) {
			refresh();
		}
	}

	/**
	 * New casinos generate only while casino mode is on (GAME_DESIGN §2.1: a dormant mode does nothing) and
	 * {@code worldgen.enabled} (§16). Already generated casinos are never removed. Any thread; a field read.
	 */
	public static boolean active() {
		return active;
	}

	/** {@link #active()} and {@code worldgen.standalone.enabled}: the v0.2 standalone casinos may start. Any thread. */
	public static boolean standaloneActive() {
		return standalone;
	}

	public static double villageChance() {
		return CasinoConfig.worldgen().villageCasino.chance;
	}

	public static double parlorChance() {
		return CasinoConfig.worldgen().piglinParlor.chance;
	}

	public static double loungeChance() {
		return CasinoConfig.worldgen().highRoller.chance;
	}
}
