package dev.nezo.burmaldaholic.games.roulette;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Roulette table.
 *
 * <p>Owner: the "roulette" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/roulette/}, {@code src/main/sounds/roulette/}, and assets/data files named
 * {@code roulette_*} or inside {@code roulette/} folders. See docs/architecture/java.md.
 */
public final class RouletteModule implements CasinoModule {
	public static final String ID = "roulette";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(roulette): register content here, e.g.
		// TABLE = ctx.tables().register("roulette_table", RouletteTableBlockEntity::new);
	}
}
