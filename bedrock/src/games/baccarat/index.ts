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
 * Config keys are declared here because the generated core catalog predates §20 (CONFIG.md
 * already lists them; core skips a declaration once the catalog has the key).
 */
import type { CasinoModule, ModuleConfigDef } from '../../core';
import { BACCARAT_SERVICE, type BaccaratApi } from './api';
import { BaccaratGame } from './game';

const CONFIG: readonly ModuleConfigDef[] = [
  { type: 'bool', name: 'baccarat.enabled', default: true },
  { type: 'int', name: 'baccarat.decks', default: 8, min: 1, max: 8 },
  { type: 'double', name: 'baccarat.penetration', default: 0.8, min: 0.25, max: 0.9 },
  { type: 'bool', name: 'baccarat.burnCards', default: true },
  { type: 'double', name: 'baccarat.bankerCommission', default: 0.05, min: 0, max: 0.1 },
  { type: 'int', name: 'baccarat.tiePays', default: 8, min: 8, max: 9 },
  { type: 'bool', name: 'baccarat.pairBets', default: true },
  { type: 'int', name: 'baccarat.pairPays', default: 11, min: 1, max: 12 },
  { type: 'int', name: 'baccarat.minBet', default: 1, min: 1, max: 1_000_000 },
  { type: 'double', name: 'baccarat.sideMaxFraction', default: 0.25, min: 0.01, max: 1 },
  { type: 'int', name: 'baccarat.highRollerMinTotal', default: 100, min: 1, max: 1_000_000 },
  { type: 'double', name: 'baccarat.highRollerMaxMultiplier', default: 2, min: 1, max: 10 },
  { type: 'int', name: 'baccarat.highRollerMinVipTier', default: 2, min: 0, max: 5 },
  { type: 'int', name: 'baccarat.seats', default: 7, min: 1, max: 7 },
  { type: 'int', name: 'baccarat.betTimerTicks', default: 400, min: 100, max: 2400, step: 20 },
  { type: 'int', name: 'baccarat.revealTicks', default: 80, min: 20, max: 300, step: 10 },
  { type: 'int', name: 'baccarat.historyLength', default: 60, min: 0, max: 120 },
  { type: 'int', name: 'baccarat.tieStreakChaos', default: 3, min: 0, max: 10 },
  { type: 'bool', name: 'baccarat.chemmy.enabled', default: true },
  { type: 'int', name: 'baccarat.chemmy.minBank', default: 20, min: 1, max: 1_000_000_000 },
  { type: 'double', name: 'baccarat.chemmy.rakePercent', default: 0.05, min: 0, max: 0.1 },
  { type: 'int', name: 'baccarat.chemmy.bankOfferTicks', default: 200, min: 100, max: 1200, step: 20 },
  { type: 'int', name: 'baccarat.chemmy.idleTicks', default: 600, min: 100, max: 6000, step: 20 },
  { type: 'bool', name: 'baccarat.chemmy.houseCoupWhenNoBanker', default: true },
];

export const baccaratModule: CasinoModule = {
  id: 'baccarat',
  config: CONFIG,
  onWorldLoad(ctx) {
    const game = new BaccaratGame(ctx);
    game.start();
    ctx.services.provide<BaccaratApi>(BACCARAT_SERVICE, game);
  },
};
