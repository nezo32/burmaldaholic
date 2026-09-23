/**
 * Craps module (GAME_DESIGN §10, UI.md §8): the `burmaldaholic:craps_table` block seats up to
 * `craps.seats` players; Pass / Don't Pass / Come / Don't Come / Field / Odds, come-out and
 * point state machine, shooter rotation, betting window and auto roll.
 * Rules and payouts are pure in ./logic; ./runtime drives forms, money and timers.
 */
import type { CasinoModule } from '../../core';
import { CRAPS_SERVICE, type CrapsApi } from './api';
import { CrapsRuntime } from './runtime';

export const crapsModule: CasinoModule = {
  id: 'craps',
  onWorldLoad(ctx) {
    const rt = new CrapsRuntime(ctx);
    rt.start();
    const api: CrapsApi = { onPointMade: (l) => rt.onPointMade(l) };
    ctx.services.provide(CRAPS_SERVICE, api);
  },
};
