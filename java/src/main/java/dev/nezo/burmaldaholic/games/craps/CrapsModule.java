package dev.nezo.burmaldaholic.games.craps;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Craps table.
 *
 * <p>Owner: the "craps" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/craps/}, {@code src/main/sounds/craps/}, and assets/data files named
 * {@code craps_*} or inside {@code craps/} folders. See docs/architecture/java.md.
 */
public final class CrapsModule implements CasinoModule {
	public static final String ID = "craps";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(craps): register content here, e.g.
		// TABLE = ctx.tables().register("craps_table", CrapsTableBlockEntity::new);
	}
}
