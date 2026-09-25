package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;

/** Client copy of the local player's VIP / contracts data ({@link VipSyncPayload}). Read-only for screens and the HUD. */
public final class VipClientState {
	private static volatile VipSyncPayload data = VipSyncPayload.EMPTY;
	private static volatile boolean received;
	private static volatile long receivedAt;

	private VipClientState() {}

	public static VipSyncPayload data() {
		return data;
	}

	public static boolean received() {
		return received;
	}

	/** Ticks until the next day's contracts, extrapolated from the last sync. */
	public static long resetTicks() {
		return Math.max(0, data.resetTicks() - (ClientCasinoState.clientTicks() - receivedAt));
	}

	static void accept(VipSyncPayload next) {
		data = next.withoutOpen();
		received = true;
		receivedAt = ClientCasinoState.clientTicks();
	}

	static void reset() {
		data = VipSyncPayload.EMPTY;
		received = false;
	}
}
