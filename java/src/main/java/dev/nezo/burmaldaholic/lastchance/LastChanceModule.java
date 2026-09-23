package dev.nezo.burmaldaholic.lastchance;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Last Chance (GAME_DESIGN §15): a coin flip with Death on a lethal hit. Server logic in
 * {@link LastChance}, pure rules in {@code logic.LastChanceRules}, the coin-flip overlay in the client
 * module. Public read API: {@link LastChance#status}.
 */
public final class LastChanceModule implements CasinoModule {
	public static final String ID = "lastchance";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		LastChance.register(ctx);
	}
}
