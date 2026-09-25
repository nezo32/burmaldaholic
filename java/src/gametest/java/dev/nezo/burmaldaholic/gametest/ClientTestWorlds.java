package dev.nezo.burmaldaholic.gametest;

import dev.nezo.burmaldaholic.client.CasinoModeCreationState;
import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.pvp.client.ClientPvp;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;

/** Client GameTest worlds. Casino mode is OFF by default, so tests that play the casino opt in at creation. */
public final class ClientTestWorlds {
	/** Longest wait for the integrated server to become idle before the world closes (ticks). */
	public static final int QUIET_TIMEOUT_TICKS = 200;

	private ClientTestWorlds() {}

	/** A world created with Casino Mode ON, exactly like pressing the Create World button. */
	public static TestWorldBuilder casino(ClientGameTestContext context) {
		return context.worldBuilder().adjustSettings(state -> ((CasinoModeCreationState) state).burmaldaholic$setCasinoMode(true));
	}

	/**
	 * Quiets a test world before it closes; use as the second resource of the try-with-resources so it runs first even
	 * when the test fails: {@code try (var world = …create(); var q = ClientTestWorlds.quiet(context, world))}.
	 *
	 * <p>Closing a singleplayer world while the server is still busy (a PvP match revealing, a lobby timer, a celebration
	 * on screen) deadlocked two client test runs under load: the render thread waited in {@code IntegratedServer.halt()}
	 * while the server thread was parked in Fabric's gametest phaser. So: close every screen and client PvP state, cancel
	 * every live PvP match / lobby / invite on the server (stakes are refunded by the engine), then wait — with a timeout
	 * — until no match is left and a few idle ticks have passed.
	 */
	public static Quiet quiet(ClientGameTestContext context, TestSingleplayerContext world) {
		return () -> quiesce(context, world);
	}

	/** {@link #quiet}'s resource (no checked exception). */
	public interface Quiet extends AutoCloseable {
		@Override
		void close();
	}

	public static void quiesce(ClientGameTestContext context, TestSingleplayerContext world) {
		try {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				CelebrationOverlay.get().clear();
				ClientPvp.setStateForTests(null, "clear");
			});
			world.getServer().runOnServer(server -> {
				for (PvpMatch m : List.copyOf(Pvp.service().all())) {
					if (m.state() != MatchState.SETTLED) Pvp.service().cancel(m.id);
				}
				dev.nezo.burmaldaholic.games.extras.server.CoinToss.clear();
			});
			// polled from the test thread (never from the render thread), bounded by QUIET_TIMEOUT_TICKS
			for (int waited = 0; waited < QUIET_TIMEOUT_TICKS; waited += 10) {
				boolean idle = world.getServer().computeOnServer(server ->
					Pvp.service().all().stream().allMatch(m -> m.state() == MatchState.SETTLED || m.state() == MatchState.CANCELLED));
				if (idle) break;
				context.waitTicks(10);
			}
		} catch (RuntimeException | AssertionError e) {
			// a best effort: the world still closes (a failure here must not hide the test's own result)
		}
		context.waitTicks(10);
	}
}
