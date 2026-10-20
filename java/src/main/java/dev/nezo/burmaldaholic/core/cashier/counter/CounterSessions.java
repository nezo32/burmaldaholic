package dev.nezo.burmaldaholic.core.cashier.counter;

import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.store.net.CounterOpenPayload;
import dev.nezo.burmaldaholic.core.store.net.CounterResultPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the counter (cashier / merchant / {@code /casino store}; EC-SPEC §3): which source serves a player,
 * range checks, the per-player action cooldown, the tab registry and the result line.
 *
 * <p>Every store / comps action handler starts with
 * <pre>
 * CounterSessions.Session s = CounterSessions.validate(player);
 * if (s == null || !CounterSessions.tryAct(player)) return;
 * </pre>
 *
 * <p>Cashier sessions are derived from the open cashier table menu (no {@link #open} needed); merchant and command
 * sessions are stored by {@link #open} / {@link #openAndSend}.
 */
public final class CounterSessions {
	/** Max distance (blocks, player position to the source) of every counter action. */
	public static final double RANGE = 8.0;
	/** Ticks between two exchange / store / redeem actions of one player (a second "all" in the same tick does nothing). */
	public static final int COOLDOWN_TICKS = 4;

	public static final String TAB_EXCHANGE = "exchange";
	public static final String TAB_STORE = "store";
	public static final String TAB_COMPS = "comps";

	/** An active session: who serves, and the source's dimension (filters the {@code dims} of rows and offers). */
	public record Session(CounterSource source, ResourceKey<Level> dimension) {
		/** {@code O / N / E / ?} ({@link #dimCode}). */
		public String dim() {
			return dimCode(dimension);
		}
	}

	/** A tab the server offers: id, order (lower = further left), and for which sources. */
	public record TabSpec(String id, int order, Predicate<CounterSource> available) {}

	private static final Map<UUID, Session> OPEN = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_ACT = new ConcurrentHashMap<>();
	private static final List<TabSpec> TABS = new CopyOnWriteArrayList<>();

	static {
		registerTab(TAB_EXCHANGE, 0, CounterSource::isCashier);
		registerTab(TAB_STORE, 10, s -> true);
	}

	private CounterSessions() {}

	/** Core: payload types and the disconnect cleanup. */
	public static void register(ModuleContext core) {
		CounterOpenPayload.TYPE = core.payloads().clientbound("counter_open", CounterOpenPayload.CODEC);
		CounterResultPayload.TYPE = core.payloads().clientbound("counter_result", CounterResultPayload.CODEC);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OPEN.remove(handler.getPlayer().getUUID());
			LAST_ACT.remove(handler.getPlayer().getUUID());
		});
	}

	/** Adds (or replaces) a tab, e.g. EC-2: {@code registerTab(TAB_COMPS, 20, s -> true)}. */
	public static void registerTab(String id, int order, Predicate<CounterSource> available) {
		TABS.removeIf(t -> t.id().equals(id));
		TABS.add(new TabSpec(id, order, available));
	}

	/** Tab ids for {@code source}, in order. */
	public static List<String> tabsFor(CounterSource source) {
		List<TabSpec> sorted = new ArrayList<>(TABS);
		sorted.sort(Comparator.comparingInt(TabSpec::order));
		List<String> out = new ArrayList<>();
		for (TabSpec t : sorted) {
			if (t.available().test(source)) out.add(t.id());
		}
		return out;
	}

	/** Starts a menu-less session (merchant, command); replaces the previous one. */
	public static Session open(ServerPlayer player, CounterSource source) {
		Session s = new Session(source, player.level().dimension());
		OPEN.put(player.getUUID(), s);
		return s;
	}

	/** {@link #open} and tells the client to show the counter screen with the source's tabs. */
	public static Session openAndSend(ServerPlayer player, CounterSource source) {
		Session s = open(player, source);
		List<String> tabs = new ArrayList<>(tabsFor(source));
		if (!source.isCashier()) tabs.remove(TAB_EXCHANGE);
		if (CounterOpenPayload.TYPE != null && ServerPlayNetworking.canSend(player, CounterOpenPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CounterOpenPayload(source, tabs, s.dim()));
		}
		return s;
	}

	public static void close(ServerPlayer player) {
		OPEN.remove(player.getUUID());
	}

	/**
	 * The player's valid session, or null: the open cashier menu (still valid, within {@link #RANGE}), else the stored
	 * merchant session (entity alive, same level, within {@link #RANGE}) or command session (same dimension).
	 */
	public static @Nullable Session validate(ServerPlayer player) {
		if (player.containerMenu instanceof CasinoTableMenu menu && player.level().getBlockEntity(menu.pos()) instanceof CashierBlockEntity) {
			BlockPos p = menu.pos();
			if (menu.stillValid(player) && near(player, p)) {
				return new Session(CounterSource.cashier(p), player.level().dimension());
			}
			return null;
		}
		Session s = OPEN.get(player.getUUID());
		if (s == null || !s.dimension().equals(player.level().dimension())) return null;
		return switch (s.source().kind()) {
			case MERCHANT -> {
				Entity e = player.level().getEntity(s.source().entityId());
				yield e != null && e.isAlive() && e.level() == player.level() && player.distanceToSqr(e) <= RANGE * RANGE ? s : null;
			}
			case COMMAND -> s;
			case CASHIER -> player.level().getBlockEntity(s.source().pos()) instanceof CashierBlockEntity && near(player, s.source().pos()) ? s : null;
		};
	}

	private static boolean near(ServerPlayer player, BlockPos p) {
		return player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= RANGE * RANGE;
	}

	/** The cooldown gate: true (and records the tick) when the player may act now, false within {@link #COOLDOWN_TICKS}. */
	public static boolean tryAct(ServerPlayer player) {
		long now = player.level().getServer().getTickCount();
		Long last = LAST_ACT.get(player.getUUID());
		if (last != null && now >= last && now - last < COOLDOWN_TICKS) return false;
		LAST_ACT.put(player.getUUID(), now);
		return true;
	}

	/** Test hook: forgets the player's cooldown. */
	public static void resetCooldown(ServerPlayer player) {
		LAST_ACT.remove(player.getUUID());
	}

	/** Sends the result line to the player's counter screen (no-op for mock players without a connection). */
	public static void result(ServerPlayer player, boolean ok, Component message) {
		if (CounterResultPayload.TYPE != null && ServerPlayNetworking.canSend(player, CounterResultPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CounterResultPayload(ok, message));
		}
	}

	/** {@code O} overworld, {@code N} nether, {@code E} end, {@code ?} any other dimension. */
	public static String dimCode(ResourceKey<Level> dim) {
		if (dim == Level.OVERWORLD) return "O";
		if (dim == Level.NETHER) return "N";
		if (dim == Level.END) return "E";
		return "?";
	}

	/** True when {@code dims} (codes; null / empty = every dimension) contains {@code dim}. */
	public static boolean dimAllowed(@Nullable Collection<String> dims, String dim) {
		return dims == null || dims.isEmpty() || dims.contains(dim);
	}
}
