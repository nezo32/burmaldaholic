package dev.nezo.burmaldaholic.games.craps;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableType;

/**
 * Craps table (GAME_DESIGN §10): Pass / Don't Pass, Come / Don't Come, Field, take and lay odds,
 * point state machine, clockwise shooter rotation, betting window and auto roll.
 *
 * <p>Rules: {@code games.craps.logic} (pure, unit-tested). Server: {@link CrapsTableBlockEntity}.
 * Client: {@code games.craps.client.CrapsScreen}. Recipe {@code data/burmaldaholic/recipe/craps_table.json}
 * needs the {@code burmaldaholic:dice} item (extras); it only loads when that item exists.
 */
public final class CrapsModule implements CasinoModule {
	public static final String ID = "craps";
	public static TableType<CrapsTableBlockEntity> TABLE;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register("craps_table", CrapsTableBlockEntity::new);
	}
}
