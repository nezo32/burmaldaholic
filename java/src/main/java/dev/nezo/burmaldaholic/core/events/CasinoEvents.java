package dev.nezo.burmaldaholic.core.events;

import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.core.wager.Stake;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Cross-module events. This is how modules talk to each other WITHOUT importing each other's
 * packages: games fire {@link #PLAY_RESOLVED}; chaos/lastchance/vip/loan listen.
 * Need a new cross-module event? Ask core to add it here (it is a shared file).
 */
public final class CasinoEvents {
	private CasinoEvents() {}

	/**
	 * A bet was settled. Fire it through {@link PlayResults#fire} (never the invoker directly): that
	 * stamps the Golden Hour flag and queues results of offline players until they join
	 * ({@link PlayResult#deferred()} = true), so streak, VIP, contracts, cashback, statistics and
	 * advancements see every round exactly once. Payouts themselves are credited at settlement.
	 */
	public static final Event<PlayResolved> PLAY_RESOLVED = EventFactory.createArrayBacked(PlayResolved.class,
		listeners -> (player, result) -> {
			for (PlayResolved l : listeners) {
				l.onPlayResolved(player, result);
			}
		});

	/** Fired by the economy after any balance change. */
	public static final Event<BalanceChanged> BALANCE_CHANGED = EventFactory.createArrayBacked(BalanceChanged.class,
		listeners -> (player, before, after, reason) -> {
			for (BalanceChanged l : listeners) {
				l.onBalanceChanged(player, before, after, reason);
			}
		});

	@FunctionalInterface
	public interface PlayResolved {
		void onPlayResolved(ServerPlayer player, PlayResult result);
	}

	@FunctionalInterface
	public interface BalanceChanged {
		void onBalanceChanged(ServerPlayer player, long before, long after, Economy.Transaction reason);
	}

	/** A seated player left a table (button, walked away, disconnected, table removed). */
	public static final Event<TableLeft> TABLE_LEFT = EventFactory.createArrayBacked(TableLeft.class,
		listeners -> (level, pos, player, reason) -> {
			for (TableLeft l : listeners) {
				l.onTableLeft(level, pos, player, reason);
			}
		});

	/** Rake of a PvP pot was collected at a table (already moved to the owner's bankroll at owned tables). */
	public static final Event<RakeCollected> RAKE_COLLECTED = EventFactory.createArrayBacked(RakeCollected.class,
		listeners -> (level, pos, rake, bankroll) -> {
			for (RakeCollected l : listeners) {
				l.onRake(level, pos, rake, bankroll);
			}
		});

	@FunctionalInterface
	public interface TableLeft {
		void onTableLeft(ServerLevel level, BlockPos pos, UUID player, CasinoTableBlockEntity.LeaveReason reason);
	}

	@FunctionalInterface
	public interface RakeCollected {
		/** @param bankroll owner bankroll id that received it ("" = house table, rake stays in the bank) */
		void onRake(ServerLevel level, BlockPos pos, long rake, String bankroll);
	}

	/**
	 * One settled wager (GAME_DESIGN.md §4.1). Build with {@link #of} and the {@code with*} methods;
	 * fire through {@link PlayResults#fire} (queues it for offline players, see there).
	 *
	 * @param gameId      game id (e.g. "blackjack", "coin_flip")
	 * @param bet         chips staked / stake value V (all chips put at risk in the round)
	 * @param payout      total return (0 = lost, bet = push, &gt; bet = win)
	 * @param houseBanked false for PvP rounds (poker pots, PvP dice duels)
	 * @param stakeKind   chips or a pawn kind (§4.3)
	 * @param houseEdge   §17 edge of the bet(s) of this round (stake-weighted when several)
	 * @param tags        bet details, e.g. roulette {@code "red"}, {@code "straight"}, slots tier, craps bet kind
	 * @param table       table / machine position (null = no block, e.g. coin flip)
	 * @param bankroll    owned-casino bankroll that banked the round ("" = the world bank)
	 * @param deferred    settled while the player was offline; fired on their next join
	 * @param goldenHour  a Golden Hour was active when the round settled (stamped by core)
	 */
	public record PlayResult(String gameId, long bet, long payout, boolean houseBanked, Stake.Kind stakeKind, double houseEdge,
		List<String> tags, @Nullable GlobalPos table, String bankroll, boolean deferred, boolean goldenHour) {

		public PlayResult {
			tags = tags == null ? List.of() : List.copyOf(tags);
			bankroll = bankroll == null ? "" : bankroll;
			stakeKind = stakeKind == null ? Stake.Kind.CHIPS : stakeKind;
			houseEdge = houseEdge > 0 && Double.isFinite(houseEdge) ? houseEdge : 0;
		}

		/** House-banked chip round with the game's default (lowest) edge. */
		public PlayResult(String gameId, long bet, long payout) {
			this(gameId, bet, payout, true, Stake.Kind.CHIPS, HouseEdges.of(gameId), List.of(), null, "", false, false);
		}

		public static PlayResult of(String gameId, long bet, long payout) {
			return new PlayResult(gameId, bet, payout);
		}

		public boolean won() {
			return payout > bet;
		}

		public boolean lost() {
			return payout < bet;
		}

		public long net() {
			return payout - bet;
		}

		/** Banked by an owned casino (§18.2) rather than the world bank. */
		public boolean ownedCasino() {
			return !bankroll.isEmpty();
		}

		/** A non-chip stake (§4.3). */
		public boolean pawn() {
			return stakeKind != Stake.Kind.CHIPS;
		}

		public boolean hasTag(String tag) {
			return tags.contains(tag);
		}

		/** Σ stake × edge of this round (0 for PvP). */
		public double theoreticalLoss() {
			return houseBanked ? HouseEdges.theoreticalLoss(bet, houseEdge) : 0;
		}

		/** PvP round: no house edge, no Golden Hour, no cashback. */
		public PlayResult pvp() {
			return new PlayResult(gameId, bet, payout, false, stakeKind, 0, tags, table, bankroll, deferred, goldenHour);
		}

		public PlayResult withKind(Stake.Kind kind) {
			return new PlayResult(gameId, bet, payout, houseBanked, kind, houseEdge, tags, table, bankroll, deferred, goldenHour);
		}

		public PlayResult withEdge(double edge) {
			return new PlayResult(gameId, bet, payout, houseBanked, stakeKind, edge, tags, table, bankroll, deferred, goldenHour);
		}

		public PlayResult withTags(String... more) {
			List<String> t = new ArrayList<>(tags);
			for (String m : more) {
				if (m != null && !m.isEmpty() && !t.contains(m)) {
					t.add(m);
				}
			}
			return new PlayResult(gameId, bet, payout, houseBanked, stakeKind, houseEdge, t, table, bankroll, deferred, goldenHour);
		}

		public PlayResult withTable(@Nullable GlobalPos pos, String bankrollId) {
			return new PlayResult(gameId, bet, payout, houseBanked, stakeKind, houseEdge, tags, pos, bankrollId, deferred, goldenHour);
		}

		public PlayResult withTable(Level level, BlockPos pos, String bankrollId) {
			return withTable(GlobalPos.of(level.dimension(), pos.immutable()), bankrollId);
		}

		public PlayResult asDeferred() {
			return new PlayResult(gameId, bet, payout, houseBanked, stakeKind, houseEdge, tags, table, bankroll, true, goldenHour);
		}

		public PlayResult withGoldenHour(boolean active) {
			return new PlayResult(gameId, bet, payout, houseBanked, stakeKind, houseEdge, tags, table, bankroll, deferred, active);
		}

		/** Persistence of the offline queue. */
		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putString("game", gameId);
			t.putLong("bet", bet);
			t.putLong("payout", payout);
			t.putBoolean("house", houseBanked);
			t.putString("kind", stakeKind.name());
			t.putDouble("edge", houseEdge);
			ListTag list = new ListTag();
			tags.forEach(s -> list.add(StringTag.valueOf(s)));
			t.put("tags", list);
			if (table != null) {
				t.putString("dim", table.dimension().identifier().toString());
				t.putLong("pos", table.pos().asLong());
			}
			t.putString("bankroll", bankroll);
			t.putBoolean("golden_hour", goldenHour);
			return t;
		}

		public static PlayResult load(CompoundTag t) {
			Stake.Kind kind;
			try {
				kind = Stake.Kind.valueOf(t.getStringOr("kind", "CHIPS"));
			} catch (IllegalArgumentException e) {
				kind = Stake.Kind.CHIPS;
			}
			List<String> tags = new ArrayList<>();
			t.getListOrEmpty("tags").forEach(tag -> tag.asString().ifPresent(tags::add));
			GlobalPos table = null;
			Identifier dim = Identifier.tryParse(t.getStringOr("dim", ""));
			if (dim != null && t.contains("pos")) {
				table = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLongOr("pos", 0)));
			}
			return new PlayResult(t.getStringOr("game", ""), t.getLongOr("bet", 0), t.getLongOr("payout", 0), t.getBooleanOr("house", true),
				kind, t.getDoubleOr("edge", 0), tags, table, t.getStringOr("bankroll", ""), false, t.getBooleanOr("golden_hour", false));
		}
	}
}
