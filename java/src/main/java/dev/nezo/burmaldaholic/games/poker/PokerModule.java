package dev.nezo.burmaldaholic.games.poker;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Poker (Texas hold'em) table game, incl. multiplayer seats.
 *
 * <p>Owner: the "poker" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/poker/}, {@code src/main/sounds/poker/}, and assets/data files named
 * {@code poker_*} or inside {@code poker/} folders. See docs/architecture/java.md.
 */
public final class PokerModule implements CasinoModule {
	public static final String ID = "poker";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(poker): register content here, e.g.
		// TABLE = ctx.tables().register("poker_table", PokerTableBlockEntity::new);
	}
}
