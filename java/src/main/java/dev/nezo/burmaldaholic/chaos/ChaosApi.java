package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public trigger API of the chaos module (GAME_DESIGN.md §13.1). Server thread only.
 *
 * <p>Contract: slot / wheel / scratch specials call {@link #trigger} AFTER crediting the payout
 * (§8.1: at most one event per spin). It bypasses the ambient chance but respects casino mode,
 * {@code chaos.enabled}, {@code chaos.event.<id>.enabled}, the per-player cooldown and the §13.4
 * safety rules. If it does not start, the game still pays and needs no fallback. Three Clocks:
 * {@code trigger(player, "golden_hour", "slots")} → {@code "started"} (show
 * {@code msg.burmaldaholic.slots.three_clocks}) or anything else (show {@code three_clocks_cooldown}).
 * Progressive jackpot: {@link #jackpot}. Big wins and Golden Hour bonuses need no call (chaos
 * listens to {@code CasinoEvents.PLAY_RESOLVED}).
 *
 * <p><b>Other feature modules must not import this package</b> (java.md §4). They look the API up
 * through Fabric Loader's {@link ObjectShare}, typed with JDK/Minecraft types only:
 *
 * <pre>
 * // "burmaldaholic:chaos/trigger" → BiFunction&lt;ServerPlayer, String, String&gt;
 * //   arg: "&lt;eventId&gt;" or "&lt;eventId&gt;@&lt;source&gt;" (e.g. "mob_wave@slots"); returns the result id:
 * //   "started" | "deferred" | "skipped" | "cooldown" | "disabled"  (null if chaos is absent)
 * &#64;SuppressWarnings("unchecked")
 * var chaos = (BiFunction&lt;ServerPlayer, String, String&gt;) FabricLoader.getInstance().getObjectShare().get("burmaldaholic:chaos/trigger");
 * String r = chaos == null ? "disabled" : chaos.apply(player, "random_teleport@slots");
 *
 * // "burmaldaholic:chaos/jackpot"               → Consumer&lt;ServerPlayer&gt;      (diamond rain + chip shower within 16 blocks)
 * // "burmaldaholic:chaos/golden_hour_ready"     → Predicate&lt;MinecraftServer&gt;  (a Golden Hour could start now)
 * // "burmaldaholic:chaos/golden_hour_remaining" → Function&lt;MinecraftServer, Long&gt; (also CoreServices.goldenHour())
 * </pre>
 *
 * The static methods below have the same JDK/Minecraft-only signatures, so reflection works too:
 * {@code Class.forName("dev.nezo.burmaldaholic.chaos.ChaosApi").getMethod("trigger", ServerPlayer.class, String.class, String.class)}.
 */
public final class ChaosApi {
	public static final String KEY_TRIGGER = "burmaldaholic:chaos/trigger";
	public static final String KEY_JACKPOT = "burmaldaholic:chaos/jackpot";
	public static final String KEY_GOLDEN_HOUR_READY = "burmaldaholic:chaos/golden_hour_ready";
	public static final String KEY_GOLDEN_HOUR_REMAINING = "burmaldaholic:chaos/golden_hour_remaining";

	private ChaosApi() {}

	/**
	 * Fires the named event for {@code player} (§13.1.2). {@code source}: "slots", "wheel", "scratch",
	 * "plinko", "coin_flip", "jackpot", "admin", ... ("slots" + golden_hour announces "X rang in
	 * Golden Hour with three clocks"). Returns the {@link TriggerResult#id()}; unknown ids → "disabled".
	 */
	public static String trigger(ServerPlayer player, String eventId, String source) {
		return ChaosEvent.byId(eventId).map(e -> triggerEvent(player, e, source, false)).orElse(TriggerResult.DISABLED).id();
	}

	/** Typed variant; {@code ignoreCooldown} skips the per-player cooldown (jackpots, admin) but never the safety rules. */
	public static TriggerResult triggerEvent(ServerPlayer player, ChaosEvent event, String source, boolean ignoreCooldown) {
		if (player == null || event == null) {
			return TriggerResult.DISABLED;
		}
		return ChaosEngine.trigger(player, event, source == null ? "api" : source, ignoreCooldown);
	}

	/** Progressive jackpot celebration (§8.5): diamond rain for the winner, chip shower for players within 16 blocks. */
	public static void jackpot(ServerPlayer winner) {
		if (winner != null) {
			ChaosEngine.jackpot(winner);
		}
	}

	/** True when a Golden Hour could start right now (enabled, not active, off cooldown). */
	public static boolean canStartGoldenHour(MinecraftServer server) {
		return server != null && GoldenHour.canStart(server);
	}

	/** Remaining ticks of the current Golden Hour (0 = inactive). */
	public static long goldenHourRemaining(MinecraftServer server) {
		return GoldenHour.remainingTicks(server);
	}

	/** Casino mode on and {@code chaos.enabled}. */
	public static boolean isEnabled(MinecraftServer server) {
		return ChaosEngine.enabled(server);
	}

	/** Parses "event" / "event@source" for the ObjectShare entry. */
	static String triggerShared(ServerPlayer player, String spec) {
		if (spec == null) {
			return TriggerResult.DISABLED.id();
		}
		int at = spec.indexOf('@');
		String event = at < 0 ? spec : spec.substring(0, at);
		String source = at < 0 ? "api" : spec.substring(at + 1);
		return trigger(player, event, source);
	}

	static void publish() {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		share.put(KEY_TRIGGER, (BiFunction<ServerPlayer, String, String>) ChaosApi::triggerShared);
		share.put(KEY_JACKPOT, (Consumer<ServerPlayer>) ChaosApi::jackpot);
		share.put(KEY_GOLDEN_HOUR_READY, (Predicate<MinecraftServer>) ChaosApi::canStartGoldenHour);
		share.put(KEY_GOLDEN_HOUR_REMAINING, (Function<MinecraftServer, Long>) ChaosApi::goldenHourRemaining);
	}
}
