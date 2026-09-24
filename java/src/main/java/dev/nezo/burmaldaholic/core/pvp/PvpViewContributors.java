package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Extra fields for the public match JSON that the pvp module sends to the PvP screens ({@code PvpSyncPayload},
 * mode screens registered with {@code client.pvp.PvpScreens}). The pvp module builds the common fields; the
 * engine and the modes add what only they know, e.g. {@code "decision"} (the decision waiting for this viewer:
 * {@code {"id", "ticksLeft", "deficit", …}}), {@code "chain"} (Coin Flip Duel chain status) or a countdown
 * {@code "ticksLeft"}. A contributor must only add PUBLIC information for {@code viewer} (never unrevealed tape).
 * Server thread; contributors run for every sync, so keep them cheap.
 */
public final class PvpViewContributors {
	/** Adds / overrides fields of {@code view} for one viewer (null = spectator-neutral view). */
	@FunctionalInterface
	public interface Contributor {
		void contribute(PvpMatch match, @Nullable ServerPlayer viewer, JsonObject view);
	}

	private static final List<Contributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();

	private PvpViewContributors() {}

	public static void register(Contributor c) {
		CONTRIBUTORS.add(c);
	}

	/** pvp module: applies every contributor (a failing one is logged and skipped). */
	public static void apply(PvpMatch match, @Nullable ServerPlayer viewer, JsonObject view) {
		for (Contributor c : CONTRIBUTORS) {
			try {
				c.contribute(match, viewer, view);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP view contributor failed for match {}", match.id, e);
			}
		}
	}
}
