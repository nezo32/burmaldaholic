package dev.nezo.burmaldaholic.core.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/**
 * "The player did something on purpose" (EC-SPEC §4.4 activity gate): table actions sent by the client (never
 * server-driven autoplay rounds, never refresh / sync requests). Listeners: the comps daily-bonus gate. Server thread.
 */
public final class PlayerActivity {
	private static final List<Consumer<ServerPlayer>> LISTENERS = new CopyOnWriteArrayList<>();

	private PlayerActivity() {}

	public static void register(Consumer<ServerPlayer> listener) {
		LISTENERS.add(listener);
	}

	/** A deliberate action of {@code player} ({@code action} = its name; refresh / sync are not activity). */
	public static void touch(ServerPlayer player, String action) {
		if (action == null || action.equals("refresh") || action.equals("sync") || action.startsWith("sync_")) {
			return;
		}
		for (Consumer<ServerPlayer> l : LISTENERS) {
			l.accept(player);
		}
	}
}
