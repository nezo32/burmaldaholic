package dev.nezo.burmaldaholic.client;

import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;

/**
 * Client-side copy of the local player's casino status, synced from the server
 * ({@link PlayerStatusPayload}). Read it from HUD segments and screens; never write it.
 */
public final class ClientCasinoState {
	private static volatile PlayerStatusPayload status = new PlayerStatusPayload(0, 0, 0, 0, 0, false, 0);
	private static volatile boolean received;
	private static volatile long lastDelta;
	private static volatile long lastDeltaTick = Long.MIN_VALUE / 2;
	private static volatile long clientTicks;

	private ClientCasinoState() {}

	public static PlayerStatusPayload status() {
		return status;
	}

	/** True once the server sent a status (i.e. the mod is present on the server and casino mode is on). */
	public static boolean hasStatus() {
		return received;
	}

	public static long balance() {
		return status.balance();
	}

	public static int streak() {
		return status.streak();
	}

	public static int vipTier() {
		return status.vipTier();
	}

	public static long goldenHourTicks() {
		return status.goldenHourTicks();
	}

	/** Last balance change (for the floating "+120" animation) and how many client ticks ago it happened. */
	public static long lastDelta() {
		return lastDelta;
	}

	public static long ticksSinceDelta() {
		return clientTicks - lastDeltaTick;
	}

	public static long clientTicks() {
		return clientTicks;
	}

	static void tick() {
		clientTicks++;
	}

	static void accept(PlayerStatusPayload next) {
		if (received && next.balance() != status.balance()) {
			lastDelta = next.balance() - status.balance();
			lastDeltaTick = clientTicks;
		}
		status = next;
		received = true;
	}

	static void reset() {
		status = new PlayerStatusPayload(0, 0, 0, 0, 0, false, 0);
		received = false;
		lastDelta = 0;
	}
}
