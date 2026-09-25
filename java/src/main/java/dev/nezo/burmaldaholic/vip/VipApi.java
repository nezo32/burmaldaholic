package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.vip.logic.ContractRules;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public API of the vip module for other feature modules. They must not import this package
 * (java.md §4); they look it up through Fabric Loader's {@link ObjectShare}, typed with JDK/Minecraft
 * types only (same pattern as {@code ChaosApi}):
 *
 * <pre>
 * // "burmaldaholic:vip/contract" → BiConsumer&lt;ServerPlayer, String&gt;: "&lt;contractId&gt;" or "&lt;contractId&gt;@&lt;amount&gt;".
 * //   Progress for contracts vip cannot observe itself. Roulette: call "roulette_red" for each winning bet on red;
 * //   the first report also adds roulette_red to the contract pool (it is left out until a game reports it).
 * &#64;SuppressWarnings("unchecked")
 * var contract = (BiConsumer&lt;ServerPlayer, String&gt;) FabricLoader.getInstance().getObjectShare().get("burmaldaholic:vip/contract");
 * if (contract != null) contract.accept(player, "roulette_red");
 *
 * // "burmaldaholic:vip/wagered" → BiFunction&lt;MinecraftServer, UUID, Long&gt;   lifetime chips wagered
 * // (the tier itself: CoreServices.vip().tier(server, uuid))
 * </pre>
 */
public final class VipApi {
	public static final String KEY_CONTRACT = "burmaldaholic:vip/contract";
	public static final String KEY_WAGERED = "burmaldaholic:vip/wagered";

	private VipApi() {}

	/** Reports contract progress ({@code "id"} or {@code "id@amount"}). Unknown ids are ignored. */
	public static void contract(ServerPlayer player, String spec) {
		if (player == null || spec == null) {
			return;
		}
		String id = spec;
		long amount = 1;
		int at = spec.indexOf('@');
		if (at >= 0) {
			id = spec.substring(0, at);
			try {
				amount = Long.parseLong(spec.substring(at + 1).trim());
			} catch (NumberFormatException e) {
				return;
			}
		}
		if (!ContractRules.isId(id)) {
			return;
		}
		Contracts.registerSource(id);
		Contracts.progress(player, id, amount);
	}

	static void publish() {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		share.put(KEY_CONTRACT, (BiConsumer<ServerPlayer, String>) VipApi::contract);
		share.put(KEY_WAGERED, (BiFunction<MinecraftServer, UUID, Long>) VipService::wagered);
	}
}
