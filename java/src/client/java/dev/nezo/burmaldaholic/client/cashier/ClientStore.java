package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.core.store.net.StoreActionPayload;
import dev.nezo.burmaldaholic.core.store.net.StoreSyncPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The last store sync (EC-SPEC §5) and the store request sender. */
public final class ClientStore {
	private static List<StoreSyncPayload.OfferView> offers = List.of();
	private static long balance;
	private static int tier;
	private static int version;

	private ClientStore() {}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StoreSyncPayload.TYPE, (payload, context) -> {
			offers = payload.offers();
			balance = payload.balance();
			tier = payload.tier();
			version++;
			CounterShell.current().ifPresent(CounterShell::relayout);
		});
	}

	public static List<StoreSyncPayload.OfferView> offers() {
		return offers;
	}

	public static long balance() {
		return balance;
	}

	public static int tier() {
		return tier;
	}

	/** Increases with every sync (tests wait on it). */
	public static int version() {
		return version;
	}

	public static void send(StoreActionPayload p) {
		ClientPlayNetworking.send(p);
	}
}
