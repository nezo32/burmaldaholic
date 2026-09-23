package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Calls the chaos module without importing it (java.md §4): chaos publishes JDK/Minecraft-typed
 * functions in Fabric Loader's ObjectShare ({@code dev.nezo.burmaldaholic.chaos.ChaosApi}). Every call
 * is a no-op when chaos is absent.
 */
final class ChaosBridge {
	static final String KEY_TRIGGER = "burmaldaholic:chaos/trigger";
	static final String KEY_JACKPOT = "burmaldaholic:chaos/jackpot";
	static final String SOURCE = "slots";

	private ChaosBridge() {}

	/**
	 * Fires a named chaos event for the player ({@code "<event>@slots"}).
	 *
	 * @return chaos' result id ({@code started, deferred, skipped, cooldown, disabled}) or null if chaos is absent
	 */
	@SuppressWarnings("unchecked")
	static @Nullable String trigger(ServerPlayer player, String eventId) {
		Object fn = FabricLoader.getInstance().getObjectShare().get(KEY_TRIGGER);
		if (!(fn instanceof BiFunction<?, ?, ?>)) {
			return null;
		}
		try {
			Object r = ((BiFunction<ServerPlayer, String, Object>) fn).apply(player, eventId + "@" + SOURCE);
			return r == null ? null : r.toString();
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("chaos trigger {} failed", eventId, e);
			return null;
		}
	}

	/** Jackpot celebration: diamond rain for the winner + chip shower within 16 blocks. False if chaos is absent. */
	@SuppressWarnings("unchecked")
	static boolean jackpot(ServerPlayer winner) {
		Object fn = FabricLoader.getInstance().getObjectShare().get(KEY_JACKPOT);
		if (!(fn instanceof Consumer<?>)) {
			return false;
		}
		try {
			((Consumer<ServerPlayer>) fn).accept(winner);
			return true;
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("chaos jackpot failed", e);
			return false;
		}
	}

	/** The event actually runs (now or when the casino screen closes). */
	static boolean happened(@Nullable String result) {
		return "started".equals(result) || "deferred".equals(result);
	}
}
