package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Calls the chaos module without importing it (java.md §4) through the function chaos publishes in
 * Fabric Loader's ObjectShare ({@code burmaldaholic:chaos/trigger}); a no-op when chaos is absent.
 */
final class UthChaos {
	static final String KEY_TRIGGER = "burmaldaholic:chaos/trigger";

	private UthChaos() {}

	/** §21.7: a royal flush paying the Blind → diamond rain for that player (chaos' safety rules apply). */
	@SuppressWarnings("unchecked")
	static void diamondRain(ServerPlayer player) {
		Object fn = FabricLoader.getInstance().getObjectShare().get(KEY_TRIGGER);
		if (!(fn instanceof BiFunction<?, ?, ?>)) {
			return;
		}
		try {
			((BiFunction<ServerPlayer, String, Object>) fn).apply(player, "diamond_rain@jackpot");
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("chaos diamond_rain failed", e);
		}
	}
}
