/**
 * THE module list. Pre-populated with every planned feature; feature developers edit only
 * their own folder (the stub index.ts already exports the module referenced here).
 * Order = startup order; core first.
 */
import { coreModule } from './core';
import type { CasinoModule } from './core';
import { blackjackModule } from './games/blackjack';
import { pokerModule } from './games/poker';
import { slotsModule } from './games/slots';
import { rouletteModule } from './games/roulette';
import { crapsModule } from './games/craps';
import { baccaratModule } from './games/baccarat';
import { extrasModule } from './games/extras';
import { loanModule } from './loan';
import { chaosModule } from './chaos';
import { lastchanceModule } from './lastchance';
import { worldgenModule } from './worldgen';
import { vipModule } from './vip';
import { multiplayerModule } from './multiplayer';
import { pvpModule } from './pvp';
import { botsModule } from './bots';

export const MODULES: readonly CasinoModule[] = [
  coreModule,
  blackjackModule,
  pokerModule,
  slotsModule,
  rouletteModule,
  crapsModule,
  baccaratModule,
  extrasModule,
  loanModule,
  chaosModule,
  lastchanceModule,
  worldgenModule,
  vipModule,
  multiplayerModule,
  pvpModule,
  botsModule,
];
