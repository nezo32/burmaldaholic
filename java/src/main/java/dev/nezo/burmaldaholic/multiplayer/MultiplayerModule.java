package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Multiplayer: casino/table ownership, house cut, shared tables.
 *
 * <p>Owner: the "multiplayer" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/multiplayer/}, {@code src/main/sounds/multiplayer/}, and assets/data files named
 * {@code multiplayer_*} or inside {@code multiplayer/} folders. See docs/architecture/java.md.
 */
public final class MultiplayerModule implements CasinoModule {
	public static final String ID = "multiplayer";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(multiplayer): register content here, e.g.
		// TABLE = ctx.tables().register("multiplayer_table", MultiplayerTableBlockEntity::new);
	}
}
