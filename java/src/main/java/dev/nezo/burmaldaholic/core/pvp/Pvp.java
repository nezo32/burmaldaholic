package dev.nezo.burmaldaholic.core.pvp;

import java.util.Objects;

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

	/** Core only: lifecycle hooks (world load → play out saved matches, server stop, casino mode off, tick). */
	public static void register() {
		ENGINE.registerLifecycle();
	}
}
