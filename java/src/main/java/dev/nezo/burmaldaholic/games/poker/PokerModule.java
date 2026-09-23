package dev.nezo.burmaldaholic.games.poker;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableType;

/**
 * Poker (No-Limit Texas Hold'em, GAME_DESIGN.md §7): the {@code poker_table} block with human seats,
 * house bots, side pots, rake and turn timers. Rules live in {@code logic/} (pure, unit-tested); the
 * block entity {@link PokerTableBlockEntity} drives them server-side; the client screen only renders.
 */
public final class PokerModule implements CasinoModule {
	public static final String ID = "poker";
	public static TableType<PokerTableBlockEntity> TABLE;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register("poker_table", PokerTableBlockEntity::new);
	}
}
