package dev.nezo.burmaldaholic.core.pvp;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.server.MinecraftServer;

/**
 * Static access to the PvP engine and its edition presenter. Core creates the engine in
 * {@link #register()} (called from {@code CoreModule.register}); the {@code pvp} module installs the
 * Java presenter with {@link #setPresenter}.
 */
public final class Pvp {
	private static final PvpEngine ENGINE = new PvpEngine();
	private static PvpPresenter presenter = PvpPresenter.NONE;

	private Pvp() {}

	public static PvpService service() {
		return ENGINE;
	}

	public static PvpPresenter presenter() {
		return presenter;
	}

	/** pvp module only (once). */
	public static void setPresenter(PvpPresenter p) {
		presenter = Objects.requireNonNull(p);
	}

	/**
	 * Adds a "busy" rule for PvP eligibility rule 4 (seated at a table / in a solo round). Games that seat
	 * players outside the core table framework (poker) register one: {@code (server, player) -> seated}.
	 */
	public static void addBusyCheck(BiPredicate<MinecraftServer, UUID> check) {
		ENGINE.addBusyCheck(Objects.requireNonNull(check));
	}

	/**
	 * GameTests / admin: forgets the in-memory matches and plays out the saved ones exactly as a world load
	 * does (LOBBY refunded, DRAWN settled from the tape, SETTLED kept for the history).
	 */
	public static void simulateRestart(MinecraftServer server) {
		ENGINE.simulateRestart(server);
	}

	/** Core only: lifecycle hooks (world load → play out saved matches, server stop, casino mode off, tick). */
	public static void register() {
		ENGINE.registerLifecycle();
	}
}
