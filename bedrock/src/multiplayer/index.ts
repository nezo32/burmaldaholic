/**
 * Multiplayer module: player-owned casinos (GAME_DESIGN §18.2, UI.md §11).
 *  - Casino Charter block (`burmaldaholic:casino_charter`): license fee, cylinder claim, charter screen.
 *  - Owned tables: every `burmaldaholic:table` block the owner places inside the claim.
 *  - Owner bankroll = core bankroll account `economy.bankroll(casino.id)`.
 *  - Public API `MULTIPLAYER_SERVICE` (see api.ts) for games: houseFor / checkTable / limitsFor.
 * Pure rules live in ./logic (claims, solvency, owner settings, statistics).
 */
import type { CasinoModule } from '../core';
import { t } from '../core';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from './api';
import { CharterUi } from './charter-ui';
import { Ownership } from './ownership';

const ownership = new Ownership();

export const multiplayerModule: CasinoModule = {
  id: 'multiplayer',
  onStartup(ctx) {
    ownership.registerComponent(ctx.event);
  },
  onWorldLoad(ctx) {
    const ui = new CharterUi(ctx, ownership);
    ownership.start(ctx, (p, c) => ui.open(p, c));
    ctx.services.provide<MultiplayerApi>(MULTIPLAYER_SERVICE, ownership);
    ctx.menu.add({
      id: 'my_casino',
      order: 60,
      label: t('gui.burmaldaholic.menu.my_casino'),
      visible: (p) => ctx.config.bool('ownership.enabled') && ownership.store.ownedBy(p.id).length > 0,
      open: (p) => ui.openMine(p),
    });
  },
};
