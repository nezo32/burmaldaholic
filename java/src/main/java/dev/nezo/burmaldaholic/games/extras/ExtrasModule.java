package dev.nezo.burmaldaholic.games.extras;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Small games: coin flip, wheel of fortune, scratch cards, plinko, dice duel.
 *
 * <p>Owner: the "extras" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/extras/}, {@code src/main/sounds/extras/}, and assets/data files named
 * {@code extras_*} or inside {@code extras/} folders. See docs/architecture/java.md.
 */
public final class ExtrasModule implements CasinoModule {
	public static final String ID = "extras";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(extras): register content here, e.g.
		// TABLE = ctx.tables().register("extras_table", ExtrasTableBlockEntity::new);
	}
}
