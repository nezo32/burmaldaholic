package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * The "bots" module (BOTS.md): table settings screen + payloads, private tables / invites (Casino Card on a
 * player), {@code /casino table …} and {@code /casino bots …}, bot chatter delivery, avatars (text-display
 * nameplates), Wallet heat line, admin page and bot advancements. The seat model, policies, purses, ledger
 * and jobs are core ({@code core.bots}); each game drives its own bots through {@code TableBots}.
 * Owner: dev "Bots core" (docs/architecture/pvp-bots.md §7, J-B2).
 */
public final class BotsModule implements CasinoModule {
	@Override
	public String id() {
		return "bots";
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(J-B2): CasinoCommands.extend(BotCommands::register); settings payloads; BotChatter; avatars;
		//   CasinoMenu wallet/settings lines; advancement listeners.
	}
}
