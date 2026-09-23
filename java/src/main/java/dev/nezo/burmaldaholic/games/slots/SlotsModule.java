package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Slot machines.
 *
 * <p>Owner: the "slots" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/slots/}, {@code src/main/sounds/slots/}, and assets/data files named
 * {@code slots_*} or inside {@code slots/} folders. See docs/architecture/java.md.
 */
public final class SlotsModule implements CasinoModule {
	public static final String ID = "slots";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(slots): register content here, e.g.
		// TABLE = ctx.tables().register("slots_table", SlotsTableBlockEntity::new);
	}
}
