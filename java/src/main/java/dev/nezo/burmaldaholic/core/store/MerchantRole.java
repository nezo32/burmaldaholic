package dev.nezo.burmaldaholic.core.store;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The casino merchant NPC role (EC-SPEC §3): using the merchant opens the counter with the Store (and Comps) tabs.
 * The NPC entity belongs to package DL, which exposes {@code HouseNpcRoles.register(String, BiConsumer<ServerPlayer, Entity>)}.
 * Until that lands this adapter finds it reflectively (no compile dependency); DL or the merge can also call
 * {@link #install} directly. {@code /casino store} opens the same counter for tests.
 */
public final class MerchantRole {
	public static final String ROLE = "merchant";
	/** Where DL may put the role registry (first found wins). */
	private static final List<String> CANDIDATES = List.of(
		"dev.nezo.burmaldaholic.worldgen.npc.HouseNpcRoles",
		"dev.nezo.burmaldaholic.worldgen.HouseNpcRoles",
		"dev.nezo.burmaldaholic.core.npc.HouseNpcRoles",
		"dev.nezo.burmaldaholic.worldgen.logic.HouseNpcRoles");
	private static boolean installed;

	private MerchantRole() {}

	/** The use handler: opens the merchant counter for {@code player}. */
	public static void open(ServerPlayer player, Entity merchant) {
		CounterSessions.openAndSend(player, CounterSource.merchant(merchant.getId()));
	}

	/** Registers the role through {@code registrar} (e.g. {@code MerchantRole.install(HouseNpcRoles::register)}). */
	public static void install(BiConsumer<String, BiConsumer<ServerPlayer, Entity>> registrar) {
		registrar.accept(ROLE, MerchantRole::open);
		installed = true;
	}

	public static boolean installed() {
		return installed;
	}

	/** Looks for DL's role registry; logs (debug) and waits for {@link #install} when it is not there yet. */
	static void register() {
		for (String name : CANDIDATES) {
			try {
				Class<?> c = Class.forName(name);
				Method m = c.getMethod("register", String.class, BiConsumer.class);
				install((role, handler) -> {
					try {
						m.invoke(null, role, handler);
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
				});
				return;
			} catch (ClassNotFoundException | NoSuchMethodException e) {
				// not this one
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.warn("[store] merchant role registration via {} failed", name, e);
			}
		}
		Burmaldaholic.LOGGER.debug("[store] HouseNpcRoles not present yet; merchant role waits for MerchantRole.install");
	}
}
