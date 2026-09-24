/**
 * The "pvp" module (PVP.md): PvP hub (Casino Menu → Challenges), invite / lobby / result forms, the
 * Bedrock `PvpPresenter` (titles, action-bar ticker, chat log), `/scriptevent burmaldaholic:pvp …`,
 * rivalry display and the core PvP achievements. The engine is core (`ctx.pvp`); the modes live in the
 * modules owning their solo games (extras, slots). Owner: dev "PvP core" (pvp-bots.md §7, B-P2).
 */
import type { CasinoModule } from '../core';

export const pvpModule: CasinoModule = {
  id: 'pvp',
  onWorldLoad() {
    // TODO(B-P2): ctx.pvp.setPresenter(...); ctx.menu.add({ id: 'challenges', ... }) (takes over extras' Dice
    // Duel challenges page, which becomes the hub's "Dice Duel" button); admin action "PvP matches".
  },
};
