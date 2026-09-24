/**
 * The "bots" module (BOTS.md): Table settings / Private table forms, Casino Card on a player (invite),
 * `/scriptevent burmaldaholic:bots list|clear|heat`, Admin → Bots, chatter delivery, Wallet heat line,
 * bot achievements. The seat model, policies, purses, ledger and jobs are core (`ctx.bots`); each game
 * drives its own bots through `ctx.bots.table(...)`. Owner: dev "Bots core" (pvp-bots.md §7, B-B2).
 */
import type { CasinoModule } from '../core';

export const botsModule: CasinoModule = {
  id: 'bots',
  onWorldLoad() {
    // TODO(B-B2): ctx.services.provide(BOTS_UI_SERVICE, ...) so table forms can add "Table settings…".
  },
};
