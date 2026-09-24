package dev.nezo.burmaldaholic.core.pvp;

import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/**
 * PvP notifications for other modules (advancements, statistics, the owned-casino charter).
 * Fired by the engine on the server thread. Chips were already moved when these fire.
 */
public final class PvpEvents {
	private PvpEvents() {}

	/** Tape drawn and persisted; the reveal starts. */
	public static final Event<MatchStarted> MATCH_STARTED = EventFactory.createArrayBacked(MatchStarted.class,
		listeners -> (server, match) -> {
			for (MatchStarted l : listeners) {
				l.onStarted(server, match);
			}
		});

	/** Settled (payouts + rake in one transaction). {@code payouts[i]} per participant index. */
	public static final Event<MatchSettled> MATCH_SETTLED = EventFactory.createArrayBacked(MatchSettled.class,
		listeners -> (server, match, payouts) -> {
			for (MatchSettled l : listeners) {
				l.onSettled(server, match, payouts);
			}
		});

	/** PvP win streak reached a call-out tier (0 heating, 1 rampage, 2 legendary) or was broken (tier -1). */
	public static final Event<StreakCallout> STREAK = EventFactory.createArrayBacked(StreakCallout.class,
		listeners -> (server, player, streak, tier, breaker) -> {
			for (StreakCallout l : listeners) {
				l.onStreak(server, player, streak, tier, breaker);
			}
		});

	@FunctionalInterface
	public interface MatchStarted {
		void onStarted(MinecraftServer server, PvpMatch match);
	}

	@FunctionalInterface
	public interface MatchSettled {
		void onSettled(MinecraftServer server, PvpMatch match, long[] payouts);
	}

	@FunctionalInterface
	public interface StreakCallout {
		void onStreak(MinecraftServer server, UUID player, int streak, int tier, UUID breaker);
	}
}
