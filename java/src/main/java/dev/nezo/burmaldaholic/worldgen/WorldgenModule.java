package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Casino structures in world generation.
 *
 * <p>Owner: the "worldgen" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/worldgen/}, {@code src/main/sounds/worldgen/}, and assets/data files named
 * {@code worldgen_*} or inside {@code worldgen/} folders. See docs/architecture/java.md.
 */
public final class WorldgenModule implements CasinoModule {
	public static final String ID = "worldgen";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(worldgen): register content here, e.g.
		// TABLE = ctx.tables().register("worldgen_table", WorldgenTableBlockEntity::new);
	}
}
