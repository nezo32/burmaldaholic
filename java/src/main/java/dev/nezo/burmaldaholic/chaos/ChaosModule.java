package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Chaos events, golden hour and the win/loss streak odds hook.
 *
 * <p>Owner: the "chaos" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/chaos/}, {@code src/main/sounds/chaos/}, and assets/data files named
 * {@code chaos_*} or inside {@code chaos/} folders. See docs/architecture/java.md.
 */
public final class ChaosModule implements CasinoModule {
	public static final String ID = "chaos";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(chaos): register content here, e.g.
		// TABLE = ctx.tables().register("chaos_table", ChaosTableBlockEntity::new);
	}
}
