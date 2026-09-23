package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * VIP tiers and perks.
 *
 * <p>Owner: the "vip" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/vip/}, {@code src/main/sounds/vip/}, and assets/data files named
 * {@code vip_*} or inside {@code vip/} folders. See docs/architecture/java.md.
 */
public final class VipModule implements CasinoModule {
	public static final String ID = "vip";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(vip): register content here, e.g.
		// TABLE = ctx.tables().register("vip_table", VipTableBlockEntity::new);
	}
}
