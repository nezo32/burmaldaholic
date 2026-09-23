package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import net.minecraft.server.MinecraftServer;

/**
 * What world generation may do right now. Structure generation runs on worker threads without a
 * level, so the running server is remembered here (set on {@code SERVER_STARTING}, before any chunk
 * is generated; cleared on stop).
 */
public final class WorldgenRuntime {
	private static volatile MinecraftServer server;

	private WorldgenRuntime() {}

	static void setServer(MinecraftServer s) {
		server = s;
	}

	public static MinecraftServer server() {
		return server;
	}

	/**
	 * New casinos generate only while casino mode is on (GAME_DESIGN §2.1: a dormant mode does
	 * nothing) and {@code worldgen.enabled} (§16). Already generated casinos are never removed.
	 */
	public static boolean active() {
		MinecraftServer s = server;
		if (s == null) {
			return false;
		}
		try {
			return CasinoConfig.worldgen().enabled && CasinoMode.isEnabled(s);
		} catch (RuntimeException e) {
			return false; // config/game rules not ready (should not happen after SERVER_STARTING)
		}
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
