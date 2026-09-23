package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Chaos layer (GAME_DESIGN.md §13): ambient / special / big-win / jackpot / sunset triggers, the
 * nine events with the §13.4 safety rules, Golden Hour (bonus payouts, boss-bar timer, core's
 * {@code GoldenHourProvider}) and {@code /casino chaos}. Other modules use {@link ChaosApi}
 * (through Fabric's ObjectShare). Streak math, its messages and the HUD streak line are core's.
 */
public final class ChaosModule implements CasinoModule {
	public static final String ID = "chaos";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		ChaosEffects.register(ctx);
		GoldenHour.register(ctx);
		ChaosEngine.register();
		ChaosCommands.register();
		ChaosApi.publish();
	}
}
