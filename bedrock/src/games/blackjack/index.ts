/**
 * Blackjack module (owner: blackjack feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/blackjack/*.lang; assets under packs/blackjack/.
 */
import type { CasinoModule } from '../../core';

export const blackjackModule: CasinoModule = {
  id: 'blackjack',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'blackjack', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
