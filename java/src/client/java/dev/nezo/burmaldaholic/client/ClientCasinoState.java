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
	/** {@link #holdBalanceDelta}: wall-clock end of the hold and the balance shown meanwhile. */
	private static volatile long holdUntilMs;
	private static volatile long heldBalance;
	private static volatile boolean releasePending;

	private ClientCasinoState() {}

	/**
	 * F6 / global.md J6: keep showing the current balance (and no "+N" floater) for {@code ms} while a presentation
	 * reveals the result; the delta appears when the hold ends. Holds extend, never shorten. ({@code ClientFx.balanceHold})
	 */
	public static void holdBalanceDelta(int ms) {
		long now = net.minecraft.util.Util.getMillis();
		if (!holding(now)) heldBalance = status.balance();
		holdUntilMs = Math.max(holdUntilMs, now + Math.max(0, ms));
		releasePending = true;
	}

	private static boolean holding(long now) {
		return now < holdUntilMs;
	}

	/** The balance the HUD shows: the held value during {@link #holdBalanceDelta}, else the synced one. */
	public static long shownBalance() {
		return holding(net.minecraft.util.Util.getMillis()) ? heldBalance : status.balance();
	}

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
		if (releasePending && !holding(net.minecraft.util.Util.getMillis())) {
			releasePending = false;
			long d = status.balance() - heldBalance;
			if (d != 0) {
				lastDelta = d;
				lastDeltaTick = clientTicks;
			}
		}
	}

	static void accept(PlayerStatusPayload next) {
		if (received && next.balance() != status.balance() && !holding(net.minecraft.util.Util.getMillis())) {
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
		holdUntilMs = 0;
		releasePending = false;
	}
}
