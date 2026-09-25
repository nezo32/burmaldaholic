package dev.nezo.burmaldaholic.games.extras.pvp.coin;

import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Follows every Coin Flip Duel chain from the settled flips ({@code PvpEvents.MATCH_SETTLED}), independent of
 * how the engine orders the participants of a link: the chain's {@link CoinChain.State} (who is down how much),
 * for the chain status line ("Chain: Alex down 400"), the Double-or-nothing offer check and the
 * {@code pvp_all_square} advancement (the chain loser wins a linked flip). Server thread only; a chain ends at
 * all square, and at most {@link #MAX} chains are remembered (a server stop ends every chain, PVP.md §4.2).
 */
public final class CoinChains {
	static final int MAX = 256;
	private static final Map<String, CoinChain.State> CHAINS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CoinChain.State> eldest) {
			return size() > MAX;
		}
	};

	private CoinChains() {}

	/** Chain id of a flip: the first flip's match id. */
	public static String root(PvpMatch match) {
		return match.chainOf == null || match.chainOf.isEmpty() ? match.id : match.chainOf;
	}

	/** State after the latest settled flip of the chain {@code match} belongs to (empty after all square). */
	public static Optional<CoinChain.State> state(PvpMatch match) {
		return Optional.ofNullable(CHAINS.get(root(match)));
	}

	static void onSettled(MinecraftServer server, PvpMatch match, long[] payouts) {
		if (!CoinDuelMode.ID.equals(match.mode)) {
			return;
		}
		Outcome outcome = match.outcome();
		if (outcome == null || outcome.winners().length != 1) {
			return;
		}
		Participant winner = byIndex(match, outcome.winners()[0]);
		Participant loser = byIndex(match, 1 - outcome.winners()[0]);
		if (winner == null || loser == null) {
			return;
		}
		String root = root(match);
		CoinChain.State prev = CHAINS.get(root);
		if (match.link <= 1 || prev == null || prev.allSquare()) {
			CHAINS.put(root, CoinChain.first(winner.stake(), loser.occupant.key()));
			return;
		}
		CoinChain.State next = CoinChain.next(prev, winner.occupant.key().equals(prev.loser()));
		if (next.allSquare()) {
			CHAINS.remove(root);
			if (winner.occupant instanceof SeatOccupant.Human h) {
				ModeAdvancements.grant(server, h.id(), ModeAdvancements.ALL_SQUARE);
			}
		} else {
			CHAINS.put(root, next);
		}
	}

	static @Nullable Participant byIndex(PvpMatch match, int index) {
		for (Participant p : match.participants()) {
			if (p.index == index) {
				return p;
			}
		}
		return null;
	}

	static void clear() {
		CHAINS.clear();
	}
}
