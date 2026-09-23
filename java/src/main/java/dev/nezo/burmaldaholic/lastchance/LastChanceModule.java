package dev.nezo.burmaldaholic.lastchance;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Last Chance mechanic for broke players.
 *
 * <p>Owner: the "lastchance" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/lastchance/}, {@code src/main/sounds/lastchance/}, and assets/data files named
 * {@code lastchance_*} or inside {@code lastchance/} folders. See docs/architecture/java.md.
 */
public final class LastChanceModule implements CasinoModule {
	public static final String ID = "lastchance";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(lastchance): register content here, e.g.
		// TABLE = ctx.tables().register("lastchance_table", LastChanceTableBlockEntity::new);
	}
}
