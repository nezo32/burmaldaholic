package dev.nezo.burmaldaholic.core.events;

import dev.nezo.burmaldaholic.core.economy.Economy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cross-module events. This is how modules talk to each other WITHOUT importing each other's
 * packages: games fire {@link #PLAY_RESOLVED}; chaos/lastchance/vip/loan listen.
 * Need a new cross-module event? Ask core to add it here (it is a shared file).
 */
public final class CasinoEvents {
	private CasinoEvents() {}

	/** A bet was settled. Fired by games via {@code CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(...)}. */
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

	/**
	 * @param gameId module id of the game (e.g. "blackjack")
	 * @param bet    chips staked
	 * @param payout chips returned to the player (0 = lost, bet = push, > bet = win)
	 */
	public record PlayResult(String gameId, long bet, long payout) {
		public boolean won() {
			return payout > bet;
		}

		public boolean lost() {
			return payout < bet;
		}

		public long net() {
			return payout - bet;
		}
	}
}
