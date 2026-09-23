/**
 * European Roulette (GAME_DESIGN §9, UI.md §7).
 *  - Tables: blocks `burmaldaholic:roulette_table` and `..._high_roller` (packs/roulette), both
 *    with the core `burmaldaholic:table` component (`game: roulette`).
 *  - Up to roulette.maxBettors players share one spin per table (betting window, Ready/Spin).
 *  - Pure rules in ./logic (bets, geometry, payouts, limits, state machine, animation).
 */
import type { CasinoModule } from '../../core';
import { ROULETTE_SERVICE, type RouletteApi } from './api';
import { RouletteGame } from './table';

export const rouletteModule: CasinoModule = {
  id: 'roulette',
  onWorldLoad(ctx) {
    const game = new RouletteGame(ctx);
    game.start();
    ctx.services.provide<RouletteApi>(ROULETTE_SERVICE, game);
  },
};
