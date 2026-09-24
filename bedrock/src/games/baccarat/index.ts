/**
 * Baccarat module (GAME_DESIGN §20, UI.md §14).
 *  - Tables: blocks `burmaldaholic:baccarat_table` (standard), `…_high_roller` (Gold VIP, min
 *    total per coup) and `…_player_banked` (Chemin de fer, §20.9), all with the core
 *    `burmaldaholic:table` component (`game: baccarat`), and the Baccarat Dealer NPC
 *    `burmaldaholic:baccarat_dealer` (the entity hosts a table, key `npc:<id>`).
 *  - Pure rules in ./logic: card values, the §20.3 tableau, payouts, the Banker step, limits,
 *    the 12-class reservation, shoe + burn, bead plate, the table clock, chemin de fer money,
 *    exact enumeration (§20.8), the decision interface for future bots.
 *  - Runtime: ./game.ts (shared house coups, forms, leave/break/restart), ./chemmy.ts (bank).
 *
 * Config keys (`baccarat.*`) come from the generated core catalog (CONFIG.md §baccarat).
 */
import type { CasinoModule } from '../../core';
import { BACCARAT_SERVICE, type BaccaratApi } from './api';
import { BaccaratGame } from './game';

export const baccaratModule: CasinoModule = {
  id: 'baccarat',
  onWorldLoad(ctx) {
    const game = new BaccaratGame(ctx);
    game.start();
    ctx.services.provide<BaccaratApi>(BACCARAT_SERVICE, game);
  },
};
