package dev.nezo.burmaldaholic.pvp;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * The "pvp" module (PVP.md): PvP hub (Casino Menu → Challenges), invite / lobby / result UI, the Java
 * {@code PvpPresenter} + payloads, {@code /casino pvp …}, rivalry display and the core PvP advancements
 * (pvp_first_win, pvp_all_in, pvp_full_house, pvp_revenge, pvp_rampage). The engine itself is core
 * ({@code core.pvp}); the modes live in the modules owning their solo games (extras, slots).
 * Owner: dev "PvP core" (docs/architecture/pvp-bots.md §7, J-P1/J-P2).
 */
public final class PvpModule implements CasinoModule {
	@Override
	public String id() {
		return "pvp";
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(J-P2): Pvp.setPresenter(new PvpScreensPresenter()); CasinoMenu.register(new PvpHubPage());
		//   CasinoCommands.extend(PvpCommands::register); payloads; PvpEvents listeners → CasinoAdvancements.grant.
	}
}
