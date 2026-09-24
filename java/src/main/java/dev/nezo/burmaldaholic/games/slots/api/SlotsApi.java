package dev.nezo.burmaldaholic.games.slots.api;

import dev.nezo.burmaldaholic.games.slots.JackpotPoolsV2;
import dev.nezo.burmaldaholic.games.slots.SlotMachinesV2;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Public API of the slots module for other modules (chaos, vip/advancements, statistics). This is the
 * only slots package other modules may import. Mirrors {@code bedrock/src/games/slots/api.ts}.
 *
 * <h2>Chaos trigger contract (GAME_DESIGN.md §8.1, §8.5, §13.1 triggers 2 and 4)</h2>
 *
 * After a spin is settled (payout already credited) and its reel animation has finished, slots handles
 * AT MOST ONE special per spin, the highest-priority one that hit on any payline: Star &gt; Clock &gt;
 * Pearl &gt; TNT/Creeper — only while {@code chaos.enabled} is true, the winner is online and casino
 * mode is on. It first fires {@link #TRIGGER} (an informational stream: statistics, achievements;
 * listeners must NOT start chaos events themselves), then asks chaos to run the event through the
 * JDK-typed Fabric ObjectShare entries chaos publishes ({@code dev.nezo.burmaldaholic.chaos.ChaosApi}),
 * so neither module imports the other:
 *
 * <table>
 *   <tr><th>symbol</th><th>{@link ChaosEvent}</th><th>chaos call</th></tr>
 *   <tr><td>creeper / tnt</td><td>{@code MOB_WAVE}</td><td>{@code burmaldaholic:chaos/trigger} with {@code "mob_wave@slots"}</td></tr>
 *   <tr><td>pearl</td><td>{@code RANDOM_TELEPORT}</td><td>{@code burmaldaholic:chaos/trigger} with {@code "random_teleport@slots"}</td></tr>
 *   <tr><td>clock (Netherite)</td><td>{@code GOLDEN_HOUR}</td><td>{@code burmaldaholic:chaos/trigger} with {@code "golden_hour@slots"}
 *       (starts Golden Hour server-wide unless on cooldown; the 50× is paid anyway)</td></tr>
 *   <tr><td>star</td><td>{@code JACKPOT}</td><td>{@code burmaldaholic:chaos/jackpot}: {@code diamond_rain} for the winner +
 *       {@code chip_shower} for every player within {@link #JACKPOT_SHOWER_RADIUS} blocks</td></tr>
 * </table>
 *
 * The named event bypasses the ambient chance but chaos still applies {@code chaos.event.<id>.enabled}, the
 * §13.4 safety rules and the per-player cooldown; when blocked nothing happens — the payout was already
 * made. Slots then tells the player: {@code msg.burmaldaholic.slots.three_clocks} if Golden Hour started
 * (chaos returned {@code "started"}), {@code …three_clocks_cooldown} otherwise; {@code three_creepers /
 * three_tnt / three_pearls} when the event started or was deferred. The jackpot's server-wide announcement
 * ({@code msg.burmaldaholic.slots.jackpot_broadcast}) is sent by slots, not by chaos. Without chaos every
 * call is a no-op (the Bedrock edition's {@code onTrigger} subscription has the same semantics).
 *
 * <p>Owned-casino machines (§18) have no progressive: three stars there pay {@code slots.ownedStarPays}×
 * and still fire {@code JACKPOT} (with {@code jackpotAward == 0}), exactly like the Bedrock edition.
 *
 * <p>All callbacks run on the server thread. Listener exceptions are caught and logged by slots.
 */
public final class SlotsApi {
	/** chip_shower radius around a jackpot winner (§8.5). */
	public static final int JACKPOT_SHOWER_RADIUS = 16;

	private SlotsApi() {}

	/** Named chaos trigger of a spin (see the class table). */
	public enum ChaosEvent {
		MOB_WAVE("mob_wave"),
		RANDOM_TELEPORT("random_teleport"),
		GOLDEN_HOUR("golden_hour"),
		/** {@code diamond_rain} for the winner + {@code chip_shower} for nearby players. */
		JACKPOT("jackpot");

		private final String id;

		ChaosEvent(String id) {
			this.id = id;
		}

		/** Chaos event id ({@code chaos.event.<id>}); {@code "jackpot"} maps to {@code diamond_rain} + {@code chip_shower}. */
		public String id() {
			return id;
		}

		/** Machine tier ids: {@code copper}, {@code gold}, {@code netherite}; symbols: {@code creeper, tnt, pearl, clock, star}. */
		public static @Nullable ChaosEvent forSymbol(String symbol) {
			return switch (symbol) {
				case "creeper", "tnt" -> MOB_WAVE;
				case "pearl" -> RANDOM_TELEPORT;
				case "clock" -> GOLDEN_HOUR;
				case "star" -> JACKPOT;
				default -> null;
			};
		}
	}

	/**
	 * One chaos trigger (at most one per spin).
	 *
	 * @param symbol        config symbol id: {@code creeper, tnt, pearl, clock, star}
	 * @param tier          {@code copper, gold, netherite}
	 * @param machine       the machine block position in {@code level}
	 * @param jackpotAward  chips won with the jackpot ({@code JACKPOT} only, else 0)
	 * @param nearbyPlayers other online players within {@link #JACKPOT_SHOWER_RADIUS} blocks ({@code JACKPOT} only, else empty)
	 */
	public record Trigger(ServerPlayer player, ChaosEvent event, String symbol, String tier, ServerLevel level, BlockPos machine,
			long jackpotAward, List<ServerPlayer> nearbyPlayers) {}

	/** A paying/special line: 1-based {@code line} (1 middle, 2 top, 3 bottom, 4 diagonal ↘, 5 diagonal ↗). */
	public record LineWin(int line, String kind, String symbol, double multiplier, long payout) {}

	/**
	 * Every settled spin (achievements {@code three_sevens} / {@code jackpot}, contracts {@code spin_slots}, statistics).
	 *
	 * @param player      the online player, or null when the spin was settled after a disconnect
	 * @param threeSevens any payline won with Redstone Sevens (wild-substituted included)
	 * @param owned       played at an owned-casino machine (§18, no progressive)
	 */
	public record Spin(@Nullable ServerPlayer player, UUID playerId, String tier, long lineBet, long spinBet, long totalReturn,
			List<LineWin> wins, long jackpotAward, boolean threeSevens, boolean owned) {}

	/**
	 * Every settled slots v2 round (SLOTS.md §8: contracts {@code spin_slots} / {@code slots_feature}, statistics,
	 * advancements, Jackpot Race). Fired after {@link #SPIN} (which v2 also fires, with {@code tier} = the machine id,
	 * {@code lineBet} = the bet, {@code spinBet} = the stake, no line wins).
	 *
	 * @param player       the online player, or null when the round settled after a disconnect / restart
	 * @param machine      {@code overworld}, {@code nether}, {@code end}
	 * @param bet          the (underlying) bet
	 * @param stake        chips staked: the bet, or the buy price
	 * @param bought       a bought feature (does not count for {@code slots_feature} or {@code free_spins})
	 * @param payout       everything paid, progressive awards included
	 * @param progressive  of which from the progressive pools
	 * @param tier         slot win tier ({@code WinTier} name, SLOTS.md §10.1; jackpots excluded)
	 * @param freeSpins    free spins were played
	 * @param bonus        {@code hunt}, {@code hoard}, {@code wheel} or empty
	 * @param jackpots     jackpot tiers won (1 Mini … 4 Grand), tape order
	 * @param maxWin       the max-win cap was reached
	 * @param maxTumbles   most tumbles in one reel spin (Nether)
	 */
	public record Round(@Nullable ServerPlayer player, UUID playerId, String machine, long bet, long stake, boolean bought, boolean owned,
			long payout, long progressive, String tier, boolean freeSpins, String bonus, int[] jackpots, boolean maxWin, int maxTumbles,
			ServerLevel level, BlockPos pos) {
		/** A feature was triggered by the spin itself (SLOTS.md §8.7 {@code slots_feature}). */
		public boolean featureTriggered() {
			return !bought && (freeSpins || !bonus.isEmpty());
		}
	}

	@FunctionalInterface
	public interface RoundListener {
		void onRound(Round round);
	}

	/** Every settled v2 round (see {@link Round}). */
	public static final Event<RoundListener> ROUND = EventFactory.createArrayBacked(RoundListener.class, listeners -> r -> {
		for (RoundListener l : listeners) {
			l.onRound(r);
		}
	});

	@FunctionalInterface
	public interface TriggerListener {
		void onTrigger(Trigger trigger);
	}

	@FunctionalInterface
	public interface SpinListener {
		void onSpin(Spin spin);
	}

	/** Chaos trigger stream (contract above). */
	public static final Event<TriggerListener> TRIGGER = EventFactory.createArrayBacked(TriggerListener.class, listeners -> t -> {
		for (TriggerListener l : listeners) {
			l.onTrigger(t);
		}
	});

	/** Every settled spin. */
	public static final Event<SpinListener> SPIN = EventFactory.createArrayBacked(SpinListener.class, listeners -> s -> {
		for (SpinListener l : listeners) {
			l.onSpin(s);
		}
	});

	/**
	 * Current Grand jackpot meter (chips) of the machine on a cabinet tier ({@code copper} Overworld Riches, {@code gold}
	 * Nether Inferno, {@code netherite} End Void); 0 for unknown tiers.
	 */
	public static long jackpotPool(MinecraftServer server, String tier) {
		dev.nezo.burmaldaholic.games.slots.logic.Tier t = dev.nezo.burmaldaholic.games.slots.logic.Tier.byId(tier);
		if (t == null) return 0;
		dev.nezo.burmaldaholic.games.slots.v2.logic.Machine m = SlotMachinesV2.machine(t);
		return JackpotPoolsV2.get(server).meter(m, SlotMachinesV2.def(m), 4);
	}

	/** Reset one cabinet tier's machine (or, with null, every machine) to its jackpot seeds (admin). */
	public static void resetJackpots(MinecraftServer server, @Nullable String tier) {
		dev.nezo.burmaldaholic.games.slots.logic.Tier t = tier == null ? null : dev.nezo.burmaldaholic.games.slots.logic.Tier.byId(tier);
		for (dev.nezo.burmaldaholic.games.slots.v2.logic.Machine m : dev.nezo.burmaldaholic.games.slots.v2.logic.Machine.values()) {
			if (tier == null || t != null && SlotMachinesV2.machine(t) == m) JackpotPoolsV2.get(server).reset(m);
		}
	}
}
