package dev.nezo.burmaldaholic.games.blackjack;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Blackjack table game.
 *
 * <p>Owner: the "blackjack" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/blackjack/}, {@code src/main/sounds/blackjack/}, and assets/data files named
 * {@code blackjack_*} or inside {@code blackjack/} folders. See docs/architecture/java.md.
 */
public final class BlackjackModule implements CasinoModule {
	public static final String ID = "blackjack";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(blackjack): register content here, e.g.
		// TABLE = ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new);
	}
}
